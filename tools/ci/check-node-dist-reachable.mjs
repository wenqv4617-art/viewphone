/**
 * 预检外部下载源可达性（Node 分发包）
 *
 * 背景：内核的 Node 运行环境由 KGP 从 **nodejs.org** 下载（版本已锁定，
 *       但**下载源本身是一个未声明的外部依赖**）。第一个 CI run 会拉约 50MB，
 *       若该域名不可达，失败会以难懂的形式出现（甚至被误读成代码问题）。
 *
 * 本脚本把这一步变成**显式、易定位的前置检查**：
 *   - 不可达时明确报 "Node 分发包下载失败" 并给出可操作提示；
 *   - 明确**不建议**改成自建分发（那会引入新的维护面与信任面）。
 *
 * 用法：
 *   node tools/ci/check-node-dist-reachable.mjs
 *   node tools/ci/check-node-dist-reachable.mjs --version 24.16.0   # 顺带校验该版本存在
 *
 * 退出码：0 = 可达/跳过；1 = 不可达（应当阻断，让失败早期、可读）。
 */

const NODE_DIST_BASE = 'https://nodejs.org/dist'
const TIMEOUT_MS = Number(process.env.VP_NODE_PREFLIGHT_TIMEOUT_MS ?? 20000)

const args = process.argv.slice(2)
const versionArgIdx = args.indexOf('--version')
const version = versionArgIdx >= 0 ? args[versionArgIdx + 1] : undefined

async function head(url) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const res = await fetch(url, { method: 'HEAD', signal: controller.signal, redirect: 'follow' })
    return { ok: res.ok, status: res.status }
  } catch (e) {
    return { ok: false, status: 0, error: e?.message ?? String(e) }
  } finally {
    clearTimeout(timer)
  }
}

const targets = [NODE_DIST_BASE + '/']
if (version) {
  // 平台无关地只校验目录存在（具体归档名随平台不同，交给 KGP 决定）
  targets.push(`${NODE_DIST_BASE}/v${version}/`)
}

let failed = false
for (const url of targets) {
  const r = await head(url)
  if (r.ok) {
    console.log(`[node-preflight] ✓ 可达 ${url} (HTTP ${r.status})`)
  } else {
    failed = true
    console.error(`[node-preflight] ✗ 不可达 ${url} (HTTP ${r.status}${r.error ? ', ' + r.error : ''})`)
  }
}

if (failed) {
  console.error('')
  console.error('[node-preflight] Node 分发包下载失败 —— 这不是代码问题。')
  console.error(`  KGP 需要从 ${NODE_DIST_BASE} 获取 Node（版本由 shared/build.gradle.kts 锁定）。`)
  console.error('  处理顺序：')
  console.error('    1) 确认网络/代理可达该域名（CI runner 通常可达，中国大陆本机常需代理）；')
  console.error('    2) 若已有本地缓存（~/.gradle/nodejs），可先离线复用，不必重下；')
  console.error('    3) 重试一次 —— 该域名偶发超时。')
  console.error('  明确不做：改成自建分发/镜像。那会引入新的维护面与信任面，')
  console.error('            且让"本地与云端同源"这条约束失效（见 docs/DECISIONS.md DEC-011）。')
  process.exit(1)
}

console.log('[node-preflight] ✓ Node 分发包源可达')
