---
name: 'viewphone-minimal-light'
tags: [light-mode, minimal, blue-accent, mobile, rounded, clean, cjk-type]
platform: mobile
---

> **单一事实源声明**：色值 / 圆角 / 间距 / 字号的**唯一事实源是 `design/tokens.json`**。
> 本文件的 YAML frontmatter 与下表必须与它保持一致；Compose Theme 与 Web CSS 变量由它生成。
> 修改顺序：先改 `tokens.json` → 再同步本文件 → 最后重新生成两端产物。禁止手写色值。

## Style Scope

本规范只服务「微光机 ViewPhone」这一款产品，不外借、不混搭其他风格。未命名的布局 frame 默认是**结构性**的（无填充、无描边、无圆角、无阴影）；只有卡片、输入框、按钮、徽标、图标块、导航面这些**有意可见的组件**才给填充与圆角。禁止给组件套装饰性外壳——层级靠间距、字阶和明确的表面来表达。

## Style Summary

一台**像真手机**的 AI 聊天应用外壳。底色是接近白的冷灰（`#F5F6F8`），不是纯白也不是暖白，长时间看不刺眼；表面是纯白卡片，靠**发丝描边**（`#E6E9EF`）而不是重阴影来分界；全屏只有**一个**强调色 `#3B6FE0`，且只出现在三处（状态点、发送按钮、激活项）。

字阶走"大时钟 + 小标签"的对比：桌面顶部是 76px 极细字重（200）的大字时间，其余信息压到 11–15px。这种强对比让界面看起来**克制、干净、明亮**，而不是塞满控件。

图标**一律矢量 SVG**（24 栅格 / 线宽 1.75 / 圆头圆角），**禁止 emoji**。应用入口用一个浅彩底 + 同色系深色字形来区分，但那是**图标块内部**的处理，不得外溢成第二个强调色。

关键美学：

- **冷灰底 + 纯白面**：`#F5F6F8` / `#FFFFFF`，靠 1px `#E6E9EF` 发丝描边分层
- **单一强调色**：只有 `#3B6FE0`，且 ≤ 3 处
- **微光 = 高亮度小面积**：状态点、流式光标、按钮按下态；**全屏辉光元素 ≤ 2 处**
- **大留白**：屏幕左右 20px，区块间距 24px，图标网格行距 116px
- **图标矢量**：24 栅格线性图标，绝不使用 emoji

## Color System

### Core Backgrounds

| Token | Value | Usage |
| --- | --- | --- |
| Page Background | `#F5F6F8` | 根屏幕底色（冷灰，非纯白） |
| Card Surface | `#FFFFFF` | 卡片、列表容器、气泡（角色侧） |
| Inset Surface | `#EEF1F6` | 搜索条、输入框内部 |
| Shell Surface | `#FFFFFF` | Dock、导航栏、输入栏 |

### Text Colors

| Token | Value | Usage |
| --- | --- | --- |
| Primary Text | `#1B1F27` | 标题、时间、气泡正文 |
| Secondary Text | `#6B7280` | 正文说明、图标名称 |
| Tertiary Text | `#9AA2AF` | 时间戳、占位符、未激活项 |
| On Accent | `#FFFFFF` | 强调色块上的文字 |

### Border & Accent

| Token | Value | Usage |
| --- | --- | --- |
| Default Border | `#E6E9EF` | 卡片描边、分隔线、输入框描边 |
| Strong Border | `#D3D9E2` | 需要更明确分界时 |
| **Primary Accent** | `#3B6FE0` | **仅三处**：状态点 / 发送按钮 / 激活项 |
| Accent Pressed | `#2F5AC4` | 按钮按下态 |
| Accent Soft | `#EAF0FF` | 强调色的极浅底（谨慎使用，不作为普通卡片底） |
| Glow (18%) | `#3B6FE0` @ 0.18 | 状态点外的一圈微光 |
| Success | `#3DD68C` | 电量、成功 |
| Warning | `#F5A524` | 待处理 |
| Error | `#E5484D` | 失败、删除 |

### App Entry Tiles（仅图标块内部）

| 入口 | 底 | 字/图标 |
| --- | --- | --- |
| 聊天 | `#E8F0FF` | `#2A5BD7` |
| 角色 | `#FDEEE6` | `#C2410C` |
| 世界 | `#E9F6EF` | `#12805C` |
| 记忆 | `#F1ECFD` | `#6D45D0` |
| 音乐 | `#FDEBF3` | `#C22F72` |
| 书城 | `#EAF4FD` | `#1B6FB8` |
| 相册 | `#FBF3E4` | `#A8781A` |
| 设置 | `#EFF1F5` | `#4A5261` |

## Typography

### Font Families

| Role | Family | Usage |
| --- | --- | --- |
| 中文 | HarmonyOS Sans SC → PingFang SC → Source Han Sans SC → Noto Sans CJK SC | 全部中文（系统字体优先，不打包字体文件） |
| 数字 / 英文 | Inter → SF Pro Text → Segoe UI | 时间、数字、英文标签 |
| 等宽 | JetBrains Mono → SF Mono | 仅调试面板与 Token 显示 |

### Type Scale

| Level | Size | Weight | Line Height | Letter Spacing | Usage |
| --- | --- | --- | --- | --- | --- |
| Display | 76px | 200 | 1.0 | -1.5px | 桌面大字时间 |
| Large Title | 30px | 600 | 1.2 | -0.4px | 屏幕主标题（如"微光机"） |
| Title | 22px | 600 | 1.25 | -0.3px | 区块标题、卡片大标题 |
| Headline | 17px | 600 | 1.3 | -0.2px | 列表主文本、导航标题 |
| Body | 15px | 400 | 1.5 | 0 | 气泡正文、按钮文字 |
| Label | 13px | 500 | 1.4 | 0 | 次级按钮、字段标签 |
| Caption | 12px | 400 | 1.4 | 0 | 时间戳、辅助信息 |
| Micro | 11px | 400 | 1.35 | 0 | 图标名称、角标 |
| Icon Glyph | 27px | 600 | 1.0 | 0 | 图标块内的字形（SVG 就位后废弃） |

## Spacing System

### Scale（4pt 栅格，禁止任意值）

`0 · 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48`

### Semantic

| Token | Value | Usage |
| --- | --- | --- |
| screenPaddingX | 20px | 屏幕左右内边距 |
| sectionGap | 24px | 区块之间 |
| cardPadding | 16px | 卡片内边距 |
| listRowGap | 12px | 列表行间 |
| iconToLabel | 8px | 图标与名称 |
| statusBarHeight | 44px | 状态栏 |
| navBarHeight | 62px | 导航栏 |
| inputBarHeight | 96px | 输入栏 |
| dockHeight | 84px | Dock |

### Layout Pattern

- 画布：390 × 844（外壳圆角 40）
- 内容区：水平内边距 20，垂直从状态栏下方开始
- 图标网格：4 列，单元 82px，图标块 66px，行距 116px
- 桌面翻页点：底部居中，6px 圆点，当前页用强调色

## Corner Radius

| Token | Value | Usage |
| --- | --- | --- |
| xs | 8px | 小标签、角标 |
| sm | 10px | 小按钮 |
| md | 14px | 卡片、按钮、输入框 |
| lg | 18px | 气泡、图标块 |
| xl | 22px | 搜索条、大按钮 |
| dock | 30px | 底部 Dock |
| shell | 40px | 手机外壳 |
| full | 9999px | 头像、状态点（**只用于真正的圆**） |

> 规则：`full` 只给头像、状态点、短徽标；**绝不**用于卡片、搜索外壳、导航条。

## Elevation & Depth

浅色界面靠**描边**分层，阴影只留给弹层。

| Level | Treatment | Usage |
| --- | --- | --- |
| 0 | 无 | 默认表面（靠描边区分） |
| 1 | `0 1px 2px rgba(17,20,24,.06)` | 卡片可选轻微浮起 |
| 2 | `0 4px 12px rgba(17,20,24,.08)` | 浮层、"回到底部"胶囊 |
| 3 | `0 12px 32px rgba(17,20,24,.12)` | 模态、抽屉 |

## 微光（Glow）规范

**微光 = 高亮度小面积点缀，不是扩散光晕。**

| 允许 | 规格 |
| --- | --- |
| 未读 / 在线状态点 | 直径 8–10px，实心强调色；可加一圈 18% 透明度同色光环（直径 ×2.2） |
| 流式打字光标 | 2 × 18px 竖条，实心强调色，跟随文本末位 |
| 发送按钮 | 44px 圆形实心强调色（按下时叠加 16% 光环） |

**绝对禁止**：大面积发光背景、霓虹描边、全局扫描线、赛博朋克堆砌。
**硬约束**：全屏辉光元素 ≤ **2 处**。

## Icons

**一律矢量 SVG。禁止 emoji 当图标，禁止位图图标。**

| 项 | 规格 |
| --- | --- |
| 栅格 | 24 × 24 |
| 线宽 | 1.75 |
| 端点 / 拐角 | round / round |
| 尺寸 | inline 14 · list 18 · action 20 · header 24 · 应用入口 26 |
| 颜色态 | active `#3B6FE0` · default `#6B7280` · muted `#9AA2AF` · onAccent `#FFFFFF` |

### 应用入口图标集（8 个，必须成套）

| id | 图形 | 说明 |
| --- | --- | --- |
| `chat` | 对话气泡 | 圆角矩形 + 左下小尾巴 |
| `character` | 人像 | 圆（头）+ 肩线弧 |
| `world` | 地球 | 圆 + 一条纬线 + 一条经线 |
| `memory` | 星标 | 四角星 / 书签形 |
| `music` | 音符 | 符头圆 + 符干 |
| `reader` | 翻开的书 | 两页弧线 + 中缝 |
| `gallery` | 照片 | 圆角矩形 + 山形 + 小圆（太阳） |
| `settings` | 齿轮 | 圆 + 六齿 |

> 本设计稿（`design/samples/S1.png`）里图标块内是**汉字字形占位**，实施时替换为上述 SVG。
> 切换时必须**整套替换**，不得同一入口出现两套图标。

## Components

### 桌面

- **搜索条**：高 40，圆角 20，白底，Micro 级提示文字；不使用第二条嵌套外壳
- **大字时钟**：Display（76/200）；日期 Caption 12；直接落在页面上，无卡片
- **图标块**：66 × 66，圆角 18，浅彩底（见 App Entry Tiles），内部 SVG 26px
- **图标名称**：Micro 11 / `#6B7280`，居中，距图标 8px
- **Dock**：高 84，圆角 30，白底，4 个 60×60 图标块（圆角 17）

### 单聊

- **导航栏**：高 62，白底，左侧返回箭头（强调色），头像 36 圆，标题 Headline 17 + 状态 Caption 12，右侧在线点 8px
- **时间分隔**：Micro 11 居中，`#9AA2AF`
- **角色气泡**：白底 + 1px `#E6E9EF` 描边，圆角 18，内边距 16/14，正文 Body 15 / `#1B1F27`
- **用户气泡**：`#3B6FE0` 实心，圆角 18，文字 `#FFFFFF`
- **气泡最大宽**：`W - 138`（约 65%）
- **输入栏**：高 96，白底，上边 1px 描边；输入框高 44 圆角 22 内底 `#F5F6F8`；发送按钮 44 圆

## Do's and Don'ts

### Do

- 用 `#F5F6F8` 冷灰底 + 纯白卡片 + 1px 发丝描边来建立层级
- 强调色只用 `#3B6FE0`，且全屏 ≤ 3 处
- 微光只做**高亮度小面积**（状态点 / 光标 / 按钮按下态）
- 应用入口图标**成套**使用 24 栅格线性 SVG
- 大留白：左右 20，区块间距 24

### Don't

- ❌ 不要用暗色背景（本设计是**浅色为主**）
- ❌ 不要高饱和刺眼的色块
- ❌ 不要莫兰迪式的灰调低饱和（会显得脏、没精神）
- ❌ 不要 emoji 当图标
- ❌ 不要参考任何外部项目或旧项目素材
- ❌ 不要扩散光晕 / 霓虹描边 / 大面积发光
- ❌ 不要在源码里写裸色值 —— 一律取自 `design/tokens.json`
