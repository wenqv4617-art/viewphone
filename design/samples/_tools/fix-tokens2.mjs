// 用"正则定位 + 单次替换"修好 color 段的闭合（避免手工改括号）
import { readFileSync, writeFileSync } from 'node:fs'
const p = 'D:/deepseek/viewphone/design/tokens.json'
let raw = readFileSync(p, 'utf8')

// 目标：让 color 对象只在自己结尾处闭合一次，contrast 位于顶层
// 做法：删掉多余的 "  },"（出现在 contrast 段之前、紧跟在 app-tiles 之后的那一个）
raw = raw.replace(/("gallery"[\s\S]*?"settings"[^\n]*\n)    \}\n  \},\n\n  "contrast": \{/, '$1    }\n  },\n\n  "contrast": {')
// 再确保 contrast 段之后只有一个顶层闭合
raw = raw.replace(/(\n  \],\n  \}\n)\},\n\n"typography"/, '$1\n\n"typography"')

writeFileSync(p, raw, 'utf8')

// 立即校验
try {
  const j = JSON.parse(raw)
  console.log('✓ JSON 语法 OK')
  console.log('顶层键:', Object.keys(j).join(', '))
} catch (e) {
  console.log('✗ 仍失败:', e.message)
  const m = /position (\d+)/.exec(e.message)
  if (m) {
    const pos = Number(m[1])
    const all = raw.split(/\r?\n/)
    const ln = raw.slice(0, pos).split(/\r?\n/).length
    for (let i = Math.max(0, ln - 6); i < Math.min(all.length, ln + 3); i++) {
      console.log(`${String(i + 1).padStart(3)}${i + 1 === ln ? ' >>' : '   '} ${all[i]}`)
    }
  }
}
