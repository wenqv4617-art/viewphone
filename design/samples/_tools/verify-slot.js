// 最小验证：①角标槽位 72×72（图标 60×60）不裁切 ②文件夹第 9 格改为「群」
const C = {
  bg: '#F5F6F8', surface: '#FFFFFF', text: '#1B1F27', text2: '#6B7280',
  accent: '#3B6FE0', danger: '#E5484D',
}
const T = (p, n, x, y, w, h, c, s, fw, fill, al) => I(p, { type: 'text', name: n, x, y, width: w, height: h, content: c, fontSize: s, fontWeight: fw, textAlign: al || 'left', fill })
const R = (p, n, x, y, w, h, fill, r) => I(p, { type: 'rectangle', name: n, x, y, width: w, height: h, fill, cornerRadius: r })
const F = (p, n, x, y, w, h, fill, r) => I(p, { type: 'frame', name: n, x, y, width: w, height: h, fill, cornerRadius: r })

// 角标槽位：容器 72×72，图标 60×60，角标落容器右上角外侧
function slot(parent, key, x, y, glyph, ink, bg, opts) {
  const o = opts || {}
  const S = 72, I2 = 60, off = (S - I2) / 2
  const box = F(parent, 'slot_' + key, x, y, S, S, '#00000000', 0)
  const t = F(box, 'ic_' + key, off, off, I2, I2, bg, 16)
  T(t, 'gl_' + key, 0, 14, I2, 30, glyph, 25, 600, ink, 'center')
  if (o.dot) {
    const d = 15
    R(box, 'dot_' + key, S - d - 1, 1, d, d, C.danger, d / 2)
    T(box, 'dotT_' + key, S - d - 1, 4, d, 13, String(o.dot), 9, 600, '#FFFFFF', 'center')
  }
  return box
}

const p = F(null, 'VERIFY', 0, 60, 520, 400, C.surface, 0)
T(p, 'title', 24, 18, 460, 30, '最小验证：角标槽位 72×72 · 图标 60×60', 17, 600, C.text)
T(p, 'sub', 24, 46, 460, 20, '红点/灰点落在槽位内、图标外 → 不应被图标圆角裁切', 12, 400, C.text2)

// 三个样例：无角标 / 未读红点 / 开发中灰显
slot(p, 'plain', 30, 80, '聊', '#2B4C8C', '#E3EBFA', {})
slot(p, 'unread', 130, 80, '角', '#2F6B4A', '#D7EFDD', { dot: 3 })
slot(p, 'dev', 230, 80, '相', '#B9BEC7', '#F0F1F3', {})

T(p, 'l1', 30, 158, 72, 18, '无角标', 11, 400, C.text2, 'center')
T(p, 'l2', 130, 158, 72, 18, '未读红点', 11, 400, C.text2, 'center')
T(p, 'l3', 230, 158, 72, 18, '灰显（开发中）', 11, 400, C.text2, 'center')

// 文件夹第 9 格字形验证：9 个互不相同的字形
T(p, 't2', 24, 200, 460, 24, '文件夹 9 格字形（互不相同，第 9 格 = 群）', 14, 600, C.text)
const CELLS = [
  ['聊', '聊天', '#E3EBFA', '#2B4C8C'],
  ['角', '角色', '#D7EFDD', '#2F6B4A'],
  ['世', '世界', '#EAE5F4', '#5A3E96'],
  ['忆', '记忆', '#E6F1F3', '#26606B'],
  ['乐', '音乐', '#F9E8F0', '#8C2F63'],
  ['书', '书城', '#FBEDE6', '#8C4A2B'],
  ['相', '相册', '#F1E7CE', '#7A6023'],
  ['设', '设置', '#EDEFF3', '#4A5261'],
  ['群', '群聊', '#E8EEF7', '#31527F'],
]
CELLS.forEach((c, i) => {
  const col = i % 3, row = (i - col) / 3
  const x = 30 + col * 92, y = 236 + row * 52
  const t = F(p, 'c' + i, x, y, 40, 40, c[2], 12)
  T(t, 'cg' + i, 0, 9, 40, 22, c[0], 17, 600, c[3], 'center')
  T(p, 'cn' + i, x + 44, y + 10, 60, 20, c[1], 12, 400, C.text2)
})
T(p, 'note', 24, 396 - 18, 460, 18, '注：文件夹内为演示用占位应用；实际由用户拖入决定', 11, 400, C.text2)
