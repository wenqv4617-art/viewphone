/**
 * 安装本地门禁钩子（**只作用于本仓库**）
 *
 * 做的事仅一件：`git config --local core.hooksPath .githooks`
 *   - 用 `--local`，绝不碰 `--global`；
 *   - 若仓库里**已存在**其他 pre-push（例如 `.git/hooks/pre-push`），
 *     默认**拒绝安装**并列出现有文件，避免静默覆盖别人的钩子；
 *     确需覆盖时加 `--force`。
 *
 * 用法：
 *   node tools/verify/install-hooks.mjs
 *   node tools/verify/install-hooks.mjs --force
 *   node tools/verify/install-hooks.mjs --uninstall
 */

import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const force = process.argv.includes('--force')
const uninstall = process.argv.includes('--uninstall')

function git(args, opts = {}) {
  return execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8', ...opts }).trim()
}

const current = (() => {
  try {
    return git(['config', '--local', '--get', 'core.hooksPath'])
  } catch {
    return ''
  }
})()

if (uninstall) {
  if (!current) {
    console.log('本仓库未设置 core.hooksPath，无需卸载。')
    process.exit(0)
  }
  git(['config', '--local', '--unset', 'core.hooksPath'])
  console.log(`已卸载：core.hooksPath（原值 ${current}）`)
  process.exit(0)
}

// ---- 冲突检测：仓库里是否已有其他 pre-push ----
const conflicts = []
const gitHooksDir = (() => {
  try {
    const p = git(['rev-parse', '--git-path', 'hooks'])
    return resolve(repoRoot, p)
  } catch {
    return join(repoRoot, '.git', 'hooks')
  }
})()

const candidates = []
if (current && current !== '.githooks') {
  candidates.push(join(resolve(repoRoot, current), 'pre-push'))
}
candidates.push(join(gitHooksDir, 'pre-push'))

for (const p of candidates) {
  if (existsSync(p)) {
    let preview = ''
    try {
      preview = readFileSync(p, 'utf8').split(/\r?\n/).slice(0, 3).join(' | ')
    } catch {
      preview = '(无法读取)'
    }
    // 我们自己的钩子不算冲突
    if (!preview.includes('ViewPhone') && !preview.includes('tools/verify/verify.mjs')) {
      conflicts.push({ path: p, preview })
    }
  }
}

if (conflicts.length > 0 && !force) {
  console.error('✗ 检测到本仓库已存在的 pre-push 钩子，**未做任何修改**：')
  for (const c of conflicts) {
    console.error(`    - ${c.path}`)
    console.error(`      开头: ${c.preview}`)
  }
  console.error('\n请手工合并，或确需覆盖时重跑：node tools/verify/install-hooks.mjs --force')
  process.exit(1)
}

git(['config', '--local', 'core.hooksPath', '.githooks'])
console.log('✓ 已安装本地门禁钩子（仅本仓库）')
console.log(`  core.hooksPath = ${git(['config', '--local', '--get', 'core.hooksPath'])}`)
console.log('  生效的钩子文件：.githooks/pre-push → node tools/verify/verify.mjs')
console.log('  未改动任何全局 git 配置。')
console.log('')
console.log('  手动跑门禁：pnpm verify')
console.log('  卸载：      node tools/verify/install-hooks.mjs --uninstall')
