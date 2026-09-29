# 实施决策记录（ADR）

> 规则：**只记录已经拍板、并且有证据支撑的决定。** 未验证的猜测一律不写进这里（写在 `docs/P0-TOOLCHAIN-BASELINE.md` 的「未验证」表里）。
> 每条决策必须写明：背景 / 证据 / 结论 / 复查点。没有复查点的决策视为不完整。

---

## DEC-012｜**不做酒馆（SillyTavern）格式兼容**——体验相似，格式无关

> 背景：项目重定调（2026-09-29）覆盖此前的项目理解与排期，口径见 `docs/README.md` §〇 与 `CRITICAL.md` §0.1。
> 本条是重定调的「绝对不做」之一。

- **日期**：项目重定调当日
- **状态**：已生效（**绝对不做**，写入 `docs/README.md` §〇 与 `CRITICAL.md` §0.1）
- **背景**：本项目核心是「类酒馆的 AI 聊天」：角色卡（档案 / 人设）、世界书、多轮长对话、
  长周期记忆、多套 API 配置；形态是拟真手机 OS 外壳。用户诉求原话：「像手机一样跟 AI 好友交互」。
- **结论**：
  1. **不导入角色卡 PNG**（不做酒馆的元数据嵌图规范）；
  2. **不兼容世界书 JSON**（不匹配其字段与结构）；
  3. **不匹配酒馆任何字段/文件名/导出格式**；
  4. 我们做的是**同等体验的自有模型**：角色档案、世界书、对话与记忆都用自己的 schema 与存储。
- **为什么写进决策**：避免后续任何一轮"顺手兼容一下"把格式耦合引进来——
  一旦兼容，字段归属、迁移、校验都会被迫跟着对方的节奏走。
- **复查点**：若将来用户明确要求"导入酒馆角色卡"，**必须先改本条并单独评估**（不得作为顺手功能实现）。
  届时它是**一次性导入器**，而不是运行时格式依赖。

## DEC-013｜模块策略改为「**用到哪个建哪个**」，只保留一条模块边界检查

- **日期**：项目重定调当日
- **状态**：已生效（**作废旧排期**）
- **背景**：旧口径要求 P0 建出 20 个空模块骨架 + 5 条 Detekt 自定义规则。
  重定调明确：**不再产出「骨架 / 框架 / 门禁 / CI / 规则」轮次；与业务无关的一律不做**，
  每轮只做一个**小模块 + 其测试**。
- **结论**：
  1. **删除**「20 个空模块骨架」与「Detekt 5 条规则」两项待办；
  2. 模块**按需创建**：做到哪个功能才建哪个模块；
  3. **只保留一条模块边界检查**：根 `build.gradle.kts` 里已有的
     「生产模块不得依赖 `toolchain-probe`」（配置期抛错，已自证会红）；
  4. `shared` 单模块 + 多源集的结构不变（它是"唯一实现"的载体，不是空骨架）。
- **复查点**：当功能模块数量增长到「改一行要全量重编」或「有人开始跨 feature 直接 import」时，
  再评估是否引入依赖方向检查——**按需**，不预先铺开。

## DEC-014｜视觉先行：设计系统先于界面代码

- **日期**：项目重定调当日
- **状态**：进行中
- **背景**：第一版界面绝不允许是「充满各种 emoji 的 UI 小废物」。
- **结论（硬约束，后续所有界面代码必须满足）**：
  1. **图标一律矢量**；**禁止 emoji 当图标**；
  2. **禁止硬编码颜色**：颜色只能来自 `design/tokens.json` 的生成物；
  3. 风格：暗色为主 + 单一强调色 + 微光点缀；**全屏辉光元素 ≤ 2 处**；**主色占比 ≤ 10%**；
  4. 圆角 / 间距 / 字号**全部取自 tokens.json**，不写任意值；
  5. **设计未获用户确认前，不写任何界面代码**。
- **复查点**：界面代码评审时按上述 5 条逐条对照；新增色值/尺寸必须先改 `tokens.json` 再重新生成。

---

## DEC-015｜视觉方向定为「极简留白」（浅色为主 + 单一强调色 + 微光点缀）

- **日期**：视觉先行阶段
- **状态**：已拍板（产品方在 1/2/3 三个结构方向中选定**方向 1**）
- **背景**：
  1. 原「暗色 + 外部设计系统基底」被否决；产品方否掉**暗色 / 高饱和 / 莫兰迪**，并**禁止参考任何外部项目或旧项目素材**；
  2. 我先后交过两批稿：第一批是"同结构换色"（被否），第二批给出**三种结构不同**的方向：
     ① 极简留白（背景 + 图标层）② 卡片仪表盘 ③ 分组列表；
  3. 产品方选定 **①极简留白**，并明确**色彩沿用 A 色系**。
- **结论**：
  - **设计方向**：极简留白。底色冷灰 `#F5F6F8`（非纯白、非暖白），表面纯白，1px 发丝描边分层；
  - **强调色**：**只有一个** `#3B6FE0`，全屏 ≤ 3 处（状态点 / 发送按钮 / 激活项）；
  - **微光**：高亮度小面积点缀，**全屏辉光元素 ≤ 2 处**，禁止扩散光晕；
  - **图标**：**一律矢量 SVG**（24 栅格 / 线宽 1.75 / 圆头圆角），**禁止 emoji、禁止位图**；
  - **唯一事实源**：`design/tokens.json`；规范文档 `design/DESIGN-SYSTEM.md`（带 YAML frontmatter，可被 `op styles` 读取）；
  - **设计稿**：`design/samples/S1.png`（桌面）、`design/samples/S1-chat.png`（单聊）。
- **复查点**：
  1. `docs/09 §2` 已重写为指针，**不再承载色值**；若发现任何文档仍写色值，视为违规；
  2. 实施界面时按 `DESIGN-SYSTEM.md` 的 Do/Don't 逐条对照；
  3. 图标成套切换（8 个入口一次换齐），禁止同一入口出现两套图标。

## DEC-016｜OpenPencil 渲染约束（像素稿的硬规则）

- **日期**：视觉先行阶段
- **状态**：已实测定位，**后续所有像素稿必须遵守**
- **背景**：出图反复出现"整屏空白""文字/字形消失"，一度误判为工具不可用。
  通过最小对照实验逐条排除后，定位到**两条渲染规则**。
- **实测结论**：
  1. **同一层里，矩形（rectangle）会盖住其范围内的所有文字** —— 无论文字在矩形之前还是之后创建。
     对照实验：`矩形内文字`（20px 与 13px）全部不可见，`矩形外文字`正常（见 `.buildlogs` 与 `_inframe.png`）；
  2. **frame 内的文字正常渲染**（卡片用 frame 承载文字即可）。
- **因此固定写法**：
  - 卡片 / 气泡 / 图标块 **一律用 `frame` 承载文字**，不要用 `rectangle`；
  - 图标块 = `frame(底色) + 内部文字/字形`；
  - 不要为了描边而叠加一个同尺寸 `rectangle`（那会盖住整张卡片的文字）。
- **顺带记录的其它实测事实**：
  - `op export` **必须给 `--item <id>`**、或先 `set_selection`；省略会报 `no node is selected`（不指定时默认取第一个顶层节点）；
  - `batch_design` 的 `nodes_json` **只接受内联数组**，`@文件` 不支持；
  - 脚本模式正确调用：`op design @<file>.js --script`（沙箱 JS 里用 `I(parent, obj)`，文本内容字段是 **`content`**，kind 是 **`rectangle`** 而非 `rect`）；
  - `op tools` 的输出是 **UTF-16LE**，按 UTF-8 解析会全错（我为此浪费过一轮）。
- **复查点**：换 OpenPencil 版本后**先跑一次最小对照实验**（矩形+文字 / frame+文字），确认规则是否仍成立，再批量出图。
  不要再凭"导出成功"判断稿子可用——**必须读图确认**。

---

## DEC-017｜设计工作流：**设计 → 用户看图 → 明确认可 → 才写该页代码**

- **日期**：视觉先行阶段
- **状态**：已生效（用户明确要求的固定工作流）
- **背景**：避免"设计没定就写界面，写完发现视觉语言不一致要返工"。
- **结论**：
  1. **不预先冻结页面清单**；模块级推进 —— 用户说"做 X"或"X 里有这些元素"，再出稿、再认可、再实现；
  2. **每轮只交 1~2 页**，禁止一次铺开一堆图；
  3. **用户没明确说"可以"之前，不许写该页的 Compose / CSS**；
  4. 提交设计稿时必须给出：**PNG（`op export --format png --scale 2`）+ 本轮用到的 token 值**。
- **本轮执行情况**：桌面第一批出了 5 张（主态 ×2 / 文件夹平铺预览 / 文件夹展开态 / 图标对照板），
  **未写任何界面代码**，等用户看图认可。
- **复查点**：若出现"先写代码后补设计"的情况，视为违反本条并返工。

---

## DEC-018｜视觉与产品口径补充（6 条，一次记清）

- **日期**：桌面主态定稿当日
- **状态**：已生效

### 1. 工作流：设计 → 审 → 实现（已在 DEC-017 立为规则）

补充执行细节：**不预先冻结页面清单**；用户说"做 X"或"X 里有这些元素"再出稿；
**每轮只交 1~2 页**；**用户明确说"可以"之前不写该页的 Compose / CSS**；
交稿必须带 PNG（`--scale 2`）+ 本轮用到的 token 值。

### 2. **不做酒馆（SillyTavern）格式兼容**（同 DEC-012，此处重申为硬约束）

不导入角色卡 PNG、不兼容世界书 JSON、不匹配酒馆字段。**体验相似，格式无关。**

### 3. 8 套应用色块：**以 hex 为准**，OKLCH 仅作参考区间

- 用户给定的 8 对 hex 为**最终值**（对比度 4.84~6.97:1 全部达标）；
- **OKLCH 标准降级为「参考区间」**，并注明其值为 **sRGB 可达范围内的近似**；
- **禁止为凑 OKLCH 标准而把颜色改艳**；
- 仅对 L 偏高的三处做压暗（≤94）并取该色相"不显艳上限"的彩度：

| 入口 | 原值 | 定稿值 | 底块 L | 底块 C | 对比度 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| character | `#EAF3EC` | **`#D7EFDD`** | 95.5 → 93.0 | 0.013 → 0.035 | 5.58 → **5.20:1** |
| world | `#F2EDFA` | **`#EAE5F4`** | 95.4 → 93.0 | 0.018 → 0.021 | 7.12 → **6.63:1** |
| gallery | `#F7F0DF` | **`#F1E7CE`** | 95.6 → 92.9 | 0.024 → 0.035 | 5.24 → **4.84:1** |

其余 5 套（chat / memory / music / reader / settings）**原值不动**。
实测：部分色相（蓝 248°、紫 250°）在 sRGB 内根本达不到 C=0.05~0.07，硬拉会变艳。

### 4. 文字分层：`#9AA2AF` **禁止用于文字**

| 角色 | 色值 | 对 `#FFFFFF` | 对 `#F5F6F8` | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| 正文 / 主文本 | `#1B1F27` | 16.51:1 | 15.27:1 | — |
| **次要 + 时间戳 + 说明小字** | `#6B7280` | 4.83:1 | **4.47:1** | 对表面达标；对底色差 0.03 |
| **装饰性（禁止用于文字）** | `#9AA2AF` | 2.57:1 | 2.38:1 | 仅限分隔线/占位图形等非文字元素 |

> `#6B7280` 对底色 `#F5F6F8` 为 **4.47:1**（差 0.03）。若要求"任何底色下都 ≥4.5:1"，
> 建议改用 **`#686F7B`**（5.06:1 / 4.68:1）或 **`#666D78`**（5.22:1 / 4.83:1）。**待用户裁决**。

### 5. 相册导入用 **Photo Picker**，不申请读相册权限

内置图库的两个入口：① 系统照片选择器（`ACTION_PICK_IMAGES`）**零权限**；② 从内置图库选择（复用已有文件）。
**不申请** `READ_MEDIA_IMAGES`（与 `CRITICAL §6.8` 一致）。

### 6. 图库引用计数 + **不做独立相册 App**

- 图片一律落**私有目录文件**，库只存**路径 + 元数据**（宽/高/字节/来源/加入时间/**引用计数**）；
- **内容寻址**（文件哈希）天然去重；**原图 + 缩略图两档**，列表只加载缩略图；
- **引用计数**：被引用的图不可直接删；删除前提示"仍被 N 处使用"；**引用为 0 且超 24h 才可物理删除**；
- **独立"相册 App"不在 v1**（它只是同一种数据的另一个视图）。

- **复查点**：实施内置图库时逐条对照第 5、6 条；改色值前先看第 3 条（以 hex 为准）。

---

## DEC-019｜Android 应用工具链版本链条（**硬链条，不能只升其一**）

- **日期**：第 1 轮（出可用 APK）
- **状态**：已生效
- **背景**：androidApp 首次构建连续失败三次，每次报错都指向版本链条的下一环。
- **实测得出的依赖链**：

```
Compose BOM 2026.09.00  →  要求 compileSdk ≥ 37（成员 animation-core 1.12.1 明确要求）
compileSdk 37           →  要求 AGP ≥ 9.1.0（AGP 9.0.1 的"最高推荐 compileSdk"是 36）
AGP 9.4.1               →  要求 Gradle ≥ 9.6.0
```

- **三次报错原文（摘）**：
  1. `Dependency 'androidx.compose.animation:animation-core-android:1.12.1' requires ... compile against version 37 or later` +
     `requires Android Gradle plugin 9.1.0 or higher`（`checkDebugAarMetadata` 一次报 22 条）
  2. AGP 9.4.1：`Minimum supported Gradle version is 9.6.0. Current version is 9.3.0.`
- **结论（本仓库锁定的组合）**：

| 组件 | 版本 |
| :--- | :--- |
| Gradle | **9.6.0**（wrapper 锁定） |
| AGP | **9.4.1** |
| Kotlin | 2.4.20 |
| compileSdk / targetSdk / minSdk | **37 / 37 / 26** |
| Compose BOM | 2026.09.00 |

- **连带必须记住的一条**：**不要应用 `org.jetbrains.kotlin.android`**。
  AGP 9 内置 Kotlin 支持，两者同时应用会直接失败：
  `Failed to apply plugin 'org.jetbrains.kotlin.android' ... Remove the plugin`。
  这与 DEC-010 记录的 KMP 情形同源（都是 AGP 9 的 built-in Kotlin）。
- **本机额外动作**：手工安装了 `platforms/android-37`（`platform-37.1_r01.zip`），
  因为本机原本只有 34/36/36.1。CI 里改为 `sdkmanager --install "platforms;android-37"`。
- **复查点**：升级其中任意一个版本时，必须按链条**自下而上**重验（Gradle → AGP → compileSdk → Compose BOM）。

## DEC-020｜APK 构建以 **GitHub Actions 为准**（本机构建受版本链限制）

- **日期**：第 1 轮
- **状态**：已生效
- **背景**：本机 Android SDK 只有 34/36/36.1，且 Gradle/AGP 需要升级才能满足 Compose 要求；
  在一台机器上反复调版本链条成本高。
- **结论**：
  1. 公开仓库：**https://github.com/wenqv4617-art/viewphone**（`gh` 未登录，用 REST API + PAT 创建）；
  2. 工作流 `.github/workflows/build-apk.yml`：`sdkmanager` 装 platform 36/37 → `:androidApp:assembleDebug` → 上传 APK；
  3. **产物保留 1 天**（`retention-days: 1`，用户明确要求）；
  4. 本机仍保留构建能力（已装 platform 37），但**以云端产物为准**。
- **安全说明**：GitHub PAT **只写入** `.buildlogs/.gh-token`（已 gitignore）与
  `%USERPROFILE%\.viewphone-github-token`，**不入仓库、不进日志**。
  token 曾出现在对话中，**已提醒用户撤销并重发**。
- **复查点**：token 轮换后只需更新上述两个本地文件；不要把 token 写进任何被跟踪的文件。

---

## DEC-001｜配置缓存默认开启（`org.gradle.configuration-cache=true`）

- **日期**：P0 第 1 天
- **状态**：已生效
- **背景**：P0 首次构建时同时出现两个报错（`.buildlogs/build1.log`）：
  1. `kotlin-compiler-embeddable-2.4.20.jar` 被 Maven Central 302 到 github.com（本机不可达）→ 下载失败；
  2. `Configuration cache state could not be cached. field __classpathSnapshotProperties__ of task ':probe-jvm:compileKotlin' of type 'org.jetbrains.kotlin.gradle.tasks.KotlinCompile': error writing value of type 'org.gradle.api.internal.DefaultNamedDomainObjectCollection$ExistingNamedDomainObjectProvider'`
  我最初按「KGP 与 Gradle 9.3 的配置缓存不兼容」处置，把配置缓存关掉了。**这个判断是错的。**

- **证据**（修好镜像后重测，四次实验）：

  | # | 命令 | 结果 |
  | :-- | :--- | :--- |
  | 1 | `./gradlew build --configuration-cache` | `BUILD SUCCESSFUL in 6s` + `Configuration cache entry stored.` |
  | 2 | 删掉全部 `build/` 与项目 `.gradle/`，`./gradlew build --configuration-cache --no-build-cache` | `BUILD SUCCESSFUL in 8s`，且 `:probe-jvm:compileKotlin`、`:probe-jvm:test` **真实执行**（不是 UP-TO-DATE）+ entry stored |
  | 3 | 再次 `./gradlew build --configuration-cache` | **`Reusing configuration cache.`** |
  | 4 | 检查 `.gradle/configuration-cache/` | 缓存条目确实落盘（`9tbha33s72fuvsqxgrvp9ylku` 等） |

- **结论**：原文的序列化报错是**依赖解析失败（下载超时）的连带产物**，不是 KGP 的配置缓存缺陷。
  链路修复后，「写入 + 从零编译 + 复用」三段全部通过。
  → **配置缓存保持开启**（`gradle.properties`）。

- **复查点（挂账）**：
  1. 若**任意一次** CI 或本地构建出现 `Configuration cache state could not be cached`，或报告里不再出现 `Reusing configuration cache`，**立即按新事实回改本条**，并记录当时的 Gradle / AGP / Kotlin 精确版本。
  2. 在 **step 2（`js(IR)`）与 step 3（`androidTarget`）加入后**各复查一次 —— 新 target 会引入新的任务类型，是这类冲突的高发点。
  3. 本条的结论**绑定在** Gradle 9.3.0 + AGP 9.0.1 + Kotlin 2.4.20 这组版本上；升级任一版本都要复查。

---

## DEC-002｜`minSdk = 26`

- **日期**：P0 第 1 天
- **状态**：已生效（产品方确认，不再询问）
- **背景**：`docs/04`、`CRITICAL.md` 均未给出 `minSdk`；`compileSdk = 36`（P0 口径），`targetSdk` 的 Play 合规路径在 `docs/README` §五 列为「不阻塞 P0 的待决策」。
- **结论**：`minSdk = 26`。
- **复查点**：`targetSdk` 定稿时（P7 上架前）复核一次；若将来要支持更低版本，须单独评估前台服务与通知渠道 API 的降级成本。

---

## DEC-003｜Maven 仓库策略：镜像优先、官方兜底

- **日期**：P0 第 1 天
- **状态**：已生效
- **背景**：本机无法访问 github.com，而 Maven Central 会把大 artifact（如 58MB 的 `kotlin-compiler-embeddable`）302 到 github.com releases。
- **证据**：真实 HEAD 请求 —— `maven.aliyun.com/repository/public` 200（同一 artifact 58,593,245 字节）、`.../repository/google` 200、`repo1.maven.org` 同 artifact 仍 302。
- **结论**：`settings.gradle.kts` 中镜像在前、`google()`/`mavenCentral()` 在后兜底；仓库只在此处声明（`FAIL_ON_PROJECT_REPOS`）。
- **复查点**：**CI 必须与本机同镜像同版本**（要求 ③）。在 `ci.yml` 落地时逐项核对本文件与 `settings.gradle.kts` 一致；若 CI 环境可直连官方源，**也要保持同一配置顺序**，避免本地绿 / CI 红的行为分叉。

---

## DEC-004｜`shared` 的 target 分步引入，`androidTarget` 失败不阻塞

- **日期**：P0 第 1 天
- **状态**：进行中
- **背景**：KMP 单个 target 配置失败可能拖垮整个模块，导致无法定位是哪个 target 的问题；而 `androidTarget` 真正被依赖要等到 P4。
- **结论**：
  1. 按 `jvm()` → `js(IR)` → `androidTarget()` 三小步推进，**一次只加一个**，每步立即验证；
  2. 唯一门槛：`:shared:jvmTest` 必须绿；
  3. `jvm` 或 `js` 失败 = 真问题，停下带报错回报；
  4. `androidTarget` 失败 = 记录完整报错为待解决项，继续推进。
- **复查点**：`js(IR)` 通过后复查 DEC-001；P4 开工前必须让 `androidTarget` 落地。

---

## DEC-005｜工具链探针保留为 `toolchain-probe`，并加依赖门禁

- **日期**：P0 第 1 天
- **状态**：已生效
- **背景**：为让「构建成功」有实证而非空声明，P0 建立了两个探针模块（`probe-jvm` / `probe-android`），但它们不在 `CRITICAL §3.1` 的交付结构里。
- **结论**：
  1. 合并为**一个**模块 `toolchain-probe`（内含 `jvm-test` + `android-lib` 两个子模块）；
  2. **不删除**，待 `core:testing` 落地后并入它；
  3. 生产模块**禁止依赖** `toolchain-probe`，由根 `build.gradle.kts` 在配置阶段强制抛错。
- **复查点**：`core:testing` 创建时执行并入，并在同一次提交里删掉本条门禁的豁免名单。

---

## DEC-006｜`gradlew` 的 Gradle 发行版来源

- **日期**：P0 第 1 天
- **状态**：已生效
- **背景**：`gradle-wrapper.properties` 的 `distributionUrl` 指向 `services.gradle.org`，而 CI 需要可复现的构建。
- **结论**：Gradle **9.3.0** 通过 wrapper 锁定；本机首次生成 wrapper 时使用的是**已缓存的同一发行版**（离线可行）。
- **复查点**：CI 首次运行需确认 runner 能下载该发行版；若不能，则改为内置发行版或加镜像（届时更新本条）。

---

## DEC-007｜KMP 的**每个**测试源集都必须显式声明 `kotlin("test")`

- **日期**：P0 第 1 天（step 1 踩到，step 2 复用该结论）
- **状态**：已生效，**属可复用条目，后续每个模块都照此办理**
- **背景**：step 1 首次执行 `:shared:jvmTest` 直接失败（`exit code 1`）：
  ```
  e: shared/src/jvmTest/.../SharedKernelTest.kt:3:15 Unresolved reference 'test'.
  ```
  而 `src/jvmTest` 目录**是被识别的**（任务名就叫 `:shared:jvmTest`），
  所以问题不是"目录放错"，而是 **KMP 不会自动注入 `kotlin-test` 依赖**。
- **结论**：每新增一个 test 源集，都必须显式写依赖：
  ```kotlin
  sourceSets {
      jvmTest.dependencies { implementation(kotlin("test")) }
      jsTest.dependencies  { implementation(kotlin("test")) }
  }
  ```
  jvm 已实测；加 js 后同样必须声明，否则 `:shared:jsTest` 会以同样的方式失败。
- **复查点**：新增任何 target / 模块时，先检查其 test 源集是否有该声明。
  这是**机械性**步骤，不要依赖记忆。

---

## DEC-008｜仓库解析模式 = `PREFER_PROJECT`（**这是 KGP 的现实约束，不是护栏丢失**）

- **日期**：P0 第 1 天（step 2）
- **状态**：已生效，**结论已按完整取证修正**
- **背景**：加入 `js` target 后，`:kotlinNodeJsSetup` 需要解析 `org.nodejs:node:24.16.0`，
  而 Kotlin/JS 插件会为该依赖**注入一个仓库**。需要确定该仓库的层级，以及它与
  `repositoriesMode` 的互动。

### 8.1 先回答「仓库声明在哪」——两处，都不是问题所在

`settings.gradle.kts` 里我的声明分两块：

| 位置 | 作用 | 声明的仓库 |
| :--- | :--- | :--- |
| `pluginManagement.repositories` | 解析**构建插件** | aliyun public / aliyun google / google() / mavenCentral() / gradlePluginPortal() |
| `dependencyResolutionManagement.repositories` | 解析**依赖**（含 Node 那个） | aliyun public / aliyun google / google() / mavenCentral() |

**`pluginManagement` 里没有任何 Node 仓库**；出问题的是 `dependencyResolutionManagement`
管辖的解析链，而 KGP 注入的仓库**落在项目级（project-level）**，不在 settings 里。
判断依据是 Gradle 自己的取名：项目级注入的仓库名为
**`Distributions at https://nodejs.org/dist`**，而 settings 里的仓库名是
`maven` / `maven2` / `Google` / `MavenRepo`（见 8.2 实验②的搜索列表）——名字对不上。

### 8.2 两个实验的**完整报错原文**（可复现）

复现：脚本只负责改写 `settings.gradle.kts`（含恢复），**不自己调 Gradle** ——
因为不同宿主把整条命令行交给 `cmd.exe` 的方式不一样（我们在某个宿主里对
`cmd /c "gradlew.bat --version"` 直接拿到 exit 9009），把命令交给调用者才能保证取证与宿主无关。

```powershell
# 1) 切模式（脚本会打印本次的预期失败签名）
powershell -ExecutionPolicy Bypass -File tools/experiments/repo-mode-evidence.ps1 -Mode FAIL_ON_PROJECT_REPOS
# 2) 自己跑并留存输出
.\gradlew.bat :kotlinNodeJsSetup --console=plain
# 3) 恢复
powershell -ExecutionPolicy Bypass -File tools/experiments/repo-mode-evidence.ps1 -Restore
```

两种模式**都已用该流程复现出下面的签名**。原始日志：
`.buildlogs/exp1-fail-on-project-repos.log`、`.buildlogs/exp2-prefer-settings.log`。

**实验① `FAIL_ON_PROJECT_REPOS`：**
```
FAILURE: Build failed with an exception.

* What went wrong:
Could not determine the dependencies of task ':kotlinNodeJsSetup'.
> Build was configured to prefer settings repositories over project repositories but
  repository 'Distributions at https://nodejs.org/dist' was added by unknown code

BUILD FAILED in 3s
```

**实验② `PREFER_SETTINGS`：**
```
Build was configured to prefer settings repositories over project repositories but
repository 'Distributions at https://nodejs.org/dist' was added by unknown code

FAILURE: Build failed with an exception.

* What went wrong:
Could not determine the dependencies of task ':kotlinNodeJsSetup'.
> Could not resolve all files for configuration ':detachedConfiguration1'.
   > Could not resolve all dependencies for configuration ':detachedConfiguration1'.
      > Could not find org.nodejs:node:24.16.0.
        Searched in the following locations:
          - https://maven.aliyun.com/repository/public/org/nodejs/node/24.16.0/node-24.16.0.pom
          - https://maven.aliyun.com/repository/google/org/nodejs/node/24.16.0/node-24.16.0.pom
          - https://dl.google.com/dl/android/maven2/org/nodejs/node/24.16.0/node-24.16.0.pom
          - https://repo.maven.apache.org/maven2/org/nodejs/node/24.16.0/node-24.16.0.pom
        Required by:
            root project 'viewphone'

* Try:
> The project declares repositories, effectively ignoring the repositories you have
  declared in the settings.

BUILD FAILED in 3s
```

### 8.3 由原文可确证的四点

1. 两条报错都出现 **"prefer settings repositories over project repositories but repository
   'Distributions at https://nodejs.org/dist' was added by unknown code"** ——
   这句只在**项目级仓库与 settings 仓库同时存在**时才会被打印。
   ⇒ **KGP 注入的 Node 仓库确实是项目级仓库。这个怀疑成立。**
2. 实验②的 `Searched in the following locations` 里**只有我声明的 4 个仓库**、
   没有 `nodejs.org` ⇒ 注入的仓库被降级忽略，因此找不到 `org.nodejs:node`。
3. 实验②的提示语 "The project declares repositories, effectively ignoring the repositories
   you have declared in the settings" 印证：`PREFER_SETTINGS` 的语义是
   **"有项目级仓库时忽略 settings 仓库"**（与字面直觉相反，这是它比①更糟的原因）。
4. 已查证 **KGP 2.4.20 没有关闭 Node 下载的 Gradle 属性或环境变量**
   （jar 内可检索的 JS 开关只有 `kotlin.js.yarn` 等；`BaseNodeJsRootExtension.download`
   与 `installationDir` 均 `@Deprecated`，替代入口 `NodeJsEnvSpec` 在当前 `js { nodejs { } }`
   DSL 里拿不到）。⇒ "复用宿主 Node"这条退路也无法保住①。

### 8.4 修正后的结论（口径要写对）

- **这不是「护栏丢失」，而是 Kotlin/JS 插件在当前版本下的现实约束**：
  KGP 以项目级仓库方式获取 Node 分发包，而 `FAIL_ON_PROJECT_REPOS` 的定义就是
  "只要出现项目级仓库就失败"——两者在设计上不可共存。
- 因此 `PREFER_PROJECT` 是**唯一可用**取值。此前"护栏从编译期强制降级为约定+review"的表述
  **不够准确**，现更正为：**该模式是外部约束下的唯一选项，不是我们主动放弃护栏。**
- 我们自己的约束依然成立：**所有 Maven 依赖仍集中在
  `dependencyResolutionManagement.repositories` 声明，模块内不得自建 `repositories {}`**。
  这条目前由 review 保证而**没有**构建期兜底 —— **但这属于"我们没做机器强制"，
  而非"KGP 破坏了我们已有的强制"。**

- **复查点**：
  1. 若将来 KGP 提供把 Node 分发放入 settings 的机制、或关闭 Node 下载的官方开关，
     复查本条并**优先恢复 `FAIL_ON_PROJECT_REPOS`**（那才是我们真正想要的形态）。
  2. 升级 Kotlin / Gradle 时复查这两条报错是否仍成立。
  3. **不要**为了"看起来更严格"把 `RepositoriesMode` 改回 `FAIL_ON_PROJECT_REPOS` ——
     那会让 `js` target 直接不可用（证据见 8.2）。

---

## DEC-009｜工具链探针的效力边界（不许被误读为工程语义）

- **日期**：P0 第 1 天
- **状态**：已生效
- **背景**：`toolchain-probe` 与 `shared` 里的那几个断言，容易被后人误读成"已有测试覆盖"。
- **结论**：
  1. `toolchain-probe` 的绿**只证明"工具链跑得通"**（编译链、JDK、JUnit、Node 可执行），
     **不证明任何工程语义**；它那 1 个断言是脚手架，P1 起被编译器的 golden 测试取代。
  2. `shared/src/jsTest` 里的冒烟断言同样**只证明 JS target 跑得通**，
     它不是 Web 端一致性测试（那是 P6 的 ≥200 组 `ContextBundle` 逐字节比对），
     也不覆盖任何内核算法。**禁止**在此处扩展成"看起来像在测功能"的测试。
  3. **Q1 的依赖门禁与 Detekt 是两件事，不要混为一谈**：
     - 现在生效的门禁只有一条：*生产模块不得依赖 `toolchain-probe`*（根 `build.gradle.kts`，配置期抛错）；
     - `CRITICAL §3.3` 要求的 **5 条 Detekt 规则**（跨 feature import、`shared/commonMain` 平台 API、
       文件 >500 行、函数 >80 行、禁直调 `System.currentTimeMillis()`）**尚未实现**，属 P0 第 3 步。
- **复查点**：Detekt 落地时，在本文新增条目，并明确它与 Q1 门禁的覆盖范围差异。

---

## DEC-010｜KMP 的 Android target 必须用 `com.android.kotlin.multiplatform.library`（旧 DSL 已被 AGP 9 封死）

- **日期**：P0 第 1 天（step 3）
- **状态**：**已解决**（原按"失败不阻塞"挂账，实测过程中当场解决）
- **背景**：Q2 口径要求 step 3 先试旧路径，失败则记录完整报错为待解决。
- **实测报错（原文，非转述）**：
  ```
  > An exception occurred applying plugin request [id: 'com.android.library', version: '9.0.1']
    > Failed to apply plugin 'com.android.internal.library'.
       > The 'com.android.library' (or 'com.android.application') plugin is not compatible
         with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0.
         Solution:
           - [Recommended] Replace the 'com.android.library' plugin with the
             'com.android.kotlin.multiplatform.library' plugin
           - Or set the Gradle property 'android.builtInKotlin=false' and
             'android.newDsl=false' to temporarily bypass this issue.
  ```
- **是否与 KGP/AGP 9 的已知 DSL 迁移相关**：**是，直接相关**。AGP 9 做了两项改动共同导致：
  ① `builtInKotlin`（AGP 自带 Kotlin 支持）与 KMP 的 Kotlin 编译互斥；
  ② `newDsl`。官方给出的临时绕过方式正是把这两项关掉，说明旧组合并未被删掉，只是不再被默认允许。
- **解决过程（三个报错，逐个消掉）**：
  1. `com.android.library` + `androidTarget()` → 上面的不兼容报错；
  2. 换成 `com.android.kotlin.multiplatform.library` 且**在子模块里带版本号** →
     `Error resolving plugin ...: the plugin is already on the classpath with an unknown version`
     （AGP 9 已把该插件带上 classpath）；
  3. **最终可用写法**：根 `build.gradle.kts` 用 `alias(...) apply false` 固定版本一次，
     子模块只写 `id("com.android.kotlin.multiplatform.library")`（**不带版本**），
     target 配置用 `kotlin { androidLibrary { namespace/compileSdk/minSdk } }`。
- **结论**：**没有走例外路径**（未设置 `android.builtInKotlin=false` / `android.newDsl=false`），
  直接采用官方推荐的新插件与新 DSL。
- **实测证据**：
  ```
  > Task :shared:compileAndroidMain
  > Task :shared:bundleAndroidMainAar
  > Task :shared:assembleAndroidMain
  BUILD SUCCESSFUL in 18s
  112 actionable tasks: 102 executed, 4 from cache, 6 up-to-date
  ```
  产物落盘：`shared/build/outputs/aar/shared.aar`（1,590 B）、
  `shared/build/classes/kotlin/android/main/com/viewphone/shared/SharedKernel.class`（848 B），
  与 jvm / js 三份产物并存（`build/classes/kotlin/{android,js,jvm,metadata}`）。
- **顺带被证实的既有「未验证项」**：**AGP 9 确有内置 Kotlin 支持**（报错里点名 `android.builtInKotlin`）。
  这同时解释了 `toolchain-probe:android-lib` 为何未加 Kotlin 插件也能编译 `.kt`。
- **复查点**：
  1. 升级 AGP / Kotlin 时复查插件 id 与 DSL 是否再次变更（AGP 9.x 系列仍在演进）。
  2. `androidLibrary {}` 里目前只配了 `namespace/compileSdk/minSdk`；
     加入 Compose、单元测试（`withHostTest`）、变体等能力时需按新 DSL 重新确认写法。
  3. 已知无害告警：配置期解析 `jsNpmAggregated` / `jsTestNpmAggregated` 触发 Gradle
     的 "build performance and scalability issue"（上游 issue 2298）。**不影响正确性**，
     但若将来配置期明显变慢，从这里查。

---

## DEC-011｜R1 结论：**KMP→JS 集成可行**（走单实现路线，不启用双实现豁免）

- **日期**：P0 第 1 天（step 4）
- **状态**：**已结案：可行**
- **背景**：`CRITICAL §7.6` 与风险 R1 要求 **P0 阶段**就做「hello world 级」KMP→JS 集成验证；
  不通过则需提前决策走"双实现 + CI 双向一致性测试"的豁免路径（`docs/02 §六`）。
  验收标准被明确要求"硬到 TS 真的用上了"，不能只看产物存在。

### 11.1 验收结果（全部真实执行，非推断）

| # | 验收项 | 结果 | 证据 |
| :-- | :--- | :--- | :--- |
| 1 | 最小 TS 消费工程，pnpm workspace 引用内核 | **通过** | `web/probe-web`（`dependencies: { "@viewphone/shared": "file:../.kernel" }`），`pnpm install` 建立 junction |
| 2 | 真的 import + 调用 + 断言返回值 | **通过（10 项断言）** | `pnpm -C web/probe-web probe` → exit 0 |
| 3 | TS 能拿到类型 / `.d.ts` | **通过（强类型，非弱类型）** | `pnpm -C web/probe-web typecheck` → exit 0；`.d.mts` 内有真实签名 |
| 4 | 内核 JS 体积（含 kotlin-stdlib） | **见 11.3（含对早期数字的更正）** | `pnpm -C web/probe-web bundle` |
| 5 | 导出面数量与调用语法是否别扭 | **见 11.4 / 11.5** | `web/.kernel/package.json` 的 `viewphoneBuild.kotlinTopLevelExports` 字段 |

`probe` 输出（原文摘录）：
```
形态 A · 直接消费 Kotlin 扁平导出
  ✓ A1 flatVersion() 返回预期版本串
  ✓ A2 flatEcho() 真的裁剪首尾空白
形态 B · 经装配层聚合对象（推荐）
  ✓ B1 导入的 kernel 是对象
  ✓ B2 kernel.kernelVersion() 返回预期版本串
  ✓ B3 kernel.echoTrimmed() 真的裁剪首尾空白
  ✓ B4 纯空白输入返回空串（非恒等返回）
  ✓ B5 无空白输入原样返回
  ✓ B6 中文输入正确往返（跨语言边界语义）
形态 C · 命名空间写法（兼容 UMD 时代）
  ✓ C1 com.viewphone.shared.api.kernelVersion() 可用
  ✓ C2 com 命名空间与 kernel 指向同一实现
```

### 11.2 集成过程中踩到的四个**真实**约束（这才是 R1 的价值）

1. **`@JsExport` 在 `commonMain` 里不可用**。
   它是 `kotlin.js` 的注解，写在 commonMain 会导致 `:shared:compileKotlinJvm` /
   `compileAndroidMain` 报 `Unresolved reference 'JsExport'`。
2. **`@JsExport` 不能标注 `object` 成员**。报错：
   `'@JsExport' is only allowed on files and top-level declarations.`
   ⇒ **固定写法：内核实现只有一份（`commonMain` 的 `SharedKernel`），
   导出面是各平台 `*Main` 里的顶层函数薄包装**（`jsMain` 带 `@JsExport`、`jvmMain` 不带）。
   这既守住宪法 §二.1（单一实现），也守住 `CRITICAL §7.6`（导出面窄）。
3. **默认产出是 UMD/CJS，在 ESM 工程里直接不可用**。报错：
   `SyntaxError: The requested module '@viewphone/shared' does not provide an export named 'com'`。
   ⇒ 必须显式 `useEsModules()`。开启后产物扩展名也变成 `.mjs` + **`.d.mts`**（不是 `.d.ts`），
   装配脚本与 `exports.types` 必须跟着改，否则 TS 拿不到类型。
4. **ESM 下没有命名空间**。Kotlin 的导出变成**扁平顶层具名导出**
   （`export { kernelVersion, echoTrimmed }`），UMD 时代的
   `com.viewphone.shared.api.xxx` 形态**不复存在**。⇒ 若产品代码习惯命名空间写法，
   需要一个薄 adapter（本仓库由装配脚本生成，形态 C）。

### 11.3 体积（验收项 4）——**并更正我此前给出的误导性数字**

我在此前简报里引用过"kotlin-stdlib 就 620KB"。**那个数字是 dev 测试运行器的拼接产物**
（`compileSync/js/test/testDevelopmentExecutable/kotlin/kotlin-stdlib.js`），
**不是生产库产物**，用它做首屏预算会严重高估。真实测量如下：

| 口径 | 字节 | KiB | gzip |
| :--- | :--- | :--- | :--- |
| esbuild bundle（未压缩，含 stdlib） | 27,128 | 26.5 | 5,783 B = 5.6 KiB |
| esbuild bundle（**压缩**，含 stdlib） | **9,756** | **9.5** | **3,830 B = 3.7 KiB** |
| 内核产物逐文件合计（未压缩，不含 .map） | 30,308 | 29.6 | — |

逐文件占比：`kotlin-kotlin-stdlib.mjs` 28,936 B（**95.5%**）、
`viewphone-shared.mjs` 1,248 B（4.1%）、`kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.mjs` 124 B（0.4%）。

**结论**：在当前（仅 2 个导出函数）规模下，**gzip 3.7 KiB** 即可接入内核，
瓶颈确实是 stdlib 但绝对量很小；且 esbuild 能 tree-shake 掉大部分 stdlib
（未压缩从 620KB 级降到 29.6 KiB 级）。**P1 加入编译器/记忆算法后必须重测**，
本条数字只对"当前导出面"有效。

### 11.4 导出面登记（验收项 5）

- 当前导出：**2 个**（`kernelVersion`、`echoTrimmed`），上限 10（`CRITICAL §7.6`）。
- 登记位置：`shared/src/jsMain/.../api/KernelApi.kt` 的 KDoc；
  装配产物 `web/.kernel/package.json` 的 `viewphoneBuild.kotlinTopLevelExports` 字段也记了一份，
  便于 CI 比对"声明 vs 实际"。

### 11.5 调用语法评价（如实说，不粉饰）

- 形态 B（推荐，已生成的 adapter）：
  `import { kernel } from '@viewphone/shared'` → `kernel.kernelVersion()` —— **顺畅**。
- 形态 A（直接吃 Kotlin 扁平导出）：
  `import { kernelVersion } from '@viewphone/shared/kotlin/viewphone-shared.mjs'` —— 路径长，但零封装。
- 形态 C（命名空间）：`com.viewphone.shared.api.kernelVersion()` —— 冗长，仅为兼容习惯保留。
- **别扭之处**：① 类型是手写 `.d.mts`，**导出面变化时必须同步维护**（不是自动派生）；
  ② adapter 用的 `export const { a, b } = flat` 在 TS 7 下可用，但它**不携带类型**，
  类型完全依赖手写 `.d.mts` —— 这是当前方案最脆的一环，已登记为复查点。

### 11.6 **JS vs Wasm 决策（口径钉死）**

- **决定：JS 目标按计划走 Kotlin/JS，不用 Kotlin/Wasm。**
- 理由：① 本次实测证明 JS 路线**可行且够用**（类型可用、体积 gzip 3.7 KiB）；
  ② Wasm 会引入额外的 WebAssembly GC 与浏览器兼容性门槛，
  而 Web 端的使命是**覆盖 iOS 用户**（`docs/01 §2.3`），Safari 的 Wasm 支持面是最需要保守的地方；
  ③ P0 阶段换栈会推翻刚建立的 KGP/AGP/Gradle 版本基线（DEC-001/003/010 全部要重验）。
- **重新评估条件（唯一触发条件）**：P1/P6 实测**体积或性能不达标**时重新评估 ——
  具体阈值待 P1 结束时按"内核 gzip 预算"定稿（届时在本条补上数字）。

### 11.7 结论与后续动作

- **R1 关闭：不走豁免路径。** 单实现（Kotlin 内核 → JS 产物 → TS 消费）成立，
  因此 `docs/02 §六` 的"双实现 + 一致性测试"**不启用**，
  `CRITICAL §7.6` 的唯一豁免条件不触发。
- 后续必须做的两件事：
  1. **CI 纳入三关**：`typecheck`、`probe`、`bundle`（体积记录，超阈值失败）；
  2. P1 结束时用真实内核重测 11.3 的体积，并回填 11.6 的重评阈值。

- **复查点**：
  1. 导出面每次增加，同步更新 `KernelApi.kt` KDoc、装配脚本的 `exportedFns`、
     手写 `.d.mts` 三处 —— **少改一处 TS 就会静默失去类型**（最脆环节）。
  2. Kotlin 升级时复查 `useEsModules()` 与 `.d.mts` 命名规则是否变化。
  3. P6 真机（尤其 iOS Safari）验证前，本条 11.5 的 adapter 形态可能需要再简化。
