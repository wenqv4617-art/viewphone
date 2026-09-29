// 精确修复：把 color 对象里的 contrast 段替换到顶层，并修正括号层级
import { readFileSync, writeFileSync } from 'node:fs'
const p = 'D:/deepseek/viewphone/design/tokens.json'
const raw = readFileSync(p, 'utf8')
const lines = raw.split(/\r?\n/)

// 找到 "contrast": { 起始行与它的闭合行
const start = lines.findIndex((l) => l.trim() === '"contrast": {')
if (start < 0) { console.error('未找到 contrast 段'); process.exit(1) }
let depth = 0
let end = -1
for (let i = start; i < lines.length; i++) {
  for (const ch of lines[i]) {
    if (ch === '{') depth++
    if (ch === '}') depth--
  }
  if (depth === 0) { end = i; break }
}
console.log(`contrast 段：第 ${start + 1} ~ ${end + 1} 行`)

// 取出该段（含缩进调整：从 color 内部提出来，去掉 2 空格缩进）
const block = lines.slice(start, end + 1).map((l) => l.replace(/^  /, ''))
const before = lines.slice(0, start)
const after = lines.slice(end + 1)

// before 的最后一行应是 color 的闭合 "  }," —— 去掉它，改由 contrast 之后闭合
// 打印 before 末尾几行以便确认
console.log('before 末尾 3 行:', JSON.stringify(before.slice(-3)))

// 去掉 before 末尾的 "  }," （color 的闭合）
while (before.length && before[before.length - 1].trim() === '') before.pop()
const lastNonEmpty = before[before.length - 1].trim()
if (lastNonEmpty !== '},') { console.error('before 末尾不是 "},"，实际:', lastNonEmpty); process.exit(1) }
before.pop()

// 组装：before + color 闭合 + contrast 段（顶层） + after（去掉首个空行）
const out = [...before, '  },', '', ...block, ...after]
writeFileSync(p, out.join('\n'), 'utf8')
console.log('已重写')
