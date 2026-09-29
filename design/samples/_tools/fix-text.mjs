// #6B7280 对底色 #F5F6F8 只有 4.47:1（差 0.03）→ 给出微调候选
const toLin = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
const hexToRgb = (h) => [0, 2, 4].map((i) => parseInt(h.replace('#', '').slice(i, i + 2), 16) / 255)
const relLum = (hex) => {
  const [r, g, b] = hexToRgb(hex)
  return 0.2126 * toLin(r) + 0.7152 * toLin(g) + 0.0722 * toLin(b)
}
const contrast = (a, b) => {
  const la = relLum(a), lb = relLum(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

console.log('=== 候选：让 #6B7280 系在「表面」与「底色」上都 ≥4.5:1 ===\n')
console.log('候选       对 #FFFFFF   对 #F5F6F8   判定')
const cands = ['#6B7280', '#686F7B', '#666D78', '#646B76', '#626974', '#606772']
for (const c of cands) {
  const a = contrast(c, '#FFFFFF')
  const b = contrast(c, '#F5F6F8')
  const ok = a >= 4.5 && b >= 4.5
  console.log(`${c}   ${a.toFixed(2).padStart(5)}:1     ${b.toFixed(2).padStart(5)}:1    ${ok ? '✅ 两底都达标' : '❌ 底色不达标'}`)
}

console.log('\n=== 三级/装饰色（禁用于文字）===')
for (const c of ['#9AA2AF', '#8E96A3', '#868E9B']) {
  console.log(`${c}  对 #FFFFFF ${contrast(c, '#FFFFFF').toFixed(2)}:1   对 #F5F6F8 ${contrast(c, '#F5F6F8').toFixed(2)}:1`)
}
