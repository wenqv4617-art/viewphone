import { readFileSync } from 'node:fs'
const raw = readFileSync('D:/deepseek/viewphone/design/tokens.json', 'utf8')
try {
  JSON.parse(raw)
  console.log('JSON OK')
} catch (e) {
  const m = /position (\d+)/.exec(e.message)
  const pos = m ? Number(m[1]) : 0
  console.log('错误:', e.message)
  const before = raw.slice(0, pos)
  const line = before.split(/\r?\n/).length
  const col = pos - before.lastIndexOf('\n')
  console.log(`位置 ${pos} → 第 ${line} 行 第 ${col} 列`)
  const all = raw.split(/\r?\n/)
  for (let i = Math.max(0, line - 8); i < Math.min(all.length, line + 4); i++) {
    console.log(`${String(i + 1).padStart(3)}${i + 1 === line ? ' >>' : '   '} ${all[i]}`)
  }
}
