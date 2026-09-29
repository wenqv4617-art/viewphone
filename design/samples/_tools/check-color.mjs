// OKLCH 校验 + WCAG 对比度计算（纯 JS，无依赖）
// 用途：验证 8 套应用色块是否符合「统一明度与彩度」标准，并出对比度表

// ---------- sRGB <-> 线性 ----------
const toLin = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
const toSrgb = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255)
}

// ---------- OKLab / OKLCH ----------
function rgbToOklab([r, g, b]) {
  const R = toLin(r), G = toLin(g), B = toLin(b)
  const l = 0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B
  const m = 0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B
  const s = 0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B
  const l_ = Math.cbrt(l), m_ = Math.cbrt(m), s_ = Math.cbrt(s)
  return [
    0.2104542553 * l_ + 0.793617785 * m_ - 0.0040720468 * s_,
    1.9779984951 * l_ - 2.428592205 * m_ + 0.4505937099 * s_,
    0.0259040371 * l_ + 0.7827717662 * m_ - 0.808675766 * s_,
  ]
}
function oklabToRgb([L, a, bb]) {
  const l_ = L + 0.3963377774 * a + 0.2158037573 * bb
  const m_ = L - 0.1055613458 * a - 0.0638541728 * bb
  const s_ = L - 0.0894841775 * a - 1.291485548 * bb
  const l = l_ ** 3, m = m_ ** 3, s = s_ ** 3
  const R = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
  const G = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
  const B = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s
  return [R, G, B].map((v) => Math.min(1, Math.max(0, toSrgb(v))))
}
const oklch = (hex) => {
  const [L, a, b] = rgbToOklab(hexToRgb(hex))
  const C = Math.hypot(a, b)
  let H = (Math.atan2(b, a) * 180) / Math.PI
  if (H < 0) H += 360
  return { L: L * 100, C, H }
}
const rgbToHex = ([r, g, b]) =>
  '#' + [r, g, b].map((v) => Math.round(v * 255).toString(16).padStart(2, '0')).join('').toUpperCase()

// 在 OKLCH 空间构造颜色（用于按标准生成/修正）
function oklchToHex(L, C, H) {
  const h = (H * Math.PI) / 180
  return rgbToHex(oklabToRgb([L / 100, C * Math.cos(h), C * Math.sin(h)]))
}

// ---------- WCAG 对比度 ----------
const relLum = (hex) => {
  const [r, g, b] = hexToRgb(hex)
  return 0.2126 * toLin(r) + 0.7152 * toLin(g) + 0.0722 * toLin(b)
}
const contrast = (a, b) => {
  const la = relLum(a), lb = relLum(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

// ================= 数据 =================
const APPS = [
  ['chat', '#E3EBFA', '#2B4C8C'],
  ['character', '#EAF3EC', '#2F6B4A'],
  ['world', '#F2EDFA', '#5A3E96'],
  ['memory', '#E6F1F3', '#26606B'],
  ['music', '#F9E8F0', '#8C2F63'],
  ['reader', '#FBEDE6', '#8C4A2B'],
  ['gallery', '#F7F0DF', '#7A6023'],
  ['settings', '#EDEFF3', '#4A5261'],
]

// 标准区间
const BG = { L: [92, 94], C: [0.05, 0.07] }
const INK = { L: [38, 45], C: [0.1, 0.12] }

console.log('=== 8 套应用色块 · OKLCH 校验 ===')
console.log('标准：底色块 L=92~94% C=0.05~0.07 ｜ 字色 L=38~45% C=0.10~0.12\n')
console.log('app       角色   hex       L%     C       H    判定')
const rows = []
for (const [name, bg, ink] of APPS) {
  for (const [role, hex, spec] of [['底色', bg, BG], ['字色', ink, INK]]) {
    const o = oklch(hex)
    const okL = o.L >= spec.L[0] && o.L <= spec.L[1]
    const okC = o.C >= spec.C[0] && o.C <= spec.C[1]
    const verdict = okL && okC ? 'OK' : `偏离(${!okL ? 'L' : ''}${!okC ? 'C' : ''})`
    console.log(
      `${name.padEnd(10)}${role}  ${hex}  ${o.L.toFixed(1).padStart(5)}  ${o.C.toFixed(3)}  ${o.H.toFixed(0).padStart(3)}   ${verdict}`,
    )
    rows.push({ name, role, hex, ...o, okL, okC })
  }
}

console.log('\n=== 对比度（字色 on 底块）===')
console.log('标准：正文 ≥4.5:1，大字 ≥3:1\n')
console.log('app        底块       字色       对比度   判定')
for (const [name, bg, ink] of APPS) {
  const c = contrast(bg, ink)
  const ok = c >= 4.5 ? 'OK' : c >= 3 ? '仅大字可用' : '不达标'
  console.log(`${name.padEnd(10)} ${bg}  ${ink}  ${c.toFixed(2).padStart(5)}:1   ${ok}`)
}

console.log('\n=== 基础色对比度 ===')
const BASE = [
  ['正文 主文本 / 底色', '#1B1F27', '#F5F6F8'],
  ['正文 主文本 / 表面', '#1B1F27', '#FFFFFF'],
  ['次要文本 / 表面', '#6B7280', '#FFFFFF'],
  ['三级文本 / 表面', '#9AA2AF', '#FFFFFF'],
  ['强调色 / 表面', '#3B6FE0', '#FFFFFF'],
  ['白字 / 用户气泡', '#FFFFFF', '#22262B'],
  ['强调色 / 强调底(软)', '#3B6FE0', '#EAF0FF'],
  ['主文本 / 强调底(软)', '#1B1F27', '#EAF0FF'],
]
console.log('场景                        前色       后色       对比度   判定')
for (const [label, fg, bgc] of BASE) {
  const c = contrast(fg, bgc)
  const ok = c >= 4.5 ? 'OK(正文)' : c >= 3 ? 'OK(大字)' : '不达标'
  console.log(`${label.padEnd(26)} ${fg}  ${bgc}  ${c.toFixed(2).padStart(5)}:1   ${ok}`)
}

// 若底色块偏离标准，给出按标准修正后的值（保持色调 H 不变）
console.log('\n=== 修正建议（若上方出现"偏离"，按标准区间取中点重算，保持 H 不变）===')
for (const [name, bg, ink] of APPS) {
  const ob = oklch(bg), oi = oklch(ink)
  const needB = ob.L < BG.L[0] || ob.L > BG.L[1] || ob.C < BG.C[0] || ob.C > BG.C[1]
  const needI = oi.L < INK.L[0] || oi.L > INK.L[1] || oi.C < INK.C[0] || oi.C > INK.C[1]
  if (!needB && !needI) continue
  const nb = oklchToHex(93, Math.min(BG.C[1], Math.max(BG.C[0], ob.C)), ob.H)
  const ni = oklchToHex(42, Math.min(INK.C[1], Math.max(INK.C[0], oi.C)), oi.H)
  console.log(
    `${name.padEnd(10)} 底 ${bg} -> ${nb}   字 ${ink} -> ${ni}   新对比度 ${contrast(nb, ni).toFixed(2)}:1`,
  )
}
