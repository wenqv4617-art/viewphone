// 三处色值修正：character / world / gallery 的 L 压到 ≤94，彩度取各色相"不显艳上限"
// 其余 5 套采信用户 hex，不动。
const toLin = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
const toSrgb = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)
const hexToRgb = (h) => [0, 2, 4].map((i) => parseInt(h.replace('#', '').slice(i, i + 2), 16) / 255)
function rgbToOklab([r, g, b]) {
  const l = Math.cbrt(0.4122214708 * toLin(r) + 0.5363325363 * toLin(g) + 0.0514459929 * toLin(b))
  const m = Math.cbrt(0.2119034982 * toLin(r) + 0.6806995451 * toLin(g) + 0.1073969566 * toLin(b))
  const s = Math.cbrt(0.0883024619 * toLin(r) + 0.2817188376 * toLin(g) + 0.6299787005 * toLin(b))
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ]
}
function oklabToRgb([L, a, bb]) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * bb) ** 3
  const m = (L - 0.1055613458 * a - 0.0638541728 * bb) ** 3
  const s = (L - 0.0894841775 * a - 1.291485548 * bb) ** 3
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ].map((v) => toSrgb(v))
}
const inGamut = (L, C, H) => {
  const h = (H * Math.PI) / 180
  const rgb = oklabToRgb([L / 100, C * Math.cos(h), C * Math.sin(h)])
  return rgb.every((v) => v >= -0.001 && v <= 1.001)
}
const toHex = (L, C, H) => {
  const h = (H * Math.PI) / 180
  const rgb = oklabToRgb([L / 100, C * Math.cos(h), C * Math.sin(h)])
  return '#' + rgb.map((v) => Math.round(Math.min(1, Math.max(0, v)) * 255).toString(16).padStart(2, '0')).join('').toUpperCase()
}
const oklch = (hex) => {
  const [L, a, b] = rgbToOklab(hexToRgb(hex))
  let H = (Math.atan2(b, a) * 180) / Math.PI
  if (H < 0) H += 360
  return { L: L * 100, C: Math.hypot(a, b), H }
}
const relLum = (hex) => {
  const [r, g, b] = hexToRgb(hex)
  return 0.2126 * toLin(r) + 0.7152 * toLin(g) + 0.0722 * toLin(b)
}
const contrast = (a, b) => {
  const la = relLum(a), lb = relLum(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}
// 色相在 L 上的 sRGB 彩度上限
function maxChroma(L, H) {
  let lo = 0, hi = 0.4
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2
    if (inGamut(L, mid, H)) lo = mid
    else hi = mid
  }
  return lo
}

const ORIG = [
  ['chat', '#E3EBFA', '#2B4C8C'],
  ['character', '#EAF3EC', '#2F6B4A'],
  ['world', '#F2EDFA', '#5A3E96'],
  ['memory', '#E6F1F3', '#26606B'],
  ['music', '#F9E8F0', '#8C2F63'],
  ['reader', '#FBEDE6', '#8C4A2B'],
  ['gallery', '#F7F0DF', '#7A6023'],
  ['settings', '#EDEFF3', '#4A5261'],
]
const FIX = new Set(['character', 'world', 'gallery'])

console.log('=== 修正：L 压到目标值，C 取各色相"不显艳上限"（50% of sRGB 上限，且 ≤0.035）===\n')
const out = []
for (const [name, bg, ink] of ORIG) {
  const o = oklch(bg)
  if (!FIX.has(name)) {
    out.push({ name, bg, ink, o, changed: false })
    continue
  }
  const targetL = 93 // ≤94 且接近原值
  const cap = Math.min(maxChroma(targetL, o.H) * 0.5, 0.035)
  const nb = toHex(targetL, cap, o.H)
  const no = oklch(nb)
  const nc = contrast(nb, ink)
  console.log(
    `${name.padEnd(10)} L ${o.L.toFixed(1)} -> ${no.L.toFixed(1)} | C ${o.C.toFixed(3)} -> ${no.C.toFixed(3)} (该色相 sRGB 上限 ${maxChroma(targetL, o.H).toFixed(3)}) | ${bg} -> ${nb} | 对比度 ${contrast(bg, ink).toFixed(2)} -> ${nc.toFixed(2)}:1`,
  )
  out.push({ name, bg: nb, ink, o: no, changed: true })
}

console.log('\n=== 修正后完整对比度表 ===\n')
console.log('入口        底块       字色       对比度   达标(≥4.5)   变动')
for (const r of out) {
  const c = contrast(r.bg, r.ink)
  console.log(
    `${r.name.padEnd(11)} ${r.bg}  ${r.ink}  ${c.toFixed(2).padStart(5)}:1   ${c >= 4.5 ? '✅' : '❌'}        ${r.changed ? '已修正' : '采信 hex'}`,
  )
}

console.log('\n=== 修正后 OKLCH（供 tokens 注释用）===\n')
for (const r of out) {
  console.log(`${r.name.padEnd(11)} 底 L${r.o.L.toFixed(1)} C${r.o.C.toFixed(3)} H${r.o.H.toFixed(0)}`)
}

console.log('\n=== 文字分层对比度（决策②）===\n')
const TEXT = [
  ['正文 / 底色', '#1B1F27', '#F5F6F8'],
  ['正文 / 表面', '#1B1F27', '#FFFFFF'],
  ['次要 + 时间戳 + 说明小字 / 表面', '#6B7280', '#FFFFFF'],
  ['次要 + 时间戳 + 说明小字 / 底色', '#6B7280', '#F5F6F8'],
  ['装饰性 #9AA2AF / 表面（禁用于文字）', '#9AA2AF', '#FFFFFF'],
]
for (const [l, f, b] of TEXT) {
  const c = contrast(f, b)
  console.log(`${l.padEnd(36)} ${f} on ${b}  ${c.toFixed(2).padStart(5)}:1  ${c >= 4.5 ? '✅' : '仅装饰'}`)
}

console.log('\n=== 角标槽位（决策③）===\n')
console.log('角标槽位 72×72 · 图标 60×60 · 角标落容器右上角外侧')
console.log('未读红点：直径 15，位于槽位内右上 (72-15-3, 3)')
console.log('开发中：灰显图标 + 右下 18 灰点，同槽位内')
