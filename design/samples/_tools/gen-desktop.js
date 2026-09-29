// 桌面第一批稿 + 8 图标对照
// 渲染铁律（DEC-016 实测）：卡片/图标块/气泡一律用 frame 承载文字；矩形会盖住同层文字
const W = 390, H = 844
const C = {
  bg: '#F5F6F8', surface: '#FFFFFF', surface2: '#EEF1F6',
  text: '#1B1F27', text2: '#6B7280', text3: '#9AA2AF',
  outline: '#E6E9EF', accent: '#3B6FE0',
  userBubble: '#22262B', danger: '#E5484D',
}
// 用户给定起点值（原样使用，不改）
const TILE = {
  chat: ['#E3EBFA', '#2B4C8C', '聊'],
  character: ['#EAF3EC', '#2F6B4A', '角'],
  world: ['#F2EDFA', '#5A3E96', '世'],
  memory: ['#E6F1F3', '#26606B', '忆'],
  music: ['#F9E8F0', '#8C2F63', '乐'],
  reader: ['#FBEDE6', '#8C4A2B', '书'],
  gallery: ['#F7F0DF', '#7A6023', '相'],
  settings: ['#EDEFF3', '#4A5261', '设'],
}

const T = (p, n, x, y, w, h, c, s, fw, fill, al) =>
  I(p, { type: 'text', name: n, x, y, width: w, height: h, content: c, fontSize: s, fontWeight: fw, textAlign: al || 'left', fill })
const R = (p, n, x, y, w, h, fill, r) =>
  I(p, { type: 'rectangle', name: n, x, y, width: w, height: h, fill, cornerRadius: r })
const F = (p, n, x, y, w, h, fill, r) =>
  I(p, { type: 'frame', name: n, x, y, width: w, height: h, fill, cornerRadius: r })

// 图标块 = frame(彩底) + 内部字形/SVG
function tile(parent, key, x, y, size, opts = {}) {
  const [bg, ink, glyph] = TILE[key]
  const f = F(parent, 'tile_' + key + (opts.suffix || ''), x, y, size, size, bg, Math.round(size * 0.27))
  T(f, 'glyph_' + key + (opts.suffix || ''), 0, Math.round(size * 0.23), size, Math.round(size * 0.5), glyph, Math.round(size * 0.42), 600, ink, 'center')
  return f
}

// ---------- 状态栏 ----------
function statusBar(p, oy) {
  T(p, 'time', 26, oy + 12, 70, 22, '9:41', 15, 600, C.text)
  T(p, 'battery', 300, oy + 14, 70, 20, '100%', 11, 500, C.text2, 'right')
  R(p, 'wifiDot', 288, oy + 20, 6, 6, C.text2, 3)
}

// ---------- 桌面屏 ----------
function desktop(ox, opts) {
  const { name, folderMode, showUnread, showGrayed } = opts
  const p = F(null, name, ox, 60, W, H, C.bg, 40)
  statusBar(p, 4)

  // 大标题区：时间 + 日期 + 天气
  T(p, 'bigTime', 24, 52, 220, 84, '9:41', 68, 200, C.text)
  T(p, 'bigDate', 26, 138, 220, 24, '9月29日 星期一', 14, 400, C.text2)
  // 天气（右上，小面积）
  T(p, 'weatherT', 268, 58, 96, 30, '21°', 26, 300, C.text)
  T(p, 'weatherD', 268, 92, 96, 20, '多云', 12, 400, C.text2)

  // 未读提示（顶部一条极小指示，属于"微光 ≤2 处"之一）
  if (showUnread) {
    R(p, 'glowDot', 358, 56, 7, 7, C.accent, 4)
  }

  // 图标网格
  const gx = 44, gy = 300, cell = 82, rowH = 116
  const order = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings']
  const labels = { chat: '聊天', character: '角色', world: '世界', memory: '记忆', music: '音乐', reader: '书城', gallery: '相册', settings: '设置' }

  if (folderMode === 'grid') {
    // 主态：8 个应用平铺
    order.forEach((k, i) => {
      const col = i % 4, row = (i - col) / 4
      const x = gx + col * cell, y = gy + row * rowH
      const grayed = showGrayed && k === 'gallery'
      const f = F(p, 'tile_' + k, x, y, 66, 66, grayed ? '#F0F1F3' : TILE[k][0], 18)
      T(f, 'glyph_' + k, 0, 15, 66, 34, TILE[k][2], 27, 600, grayed ? '#B9BEC7' : TILE[k][1], 'center')
      T(p, 'lab_' + k, x - 4, y + 72, 74, 18, labels[k], 11, 400, grayed ? '#B9BEC7' : C.text2, 'center')
      if (grayed) {
        // 「开发中」角标
        const b = F(p, 'badge_' + k, x + 40, y + 44, 40, 18, '#E3E5E9', 9)
        T(b, 'badgeT_' + k, 0, 3, 40, 14, '开发中', 9, 500, '#7A8090', 'center')
      }
      if (showUnread && k === 'chat') {
        R(p, 'unread_' + k, x + 52, y - 2, 14, 14, C.danger, 7)
        T(p, 'unreadT_' + k, x + 52, y + 1, 14, 12, '3', 9, 600, '#FFFFFF', 'center')
      }
    })
  } else {
    // 变体：一个 2×2 大文件夹占位 + 其余应用
    const fx = gx, fy = gy
    const fw = cell * 2 - 16, fh = rowH * 2 - 50
    const folder = F(p, 'folder', fx, fy, fw, fh, '#FFFFFF', 22)
    // 内部 3×3 平铺预览（小图标）
    const miniKeys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
    miniKeys.slice(0, 9).forEach((k, i) => {
      const col = i % 3, row = (i - col) / 3
      const mx = 14 + col * 44, my = 12 + row * 44
      const mf = F(folder, 'mini_' + i, mx, my, 36, 36, TILE[k][0], 10)
      T(mf, 'miniG_' + i, 0, 8, 36, 20, TILE[k][2], 15, 600, TILE[k][1], 'center')
    })
    T(p, 'folderName', fx - 4, fy + fh + 6, fw + 8, 18, '社交', 11, 400, C.text2, 'center')

    // 其余应用放到右侧两列
    const rest = ['music', 'reader', 'gallery', 'settings']
    rest.forEach((k, i) => {
      const col = i % 2, row = (i - col) / 2
      const x = gx + (col + 2) * cell + 8, y = gy + row * rowH
      const grayed = showGrayed && k === 'gallery'
      const f = F(p, 'tile_' + k, x, y, 66, 66, grayed ? '#F0F1F3' : TILE[k][0], 18)
      T(f, 'glyph_' + k, 0, 15, 66, 34, TILE[k][2], 27, 600, grayed ? '#B9BEC7' : TILE[k][1], 'center')
      T(p, 'lab_' + k, x - 4, y + 72, 74, 18, labels[k], 11, 400, grayed ? '#B9BEC7' : C.text2, 'center')
      if (showUnread && k === 'chat') { /* 已进文件夹 */ }
    })
    // 文件夹上的未读汇总点
    if (showUnread) {
      R(p, 'unread_f', fx + fw - 20, fy - 2, 14, 14, C.danger, 7)
      T(p, 'unreadT_f', fx + fw - 20, fy + 1, 14, 12, '5', 9, 600, '#FFFFFF', 'center')
    }
  }

  // 翻页点
  R(p, 'd1', W / 2 - 14, H - 130, 6, 6, C.accent, 3)
  R(p, 'd2', W / 2 - 3, H - 130, 6, 6, C.text3, 3)
  R(p, 'd3', W / 2 + 8, H - 130, 6, 6, C.text3, 3)

  // Dock
  const dock = F(p, 'dock', 20, H - 110, W - 40, 86, C.surface, 30)
  ;['chat', 'settings', 'music', 'gallery'].forEach((k, i) => {
    const x = 22 + i * 84
    const df = F(dock, 'dockTile_' + k, x, 13, 60, 60, TILE[k][0], 17)
    T(df, 'dockG_' + k, 0, 13, 60, 32, TILE[k][2], 25, 600, TILE[k][1], 'center')
  })
  return p
}

// ---------- 大文件夹展开态 ----------
function folderOpen(ox) {
  const p = F(null, 'folderOpen', ox, 60, W, H, C.bg, 40)
  // 背景变暗（浅色下的"变暗"用一层低透明度深色）
  R(p, 'scrim', 0, 0, W, H, '#1B1F27', 40)
  const panel = F(p, 'panel', 20, 150, W - 40, 420, C.surface, 26)
  T(panel, 'panelTitle', 24, 24, 200, 30, '社交', 20, 600, C.text)
  T(panel, 'panelCount', 24, 56, 200, 20, '9 个应用', 12, 400, C.text2)
  // 3×3 展开网格
  const keys = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings', 'chat']
  const nameMap = ['聊天', '角色', '世界', '记忆', '音乐', '书城', '相册', '设置', '群聊']
  keys.forEach((k, i) => {
    const col = i % 3, row = (i - col) / 3
    const x = 34 + col * 106, y = 100 + row * 104
    const f = F(panel, 'e_' + i, x, y, 62, 62, TILE[k][0], 18)
    T(f, 'eG_' + i, 0, 14, 62, 34, TILE[k][2], 26, 600, TILE[k][1], 'center')
    T(panel, 'eN_' + i, x - 8, y + 68, 78, 18, nameMap[i], 11, 400, C.text2, 'center')
  })
  // 底部提示
  T(panel, 'hint', 0, 380, W - 40, 20, '长按应用可拖出文件夹', 11, 400, C.text3, 'center')
  return p
}

// ---------- 8 图标对照（24 / 48）----------
// 真实 SVG 几何（24 栅格 / 线宽 1.75 / round）在实施阶段产出；
// 这里先用同形状向量验证排版与调色
function iconSheet(ox) {
  const p = F(null, 'iconSheet', ox, 60, 720, 420, C.surface, 0)
  T(p, 'sheetTitle', 24, 18, 660, 30, '应用入口图标 · 24px / 48px 对照', 18, 600, C.text)
  T(p, 'sheetSub', 24, 48, 660, 20, '24 栅格 · 线宽 1.75 · round（交付时替换为 SVG）', 12, 400, C.text2)
  const order = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings']
  const names = ['聊天', '角色', '世界', '记忆', '音乐', '书城', '相册', '设置']
  order.forEach((k, i) => {
    const x = 24 + i * 84
    // 24px
    const f24 = F(p, 's24_' + k, x, 92, 52, 52, TILE[k][0], 14)
    T(f24, 's24g_' + k, 0, 11, 52, 30, TILE[k][2], 21, 600, TILE[k][1], 'center')
    T(p, 's24n_' + k, x - 8, 150, 68, 16, names[i] + ' 24', 10, 400, C.text2, 'center')
    // 48px
    const f48 = F(p, 's48_' + k, x - 6, 184, 64, 64, TILE[k][0], 18)
    T(f48, 's48g_' + k, 0, 14, 64, 36, TILE[k][2], 27, 600, TILE[k][1], 'center')
    T(p, 's48n_' + k, x - 8, 254, 68, 16, names[i] + ' 48', 10, 400, C.text2, 'center')
  })
  // 色值标注
  order.forEach((k, i) => {
    const x = 24 + i * 84
    T(p, 'hexBg_' + k, x - 8, 300, 68, 14, TILE[k][0], 9, 400, C.text3, 'center')
    T(p, 'hexInk_' + k, x - 8, 316, 68, 14, TILE[k][1], 9, 400, C.text3, 'center')
  })
  T(p, 'sheetNote', 24, 356, 660, 20, '标准（OKLCH）：底 L=92~94% C=0.05~0.07 ｜ 字 L=38~45% C=0.10~0.12 —— 待用户裁决（见 token-diff.md）', 11, 400, C.text2)
  R(p, 'accentSample', 24, 388, 12, 12, C.accent, 6)
  T(p, 'accentNote', 44, 386, 400, 18, '强调色 #3B6FE0 · 全屏 ≤3 处 · glow-alpha 0.11 · 状态点 ≤8dp', 11, 500, C.text2)
  return p
}

// 布局：两张主态 + 两种文件夹 + 图标板
desktop(0, { name: 'DESK_A', folderMode: 'grid', showUnread: true, showGrayed: true })
desktop(450, { name: 'DESK_B', folderMode: 'folder', showUnread: true, showGrayed: true })
folderOpen(900)
iconSheet(1350)
