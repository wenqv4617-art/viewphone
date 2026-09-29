/**
 * web/probe-web · 把 Kotlin/JS 的 **ESM 生产库产物**装配成一个稳定路径的 npm 包。
 *
 * 为什么要这一步（而不是让 pnpm 直接指向 shared/build 里的构建目录）：
 *   Kotlin/JS 的产物落在 `shared/build/compileSync/js/main/productionLibrary/kotlin/`，
 *   该路径属于 build 目录（不入库、会被 clean 掉）。Web 端要可靠地 import 它，
 *   需要一个稳定、可被 pnpm workspace 引用的包目录，因此这里做一次**显式装配**，
 *   产出 `web/.kernel/`，并把真实来源与内容哈希写进 package.json，便于追溯。
 *
 * 本脚本产出的三种消费形态（都保留，作为"TS 侧调用是否别扭"的判断依据）：
 *   A. `import { kernelVersion } from '@viewphone/shared/kotlin/viewphone-shared.mjs'`
 *      —— 直接吃 Kotlin 产出的扁平 ESM（零封装，但路径长）
 *   B. `import { kernel } from '@viewphone/shared'`
 *      —— 经本脚本生成的 thin adapter（推荐：路径短 + 聚合成一个对象）
 *   C. `import { com } from '@viewphone/shared/kernel-namespace.mjs'`
 *      —— 兼容 UMD 时代的 `com.viewphone.shared.api.xxx` 命名空间写法（仅适配层）
 *
 * 注意：`index.d.mts` 是**手写**的类型声明文件，随内核导出面变化需同步更新；
 *       它是 "TS 能否拿到强类型" 的验收载体（不是 any 兜底）。
 *
 * 失败语义：任何一步缺失都直接抛错并以非 0 退出（不允许静默产出半个包）。
 */

import { createHash } from 'node:crypto'
import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..', '..')
const kotlinOut = join(repoRoot, 'shared', 'build', 'compileSync', 'js', 'main', 'productionLibrary', 'kotlin')
const target = join(repoRoot, 'web', '.kernel')

if (!existsSync(kotlinOut)) {
  throw new Error(
    `找不到 Kotlin/JS 生产库产物：${kotlinOut}\n` +
      `请先执行：./gradlew :shared:compileProductionLibraryKotlinJs`,
  )
}

await rm(target, { recursive: true, force: true })
await mkdir(join(target, 'kotlin'), { recursive: true })
await cp(kotlinOut, join(target, 'kotlin'), { recursive: true })

// ---- 关键：kotlin/ 目录下必须有自己的 package.json 标记 ESM ----
// 否则 Node 会把 .js 当 CJS 解析，报 "Unexpected token 'export'"。
await writeFile(
  join(target, 'kotlin', 'package.json'),
  JSON.stringify({ type: 'module' }, null, 2) + '\n',
  'utf8',
)

// ---- 生成 thin adapter（形态 B / C）----
// Kotlin 的顶层导出名，随导出面变化需同步；这里显式列出，缺失会立刻暴露。
const exportedFns = ['kernelVersion', 'echoTrimmed']

// ============================================================
// 机器校验：Kotlin 生成的 .d.mts 导出集合 == 装配层声明的导出集合
//
// 为什么必须有它：`.d.mts` 由 Kotlin 生成、adapter 的导出名由本脚本硬编码，
// 两者一旦漂移，TS 侧会**静默失去类型或拿到不存在的符号**（priblem 只在运行时才暴露）。
// 验收要求明确写着"不允许靠人记得同步"，所以这里做成硬失败。
//
// 实现（Kotlin 2.4.20 ESM 实际产物形态，已实测）：
//   export declare function kernelVersion(): string;
//   export declare function echoTrimmed(input: string): string;
// 即"扁平顶层具名导出"，没有命名空间包装。
// ============================================================
const dtsPath = join(target, 'kotlin', 'viewphone-shared.d.mts')
const dtsLegacyPath = join(target, 'kotlin', 'viewphone-shared.d.ts')
const actualDtsPath = existsSync(dtsPath) ? dtsPath : dtsLegacyPath
if (!existsSync(actualDtsPath)) {
  throw new Error(
    `找不到 Kotlin 生成的类型声明（已找 ${dtsPath} 与 ${dtsLegacyPath}）。\n` +
      `请确认 shared/build.gradle.kts 里 js { generateTypeScriptDefinitions() } 已开启并重新构建。`,
  )
}

const dtsText = await readFile(actualDtsPath, 'utf8')
const declared = new Set()
for (const m of dtsText.matchAll(/export\s+declare\s+(?:function|const|let|var|class|abstract class)\s+([A-Za-z_$][\w$]*)/g)) {
  declared.add(m[1])
}
// `export { a, b }` 形式（不同 Kotlin 版本可能产生）
for (const m of dtsText.matchAll(/export\s*\{([^}]*)\}/g)) {
  for (const part of m[1].split(',')) {
    const name = part.trim().split(/\s+as\s+/).pop()?.trim()
    if (name && /^[A-Za-z_$][\w$]*$/.test(name)) declared.add(name)
  }
}

const declaredSorted = [...declared].sort()
const expectedSorted = [...exportedFns].sort()
const expectedSet = new Set(expectedSorted)

const missingInJs = expectedSorted.filter((n) => !declared.has(n))
const missingInAdapter = declaredSorted.filter((n) => !expectedSet.has(n))

if (missingInJs.length > 0 || missingInAdapter.length > 0) {
  const lines = [
    '导出面不一致（机器校验失败）—— 不要靠人记得同步，请修正后再提交：',
    `  Kotlin 生成的类型声明: ${actualDtsPath}`,
    `  声明中实际导出 (${declaredSorted.length}): ${declaredSorted.join(', ') || '(无)'}`,
    `  装配层声明导出 (${expectedSorted.length}): ${expectedSorted.join(', ') || '(无)'}`,
  ]
  if (missingInJs.length > 0) {
    lines.push(`  ✗ 装配层声明了但 Kotlin 未导出: ${missingInJs.join(', ')}`)
    lines.push('     → 检查 shared/src/jsMain 里是否漏了 @JsExport 或函数名拼写。')
  }
  if (missingInAdapter.length > 0) {
    lines.push(`  ✗ Kotlin 已导出但装配层未登记: ${missingInAdapter.join(', ')}`)
    lines.push('     → 把新入口加进本脚本的 exportedFns，并同步 index.d.mts 与 KDoc 的导出面登记。')
  }
  throw new Error(lines.join('\n'))
}

console.log(`[build-kernel] 导出面校验通过：${declaredSorted.length} 个（${declaredSorted.join(', ')}）`)

const adapter = `// 由 web/probe-web/scripts/build-kernel.mjs 生成，勿手改。
// 作用：把 Kotlin/JS 的扁平 ESM 导出，聚合成 Web 端更好用的形态。
import * as flat from './kotlin/viewphone-shared.mjs'

/** 扁平导出（与 Kotlin 顶层导出一一对应） */
export const { ${exportedFns.join(', ')} } = flat

/** 聚合对象形态（形态 B） */
export const kernel = {
${exportedFns.map((f) => `  ${f}: flat.${f},`).join('\n')}
}

/** 命名空间形态（形态 C，兼容 UMD 时代的写法） */
export const com = { viewphone: { shared: { api: kernel } } }

export default kernel
`

await writeFile(join(target, 'index.mjs'), adapter, 'utf8')

// ---- 手写的强类型声明（形态 B 的验收载体）----
const dts = `// 由 build-kernel.mjs 一并维护；与 Kotlin 导出面必须一一对应。
export declare function kernelVersion(): string
export declare function echoTrimmed(input: string): string

export declare const kernel: {
  kernelVersion: () => string
  echoTrimmed: (input: string) => string
}

export declare const com: {
  viewphone: { shared: { api: typeof kernel } }
}

export default kernel
`

await writeFile(join(target, 'index.d.mts'), dts, 'utf8')

// ---- 内容哈希（可追溯 + 防手改）----
const files = (await readdir(join(target, 'kotlin'))).filter((f) => !f.endsWith('.map')).sort()
const hashes = {}
for (const f of files) {
  const buf = await readFile(join(target, 'kotlin', f))
  hashes[f] = createHash('sha256').update(buf).digest('hex').slice(0, 16)
}

const pkg = {
  name: '@viewphone/shared',
  version: '0.0.1-p0',
  private: true,
  type: 'module',
  description: 'Kotlin/JS 共享内核的装配产物（由 web/probe-web/scripts/build-kernel.mjs 生成，勿手改）',
  exports: {
    '.': {
      types: './index.d.mts',
      import: './index.mjs',
      default: './index.mjs',
    },
    // 形态 A：直接吃 Kotlin 扁平 ESM
    './kotlin/viewphone-shared.mjs': './kotlin/viewphone-shared.mjs',
  },
  viewphoneBuild: {
    generatedBy: 'web/probe-web/scripts/build-kernel.mjs',
    sourceDir: 'shared/build/compileSync/js/main/productionLibrary/kotlin',
    moduleFormat: 'esm',
    kotlinTopLevelExports: exportedFns,
    files: hashes,
  },
}

await writeFile(join(target, 'package.json'), JSON.stringify(pkg, null, 2) + '\n', 'utf8')

console.log('[build-kernel] 装配完成')
console.log('  来源:', kotlinOut)
console.log('  目标:', target)
console.log('  Kotlin 顶层导出:', exportedFns.join(', '))
for (const [f, h] of Object.entries(hashes)) {
  console.log(`  ${f}  sha256:${h}`)
}
