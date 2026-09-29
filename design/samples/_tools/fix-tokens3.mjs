// 按括号轨迹定位并删除多余的顶层闭合
import { readFileSync, writeFileSync } from 'node:fs'
const p = 'D:/deepseek/viewphone/design/tokens.json'
const lines = readFileSync(p, 'utf8').split(/\r?\n/)

// 逐行算深度（只统计 {} 与 []）
let depth = 0
const marks = []
for (let i = 0; i < lines.length; i++) {
  const before = depth
  for (const ch of lines[i]) {
    if (ch === '{' || ch === '[') depth++
    else if (ch === '}' || ch === ']') depth--
  }
  marks.push({ i, before, after: depth, text: lines[i] })
  if (depth === 0 && i < lines.length - 1) {
    // 根对象在此行闭合；若后面还有非空内容，说明这里多了一个闭合
    const rest = lines.slice(i + 1).filter((l) => l.trim() !== '')
    if (rest.length > 0) {
      console.log(`根对象在第 ${i + 1} 行提前闭合，但后面还有 ${rest.length} 行非空内容：`)
      console.log(`  第 ${i + 1} 行: ${JSON.stringify(lines[i])}`)
      console.log(`  下一非空行: ${JSON.stringify(rest[0])}`)
      if (lines[i].trim() === '},') {
        lines.splice(i, 1)
        console.log('→ 已删除这一行多余的 "},"')
      } else if (lines[i].trim() === '}') {
        lines.splice(i, 1)
        console.log('→ 已删除这一行多余的 "}"')
      } else {
        console.log('→ 该行不是纯闭合，需人工处理')
        process.exit(1)
      }
      break
    }
  }
}

const out = lines.join('\n')
try {
  const j = JSON.parse(out)
  writeFileSync(p, out, 'utf8')
  console.log('✓ 修复后 JSON 语法 OK')
  console.log('顶层键:', Object.keys(j).join(', '))
  console.log('color 子键:', Object.keys(j.color).join(', '))
} catch (e) {
  console.log('✗ 修复后仍失败:', e.message)
}
