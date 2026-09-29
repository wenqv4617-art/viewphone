/**
 * 本地门禁运行器（`pnpm verify` / `.githooks/pre-push` 都调它）
 *
 * 为什么它是**当前唯一真正的门禁**：
 *   本仓库目前没有任何 git remote，云端 CI 触发不了。
 *   门禁的价值在于"每次提交都跑"，不依赖 GitHub 是否可达。
 *
 * 关卡（顺序执行，**全部跑完**再汇总；任一失败即非 0 退出）：
 *   1. build（含 :shared:jvmTest、:shared:jsTest、三 target 编译）
 *   2. kernel:assemble（装配 web/.kernel，并校验 .d.mts 导出集合与实际导出一致）
 *   3. web typecheck（tsc --noEmit，证明 TS 拿到强类型）
 *   4. web probe（真实 import + 调用 + 断言）
 *   5. repo-bloat gate（构建产物/依赖/缓存/密钥不得入库）
 *
 * 用法：
 *   node tools/verify/verify.mjs
 *   node tools/verify/verify.mjs --only=typecheck,probe   # 快速自测用
 *
 * 失败语义：打印每一关的命令、退出码、耗时与日志路径；以 1 退出。
 *
 * ⚠️ 实现注意（踩过的坑，别改回去）：
 *   不要用 `cmd /c "<整条命令行>"` 的形式生成子进程 ——
 *   在本机某个宿主里，cmd.exe 收不到整条命令行（症状是 exit code 9009）。
 *   这里用 spawnSync + shell，让 shell 自己解析，并把工作目录交给 spawn 的 cwd。
 */

import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { delimiter, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const logDir = join(repoRoot, '.buildlogs', 'verify')
mkdirSync(logDir, { recursive: true })

/* 精确过滤 Node 的 DEP0190 告警。
 *
 * 为什么需要：gradlew 是 .bat/.sh 包装脚本，Windows 上必须经 shell 才能执行，
 * 而 Node 对 "shell: true + 参数数组" 会打印 DEP0190（鼓励改成单字符串命令行）。
 * 我们的参数全部是硬编码常量（不含任何外部输入），因此该告警在本场景是误报；
 * 但它的文案容易让人以为门禁有问题。
 *
 * 明确只过滤这一条：其他任何 stderr 都会原样保留并写入日志。
 */
function stripKnownNoise(text) {
  return text
    .split(/\r?\n/)
    .filter(
      (line) =>
        !line.includes('DEP0190') &&
        !line.includes('Passing args to a child process with shell option true') &&
        !line.includes('trace-deprecation'),
    )
    .join('\n')
}

/**
 * 解析可执行文件的真实路径。
 *
 * 为什么需要：Windows 上 `pnpm` / `tsc` 实际是 `.cmd`，`shell: false` 时
 * spawnSync 无法直接执行（返回 error、退出码 -1）；而全局开 `shell: true`
 * 又会触发 Node 的 DEP0190 告警，并让参数被 shell 二次解析。
 * 这里按 PATH（以及显式额外目录）逐目录探测扩展名，命中即用绝对路径。
 */
function which(cmd, extraDirs = []) {
  if (cmd.includes('/') || cmd.includes('\\')) return cmd
  const exts = process.platform === 'win32' ? ['.cmd', '.exe', '.bat', ''] : ['']
  const dirs = [...extraDirs, ...(process.env.PATH ?? '').split(delimiter)]
  for (const dir of dirs) {
    if (!dir) continue
    for (const ext of exts) {
      const candidate = join(dir, cmd + ext)
      if (existsSync(candidate)) return candidate
    }
  }
  return cmd // 交给系统报错，日志里能看到
}

const onlyArg = process.argv.find((a) => a.startsWith('--only='))
const only = onlyArg ? onlyArg.slice('--only='.length).split(',').map((s) => s.trim()) : null

const isWindows = process.platform === 'win32'
const gradlew = isWindows ? 'gradlew.bat' : './gradlew'

/** 所有关卡。`id` 用于 --only 过滤；`shell` 只在确实需要时开启（见 run() 注释）。 */
const STEPS = [
  {
    id: 'gradle',
    title: 'Gradle 全量构建（含 :shared:jvmTest / :shared:jsTest / 三 target）',
    cmd: gradlew,
    args: ['build', ':shared:jvmTest', ':shared:jsTest', ':shared:compileProductionLibraryKotlinJs', '--console=plain'],
    // 仅此关需要 shell：gradlew 是 .bat/.sh 包装脚本，Windows 上无法直接 CreateProcess
    shell: true,
  },
  {
    id: 'kernel',
    title: '装配内核产物（web/.kernel）+ 校验 .d.mts 导出集合',
    cmd: process.execPath,
    args: ['web/probe-web/scripts/build-kernel.mjs'],
  },
  {
    id: 'typecheck',
    title: 'Web 类型检查（tsc --noEmit）',
    // 用 node 直接跑 typescript 的入口脚本，不经 .bin/tsc.cmd 包装器：
    // ① 更快（少一层进程）；② Windows 上 spawnSync 执行 .cmd 不可靠（实测退出码 -1）。
    // typescript 的 bin/tsc 内容就是 `import "../lib/tsc.js"`，可以这样直接调用。
    cmd: process.execPath,
    args: [
      join(repoRoot, 'web', 'probe-web', 'node_modules', 'typescript', 'bin', 'tsc'),
      '--noEmit',
      '--project',
      join(repoRoot, 'web', 'probe-web'),
    ],
  },
  {
    id: 'probe',
    title: 'Web 真实调用内核（bundle 后从仓库根执行，import + 调用 + 断言）',
    cmd: process.execPath,
    args: ['web/probe-web/scripts/run-probe.mjs'],
  },
  {
    id: 'bloat',
    title: '仓库膨胀门禁（产物/依赖/缓存/密钥不得入库）',
    cmd: process.execPath,
    args: ['tools/ci/check-repo-bloat.mjs'],
  },
]

// ---- 环境解析：优先显式环境变量，其次本机已知位置（让钩子在新 shell 里也能跑）----
//
// 为什么需要兜底：pre-push 钩子由 git 拉起，**继承不到你当前 shell 里手工 export 的变量**。
// 如果门禁因此死在"环境未设置"，那它作为门禁就是不可用的。
// 原则：能在本机可靠发现就自动发现，发现不了才失败，并把怎么设置写清楚。
{
  if (!process.env.JAVA_HOME) {
    // ① 本机 Gradle 配置里已经固定了 JDK（见 docs/P0-TOOLCHAIN-BASELINE.md §六）
    const userGradleProps = join(process.env.USERPROFILE ?? process.env.HOME ?? '', '.gradle', 'gradle.properties')
    if (existsSync(userGradleProps)) {
      const m = /^org\.gradle\.java\.home\s*=\s*(.+)$/m.exec(readFileSync(userGradleProps, 'utf8'))
      if (m && existsSync(m[1].trim())) {
        process.env.JAVA_HOME = m[1].trim()
      }
    }
    // ② 本机唯一已知 JDK（Android Studio 自带 JBR21）
    if (!process.env.JAVA_HOME && existsSync('D:\\AndroidStdio\\jbr')) {
      process.env.JAVA_HOME = 'D:\\AndroidStdio\\jbr'
    }
  }
  if (!process.env.ANDROID_HOME && !process.env.ANDROID_SDK_ROOT) {
    const sdk = join(process.env.LOCALAPPDATA ?? '', 'Android', 'Sdk')
    if (existsSync(sdk)) process.env.ANDROID_HOME = sdk
  }
}

const preflight = []
if (!only) {
  if (!process.env.JAVA_HOME) {
    preflight.push(
      '找不到 JDK：请设置 JAVA_HOME，或在本机 %USERPROFILE%\\.gradle\\gradle.properties 写 org.gradle.java.home（见 docs/P0-TOOLCHAIN-BASELINE.md §六）',
    )
  }
  if (!process.env.ANDROID_HOME && !process.env.ANDROID_SDK_ROOT) {
    preflight.push(
      '找不到 Android SDK：请设置 ANDROID_HOME（CI 上必须显式设置，见 docs/P0-TOOLCHAIN-BASELINE.md §3.3）',
    )
  }
}
if (!existsSync(join(repoRoot, 'gradlew')) || !existsSync(join(repoRoot, 'gradlew.bat'))) {
  preflight.push('gradlew / gradlew.bat 缺失')
}

const results = []

function run(step) {
  const started = Date.now()
  process.stdout.write(`\n${'='.repeat(72)}\n▶ ${step.title}\n  $ ${step.cmd} ${step.args.join(' ')}\n${'='.repeat(72)}\n`)

  // shell 只在 gradlew 那一关开：它是 .bat/.sh 包装脚本，Windows 上必须经 shell。
  // 其余关卡直接用可执行文件（node / pnpm），既避免 Node 的 DEP0190 告警，
  // 也避免参数被 shell 二次解析。
  const r = spawnSync(step.cmd, step.args, {
    cwd: repoRoot,
    shell: step.shell === true,
    stdio: 'pipe',
    encoding: 'utf8',
    env: process.env,
    maxBuffer: 64 * 1024 * 1024,
  })

  const seconds = ((Date.now() - started) / 1000).toFixed(1)
  const out = stripKnownNoise((r.stdout ?? '') + (r.stderr ?? ''))
  const logPath = join(logDir, `${step.id}.log`)
  writeFileSync(logPath, out, 'utf8')

  if (r.error) process.stdout.write(`  (启动错误: ${r.error.message})\n`)

  // 打印尾部，控制台不刷屏；完整输出在日志里
  const lines = out.split(/\r?\n/).filter((l) => l.length > 0)
  const tail = lines.slice(-12)
  if (tail.length > 0) process.stdout.write(tail.join('\n') + '\n')

  const code = r.status === null ? -1 : r.status
  const ok = code === 0
  results.push({ id: step.id, title: step.title, ok, code, seconds, logPath })

  // 关键失败特征识别：让"Node 分发包下载失败"这类问题一眼可辨
  if (!ok) {
    const hit = /Could not (download|GET|resolve).*node|nodejs\.org|Distributions at https:\/\/nodejs\.org/i.test(out)
    if (hit) {
      process.stdout.write(
        '\n⚠️ 失败特征匹配「Node 分发包下载失败」：\n' +
          '   内核的 Node 运行环境由 KGP 从 nodejs.org 下载（版本已锁定 24.16.0，但下载源是外部依赖）。\n' +
          '   处理：确认网络可达 https://nodejs.org/dist ；重试；不要把下载源改成自建分发。\n' +
          '   详见 docs/DECISIONS.md DEC-011 与 docs/P0-TOOLCHAIN-BASELINE.md §2.6。\n',
      )
    }
  }
  return ok
}

console.log('微光机 ViewPhone · 本地门禁 verify')
console.log(`仓库根: ${repoRoot}`)
console.log(`平台: ${process.platform}  Node: ${process.version}`)

if (preflight.length > 0) {
  console.error('\n✗ 环境前置检查未通过：')
  for (const p of preflight) console.error(`    - ${p}`)
  process.exit(2)
}

const steps = only ? STEPS.filter((s) => only.includes(s.id)) : STEPS
if (steps.length === 0) {
  console.error(`--only 没有匹配到任何关卡：${only?.join(', ')}`)
  process.exit(2)
}

let failed = 0
for (const step of steps) {
  if (!run(step)) failed++
}

console.log(`\n${'='.repeat(72)}`)
console.log('门禁汇总')
console.log('='.repeat(72))
for (const r of results) {
  const mark = r.ok ? '✓ 通过' : '✗ 失败'
  console.log(`  ${mark}  ${r.id.padEnd(10)} ${r.seconds.padStart(6)}s  ${r.title}`)
  if (!r.ok) console.log(`        退出码 ${r.code}，日志: ${r.logPath}`)
}

if (failed > 0) {
  console.error(`\n✗ 门禁未通过：${failed}/${results.length} 关失败（红灯不合并）`)
  process.exit(1)
}
console.log(`\n✓ 门禁全部通过：${results.length}/${results.length}`)
