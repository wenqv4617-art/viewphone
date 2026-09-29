// 校验 tokens.json 语法 + 关键值
import { readFileSync } from 'node:fs'
const j = JSON.parse(readFileSync('D:/deepseek/viewphone/design/tokens.json', 'utf8'))
console.log('JSON 语法: OK')
console.log('顶层键:', Object.keys(j).join(', '))
console.log('强调色:', j.color.light.accent, '| glow-alpha:', j.color.light['glow-alpha'])
console.log('用户气泡:', j.color.chat['bubble-user-bg'], '/', j.color.chat['bubble-user-text'])
console.log('#9AA2AF 规则:', j.color.light['text-tertiary-decorative-only'].rule.slice(0, 30) + '...')
console.log('8 套色块:')
for (const [k, v] of Object.entries(j.color['app-tiles'])) {
  if (typeof v !== 'object' || !v.bg) continue
  console.log(`  ${k.padEnd(10)} ${v.bg} / ${v.ink}  ${v.contrast}${v.changedFrom ? '  [已修正]' : ''}`)
}
console.log('对比度表条数:', j.contrast.pairs.length, '| 未达标:', j.contrast.pairs.filter((p) => !p.pass).length)
console.log('未达标项:', j.contrast.pairs.filter((p) => !p.pass).map((p) => p.scene).join(' / '))
console.log('角标槽位:', j.radius ? (j.icons.appEntrySet ? Object.keys(j.icons.appEntrySet).length + ' 个入口定义' : '') : '')
