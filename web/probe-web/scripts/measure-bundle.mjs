/**
 * R1 验收项 4 · 内核 JS 体积实测
 *
 * 为什么要单独测：
 *   内核的 JS 体积会直接变成 Web 首屏预算的输入。P1 的编译器/记忆算法都加进来后
 *   只会更大，所以**现在**就要知道基线数字，而不是等到 P6 才发现装不下。
 *
 * 四个口径，缺一不可：
 *   1. entry-only  ：只打内核入口，看内核自身开销（esbuild bundle）
 *   2. minify      ：压缩后 + gzip 后体积（真实网络传输量）
 *   3. 逐文件明细  ：kotlin-stdlib 占多少（这是当前的大头）
 *   4. 裸最小场景  ：直接复制 3 个 .mjs 文件的总字节数（不经任何打包器）
 *
 * 失败语义：打包失败直接抛错并以非 0 退出，不产出"猜的数字"。
 */

import { gzipSync } from 'node:zlib'
import { readFile, rm, writeFile, mkdir, stat } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

const here = dirname(fileURLToPath(import.meta.url))
const pkgRoot = resolve(here, '..')
const repoRoot = resolve(pkgRoot, '..', '..')
const outDir = join(pkgRoot, '.bundle-out')
const kernelDir = join(repoRoot, 'web', '.kernel', 'kotlin')

if (!existsSync(kernelDir)) {
  throw new Error(`找不到装配后的内核：${kernelDir}\n请先运行：node web/probe-web/scripts/build-kernel.mjs`)
}

await rm(outDir, { recursive: true, force: true })
await mkdir(outDir, { recursive: true })

// 独立入口：真的走一遍 Web 端会写的 import
const entryFile = join(outDir, 'entry.ts')
await writeFile(
  entryFile,
  [
    `import { kernel } from '@viewphone/shared'`,
    `export const version: string = kernel.kernelVersion()`,
    `export const trimmed: string = kernel.echoTrimmed('  x  ')`,
    ``,
  ].join('\n'),
  'utf8',
)

const variants = [
  { label: '未压缩', minify: false },
  { label: '压缩  ', minify: true },
]

const rows = []
for (const v of variants) {
  const outfile = join(outDir, `bundle-${v.minify ? 'min' : 'raw'}.mjs`)
  await build({
    entryPoints: [entryFile],
    outfile,
    bundle: true,
    minify: v.minify,
    format: 'esm',
    platform: 'neutral',
    target: 'es2022',
    // 关键：不把 kotlin-stdlib 设为 external，否则体积数字是假的
    external: [],
    legalComments: 'none',
    logLevel: 'silent',
  })
  const buf = await readFile(outfile)
  const gz = gzipSync(buf, { level: 9 })
  rows.push({
    变体: v.label,
    字节: buf.length,
    KiB: (buf.length / 1024).toFixed(1),
    'gzip 字节': gz.length,
    'gzip KiB': (gz.length / 1024).toFixed(1),
  })
}

console.log('[measure-bundle] 验收项 4：内核 JS 体积（esbuild bundle，**含 kotlin-stdlib**）')
console.table(rows)

// ---- 逐文件明细 ----
const files = [
  'viewphone-shared.mjs',
  'kotlin-kotlin-stdlib.mjs',
  'kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.mjs',
]
const detail = []
let total = 0
for (const f of files) {
  const p = join(kernelDir, f)
  if (!existsSync(p)) continue
  const buf = await readFile(p)
  const gz = gzipSync(buf, { level: 9 })
  total += buf.length
  detail.push({
    文件: f,
    字节: buf.length,
    KiB: (buf.length / 1024).toFixed(1),
    'gzip KiB': (gz.length / 1024).toFixed(1),
    '占合计': total ? '' : '',
  })
}
// 补占比
for (const d of detail) {
  d['占合计'] = `${((d.字节 / total) * 100).toFixed(1)}%`
}
console.log('\n[measure-bundle] 内核产物逐文件（未压缩原始大小）')
console.table(detail)
console.log(`[measure-bundle] 内核产物合计（未压缩，不含 .map）: ${total} 字节 = ${(total / 1024).toFixed(1)} KiB`)

// ---- 裸最小场景：不经打包器，把 3 个 .mjs 直接上线 ----
let raw = 0
for (const f of files) {
  const p = join(kernelDir, f)
  if (!existsSync(p)) continue
  raw += (await stat(p)).size
}
console.log(`[measure-bundle] 裸最小场景（3 个 .mjs 直接上线，未压缩）: ${raw} 字节 = ${(raw / 1024).toFixed(1)} KiB`)
