// 桌面编辑视图 ×2（含壁纸三来源）
// 规则：卡片/图标块/气泡用 frame 承载文字；角标放 72×72 槽位（已验证）
const W = 390, H = 844
const C = {
  bg: '#F5F6F8', surface: '#FFFFFF', surface2: '#EEF1F6',
  text: '#1B1F27', text2: '#6B7280', text3: '#9AA2AF', // text3 仅装饰
  outline: '#E6E9EF', accent: '#3B6FE0', danger: '#E5484D',
}
// 修正后 8 套（character/world/gallery 已按决策①调整）
const TILE = {
  chat: ['#E3EBFA', '#2B4C8C', '聊'], character: ['#D7EFDD', '#2F6B4A', '角'],
  world: ['#EAE5F4', '#5A3E96', '世'], memory: ['#E6F1F3', '#26606B', '忆'],
  music: ['#F9E8F0', '#8C2F63', '乐'], reader: ['#FBEDE6', '#8C4A2B', '书'],
  gallery: ['#F1E7CE', '#7A6023', '相'], settings: ['#EDEFF3', '#4A5261', '设'],
}

const NAME = { chat: '聊天', character: '角色', world: '世界', memory: '记忆', music: '音乐', reader: '书城', gallery: '相册', settings: '设置' }
const T = (p, n, x, y, w, h, c, s, fw, fill, al) => I(p, { type: 'text', name: n, x, y, width: w, height: h, content: c, fontSize: s, fontWeight: fw, textAlign: al || 'left', fill })
const R = (p, n, x, y, w, h, fill, r) => I(p, { type: 'rectangle', name: n, x, y, width: w, height: h, fill, cornerRadius: r })
const F = (p, n, x, y, w, h, fill, r) => I(p, { type: 'frame', name: n, x, y, width: w, height: h, fill, cornerRadius: r })

// 图标槽位：容器 72×72 / 图标 60×60 / 角标落容器右上角外侧
function slot(parent, key, x, y, opts) {
  const o = opts || {}
  const S = 72, IC = 60, off = 6
  const box = F(parent, 'slot_' + key + (o.sfx || ''), x, y, S, S, '#00000000', 0)
  const [bg, ink, g] = TILE[key]
  const gray = !!o.gray
  const t = F(box, 'ic_' + key + (o.sfx || ''), off, off, IC, IC, gray ? '#F0F1F3' : bg, 16)
  T(t, 'gl_' + key + (o.sfx || ''), 0, 14, IC, 30, g, 25, 600, gray ? '#B9BEC7' : ink, 'center')
  if (o.dot) {
    const d = 15
    R(box, 'dot_' + key + (o.sfx || ''), S - d - 1, 1, d, d, C.danger, d / 2)
    T(box, 'dotT_' + key + (o.sfx || ''), S - d - 1, 4, d, 13, String(o.dot), 9, 600, '#FFFFFF', 'center')
  }
  if (gray) {
    const d = 18
    R(box, 'dev_' + key + (o.sfx || ''), S - d - 1, S - d - 1, d, d, C.bg, d / 2)
    R(box, 'devIn_' + key + (o.sfx || ''), S - d + 1, S - d + 1, d - 4, d - 4, '#C9CDD4', (d - 4) / 2)
  }
  return box
}

// ===== 编辑视图 1：放缩进入编辑态（图标抖动 + 操作条） =====
function editMode(ox) {
  const p = F(null, 'EDIT_MODE', ox, 60, W, H, C.bg, 40)
  // 顶部编辑提示条
  T(p, 'emTime', 26, 16, 70, 22, '9:41', 15, 600, C.text)
  T(p, 'emHint', 0, 56, W, 24, '编辑桌面', 15, 600, C.accent, 'center')
  T(p, 'emSub', 0, 80, W, 20, '双指放缩可退出', 11, 400, C.text2, 'center')

  // 图标网格（带"抖动"暗示：轻微旋转由实施层做，这里用虚线框示意可拖）
  const gx = 38, gy = 130, cell = 82, rowH = 118
  const order = ['chat', 'character', 'world', 'memory', 'music', 'reader', 'gallery', 'settings']
  order.forEach((k, i) => {
    const col = i % 4, row = (i - col) / 4
    const x = gx + col * cell, y = gy + row * rowH
    slot(p, k, x, y, { sfx: '_e', dot: k === 'chat' ? 3 : 0, gray: k === 'gallery' })
    T(p, 'el_' + k, x + 6, y + 74, 60, 18, NAME[k], 11, 400, C.text2, 'center')
    // 删除角标（编辑态才有）
    const d = 20
    R(p, 'del_' + k, x + 50, y + 2, d, d, '#3A3F47', d / 2)
    T(p, 'delT_' + k, x + 50, y + 5, d, 14, '×', 12, 600, '#FFFFFF', 'center')
  })

  // 底部操作条（编辑态专属）
  const bar = F(p, 'editBar', 20, H - 176, W - 40, 64, C.surface, 20)
  T(bar, 'barB1', 0, 10, 110, 20, '换壁纸', 12, 500, C.text, 'center')
  T(bar, 'barB2', 110, 10, 110, 20, '排序', 12, 500, C.text, 'center')
  T(bar, 'barB3', 220, 10, 110, 20, '建文件夹', 12, 500, C.text, 'center')
  T(bar, 'barD1', 0, 38, 110, 18, '壁纸与主题', 10, 400, C.text2, 'center')
  T(bar, 'barD2', 110, 38, 110, 18, '拖动调整', 10, 400, C.text2, 'center')
  T(bar, 'barD3', 220, 38, 110, 18, '拖入两个图标', 10, 400, C.text2, 'center')

  // 底部：完成按钮
  const done = F(p, 'doneBtn', 20, H - 96, W - 40, 48, C.accent, 14)
  T(done, 'doneT', 0, 12, W - 40, 24, '完成', 15, 600, '#FFFFFF', 'center')
  return p
}

// ===== 编辑视图 2：组件 / CSS 编辑器 =====
function cssEditor(ox) {
  const p = F(null, 'CSS_EDITOR', ox, 60, W, H, C.bg, 40)
  T(p, 'ceTime', 26, 16, 70, 22, '9:41', 15, 600, C.text)
  T(p, 'ceTitle', 20, 48, 300, 30, '组件与自定义样式', 20, 600, C.text)

  // 组件尺寸四档
  T(p, 'ceSizeLabel', 20, 88, 200, 20, '组件尺寸', 12, 500, C.text2)
  ;['1×1', '2×1', '2×2', '4×2'].forEach((s, i) => {
    const on = i === 2
    const b = F(p, 'sizeBtn' + i, 20 + i * 88, 112, 80, 34, on ? C.accent : C.surface, 10)
    T(b, 'sizeT' + i, 0, 8, 80, 20, s, 12, on ? 600 : 500, on ? '#FFFFFF' : C.text, 'center')
  })

  // 预制组件（4 个）
  T(p, 'cePreLabel', 20, 162, 200, 20, '预制组件', 12, 500, C.text2)
  const PRE = [['时钟', '4×2'], ['照片', '2×2'], ['横幅', '4×3'], ['搜索条', '4×1']]
  PRE.forEach((c, i) => {
    const col = i % 2, row = (i - col) / 2
    const x = 20 + col * 176, y = 186 + row * 76
    const f = F(p, 'pre' + i, x, y, 168, 64, C.surface, 14)
    T(f, 'preN' + i, 14, 12, 100, 22, c[0], 14, 600, C.text)
    T(f, 'preS' + i, 14, 36, 100, 18, c[1], 11, 400, C.text2)
  })

  // CSS 编辑区
  T(p, 'ceCssLabel', 20, 350, 200, 20, '自定义 CSS', 12, 500, C.text2)
  const code = F(p, 'codeBox', 20, 374, W - 40, 168, '#F2F4F7', 12)
  const lines = [
    '/* 只允许本地资源；禁止 @import / 外链 url() */',
    '.vp-clock { font-size: 68px; font-weight: 200; }',
    '.vp-clock { color: var(--text-primary); }',
    '.vp-dock  { border-radius: 30px; }',
  ]
  lines.forEach((l, i) => T(code, 'code' + i, 14, 12 + i * 22, W - 110, 20, l, 11, 400, '#3F4650'))
  T(code, 'codeCount', W - 150, 138, 120, 18, '1.2 KB / 64 KB', 10, 400, C.text2, 'right')

  // 四件套状态：清洗结果 + 上限 + 还原
  const status = F(p, 'cssStatus', 20, 550, W - 40, 44, '#EAF0FF', 12)
  T(status, 'stT', 14, 6, W - 70, 18, '清洗通过：无外链、无 @import、无 position:fixed', 11, 500, '#2B4C8C')
  T(status, 'stS', 14, 24, W - 70, 16, '失败会显式提示，不会静默丢弃', 10, 400, '#3F5FA8')

  const ops = F(p, 'cssOps', 20, 602, W - 40, 44, C.surface, 12)
  T(ops, 'opT', 14, 12, 200, 20, '实时预览已开启', 11, 500, C.text)
  T(ops, 'opR', W - 160, 12, 146, 20, '还原默认样式', 11, 500, C.accent, 'right')

  // 壁纸选择入口（三来源）
  T(p, 'wpLabel', 20, 662, 200, 20, '壁纸来源', 12, 500, C.text2)
  const SRC = [['内置', '纯色 / 渐变'], ['相册导入', '系统选择器'], ['内置图库', '复用已有图']]
  SRC.forEach((c, i) => {
    const x = 20 + i * 120
    const f = F(p, 'wp' + i, x, 686, 112, 68, C.surface, 12)
    R(f, 'wpSwatch' + i, 12, 12, 40, 24, i === 0 ? '#E3EBFA' : i === 1 ? '#D7EFDD' : '#F1E7CE', 6)
    T(f, 'wpN' + i, 12, 42, 92, 18, c[0], 11, 600, C.text)
  })
  T(p, 'wpNote', 20, 760, W - 40, 18, '相册导入用系统照片选择器，不申请读相册权限', 10, 400, C.text2)
  T(p, 'wpNote2', 20, 778, W - 40, 18, '图片落私有目录文件，库只存路径 + 元数据', 10, 400, C.text2)
  return p
}

editMode(0)
cssEditor(450)
