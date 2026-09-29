// 桌面第一批（终）：主态×2 + 文件夹平铺预览 + 文件夹展开态 + 图标对照板
const W = 390, H = 844
const C = {
  bg: '#F5F6F8', surface: '#FFFFFF', surface2: '#EEF1F6',
  text: '#1B1F27', text2: '#6B7280', text3: '#9AA2AF',
  outline: '#E6E9EF', accent: '#3B6FE0', danger: '#E5484D',
}
const TILE = {
  chat: ['#E3EBFA', '#2B4C8C', '聊'], character: ['#EAF3EC', '#2F6B4A', '角'],
  world: ['#F2EDFA', '#5A3E96', '世'], memory: ['#E6F1F3', '#26606B', '忆'],
  music: ['#F9E8F0', '#8C2F63', '乐'], reader: ['#FBEDE6', '#8C4A2B', '书'],
  gallery: ['#F7F0DF', '#7A6023', '相'], settings: ['#EDEFF3', '#4A5261', '设'],
}
const NAME = { chat: '聊天', character: '角色', world: '世界', memory: '记忆', music: '音乐', reader: '书城', gallery: '相册', settings: '设置' }
const ORDER = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings']

const T = (p, n, x, y, w, h, c, s, fw, fill, al) => I(p, { type: 'text', name: n, x, y, width: w, height: h, content: c, fontSize: s, fontWeight: fw, textAlign: al || 'left', fill })
const R = (p, n, x, y, w, h, fill, r) => I(p, { type: 'rectangle', name: n, x, y, width: w, height: h, fill, cornerRadius: r })
const F = (p, n, x, y, w, h, fill, r) => I(p, { type: 'frame', name: n, x, y, width: w, height: h, fill, cornerRadius: r })

// 图标块：frame(彩底) + 内部字形
function tileAt(parent, key, x, y, size, suffix, gray) {
  const [bg, ink, g] = TILE[key]
  const f = F(parent, 'tile_' + key + suffix, x, y, size, size, gray ? '#F0F1F3' : bg, Math.round(size * 0.27))
  T(f, 'g_' + key + suffix, 0, Math.round(size * 0.23), size, Math.round(size * 0.5), g, Math.round(size * 0.42), 600, gray ? '#B9BEC7' : ink, 'center')
  return f
}

// 未读红点：贴在图标右上角内侧（不侵入邻格）
function unreadDot(parent, tileX, tileY, tileSize, count, suffix) {
  const d = 14
  const x = tileX + tileSize - d - 2
  const y = tileY + 2
  R(parent, 'unread' + suffix, x, y, d, d, C.danger, d / 2)
  T(parent, 'unreadT' + suffix, x, y + 3, d, 12, count, 9, 600, '#FFFFFF', 'center')
}

// 「开发中」：灰显图标 + 灰色小圆点（避免角标侵入邻格）
function devBadge(parent, tileX, tileY, tileSize, suffix) {
  const d = 16
  const x = tileX + tileSize - d + 2
  const y = tileY + tileSize - d + 2
  R(parent, 'devDotBg' + suffix, x, y, d, d, C.bg, d / 2)
  R(parent, 'devDot' + suffix, x + 2, y + 2, d - 4, d - 4, '#C9CDD4', (d - 4) / 2)
  T(parent, 'devDotT' + suffix, x, y + 4, d, 12, '·', 12, 600, '#FFFFFF', 'center')
}

function statusBar(p) {
  T(p, 'time', 26, 16, 70, 22, '9:41', 15, 600, C.text)
  R(p, 'wifiDot', 288, 24, 6, 6, C.text2, 3)
  T(p, 'battery', 300, 18, 70, 20, '100%', 11, 500, C.text2, 'right')
}
function header(p) {
  T(p, 'bigTime', 24, 52, 220, 84, '9:41', 68, 200, C.text)
  T(p, 'bigDate', 26, 138, 220, 24, '9月29日 星期一', 14, 400, C.text2)
  T(p, 'weatherT', 268, 58, 96, 30, '21°', 26, 300, C.text)
  T(p, 'weatherD', 268, 92, 96, 20, '多云', 12, 400, C.text2)
  R(p, 'glowDot', 356, 58, 7, 7, C.accent, 4) // 微光第 1 处
}
function dock(p) {
  const d = F(p, 'dock', 20, H - 110, W - 40, 86, C.surface, 30)
  ;['chat', 'settings', 'music', 'gallery'].forEach((k, i) => tileAt(d, k, 22 + i * 84, 13, 60, '_dock', false))
  return d
}
function pageDots(p) {
  R(p, 'd1', W / 2 - 14, H - 130, 6, 6, C.accent, 3)
  R(p, 'd2', W / 2 - 3, H - 130, 6, 6, C.text3, 3)
  R(p, 'd3', W / 2 + 8, H - 130, 6, 6, C.text3, 3)
}
// 图标入口 = 外层透明容器（定位）+ 内层图标块 + 角标（落在容器内）
function appEntry(parent, key, x, y, size, opts) {
  const o = opts || {}
  const pad = 8
  const box = F(parent, 'entry_' + key + (o.sfx || ''), x - pad, y - pad, size + pad * 2, size + pad * 2 + 18, '#00000000', 0)
  const [bg, ink, g] = TILE[key]
  const gray = !!o.gray
  const tf = F(box, 'tile_' + key + (o.sfx || ''), pad, pad, size, size, gray ? '#F0F1F3' : bg, Math.round(size * 0.27))
  T(tf, 'g_' + key + (o.sfx || ''), 0, Math.round(size * 0.23), size, Math.round(size * 0.5), g, Math.round(size * 0.42), 600, gray ? '#B9BEC7' : ink, 'center')
  T(box, 'lbl_' + key + (o.sfx || ''), 0, pad + size + 6, size + pad * 2, 18, NAME[key], 11, 400, gray ? '#B9BEC7' : C.text2, 'center')
  if (o.unread) {
    const d = 15
    const ux = pad + size - d + 3, uy = pad - 3
    R(box, 'ud_' + key + (o.sfx || ''), ux, uy, d, d, C.danger, d / 2)
    T(box, 'udt_' + key + (o.sfx || ''), ux, uy + 3, d, 13, String(o.unread), 9, 600, '#FFFFFF', 'center')
  }
  if (gray) {
    const d = 18
    const bx = pad + size - d + 4, by = pad + size - d + 4
    R(box, 'db_' + key + (o.sfx || ''), bx, by, d, d, C.bg, d / 2)
    R(box, 'dd_' + key + (o.sfx || ''), bx + 2, by + 2, d - 4, d - 4, '#C9CDD4', (d - 4) / 2)
    T(box, 'dm_' + key + (o.sfx || ''), bx + 2, by + 2, d - 4, 14, '—', 10, 600, '#FFFFFF', 'center')
  }
  return box
}
function bigFolderAt(parent, x, y, name) {
  const cw = 82, rh = 118
  const w = cw * 2 - 16, h = rh * 2 - 50
  const f = F(parent, 'bigFolder' + (name || ''), x, y, w, h, C.surface, 22)
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  keys.slice(0, 9).forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const mf = F(f, 'mini' + (name || '') + i, 13 + col * 44, 11 + row * 44, 36, 36, TILE[k][0], 10)
    T(mf, 'miniG' + (name || '') + i, 0, 8, 36, 20, TILE[k][2], 15, 600, TILE[k][1], 'center')
  })
  return f
}

// ===== 1 主态 =====
function mainGrid(ox) {
  const p = F(null, 'DESK_MAIN', ox, 60, W, H, C.bg, 40)
  statusBar(p); header(p)
  const gx = 44, gy = 296, cell = 82, rowH = 118
  ORDER.forEach((k, i) => {
    const col = i % 4, row = (i - col) / 4
    const x = gx + col * cell, y = gy + row * rowH
    appEntry(p, k, x, y, 66, { gray: k === 'gallery', unread: k === 'chat' ? 3 : 0, sfx: '_m' })
  })
  pageDots(p); dock(p)
  return p
}

// ===== 2 变体：含大文件夹 =====
function mainFolder(ox) {
  const p = F(null, 'DESK_FOLDER', ox, 60, W, H, C.bg, 40)
  statusBar(p); header(p)
  const gx = 44, gy = 296, cell = 82, rowH = 118
  bigFolderAt(p, gx, gy, '')
  T(p, 'folderName', gx - 4, gy + 182 + 6, 148, 18, '社交', 11, 400, C.text2, 'center')
  unreadDot(p, gx, gy, 148, '5', '_folder')
  ;['music', 'reader', 'gallery', 'settings'].forEach((k, i) => {
    const col = i % 2, row = (i - col) / 2
    const x = gx + (col + 2) * cell + 8, y = gy + row * rowH
    appEntry(p, k, x, y, 66, { gray: k === 'gallery', sfx: '_v' })
  })
  pageDots(p); dock(p)
  return p
}

// ===== 3 大文件夹：平铺预览（特写） =====
function folderPreview(ox) {
  const p = F(null, 'FOLDER_PREVIEW', ox, 60, W, H, C.bg, 40)
  T(p, 'pvTitle', 24, 40, 340, 34, '大文件夹 · 平铺预览', 20, 600, C.text)
  T(p, 'pvSub', 24, 76, 340, 22, '桌面占位 2×2 格 · 内部 3×3 最多 9 个应用', 12, 400, C.text2)

  const gx = 44, gy = 130
  bigFolderAt(p, gx, gy, '_pv')
  T(p, 'pvName', gx - 4, gy + 182 + 6, 148, 18, '社交', 11, 400, C.text2, 'center')
  unreadDot(p, gx, gy, 148, '5', '_pv')

  T(p, 'zoomTitle', 24, 372, 340, 24, '放大特写 2×（看清单个图标）', 13, 600, C.text2)
  const zf = F(p, 'zoom', 24, 400, 296, 296, C.surface, 44)
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  keys.forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const mf = F(zf, 'z' + i, 26 + col * 88, 22 + row * 88, 72, 72, TILE[k][0], 20)
    T(mf, 'zG' + i, 0, 16, 72, 40, TILE[k][2], 30, 600, TILE[k][1], 'center')
  })
  T(p, 'note1', 24, 712, 340, 20, '内部图标 36px（1×），平铺不显示名称', 11, 400, C.text3)
  T(p, 'note2', 24, 732, 340, 20, '未读汇总点：14px 红点 + 数字，贴右上角内侧', 11, 400, C.text3)
  return p
}

// ===== 4 大文件夹：点开展开态（面板） =====
function folderOpen(ox) {
  const p = F(null, 'FOLDER_OPEN', ox, 60, W, H, '#DFE3E9', 40) // 模拟背景变暗
  const panel = F(p, 'panel', 16, 150, W - 32, 470, C.surface, 26)
  T(panel, 'panelTitle', 24, 22, 200, 32, '社交', 22, 600, C.text)
  T(panel, 'panelCount', 24, 56, 200, 20, '9 个应用', 12, 400, C.text2)
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  const nm = ['聊天', '角色', '世界', '记忆', '音乐', '书城', '相册', '设置', '群聊']
  keys.forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const x = 32 + col * 110, y = 104 + row * 108
    const f = F(panel, 'e' + i, x, y, 62, 62, TILE[k][0], 18)
    T(f, 'eG' + i, 0, 14, 62, 34, TILE[k][2], 26, 600, TILE[k][1], 'center')
    T(panel, 'eN' + i, x - 10, y + 68, 82, 18, nm[i], 11, 400, C.text2, 'center')
  })
  T(panel, 'hint', 0, 424, W - 32, 20, '长按应用可拖出文件夹 · 拖入可加入', 11, 400, C.text3, 'center')
  T(p, 'openNote', 16, 636, W - 32, 20, '（背景为变暗示意；实际为模糊/变暗遮罩）', 11, 400, '#8A9099', 'center')
  return p
}

// ===== 5 图标对照板（24 / 48） =====
function iconSheet(ox) {
  const p = F(null, 'ICON_SHEET', ox, 60, 700, 460, C.surface, 0)
  T(p, 'sT', 24, 20, 660, 30, '应用入口图标 · 24px / 48px 对照', 18, 600, C.text)
  T(p, 'sS', 24, 50, 660, 20, '24 栅格 · 线宽 1.75 · round（此稿为占位字形，交付时替换为 SVG）', 12, 400, C.text2)
  ORDER.forEach((k, i) => {
    const x = 22 + i * 84
    const f24 = F(p, 'i24' + k, x, 92, 52, 52, TILE[k][0], 14)
    T(f24, 'i24g' + k, 0, 11, 52, 30, TILE[k][2], 21, 600, TILE[k][1], 'center')
    T(p, 'i24n' + k, x - 8, 150, 68, 16, NAME[k] + ' 24', 10, 400, C.text2, 'center')
    const f48 = F(p, 'i48' + k, x - 6, 186, 64, 64, TILE[k][0], 18)
    T(f48, 'i48g' + k, 0, 14, 64, 36, TILE[k][2], 27, 600, TILE[k][1], 'center')
    T(p, 'i48n' + k, x - 8, 256, 68, 16, NAME[k] + ' 48', 10, 400, C.text2, 'center')
  })
  ORDER.forEach((k, i) => {
    const x = 22 + i * 84
    T(p, 'bg' + k, x - 8, 300, 68, 14, TILE[k][0], 9, 400, C.text3, 'center')
    T(p, 'ink' + k, x - 8, 316, 68, 14, TILE[k][1], 9, 400, C.text3, 'center')
  })
  T(p, 'n1', 24, 352, 660, 20, 'OKLCH 标准（你给的）：底 L=92~94% C=0.05~0.07 ｜ 字 L=38~45% C=0.10~0.12', 11, 400, C.text2)
  T(p, 'n2', 24, 372, 660, 20, '实测你给的 hex：底 L=93.8~95.6 C=0.006~0.024 ｜ 字 L=42.7~50.3 C=0.027~0.139（低于标准）', 11, 400, C.text2)
  R(p, 'accentDot', 24, 404, 12, 12, C.accent, 6)
  T(p, 'n3', 44, 402, 640, 20, '强调色 #3B6FE0 · 全屏 ≤3 处 · glow-alpha 0.11 · 状态点 ≤8dp · 气泡不用强调色', 11, 500, C.text2)
  T(p, 'n4', 24, 426, 660, 18, '用户气泡改为中性深色 #22262B / 文字 #FFFFFF（对比度 15.2:1）', 11, 400, C.text2)
  return p
}

mainGrid(0)
mainFolder(450)
folderPreview(900)
folderOpen(1350)
iconSheet(1800)
