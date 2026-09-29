// 按 OKLCH 标准区间重算 8 套色块（保留各色相 H），并验证对比度
const toLin = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
const toSrgb = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)
const hexToRgb = (h) => [0, 2, 4].map((i) => parseInt(h.replace('#', '').slice(i, i + 2), 16) / 255)
function rgbToOklab([r, g, b]) {
  const R = toLin(r), G = toLin(g), B = toLin(b)
  const l = Math.cbrt(0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B)
  const m = Math.cbrt(0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B)
  const s = Math.cbrt(0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B)
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
    +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ].map((v) => Math.min(1, Math.max(0, toSrgb(v))))
}
const rgbToHex = ([r, g, b]) => '#' + [r, g, b].map((v) => Math.round(v * 255).toString(16).padStart(2, '0')).join('').toUpperCase()
const oklch = (hex) => {
  const [L, a, b] = rgbToOklab(hexToRgb(hex))
  let H = (Math.atan2(b, a) * 180) / Math.PI
  if (H < 0) H += 360
  return { L: L * 100, C: Math.hypot(a, b), H }
}
const fromOklch = (L, C, H) => {
  const h = (H * Math.PI) / 180
  return rgbToHex(oklabToRgb([L / 100, C * Math.cos(h), C * Math.sin(h)]))
}
const relLum = (hex) => {
  const [r, g, b] = hexToRgb(hex)
  return 0.2126 * toLin(r) + 0.7152 * toLin(g) + 0.0722 * toLin(b)
}
const contrast = (a, b) => {
  const la = relLum(a), lb = relLum(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

// 原始色（保留其色相 H），按标准区间重算
const RAW = [
  ['chat', '#E3EBFA', '#2B4C8C'],
  ['character', '#EAF3EC', '#2F6B4A'],
  ['world', '#F2EDFA', '#5A3E96'],
  ['memory', '#E6F1F3', '#26606B'],
  ['music', '#F9E8F0', '#8C2F63'],
  ['reader', '#FBEDE6', '#8C4A2B'],
  ['gallery', '#F7F0DF', '#7A6023'],
  ['settings', '#EDEFF3', '#4A5261'],
]

// 标准区间：底 L 92~94 / C 0.05~0.07；字 L 38~45 / C 0.10~0.12
// 取区间内"最能拉开对比度"的组合：底取偏亮端、字取偏暗端
const BG_L = 93.5, BG_C = 0.06
const INK_L = 40, INK_C = 0.11

console.log('=== 按 OKLCH 标准重算（保留各色相 H）===')
console.log('标准：底 L=92~94 C=0.05~0.07 ｜ 字 L=38~45 C=0.10~0.12')
console.log('取定：底 L=93.5 C=0.06 ｜ 字 L=40 C=0.11（区间内取端值，最大化两两对比度）\n')
console.log('app        原底（偏离）  新底      新底OKLCH       原字（偏离）  新字      新字OKLCH      对比度')
const out = []
for (const [name, ob, oi] of RAW) {
  const hb = oklch(ob).H, hi = oklch(oi).H
  const nb = fromOklch(BG_L, BG_C, hb)
  const ni = fromOklch(INK_L, INK_C, hi)
  const cb = oklch(nb), ci = oklch(ni)
  const c = contrast(nb, ni)
  console.log(
    `${name.padEnd(10)} ${ob}      ${nb}  L${cb.L.toFixed(1)} C${cb.C.toFixed(3)} H${cb.H.toFixed(0).padStart(3)}   ${oi}      ${ni}  L${ci.L.toFixed(1)} C${ci.C.toFixed(3)} H${ci.H.toFixed(0).padStart(3)}   ${c.toFixed(2)}:1`,
  )
  out.push({ name, bg: nb, ink: ni, contrast: c })
}

console.log('\n=== 全部满足标准？===')
let allOk = true
for (const o of out) {
  const b = oklch(o.bg), i = oklch(o.ink)
  const okB = b.L >= 92 && b.L <= 94 && b.C >= 0.05 && b.C <= 0.07
  const okI = i.L >= 38 && i.L <= 45 && i.C >= 0.1 && i.C <= 0.12
  const okC = o.contrast >= 4.5
  if (!(okB && okI && okC)) { allOk = false; console.log(`  ${o.name}: 底${okB ? 'OK' : 'NG'} 字${okI ? 'OK' : 'NG'} 对比度${okC ? 'OK' : 'NG'}`) }
}
console.log(allOk ? '  ✓ 8 套全部达标（在标准区间内且对比度 ≥4.5:1）' : '  ✗ 有未达标项')

// 三级文本修正建议
console.log('\n=== 三级文本修正建议（当前 #9AA2AF 对白底 2.57:1 不达标）===')
for (const L of [52, 50, 48, 46]) {
  const cand = fromOklch(L, 0.01, 265)
  console.log(`  L=${L}%  ${cand}  对白底 ${contrast(cand, '#FFFFFF').toFixed(2)}:1  对底色F5F6F8 ${contrast(cand, '#F5F6F8').toFixed(2)}:1`)
}
console.log('\n=== 参考：二级文本 ===')
console.log(`  #6B7280 对白底 ${contrast('#6B7280', '#FFFFFF').toFixed(2)}:1（达标）`)
