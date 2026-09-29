/**
 * 跑 Web 消费端验证（probe）：**先 bundle，再在仓库根执行**
 *
 * 为什么不在原地直接 `node src/probe.ts`：
 *   probe.ts 里 `import ... from '@viewphone/shared'` 的解析依赖**文件所在目录**，
 *   而门禁运行器是从仓库根调用的。为了不依赖"跨目录模块解析"这种隐式行为，
 *   这里先用 esbuild 把 probe 打成**自包含**的单文件，再从仓库根用 node 执行。
 *
 * 副作用（正向）：这条路径与真实 Web 构建（Vite/esbuild 打包）一致，
 *   等于顺带验证了"内核能被正常打包进产物"。
 *
 * 失败语义：bundle 失败或断言失败都以非 0 退出，不吞错。
 */

import { spawnSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

const here = dirname(fileURLToPath(import.meta.url))
const pkgRoot = resolve(here, '..')
const repoRoot = resolve(pkgRoot, '..', '..')
const outDir = join(pkgRoot, '.bundle-out')
mkdirSync(outDir, { recursive: true })

const outfile = join(outDir, 'probe.bundle.mjs')

await build({
  entryPoints: [join(pkgRoot, 'src', 'probe.ts')],
  outfile,
  bundle: true,
  format: 'esm',
  platform: 'node',
  target: 'es2022',
  // probe 用到的 node 内建模块保持 external（不打包进 bundle）
  external: ['node:*'],
  logLevel: 'silent',
})

// 从仓库根执行自包含产物（cwd = 仓库根，可复现于任何调用方）
const r = spawnSync(process.execPath, [outfile], {
  cwd: repoRoot,
  stdio: 'inherit',
  env: process.env,
})

process.exit(r.status === null ? 1 : r.status)
