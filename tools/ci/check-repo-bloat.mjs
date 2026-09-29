#!/usr/bin/env node
/**
 * 仓库膨胀门禁（CI 必跑）
 *
 * 目的：把"构建产物 / 依赖目录 / Gradle 缓存 / 密钥文件不得入库"从人工体检
 *       变成机器守住的规则。用户可以本地干净，但状态必须由 CI 持续保证。
 *
 * 两类检查，任一失败即非 0 退出：
 *   ① 路径黑名单：git 跟踪文件里出现禁入路径/后缀（build/、node_modules/、.gradle/、
 *      local.properties、*.jks、*.keystore、*.log 等）
 *   ② 体积上限：单文件 > 上限、或全仓跟踪体积 > 上限（防止二进制/大文件混入）
 *
 * 用法（仓库根）：
 *   node tools/ci/check-repo-bloat.mjs
 *   node tools/ci/check-repo-bloat.mjs --json     # 机器可读输出
 *
 * 失败语义：列出每一处违规（路径 + 原因 + 字节数），退出码 1。
 */

import { execFileSync } from 'node:child_process'
import { statSync } from 'node:fs'

const JSON_OUT = process.argv.includes('--json')

/** 单文件字节上限：1 MiB。超过它必须是有意为之（并在白名单里写明理由）。 */
const MAX_FILE_BYTES = 1024 * 1024
/** 全仓跟踪文件体积上限：8 MiB（当前基线约 0.5 MiB，留足文档增长空间）。 */
const MAX_REPO_BYTES = 8 * 1024 * 1024

/**
 * 路径黑名单。注意用**路径段**匹配而不是子串，
 * 避免把 docs/build-notes.md 这类正常文件误判。
 */
const FORBIDDEN = [
  { test: (p) => p.split('/').includes('build'), why: 'Gradle/构建产物目录（build/）' },
  { test: (p) => p.split('/').includes('node_modules'), why: 'npm 依赖目录（node_modules/）' },
  { test: (p) => p.split('/').includes('.gradle'), why: 'Gradle 本地缓存（.gradle/）' },
  { test: (p) => p.split('/').includes('.buildlogs'), why: '本地构建日志目录（.buildlogs/）' },
  { test: (p) => p.split('/').includes('dist'), why: '打包产物目录（dist/）' },
  { test: (p) => p.endsWith('local.properties'), why: '机器专属配置（local.properties）' },
  { test: (p) => p.endsWith('keystore.properties'), why: '签名口令文件（keystore.properties）' },
  { test: (p) => /\.(jks|keystore)$/.test(p), why: '签名密钥文件（*.jks / *.keystore）' },
  { test: (p) => /\.(apk|aab|ap_|dex)$/.test(p), why: 'Android 打包产物' },
  { test: (p) => /\.(class|aar|jar)$/.test(p) && !p.startsWith('gradle/wrapper/'), why: '编译产物（仅允许 gradle/wrapper/gradle-wrapper.jar）' },
  { test: (p) => /\.(log)$/.test(p), why: '日志文件（*.log）' },
  { test: (p) => /\.(tsbuildinfo)$/.test(p), why: 'TypeScript 增量构建缓存' },
  { test: (p) => p.startsWith('web/.kernel/'), why: '内核装配产物（由脚本生成，不入库）' },
  { test: (p) => p.split('/').includes('.bundle-out'), why: '体积测量产物目录' },
]

/** 有意例外：必须在注释里写明理由，否则不要往这里加。 */
const ALLOWLIST = new Set([
  // Gradle wrapper 的 jar 是仓库的一部分（否则 ./gradlew 不可用）。
  'gradle/wrapper/gradle-wrapper.jar',
])

function trackedFiles() {
  const out = execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  return out.split('\0').filter(Boolean)
}

const violations = []
let totalBytes = 0
let fileCount = 0

for (const p of trackedFiles()) {
  let st
  try {
    st = statSync(p)
  } catch {
    violations.push({ path: p, reason: '跟踪但文件不存在（可能是子模块或删除未暂存）', bytes: 0 })
    continue
  }
  if (!st.isFile()) continue
  fileCount++
  totalBytes += st.size

  if (!ALLOWLIST.has(p)) {
    for (const rule of FORBIDDEN) {
      if (rule.test(p)) {
        violations.push({ path: p, reason: rule.why, bytes: st.size })
        break
      }
    }
  }
  if (st.size > MAX_FILE_BYTES) {
    violations.push({ path: p, reason: `单文件超过 ${MAX_FILE_BYTES} 字节上限`, bytes: st.size })
  }
}
if (totalBytes > MAX_REPO_BYTES) {
  violations.push({
    path: '<全仓合计>',
    reason: `跟踪体积 ${totalBytes} 字节超过 ${MAX_REPO_BYTES} 字节上限`,
    bytes: totalBytes,
  })
}

const summary = {
  trackedFiles: fileCount,
  totalBytes,
  totalMiB: (totalBytes / 1024 / 1024).toFixed(2),
  maxFileBytes: MAX_FILE_BYTES,
  maxRepoBytes: MAX_REPO_BYTES,
  violations: violations.length,
}

if (JSON_OUT) {
  console.log(JSON.stringify({ summary, violations }, null, 2))
} else {
  console.log('[repo-bloat] 仓库体积体检')
  console.log(`  跟踪文件数: ${fileCount}`)
  console.log(`  合计: ${totalBytes} 字节 = ${summary.totalMiB} MiB`)
  if (violations.length === 0) {
    console.log('  ✓ 无违规：构建产物 / 依赖目录 / 缓存 / 密钥文件均未入库')
  } else {
    console.error(`\n  ✗ 发现 ${violations.length} 处违规：`)
    for (const v of violations) {
      console.error(`    - ${v.path}  (${v.bytes} 字节)  原因：${v.reason}`)
    }
  }
}

process.exit(violations.length === 0 ? 0 : 1)
