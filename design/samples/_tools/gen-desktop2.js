// 桌面第一批（修订）：把「大文件夹平铺预览」单独成稿
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

function iconTile(parent, key, x, y, size, suffix = '') {
  const [bg, ink, g] = TILE[key]
  const f = F(parent, 'tile_' + key + suffix, x, y, size, size, bg, Math.round(size * 0.27))
  T(f, 'g_' + key + suffix, 0, Math.round(size * 0.23), size, Math.round(size * 0.5), g, Math.round(size * 0.42), 600, ink, 'center')
  return f
}

// 大文件夹：2×2 格占位，内部 3×3 平铺预览（组件化，供三处复用）
function bigFolder(parent, x, y, unread) {
  const cw = 82, rh = 116
  const w = cw * 2 - 16, h = rh * 2 - 50
  const f = F(parent, 'bigFolder', x, y, w, h, C.surface, 22)
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  keys.slice(0, 9).forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const mx = 13 + col * 44, my = 11 + row * 44
    const mf = F(f, 'mini_' + i, mx, my, 36, 36, TILE[k][0], 10)
    T(mf, 'miniG_' + i, 0, 8, 36, 20, TILE[k][2], 15, 600, TILE[k][1], 'center')
  })
  return f
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
  R(p, 'glowDot', 358, 56, 7, 7, C.accent, 4) // 微光：状态点（全屏第 1 处）
}

function dock(p) {
  const d = F(p, 'dock', 20, H - 110, W - 40, 86, C.surface, 30)
  ;['chat', 'settings', 'music', 'gallery'].forEach((k, i) => iconTile(d, k, 22 + i * 84, 13, 60, '_dock'))
  return d
}

function pageDots(p) {
  R(p, 'd1', W / 2 - 14, H - 130, 6, 6, C.accent, 3)
  R(p, 'd2', W / 2 - 3, H - 130, 6, 6, C.text3, 3)
  R(p, 'd3', W / 2 + 8, H - 130, 6, 6, C.text3, 3)
}

// ===== 稿 1：主态（8 应用平铺 + 未读红点 + 灰显应用） =====
function mainGrid(ox) {
  const p = F(null, 'DESK_MAIN', ox, 60, W, H, C.bg, 40)
  statusBar(p); header(p)
  const gx = 44, gy = 296, cell = 82, rowH = 118
  ORDER.forEach((k, i) => {
    const col = i % 4, row = (i - col) / 4
    const x = gx + col * cell, y = gy + row * rowH
    const gray = k === 'gallery'
    const f = F(p, 'tile_' + k, x, y, 66, 66, gray ? '#F0F1F3' : TILE[k][0], 18)
    T(f, 'g_' + k, 0, 15, 66, 34, TILE[k][2], 27, 600, gray ? '#B9BEC7' : TILE[k][1], 'center')
    T(p, 'n_' + k, x - 4, y + 72, 74, 18, NAME[k], 11, 400, gray ? '#B9BEC7' : C.text2, 'center')
    if (gray) {
      const b = F(p, 'badge_' + k, x + 32, y + 46, 36, 18, '#E3E5E9', 9)
      T(b, 'badgeT_' + k, 0, 3, 36, 14, '开发中', 9, 500, '#7A8090', 'center')
    }
    if (k === 'chat') {
      R(p, 'unread', x + 48, y + 2, 14, 14, C.danger, 7)
      T(p, 'unreadT', x + 48, y + 5, 14, 12, '3', 9, 600, '#FFFFFF', 'center')
    }
  })
  pageDots(p); dock(p)
  return p
}

// ===== 稿 2：变体（一个大文件夹 2×2 + 其余应用） =====
function mainFolder(ox) {
  const p = F(null, 'DESK_FOLDER', ox, 60, W, H, C.bg, 40)
  statusBar(p); header(p)
  const gx = 44, gy = 296, cell = 82, rowH = 118
  const f = bigFolder(p, gx, gy, true)
  T(p, 'folderName', gx - 4, gy + 182 - 50 + 6, 148, 18, '社交', 11, 400, C.text2, 'center')
  R(p, 'unreadF', gx + 148 - 24, gy + 6, 14, 14, C.danger, 7)
  T(p, 'unreadFT', gx + 148 - 24, gy + 9, 14, 12, '5', 9, 600, '#FFFFFF', 'center')
  ;['music', 'reader', 'gallery', 'settings'].forEach((k, i) => {
    const col = i % 2, row = (i - col) / 2
    const x = gx + (col + 2) * cell + 8, y = gy + row * rowH
    const gray = k === 'gallery'
    const tf = F(p, 'tile_' + k, x, y, 66, 66, gray ? '#F0F1F3' : TILE[k][0], 18)
    T(tf, 'g_' + k, 0, 15, 66, 34, TILE[k][2], 27, 600, gray ? '#B9BEC7' : TILE[k][1], 'center')
    T(p, 'n_' + k, x - 4, y + 72, 74, 18, NAME[k], 11, 400, gray ? '#B9BEC7' : C.text2, 'center')
  })
  pageDots(p); dock(p)
  return p
}

// ===== 稿 3：大文件夹「平铺预览」（放大特写） =====
function folderPreview(ox) {
  const p = F(null, 'FOLDER_PREVIEW', ox, 60, W, H, C.bg, 40)
  T(p, 'pvTitle', 24, 40, 340, 34, '大文件夹 · 平铺预览（2×2 格）', 20, 600, C.text)
  T(p, 'pvSub', 24, 76, 340, 22, '桌面占位 2×2，内部 3×3 最多 9 个应用', 12, 400, C.text2)

  // 实际尺寸 1:1
  const gx = 44, gy = 140
  const f = bigFolder(p, gx, gy, true)
  T(p, 'pvName', gx - 4, gy + 182 + 6, 148, 18, '社交', 11, 400, C.text2, 'center')
  R(p, 'pvUnread', gx + 148 - 24, gy + 6, 14, 14, C.danger, 7)
  T(p, 'pvUnreadT', gx + 148 - 24, gy + 9, 14, 12, '5', 9, 600, '#FFFFFF', 'center')

  // 放大特写 2×
  T(p, 'zoomTitle', 24, 370, 340, 24, '放大特写（2×）', 13, 600, C.text2)
  const zx = 24, zy = 400
  const zf = F(p, 'zoom', zx, zy, 296, 296, C.surface, 44)
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  keys.forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const mx = 26 + col * 88, my = 22 + row * 88
    const mf = F(zf, 'z_' + i, mx, my, 72, 72, TILE[k][0], 20)
    T(mf, 'zG_' + i, 0, 16, 72, 40, TILE[k][2], 30, 600, TILE[k][1], 'center')
  })
  T(p, 'zoomNote', 24, 710, 340, 20, '内部图标 36px（1×）／平铺不显示名称', 11, 400, C.text3)
  T(p, 'zoomNote2', 24, 730, 340, 20, '未读汇总点：14px 红点 + 数字，落右上角', 11, 400, C.text3)
  return p
}

mainGrid(0)
mainFolder(450)
folderPreview(900)
