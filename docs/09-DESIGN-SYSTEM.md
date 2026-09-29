# 09 · 设计系统与 UI 规范（ViewPhone / 微光机）

> 依据：`00-CONSTITUTION.md` §二（单一事实源）。
> 核心诉求：用户第一眼要以为这是一台**真的手机**，而不是"一个网页套壳"。暗色优先 + 克制的辉光（微光）+ 拟真系统 UI 质感。
> 单一事实源：`design/tokens.json` → 生成 Compose Theme 与 Web CSS 变量。**两端禁止各自手写色值。**

---

## 1. 设计原则（5 条，各带可验证判据）

| # | 原则 | 判据 |
| :--- | :--- | :--- |
| P1 | **拟真优先** | 截图给非项目成员看，第一反应是"这是手机截图" |
| P2 | **微光是点缀，不是主题** | 全屏辉光元素 ≤ 2 处；任一屏的主色占比 ≤ 10% |
| P3 | **一切走 Token** | 源码里搜不到裸 `#RRGGBB`（lint 拦截）；`!important` 数量 = 0 |
| P4 | **动效克制且可关** | 所有动效在"减弱动态效果"开启后降为淡入淡出 |
| P5 | **双端同源** | 同一屏在 Android 与 Web 上的 Token 值逐项相同 |

> 前身教训：18 个 CSS 共 8,930 行、`!important` 164 次、硬编码色值 963 个、设计 token 只有 35 个定义、`index.html` 里 2,128 处行内 `style=`、z-index 手工编排到 999999。这些数字就是"没有设计系统"的代价。

---

## 2. 视觉方向与基底

**基底语言**：暗色为主 + **单一强调色**承担张力（源自 `superhuman` 的暗色氛围光范式），叠加拟真系统 UI 的结构（iOS 18 / Android 15 观感，4 级 surface 阶梯与发丝描边）。

**本机已有参考素材**（只取布局经验、不照抄）：`D:\deepseek\storyphone-ui\android-home.png`（桌面）、`app-home.png`、`chat.png`（气泡与输入栏）。

**微光的用法（关键约束）**：
- 用在 ① 未读/在线状态的小圆点发光；② 来电/语音通话的呼吸环；③ 桌宠脚下柔光；④ 主按钮的按下态。
- **禁止**：大面积霓虹描边、全局扫描线、赛博朋克风格堆砌。

---

## 3. 设计 Token（`design/tokens.json` 的结构）

### 3.1 语义色（浅色 / 深色两套，每色必须有对应 `on-` 色）
| Token | 深色（默认） | 浅色 | 用途 |
| :--- | :--- | :--- | :--- |
| `bg-base` | `#0B0C0F` | `#F7F8FA` | 最底层背景 |
| `bg-surface` | `#14161A` | `#FFFFFF` | 卡片/面板 |
| `bg-surface-2` | `#1B1E24` | `#F1F3F6` | 次级面板、输入栏 |
| `bg-elevated` | `#22262E` | `#FFFFFF` | 弹层/抽屉 |
| `text-primary` | `#F2F4F8` | `#12141A` | 主文本 |
| `text-secondary` | `#A8AFBC` | `#5A6172` | 次要文本/时间戳 |
| `text-tertiary` | `#6E7686` | `#8A90A0` | 占位符 |
| `outline` | `rgba(255,255,255,.08)` | `rgba(0,0,0,.08)` | 发丝描边 |
| `accent` | `#6E9BFF` | `#3B6FE0` | 强调（可用户自定义） |
| `accent-glow` | `rgba(110,155,255,.35)` | `rgba(59,111,224,.25)` | 微光 |
| `success` / `warning` / `error` | `#3DD68C` / `#FFB13D` / `#FF5C5C` | 同族降亮 | 状态 |

### 3.2 聊天专用色
| Token | 深色 | 用途 |
| :--- | :--- | :--- |
| `bubble-char` | `#22262E` | 角色气泡（左） |
| `bubble-user` | `#2E5BFF`（含 8% 微光） | 用户气泡（右） |
| `bubble-system` | 透明 + `text-tertiary` | 系统灰字（居中） |
| `bubble-withdrawn` | 透明 + 虚线描边 | 已撤回 |
| `unread-dot` | `error` + `accent-glow` | 未读红点 |
| `transfer-card` / `red-envelope` | 暖金 `#E8B45A` 系 | 转账/红包卡片（与普通气泡区分） |

### 3.3 形状 · 间距 · 字号 · 字重
```
radius:  xs=8  sm=12  md=16  lg=20  xl=28  full=999   (dp/px 1:1)
space :  2 4 8 12 16 20 24 32 40 48   (4pt 栅格，禁止任意值)
type  :  caption 11 / footnote 12 / body 15 / headline 17 / title 20 / largeTitle 24 / display 32
weight:  regular 400 · medium 500 · semibold 600（暗色下用 500/600 而非 700，避免发虚）
lineHeight: 1.35×（正文）/ 1.2×（标题）/ 1.15×（数字与计时）
```
字体：中文优先系统字体（HarmonyOS Sans / 苹方 / 思源黑体），数字与英文 Inter / SF Pro。

### 3.4 材质 · 阴影 · 动效
```
blur    : sheet 24 · overlay 32 · hud 16      alpha 0.6~0.8
shadow  : e1 0 1 2 rgba(0,0,0,.24) · e2 0 4 12 rgba(0,0,0,.32) · e3 0 12 32 rgba(0,0,0,.40)
motion  : fast 150ms · base 250ms · emphasis 350ms
spring  : dampingRatio 0.78 · stiffness 380（列表项入场 0.85 / 260）
```

### 3.5 强调色派生规则（用户自定义）
输入一个 `H`（色相）→ 以 `accent` 的 OKLCH `L/C` 为基准生成 → **二分搜索调整 `L` 直到与 `bg-surface` 的对比度 ≥ 4.5:1** → 派生 `accent-glow`（alpha 0.35）与 `on-accent`（按对比度自动取黑/白）。**禁止**直接让用户填任意色值。

---

## 4. 拟真外壳规范

| 元素 | 规格 |
| :--- | :--- |
| 手机外壳容器 | 圆角 44（设备形态）/ 全屏时圆角 0；安全区内边距 上下 12 / 左右 16 |
| 状态栏 | 高 44；左：时间（`title`）；右：信号 + Wi-Fi + 电量（18dp 图标，间距 6） |
| 灵动岛 / 胶囊通知 | 居中，宽 120 高 34（空闲）/ 展开时宽至 320、高 44；圆角 full |
| 图标网格 | **4 列**；行数由主题决定（薄秋 7 行 / 清透凉夏 5 行）；图标 60×60 圆角 16（M3 皮肤用圆形）；文字 11 |
| Dock | 高 78；背景毛玻璃 blur 24 + `outline`；4 个图标；不显示文字 |
| 翻页指示器 | 底部居中，6dp 圆点，当前页 `accent`，其余 `text-tertiary` @40% |
| 小组件 | 时钟 4×2 · 照片 2×2 · 横幅 4×3 · 搜索条 4×1；间距 12，圆角 20 |
| 两态切换 | 桌面态（外壳+壁纸+网格）/ 应用全屏态（隐藏 Dock 与网格，保留状态栏）；转场用共享元素，250ms spring |
| 壁纸层 | 最底 z=0；毛玻璃层在壁纸之上、内容之下；**主题不得用 `!important` 覆盖用户壁纸** |

> 前身教训：加一个桌面图标要改 17 个注册点、三处硬编码清单已漂移、主题 CSS 写成 JS 字符串无法 lint。本项目**桌面项由注册表驱动**（见 `02-ARCHITECTURE.md` §5.5），主题只是 Token 覆盖。

---

## 5. 聊天界面专项（产品门面）

| 项 | 规格 |
| :--- | :--- |
| 列表 | `LazyColumn(reverseLayout = true)` 底部锚定；**物理节点上限 100**；游标分页每次 30 条 |
| 气泡 | 最大宽 76%；圆角 lg，靠尾侧下角改 sm；连续同人消息合并（间距 4，非连续 12） |
| 气泡尾巴 | 仅每组的最后一条显示；方向随发送者 |
| 时间分隔 | 同一天不重复；相邻间隔 > 5 分钟显示时间；跨天显示"昨天/日期" |
| 状态标记 | 发送中（转圈）· 已送达（单勾）· 已读（双勾）· 失败（红色感叹号 + 重试） |
| 语音条 | 高 40；宽度随秒数（最短 80 / 最长 220）；波形 24 根柱；播放中柱体高亮 |
| 输入栏 | 高 52；左"+"扩展面板；文字/语音切换；发送按钮在输入非空时出现（150ms 缩放） |
| 扩展面板 | 红包 · 转账 · 位置 · 图片 · 表情包 · 语音通话 · 视频通话（六宫格，行高 72） |
| 引用块 | 气泡内顶部，左侧 2dp `accent` 竖线，灰底，最大 3 行省略 |
| Markdown | 支持粗体/斜体/行内代码/代码块/引用；**不支持 HTML**；渲染不得改变气泡宽度与换行 |
| 流式 | 打字机：按 token 追加（不做逐字，避免抖动）；**自动滚底仅在用户未上滑时生效**；上滑后显示"回到底部"胶囊（右下，`.elevated` + blur 16） |
| 长按菜单 | 引用 · 复制 · 撤回 · 多选 · 重新生成 · 翻译（毛玻璃浮层，锚定气泡） |
| 心声卡片 | 独立于气泡的斜体卡片，`bg-surface-2`，左侧微光竖条 |

> 前身教训：每条气泡强制 `setTimeout(1000ms)`（5 条回复要 5 秒）；`appendMessageToDOM` 每次插入全表排序构成 O(N²)；气泡里塞 base64 图片。本项目**流式只更新一条草稿消息、节流落库、图片走文件引用**。

---

## 6. `core:ui` 必需组件清单（21 项，每项 6 态）

**基础**：AppBar · TabBar · IconButton · PrimaryButton / SecondaryButton / TextButton · TextField / SearchField · Switch / Checkbox / Radio · Slider · Badge · Divider
**容器**：Card · BottomSheet · ModalDialog · ActionSheet · Toast / Snackbar · EmptyState · Skeleton · ErrorRetryCard
**复合**：Avatar（本地文件/占位/在线/模糊四态）· ChatBubble · MessageInput · MediaGrid · WalletCard

每项必须提供状态：`default / pressed / disabled / loading / empty / error`。
**验收**：做一个「组件画廊」页面把所有组件与状态可视化陈列（`04-ROADMAP.md` P4 验收项）。

---

## 7. 图片与视觉资产规格

| 档 | 尺寸 | 格式 | 用途 |
| :--- | :--- | :--- | :--- |
| orig | 原尺寸 | WebP q88（HEIF 仅作输入） | 查看大图、导出 |
| thumb | 长边 256 | WebP q72 | 列表/气泡缩略 |
| cover | 长边 1024 | WebP q80 | 详情页/朋友圈封面 |

图标：栅格 24（线性，线宽 1.75）与 20（填充）；**应用图标由注册表提供，禁止在 3 处各写一份**。桌宠素材 9 个状态名必须与产品定义一致（`default/happy/sad/angry/hesitant/wash/eat/sleep/watch`）。

---

## 8. 动效规范（12 个场景）

| 场景 | 时长 | 曲线 |
| :--- | :--- | :--- |
| 应用打开/关闭 | 250 / 200 | spring(0.78, 380) / ease-in |
| 桌面翻页 | 300 | spring(0.85, 300) |
| 抽屉与弹层 | 250 | spring(0.80, 350) |
| 气泡入场 | 180 | ease-out + 8dp 上移 |
| 列表项错峰 | 240（间隔 30，最多 6 项） | ease-out |
| 图标按压 | 120 | scale 0.94 |
| 未读红点出现 | 200 | scale 0→1.15→1 |
| 打字指示器 | 900 循环 | 三点错峰 opacity |
| 语音波形播放 | 实时 | — |
| 来电呼吸环 | 1600 循环 | accent-glow alpha 0.15↔0.45 |
| 通知胶囊展开 | 350 | spring(0.82, 320) |
| 主题切换 | 300 | 交叉淡入（不做颜色插值动画） |

「减弱动态效果」开启后：全部改为 150ms 淡入淡出，关闭循环类动效（呼吸环、打字点改为静态）。

---

## 9. 双端一致性（单一事实源）

```
design/tokens.json  ──► tools/gen-tokens  ──┬──► Android: core/ui/.../Theme.kt + Color.kt + Type.kt
                                            └──► Web: src/styles/tokens.css (CSS 变量) + tailwind.config.ts
```
- Token 文件进版本控制；生成物也进版本控制，**CI 校验"改 token 后重新生成无差异"**（防止有人手改生成物）。
- 视觉回归：关键 5 屏（桌面 / 聊天 / 群聊 / 设置 / 我的）在两端各截一张图，人工比对（M1~M3 阶段足够；M4 起可选 Playwright 截图对比）。
- **允许的不一致**：滚动惯性、返回手势、系统字体回退、原生控件（日期选择器/分享面板）。

---

## 10. 无障碍与适配
- 动态字号：跟随系统，正文上限 1.3×（再大改用滚动而非截断）。
- 对比度：正文 ≥ 4.5:1，大字 ≥ 3:1（强调色派生算法已带闸门）。
- RTL：镜像布局，气泡方向随语言而非仅随发送者。
- 横屏：Tier A 应用仅竖屏（与产品形态一致）；设置/阅读允许横屏。
- 平板/桌面浏览器：外壳居中，最大宽 480，两侧留暗背景；不做多列重排。
- iOS 安全区：`env(safe-area-inset-*)`；刘海/灵动岛区域不得放可点元素。

---

## 11. 反面清单（前身视觉债 → 本项目对策）

| 前身事实 | 本项目对策 |
| :--- | :--- |
| `index.html` 2,128 处行内 `style=` | 禁止行内样式；lint 拦截 |
| 18 个 CSS 互相覆盖、`!important` 164 次 | 原子化/组件化样式；**`!important` 数量必须为 0** |
| 硬编码色值 963 个 | 全部走 Token；源码搜不到裸色值 |
| 设计 token 仅 35 个定义且各文件不共享 | `tokens.json` 单一来源 + 生成两端产物 |
| z-index 手工编排到 999999 | 定义 6 层层级常量（base/shell/app/overlay/sheet/hud），禁止任意值 |
| 主题 CSS 写在 JS 字符串里，无法 lint/HMR | 主题 = Token 覆盖，样式参与编译 |
| 弹窗样式 3 套近似实现并存 | 收敛为 `BottomSheet` + `ModalDialog` + `ActionSheet` 三个组件 |
| 13 处仍有原生 `alert/confirm` | 禁止平台原生弹窗，一律走 `core:ui` 组件（lint 拦截） |
