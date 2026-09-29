import { readFileSync } from 'node:fs'
const raw = readFileSync('D:/deepseek/viewphone/design/tokens.json', 'utf8')
const lines = raw.split(/\r?\n/)
let depth = 0
const trace = []
for (let i = 0; i < lines.length; i++) {
  const l = lines[i]
  for (const ch of l) {
    if (ch === '{') depth++
    if (ch === '}') depth--
    if (ch === '[') depth++
    if (ch === ']') depth--
  }
  trace.push({ line: i + 1, depth, text: l.trim().slice(0, 60) })
  if (depth < 0) { console.log('深度变负于第', i + 1, '行:', l.trim()); break }
}
console.log('最终深度:', depth, '（应为 0）')
console.log('\n每行深度（只看 depth 变化处）:')
let prev = 0
for (const t of trace) {
  if (t.depth !== prev) { console.log(`  L${String(t.line).padStart(3)}  ${prev} -> ${t.depth}   ${t.text}`); prev = t.depth }
}
