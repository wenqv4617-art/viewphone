// 重建 color 段（数据驱动，结构一定正确）
import { readFileSync, writeFileSync } from 'node:fs'
const p = 'D:/deepseek/viewphone/design/tokens.json'
const raw = readFileSync(p, 'utf8')

const colorBlock = `  "color": {
    "light": {
      "bg-base": "#F5F6F8",
      "bg-surface": "#FFFFFF",
      "bg-surface-2": "#EEF1F6",
      "bg-elevated": "#FFFFFF",
      "text-primary": "#1B1F27",
      "text-secondary": "#6B7280",
      "text-tertiary-decorative-only": {
        "value": "#9AA2AF",
        "rule": "禁止用于文字（对比度 2.57:1）。仅限分隔线/占位图形等装饰性元素。",
        "textInstead": "#6B7280",
        "textInsteadNote": "#6B7280 对表面 4.83:1 达标；对底色 #F5F6F8 为 4.47:1（差 0.03）。若要求任何底色下都 ≥4.5:1，改用 #686F7B（5.06/4.68）或 #666D78（5.22/4.83）。"
      },
      "outline": "#E6E9EF",
      "outline-strong": "#D3D9E2",
      "accent": "#3B6FE0",
      "accent-pressed": "#2F5AC4",
      "accent-soft": "#EAF0FF",
      "on-accent": "#FFFFFF",
      "glow": "#3B6FE0",
      "glow-alpha": 0.11,
      "unread-dot": "#E5484D",
      "success": "#3DD68C",
      "warning": "#F5A524",
      "error": "#E5484D"
    },
    "chat": {
      "bubble-char-bg": "#FFFFFF",
      "bubble-char-text": "#1B1F27",
      "bubble-char-outline": "#E6E9EF",
      "bubble-user-bg": "#22262B",
      "bubble-user-text": "#FFFFFF",
      "bubble-user-note": "强调色不用于气泡（符合提示词第三条）",
      "bubble-system-text": "#6B7280",
      "bubble-divider": "#E6E9EF",
      "typing-caret": "#3B6FE0"
    },
    "app-tiles": {
      "note": "每个应用入口一个浅彩底 + 同色系深字。以 hex 为准（对比度 4.84~6.97:1 全部达标）；OKLCH 仅为参考区间，其值为 sRGB 可达范围内的近似，禁止为凑标准改色致艳。",
      "oklchReference": {
        "standard": "底 L=92~94% C=0.05~0.07 ｜ 字 L=38~45% C=0.10~0.12",
        "caveat": "部分色相在 sRGB 内达不到 C=0.05~0.07（蓝 248° 上限≈0.036、紫 250°≈0.036、砖红 57°≈0.048）",
        "authority": "hex"
      },
      "chat-app": { "bg": "#E3EBFA", "ink": "#2B4C8C", "contrast": "6.97:1" },
      "character": { "bg": "#D7EFDD", "ink": "#2F6B4A", "contrast": "5.20:1", "changedFrom": "#EAF3EC" },
      "world": { "bg": "#EAE5F4", "ink": "#5A3E96", "contrast": "6.63:1", "changedFrom": "#F2EDFA" },
      "memory": { "bg": "#E6F1F3", "ink": "#26606B", "contrast": "6.15:1" },
      "music": { "bg": "#F9E8F0", "ink": "#8C2F63", "contrast": "6.60:1" },
      "reader": { "bg": "#FBEDE6", "ink": "#8C4A2B", "contrast": "5.87:1" },
      "gallery": { "bg": "#F1E7CE", "ink": "#7A6023", "contrast": "4.84:1", "changedFrom": "#F7F0DF" },
      "settings": { "bg": "#EDEFF3", "ink": "#4A5261", "contrast": "6.83:1" }
    }
  },

  "contrast": {
    "note": "正文 ≥4.5:1，大字 ≥3:1。实测值，改动色值后必须重算。",
    "pairs": [
      { "scene": "正文 / 底色", "fg": "#1B1F27", "bg": "#F5F6F8", "ratio": "15.27:1", "pass": true },
      { "scene": "正文 / 表面", "fg": "#1B1F27", "bg": "#FFFFFF", "ratio": "16.51:1", "pass": true },
      { "scene": "次要 + 时间戳 + 说明小字 / 表面", "fg": "#6B7280", "bg": "#FFFFFF", "ratio": "4.83:1", "pass": true },
      { "scene": "次要 + 时间戳 + 说明小字 / 底色", "fg": "#6B7280", "bg": "#F5F6F8", "ratio": "4.47:1", "pass": false, "fix": "改用 #686F7B（4.68:1）或 #666D78（4.83:1）" },
      { "scene": "强调色 / 表面", "fg": "#3B6FE0", "bg": "#FFFFFF", "ratio": "4.63:1", "pass": true },
      { "scene": "白字 / 用户气泡", "fg": "#FFFFFF", "bg": "#22262B", "ratio": "15.22:1", "pass": true },
      { "scene": "装饰性（禁止文字）/ 表面", "fg": "#9AA2AF", "bg": "#FFFFFF", "ratio": "2.57:1", "pass": false, "fix": "禁止用于文字" }
    ]
  },

`

// 定位 "color": { 到 "typography": { 之间，整段替换
const startIdx = raw.indexOf('  "color": {')
const endIdx = raw.indexOf('"typography": {')
if (startIdx < 0 || endIdx < 0) { console.error('定位失败'); process.exit(1) }
const out = raw.slice(0, startIdx) + colorBlock + raw.slice(endIdx)

try {
  const j = JSON.parse(out)
  writeFileSync(p, out, 'utf8')
  console.log('✓ JSON 语法 OK')
  console.log('顶层键:', Object.keys(j).join(', '))
  console.log('color 子键:', Object.keys(j.color).join(', '))
  console.log('8 套色块:', Object.keys(j.color['app-tiles']).filter((k) => j.color['app-tiles'][k].bg).join(', '))
} catch (e) {
  console.error('✗ 重建后仍失败:', e.message)
  process.exit(1)
}
