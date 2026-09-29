# 微光机 ViewPhone · 关键技术约定（合并版 · 实施窗口唯一必读）

> 本文件是 docs/ 十份文档的合并精简版；冲突时以 docs/00-CONSTITUTION.md 为准，其次以本文件为准。
> 版本：合并版 v1.0（源：docs/00–09）｜生成日期：2026-02-14
> 用法：动手前通读一遍（约 1,500 行，可一口气读完）；写代码时按 §11 的指针回查细节。
> 细节一律给指针（如「细节见 docs/03 §7」），不整段搬运。

---

## 0. 项目身份与两端分工

| 项 | 值 |
| :--- | :--- |
| 中文名 / 英文名 / slug | **微光机** / **ViewPhone**（View机） / `viewphone` |
| 新工程根目录 | `D:\deepseek\viewphone`（**全新项目，不是重构**；目前**尚未 `git init`**） |
| Android `applicationId` | `com.viewphone.app`（发布后**永不可变**） |
| 主端 | **Android 原生 App**：Kotlin 2.x + Jetpack Compose + Room + SQLite |
| 次端 | **Web / PWA**（TypeScript + React）：**唯一使命是覆盖 iOS 用户**，本项目**不做原生 iOS** |
| 共享内核 | **Kotlin Multiplatform 单模块 `shared/`**（`commonMain` / `androidMain` / `jsMain`），代码路径 `shared/src/commonMain/kotlin/com/viewphone/shared/…` |
| 前身 | 「叙事诗小手机」（`wenqv4617-art/Xvshishiapk`）——**只借鉴经验教训，不迁移任何数据、不兼容任何旧格式** |
| 一句话定位 | 一台装满 AI 角色的平行手机；**核心（≥60% 工程量）是与角色的高质量长周期对话**，其余应用是「世界感增强层」，可分期交付 |
| 前端量级警戒 | 前身 19 个应用、约 106k 行前端代码，核心对话被摊薄；本项目**分层交付**（Tier A 必做 / B / C 按需） |

---

## 1. 三条不可妥协的体验 + 四道测试红线

### 1.1 三条不可妥协的体验（E1–E3，判据必须可执行）

| # | 体验 | 可执行判据（口令 / 观测） | 违背后果 |
| :--- | :--- | :--- | :--- |
| **E1** | **长周期记忆**：三个月前说过的话今天能被自然提起 | 注入 300 轮历史后问「我上次说的那个事」，角色**答对**，且 Trace 面板里能看到**被召回的那段原文**（含 `daysAgo`） | 角色退化为无状态聊天玩具 |
| **E2** | **后台活着**：关掉 App 后仍能收到角色主动消息并自动回应 | 真机锁屏 **30 分钟**，至少收到 1 条并**自动回 1 条**；`outbox` 出现 `SENT` 记录，时间戳 − t0 ≤ 30min（允许 ±90s 抖动） | 产品护城河消失（这是选原生形态的唯一理由） |
| **E3** | **永远不卡不崩**：十万条消息、整本小说导入、上千张图片 | 见下方四道红线**全部常绿**；长对话 5,000 条不掉帧；内存常驻消息对象 < 100 条 | 实施到一半就推不动 |

> 前身：作者在开发日志里写下「不试图『让网页别被冻结』，而是**承认它随时会死**」——E2 就是这句话的直接产物。

### 1.2 四道测试红线（红了不许合并，CI 机器可判）

| # | 红线 | 可执行判据 | 挂在哪 | 细节 |
| :-- | :--- | :--- | :--- | :--- |
| ① | **提示词 golden** | 给定 **20 组** `ContextBundle`，编译产物（systemPrompt + 段序 + depth）与旧版 oracle **逐字节一致**；含线下 `-50/-40/-30` 段 | `shared/commonTest`（JVM） | docs/07 §10.1 |
| ② | **二进制入库守卫** | 把 `data:image/png;base64,...` 传给**生产仓储实现** → 抛 `IllegalStateException(code=STORAGE_GUARD_BASE64)`，且 `SELECT COUNT(*)` 前后**不变**（4 个违规 fixture 逐个断言） | `core:data`（真实调用点 + CI 源码扫描） | docs/03 §3 |
| ③ | **全表备份覆盖** | 反射枚举 `@Database.entities` ↔ `BackupRegistry.tables` **双向比对**，差集非空即失败（含「注册了但实体不存在」与「实体存在但未注册」） | `core:data` + CI | docs/03 §7.2 |
| ④ | **十万条分页性能** | 单会话 10 万条消息，第 1 页与第 1000 页（每页 50）**P95 < 50ms**；`EXPLAIN QUERY PLAN` 必须出现 `SEARCH message USING INDEX index_message_sessionId_seq`，出现 `SCAN` 或 `USE TEMP B-TREE FOR ORDER BY` 即失败 | `core:data`（Room in-memory + Robolectric） | docs/03 §5.3 |

**CI 门槛（全绿才可合并）**：编译 + 单测 + lint（依赖方向 / 重复定义 / 文件行数 / 函数行数）+ 迁移测试 + 出包。
> 前身：CI 只跑 `assembleDebug`，全仓 **0 个测试文件**。

**每一阶段结束必须跑完本节与 §9 的验收清单，未过不许进入下一阶段。**

---

## 2. 永久禁止清单（出现即返工）

> 逐条一行，最狠的写法。任何一条命中 = 不予合并。来源：docs/00 §六 + 各文档「反面清单」。

**数据类**
- ❌ 把 base64 / data URL / 大 `ByteArray` 写进**任何**持久化层（含 `message.content`、`persona`、`kv.value`）——二进制只能进 `media/` 或 OPFS。
- ❌ 无索引的全表查询拉到内存再 `filter()`（前身把它**立成了规范**）。
- ❌ `LIMIT/OFFSET` 深翻列表；长列表全量渲染。
- ❌ 把大文本字段（消息正文、章节正文、`persona`、摘要、图片 URL）加入任何索引。
- ❌ 静默吞掉存储 / 配额 / 序列化异常（`catch` 后不展示 = 否决）。
- ❌ 手工维护多份表清单（导出 / 清空 / 导入 / 容量四处各写一份）。
- ❌ 把整库 / 整表 `serialize` 成一个大字符串（40MB 级）再写盘或当一次 IPC 参数。
- ❌ 金额用 `Float` / `Double`「元」+ `toFixed(2)`；金额一律 `Long` **分**。
- ❌ 幂等靠「读-判-写」（必须靠**唯一索引**兜底）。
- ❌ 整章正文单字段存储、运行时用正则切章。
- ❌ `fallbackToDestructiveMigration()`、累积式 schema 声明（`N` 个 `version()` 并列）。
- ❌ 多套不同源持久化并存（前身：主库 + 私有音乐库 + 127 个 `localStorage` 键 + 原生沙盒）。

**架构类**
- ❌ 全局可变单例 / 全局命名空间互调（`window.X =` 式靠字符串找函数；前身 **399 处**）。
- ❌ `feature` 之间直接 import 实现类。
- ❌ 单文件 > **500 行**继续堆叠；单函数 > **80 行**。
- ❌ 同一功能在两处实现（**含 Web 与 Android 各写一份内核逻辑**）。
- ❌ 用 JSON 字符串在层与层之间传数据（必须强类型）。
- ❌ `!!`、空 `catch`、直接 `System.currentTimeMillis()` / `Date.now()`（时间必须经注入的 `Clock`）。

**内核类**
- ❌ 在多处拼装 system prompt（前台 UI / 后台服务 / 离线兜底 / Web 端**只能消费同一份编译产物**）。
- ❌ 任意修改已约定的指令字面格式：`[VOICE]` / `[SENDER:]` / `[PLAY_MUSIC]` / `[RED_ENVELOPE]` 等**方括号格式随 V1 发布即冻结**；演进只能「新增别名 + 旧格式长期兼容」，不许改义。
- ❌ 指令解析失败导致崩溃（**必须降级为纯文本**并按文本定稿）。
- ❌ 硬编码上下文条数（如只发最近 10 条）而 UI 另按 30 条渲染。
- ❌ 硬编码端点 / 鉴权 / 字段路径（`/chat/completions`、`Bearer`、`choices[0].delta.content`）。
- ❌ 对话请求无超时；流式渲染路径上加固定延迟（`setTimeout 1000ms`）。
- ❌ 每请求全表扫描 / 循环内线性查找（世界书 `toArray()`、`findRec` 的 O(n²)）。
- ❌ 模型自报执行结果（`[DEBUG_RESULT]` / `result` / `status` 一律**剥离**）。
- ❌ 在 `DirectiveParser` 内访问 Repository / DB / 发起网络。
- ❌ 字符串手拼 JSON（一律 `kotlinx.serialization`）。
- ❌ 把上下文 / 提示词全文常驻内存缓存（`MAX_KEEP=24` 那种）。

**平台类**
- ❌ 同步阻塞的跨层 / 跨进程调用（前身 110 个同步 `@JavascriptInterface`）。
- ❌ 把密钥 / 口令写进源码或仓库（签名口令、API Key、后端密钥都不行）。
- ❌ 任意 URL 的原生网络代理（必须精确 host 白名单 + 强制 https + 禁跳转）。
- ❌ 用「应用内代码」对抗系统级 force-stop（做不到，别浪费工期）。
- ❌ 小程序在宿主同 realm 执行（必须真隔离沙箱 + 显式能力清单，未声明即无权限）。
- ❌ 云备份表对匿名开放读写（`FOR ALL USING(true)`）。
- ❌ 通知使用权解析非媒体类通知；`MANAGE_EXTERNAL_STORAGE`；`USE_EXACT_ALARM`；`AccessibilityService`；`usesCleartextTraffic=true`。

---

## 3. 仓库结构与模块边界

### 3.1 目录树（Monorepo）

```
D:\deepseek\viewphone\
├── docs\                                  # 规划文档（先读 00-CONSTITUTION，其次本文件）
├── CRITICAL.md                            # ★ 本文件：实施窗口唯一必读的精简权威版
├── settings.gradle.kts
├── build.gradle.kts
├── gradle\libs.versions.toml              # ★ 唯一版本来源，禁浮动版本
├── gradle.properties
├── design\tokens.json                     # ★ 设计单一事实源（生成两端产物）
│
├── shared\                                # ★ KMP 共享内核（唯一实现；可编译到 JVM/Android 与 JS）
│   ├── build.gradle.kts                   #   targets: androidTarget + jvm + js(IR)
│   └── src\
│       ├── commonMain\kotlin\com\viewphone\shared\
│       │   ├── ai\
│       │   │   ├── api\                   # AiEngineApi：内核唯一公开入口
│       │   │   ├── compiler\              # depths / segments / templates / artifact / PromptCompiler
│       │   │   ├── directives\            # DirectiveRegistry + 各解析器 + 流式增量状态机
│       │   │   ├── memory\                # 三层记忆：摘要/召回/余弦/时间衰减/去重
│       │   │   ├── context\               # ContextBundle / Budget / Trimmer / TokenEstimator
│       │   │   ├── worldbook\             # 世界书匹配引擎（倒排索引，非全表扫描）
│       │   │   ├── client\                # LLM 抽象 + 四协议适配 + SSE + 重试/超时/取消
│       │   │   ├── embedding\             # 在线 embedding / 本地 ONNX / Cosine
│       │   │   └── proactive\             # ProactivePolicy：纯决策函数（无调度器）
│       │   ├── domain\                    # 实体 + 校验 + 领域事件
│       │   │   ├── model\                 # Character / Persona / UserMask / Session / Message / Memory / …
│       │   │   ├── repository\            # 仓储接口（实现由平台提供）
│       │   │   └── usecase\               # 纯逻辑用例（可单测；权威副作用只在这里）
│       │   ├── data\                      # 游标编解码 / 排序键 / 备份格式（manifest·注册表·校验和）
│       │   ├── economy\                   # 金额（Long 分）+ 账务规则 + 红包/转账幂等键
│       │   └── util\                      # 时间 / 格式化 / 文本 / ID（唯一实现）
│       ├── androidMain\kotlin\…           # actual：平台能力、文件存储、SQLite 驱动、ONNX 调用
│       ├── jsMain\kotlin\…                # actual：OPFS / IndexedDB、fetch、WebCrypto
│       └── commonTest\kotlin\…            # ★ 内核 golden 与算法测试（占测试量 ~60%）
│
├── androidApp\                            # Android 壳：DI 装配、导航图、Application、MainActivity
│   └── src\main\kotlin\com\viewphone\app\
│
├── core\                                  # ★ Android 侧基础层（不含业务）
│   ├── schema\                            # ★ schema.json：两端实体的唯一事实源（生成 Kotlin + TS）
│   ├── ui\                                # Design System：Theme / Token / 通用组件（气泡、抽屉、Toast…）
│   ├── data\                              # Room 数据库、DAO、仓储实现、DataStore、媒体文件仓库
│   ├── platform\                          # 纯 Kotlin/JVM 接口层（PlatformCapabilities + 数据类，零 android 依赖）
│   ├── platform-android\                  # 平台能力的 Android 实现（唯一可 import android.* 的业务无关层）
│   └── testing\                           # 测试工具、Fake 平台实现
│
├── core-service\                          # ★ :core 独立进程：CoreService + AIDL 控制面 + 收→决策→回
│   └── src\main\kotlin\com\viewphone\core\
│
├── feature\                               # 每个功能自闭环（UI + ViewModel + 用例编排）
│   ├── desktop\      # 拟真桌面、Dock、图标网格、小组件、主题皮肤
│   ├── chat\         # ★ 单聊（核心）
│   ├── character\    # 角色档案、用户面具、关系网、世界书管理
│   ├── memory\       # 记忆库查看/编辑/重总结/召回命中
│   ├── group\        # 群聊
│   ├── theater\      # 线下剧场 / 赴约 / 深谈
│   ├── moments\      # 朋友圈
│   ├── couples\      # 情侣空间
│   ├── music\        # 音乐
│   ├── reader\       # 书城
│   ├── ritual\       # 仪轨（四维状态）
│   ├── pet\          # 桌宠（悬浮窗）
│   ├── settings\     # 设置、API 配置、备份恢复、保活开关、调试面板(Trace)
│   ├── forum\ checkphone\ quicktravel\ heartgame\ shopping\ workbench\   # Tier C
│   └── miniapp\      # 小程序宿主 + 真隔离沙箱
│
├── web\                                   # Web / PWA（TypeScript）
│   ├── package.json                       # pnpm
│   ├── vite.config.ts
│   └── src\
│       ├── kernel\                        # ★ 消费 shared 的 JS 产物（唯一允许 import kotlin 输出的地方）
│       ├── apps\                          # 与 Android feature 一一对应的应用
│       ├── shell\                         # 拟真外壳（React 版）
│       ├── storage\                       # IndexedDB + OPFS
│       ├── sync\                          # changeLog、冲突解决、重试
│       └── sw\                            # Service Worker（清单由构建产物生成）
│
├── golden\                                # ★ 跨语言共用的 golden 夹具（schema / 备份 / 提示词 / 分片）
└── tools\                                 # 脚本：token 生成、golden 更新、备份往返、诊断（vp doctor）
```

**为什么 `shared` 单模块 + 多源集，而 Android 侧拆多模块**：`shared` 追求「改一处、两端同时生效」，源集本身就是平台隔离边界；Android 拆 `feature:*` 是为了编译隔离、增量构建与 `api/implementation` 强制边界。
> 前身：全部代码平铺在一个 assets 目录、无构建步骤，9,426 行的单文件既无法定位也无法单独做语法检查。

### 3.2 依赖方向（编译期强制）

```
androidApp ──► feature:* ──► core:ui ──┐
                    │                  ├──► shared ──► (无上游)
                    └──► core:data ──► core:platform (纯接口)
                                              ▲
                            core:platform-android ┘（core:platform 的唯一实现，可 import android.*）
core-service(:core 进程) ──► core:platform + core:data + shared   （禁止依赖任何 feature:* 与 core:ui）
web ──► shared(js 产物) / golden
```

### 3.3 硬规则（5~8 条，CI 必须拦得住）

1. `shared` **不得依赖任何 Android API、任何 React/DOM**：`commonMain` 里出现 `android.` 或 `document.` 即编译失败；平台差异一律 `expect/actual`。
2. `feature:*` **之间零直接依赖**：只读共享数据走 `shared.domain.repository` 接口；跨 feature 通知走 `shared.domain.event` 的 `SharedFlow`。
3. `core:data` 不得依赖任何 `feature:*`；`core:platform` 不得依赖 `core:data`；`core:ui` **不含业务概念**（不许出现「会话」「角色」）。
4. **`:core`（core-service）只允许依赖 `shared` + `core:data` + `core:platform`，禁止依赖任何 `feature:*` 与 `core:ui`**（Detekt 门禁）。
5. 上层只能依赖下层的**接口**，永不能依赖下层的**实现类**（实现经 Hilt 注入）；`api` / `implementation` 严格区分，防传递依赖泄露。
6. 禁止 `object` 单例持有可变状态；禁止全局可变状态（状态收敛到 ViewModel / 仓储 + Flow）。
7. 单文件 ≤ **500 行**、单函数 ≤ **80 行**；禁止跨 feature import；禁止 `!!`；禁止空 `catch`；禁止直调 `System.currentTimeMillis()`（走注入 `Clock`）。
8. **CI 红灯不合并**：编译 + 单测 + lint（依赖方向 / 重复定义 / 行数）+ 迁移测试 + 出包，缺一不可。

**Detekt 自定义规则清单**：① 禁止跨 feature import；② 禁止 `shared/commonMain` 出现平台 API；③ 单文件 > 500 行报错；④ 单函数 > 80 行报错；⑤ 禁止 `System.currentTimeMillis()` 直调。

### 3.4 各层职责（「这段代码该放哪」决策表，摘要）

| 你要写的东西 | 放哪 |
| :--- | :--- |
| 提示词片段文案、depth 常量 | `shared/ai/compiler`（唯一来源） |
| 一个新的模型输出标记解析 | `shared/ai/directives`（注册到 `DirectiveRegistry`） |
| 记忆召回算法改动 | `shared/ai/memory`（必须补单测） |
| 一个新的业务实体 | `core/schema/schema.json` → 生成 `shared/domain/model`（**两端共用，不许手写第二份**） |
| 一个新页面 | `feature:<app>`（自闭环，含自己的 ViewModel） |
| 一个通用气泡 / 抽屉 / Toast | `core:ui`（不含业务） |
| Room 表 / DAO 改动 | `core:data` + `docs/03-DATA-MODEL.md` + `BackupRegistry` 登记 + Migration |
| 调用蓝牙 / 闹钟 / 通知 / 悬浮窗 | 接口放 `core:platform`，实现放 `core:platform-android`（接口不得暴露 android 类型） |
| 一个新的 React 组件 | `web/src/apps/*` 或 `web/src/shell` |
| 一个跨端共用的工具函数 | `shared/util`（**禁止两端各写一份**） |

> 前身：同一个「头像解析」有 5 份实现、`escapeHtml` 有 8 份、`esc` 7 份、`loadJSZip()` 3 份、全表 schema 抄 3 遍——因为当年没有任何「放哪」的约定。

### 3.5 核心数据流（三条链路）

**A. 前台发一条消息（Happy path）**
```
① feature:chat 输入栏 → ChatViewModel.send(text)
② shared.domain.usecase.SendMessage
     ├─ 落库 Message(status=SENDING) ──► core:data MessageRepository (Room)
     ├─ 组装 ContextBundle（角色/面具/关系/记忆/世界书/时间感知/传感器）
     ├─ shared.ai.compiler.PromptCompiler.compile(bundle) ──► CompiledPrompt
     │     · 产物落盘 files/context/<sessionId>/<turnIndex>.json（供后台/离线/Web 复用）
     └─ shared.ai.client.LlmClient.stream(compiledPrompt, profile)
③ 流式分片 ──► StreamingDirectiveScanner（增量状态机）
     ├─ 纯文本增量 ──► 内存草稿消息（节流 30fps，定稿才落库；绝不每字符写库）
     └─ 完整指令   ──► Directive 强类型对象
④ 执行指令副作用（仅领域层可改数据）：
     · 建气泡类 → MessageRepository        · 账务类 → economy.WalletUseCase（事务 + 唯一约束）
     · 设备类   → core:platform             · 记忆类 → shared.ai.memory
⑤ 落库完成 ──► Room Flow ──► UI 自动刷新（不手写 DOM 刷新）
⑥ 收尾：触发自动摘要判定、更新 session.lastMessageAt、必要时发通知
```

**B. 后台收到消息并自动回复（关键路径，不经 UI）**
```
① :core 前台服务内的 WebSocket/长轮询收到入站消息
② 立即落库（senderType=REMOTE）──► Room
③ 读该会话的 CompiledPrompt（缺失/过期 → 在本进程内直接重编译，编译器就在 shared 里）
④ LlmClient.call(compiledPrompt) —— 纯 Kotlin，无需 UI
⑤ 指令解析与副作用（同 A④）
⑥ 发系统通知（含 RemoteInput 通知栏快捷回复）
⑦ UI 若在运行，经 Room Flow 自动看到新消息
```
**这条链路全部代码在 `shared` + `core:platform` + `core-service` 内，不经过任何 ViewModel、不经过 UI 进程。**
> 前身：旧版后台回复要靠「网页把拼好的 prompt 存快照、原生照抄」，作者自述「原生不可能复刻那一整套…硬拼必然分叉」。本项目从架构上消除了它——**编译器在共享核心里，原生与 UI 调的是同一个函数**。

**C. Web 端同一条链路**
```
① web/src/kernel 调 shared 的 JS 产物（同一份编译器 / 解析器 / 记忆算法）
② 存储走 IndexedDB（结构化）+ OPFS（二进制）；接口与 Android 同构，实现不同
③ 无后台能力 → 打开时补发/补回；服务端定时任务 + Web Push 兜底（见 §7）
```

### 3.6 代码规范（写进 CI）

1. Kotlin 官方风格 + `ktlint`；Web 端 `eslint` + `prettier` + `tsc --noEmit`。
2. 禁用 `!!`（除非注释说明为何不可能为 null）；**禁止空 catch**——捕获必须做三件事之一：处理、降级、向上抛，只写日志 = 禁止。
3. 所有跨层数据强类型；公开 API 必须有 KDoc（职责、参数、失败语义）。
4. 时间与随机源必须可注入：`Clock` / `Random` / `UuidGenerator` 全部走接口。
   > 前身：全仓直调 `Date.now()`，时间感知（「距上次聊天多久」）完全无法测试。
5. **账务必须事务化 + 幂等**：金额一律 `Long` 分；余额变动只走单一入口 `WalletUseCase`，内部 `transaction { }`；流水表带唯一业务键（如 `redpacket:<envelopeId>:<receiverId>`），存储层唯一索引兜底。
   > 前身：金额浮点「元」+ `toFixed(2)`；红包领取读-判-写、无唯一约束、无事务，并发双击可重复领取。
6. **导航路由表由注册表驱动**：一处声明「路由 id + 图标 + 标题 + 是否桌面显示 + 是否 Dock」。
   > 前身：加一个桌面图标要改 **17 个注册点**，三处硬编码清单已漂移。
7. 提交信息用中文，按 `feat:/fix:/refactor:/docs:/test:/chore:` 分组。
8. **第一天就建诊断**：`tools/diagnostics` 做索引齐全性、base64 残留、孤儿媒体、超大字段 TOP-N、慢查询、备份往返自检；设置页内置「开发者诊断」面板（可导出报告）。
   > 前身：没有任何自检工具，问题只能靠用户反馈「卡了」来发现。

---

## 4. 数据第一目标（永不撑爆）

> **唯一最高目标：数据结构永不因体积而崩溃。** 高于功能数量、高于开发速度、高于任何性能优化。

### 4.1 五层存储契约

| 层 | 放什么 | 放哪里（Android / Web） | 为什么 | 违反会怎样 |
| :-- | :--- | :--- | :--- | :--- |
| **A 结构化** | 实体、关系、索引、账务、任务状态 | Room DB (SQLite) / IndexedDB | 需要事务、索引、游标分页 | 无事务与索引 → 必然卡崩 |
| **B 二进制** | 图片、语音、书籍正文分片、附件、桌宠素材 | `filesDir/media/` / **OPFS** | 文件系统天然分块、可流式、可单删 | 库体积暴涨、跨进程崩溃 |
| **C 轻量设置** | ≤**200** 项、单值 <**8KB** 的开关与偏好 | DataStore(Proto) / `localStorage`（前缀 `vp.`） | 读写频次高、无查询需求 | 设置污染主库 |
| **D 临时状态** | 页面草稿、滚动位置、UI 展开态 | ViewModel + `SavedState` / `sessionStorage` | 无持久化价值 | 崩溃后脏数据 |
| **E 导出产物** | 备份 ZIP、导出 JSON | `cacheDir/export/` / OPFS `export/` | 可丢弃、需流式写 | 撑爆主存储 |

**红线**
- A 层任何字段**不得**存放 base64 / data URL / 原始字节；**B 层是二进制的唯一归宿**。
- C 层只允许标量设置，禁止业务列表 / 聊天记录 / 图片；**键总数上限 200，超限 CI 失败**。
- D 层禁止承载 > **1MB** 的 payload。
- E 层产物生成后必须可「一键清理」，且不得被主库引用。

> 前身：全库存在 **5 套不同源**的持久化（Dexie 主库 + 私有音乐库 + 127 个 `localStorage` 键 + 原生沙盒），备份只覆盖其中 1 套，「数据在哪」无人能答。

### 4.2 二进制外置规则

**目录树与命名（内容寻址 SHA-256，不用 UUID）**
```
media/
  img/<sha[0:2]>/<sha[0:32]>_orig.webp     # 原图
  img/<sha[0:2]>/<sha[0:32]>_thumb.webp    # 列表缩略图（长边 256）
  img/<sha[0:2]>/<sha[0:32]>_cover.webp    # 卡片封面（长边 1024）
  audio/<sha[0:2]>/<sha[0:32]>.opus
  book/<bookId>/<chapterIdx:05d>.txt        # 正文分片
  tmp/                                       # 导入临时区，禁止被引用
```
- **为什么内容寻址**：同一张图被收藏室 / 消息 / 壁纸多次引用时物理只有一份；导入先算 hash，命中即复用；孤儿判定 = 「磁盘有、引用表无」，可安全回收。
- **为什么两级散列目录**：单目录文件数 > 5,000 时 ext4/APFS 的 `readdir` 明显退化；二级散列把单目录控制在数百个。
- **为什么不用 UUID**：每次写入都产生新文件，去重、引用计数、孤儿回收全部失效。

**引用格式**：库里只存相对路径 `media://img/ab/abcdef..._thumb.webp`；`media://` → 平台媒体根（Android `filesDir/media`，Web OPFS `media`）。**禁止**存绝对路径、`file://`、`content://`。

**图片衍生规格（三档，仅此三档）**

| 档 | 长边 | 格式 / 质量 | 用途 |
| :-- | :--- | :--- | :--- |
| `_orig` | 不压缩（仅去元数据） | 原格式；> **2MB** 转 WebP **q=88** | 全屏查看、导出 |
| `_thumb` | **256px** | WebP **q=72** | 列表、头像、气泡 |
| `_cover` | **1024px** | WebP **q=80** | 卡片、详情头图 |

HEIF 仅作**输入**接受（相机/相册），落盘一律转 WebP。单文件上限 **64MB**，超限在导入阶段直接拒绝并报错。

**语音与书籍**
- 语音：单条上限 **10 分钟**，**Opus 64kbps**；同时写 `durationMs` 到库，列表不读文件即可渲染时长。
- 书籍：**按章切分**，一章一分片，单分片上限 **256KB**；超长章节按 256KB 二次切分并记 `partIndex`。
- 流式读取 `readChapter(bookId, idx): Flow<String>` 逐块 **32KB**；**禁止** `readText()` 整本；章节切分在**导入时**完成并落库 `chapterIndex/title/charCount`，**禁止**运行时正则切章。
  > 前身：整章塞一个字段、正则切章、编码探测只有 UTF-8 vs GBK 二选一，首屏解析卡 1~3 秒。

**导入顺序（严格不可调换）**
1. 流式写入 `media/tmp/<uuid>`；
2. 计算 SHA-256 并校验（与来源声明不一致 → 拒绝）；
3. 原子 `rename` 到内容寻址终址（同分区 rename 是原子操作）；
4. 写 `media_object` 行 + 业务实体行，**同一个事务**；
5. 事务提交后清理 tmp。

**只允许「文件多、库少」（孤儿），绝不允许「库多、文件少」（悬空引用）。**

### 4.3 写入守卫（Guard）与检测规则

**拦截点：真实仓储调用点**，守卫必须内联在**生产仓储实现**里，不是测试代码里：
```kotlin
class RoomMessageRepository(private val db: AppDb) : MessageRepository {
    override suspend fun insert(m: MessageEntity): Long {
        StorageGuard.assertStorable(m)          // 每次写都走，包括 release
        return db.messageDao().insert(m)
    }
}
```
Room 侧再加一层保险：所有含大字段的实体实现 `BinaryFree` 标记接口，配对 `@TypeConverter` 只接受 `String` 且拒绝超长；未实现该接口的实体不得进入 `MessageDao`。

**为什么「只在测试里调用守卫」等于没做**：测试只覆盖测试构造的数据路径；真实崩溃来自生产调用点（导入、生图回调、沙箱写回）。守卫不在调用点就等于没有守卫，而 CI 只会因为「守卫压根不在生产代码里」永远通过。
> 前身：为绕开一次 `SchemaError`，作者把「不改 schema、`toArray()` 全量拉内存再 `filter()`」**立成了规范**，腐化被制度化。

**检测规则（4 条）**
1. 字符串字段匹配 `^data:[a-z/+.-]+;base64,`，或长度 > **64KB** 且 base64 字符集占比 > **95%**；
2. 出现 `byte[]` / `ByteArray` / `Uint8Array` / `Blob` / `Bitmap` 类型字段声明（源码扫描）；
3. 字段名命中 `*Base64` / `*DataUrl` / `*Blob` / `*Bytes`；
4. 深度上限：递归检查至 **8 层**，遇 `Collection` / `Map` 最多遍历前 **64** 个元素做抽样——守卫不得成为性能瓶颈，但任何一层命中都必须抛错。

抛 `IllegalStateException(code=STORAGE_GUARD_BASE64, path=...)`，**绝不吞异常**。

**CI 负向测试**
- **源码扫描**（Gradle task `storageGuardScan`）：`*/repository/**` 与 `*/dao/**` 下出现 `Base64`、`toByteArray()` 直接构建 → 构建失败。
- **负向单测**：合法 fixture + **4 个**违规 fixture（base64 图、data URL、`ByteArray` 字段、1MB 纯文本），断言逐个抛错**且数据库零写入**（`SELECT COUNT(*)` 前后不变）。
- 违规 fixture 本身入库为测试资源，保证扫描器有真阳性样本。

### 4.4 关键表结构与索引清单

**核心实体（Room 注解声明，字段级同一性由 `core/schema/schema.json` 生成）**

```kotlin
@Entity(tableName = "character",
  indices = [Index("deletedAt"), Index(value = ["name"], unique = false)])
data class CharacterEntity(
  @PrimaryKey val id: String,                 // UUIDv7
  val name: String,
  val avatarMediaId: String?,                 // 引用 media_object，不存字节
  val persona: String,                        // ≤ 8KB，禁索引
  val createdAt: Long, val updatedAt: Long, val deletedAt: Long?
)

@Entity(tableName = "user_mask", indices = [Index(value = ["characterId", "isActive"])])
data class UserMaskEntity(
  @PrimaryKey val id: String, val characterId: String,
  val displayName: String, val bio: String, val isActive: Boolean
)

@Entity(tableName = "session",
  indices = [Index(value = ["characterId", "lastMessageAt"])],
  foreignKeys = [ForeignKey(CharacterEntity::class, ["id"], ["characterId"], onDelete = CASCADE)])
data class SessionEntity(
  @PrimaryKey val id: String, val characterId: String, val title: String,
  val lastMessageAt: Long,
  val messageCount: Int,                      // 冗余计数，避免 COUNT(*) 全表
  val createdAt: Long, val deletedAt: Long?
)

@Entity(tableName = "message",
  indices = [
    Index(value = ["sessionId", "seq"]),                    // ★ 游标分页主索引（热路径）
    Index(value = ["sessionId", "createdAt", "id"]),        // 备用时间轴 / 导出排序
    Index(value = ["sessionId", "role", "createdAt"]),      // 按角色筛选
    Index(value = ["status", "createdAt"])                  // 待发送 / 失败重试扫描
  ],
  foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = CASCADE)])
data class MessageEntity(
  @PrimaryKey val seq: Long,                  // 全局单调自增，游标用它
  val id: String,                             // UUIDv7，对外稳定 id
  val sessionId: String, val role: String,
  val content: String,                        // 纯文本 ≤ 64KB；更长内容转附件分片；禁索引
  val contentType: String,                    // text | media | book_ref | card
  val createdAt: Long, val status: Int, val deletedAt: Long?
)

@Entity(tableName = "message_attachment",
  indices = [Index(value = ["messageId", "ordinal"], unique = true), Index("mediaId")],
  foreignKeys = [ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = CASCADE)])
data class MessageAttachmentEntity(
  @PrimaryKey val id: String, val messageId: String,
  val mediaId: String,                        // → media_object.sha256
  val kind: String,                           // image_thumb | image_orig | audio | book_part
  val ordinal: Int
)

@Entity(tableName = "media_object",
  indices = [Index(value = ["kind", "createdAt"]), Index("refCount"),
             Index(value = ["sha256"], unique = true)])
data class MediaObjectEntity(
  @PrimaryKey val sha256: String, val kind: String, val ext: String,
  val bytes: Long, val width: Int?, val height: Int?, val durationMs: Int?,
  val refCount: Int, val createdAt: Long
)

@Entity(tableName = "ledger_entry",
  indices = [
    Index(value = ["accountId", "createdAt", "id"]),
    Index(value = ["idempotencyKey"], unique = true)        // ★ 幂等的物理保证
  ])
data class LedgerEntryEntity(
  @PrimaryKey val id: String, val accountId: String,
  val deltaCents: Long, val reason: String,
  val idempotencyKey: String,                 // 如 "redpacket:<envelopeId>:<receiverId>"
  val createdAt: Long
)
```

**其余必须建的表（同一套规范，字段见 docs/03 §4.1）**：`world_book`（`Index("updatedAt")`）、`world_book_entry`（`(bookId,priority)`、`(bookId,enabled)`）、`memory_summary`（`(sessionId,upToSeq)`）、`embedding_record`（`(ownerType,ownerId)`、`(model,dim)`，`vector: ByteArray` float32 LE，**无 base64**）、`api_config`（`(provider,isActive)`，`keyCipher` 加密密文）、`wallet_account`（`(ownerType,ownerId)` unique，`balanceCents: Long`）、`long_task`（`(state,nextRunAt)`）、`miniapp_kv`（`(appId,key)` unique，`quotaBytes = 2MB`）、`outbox`（见下）。

**`outbox`（发送队列，v1 必建）**：
```kotlin
@Entity(tableName = "outbox", indices = [Index(value = ["state", "nextAttemptAt"]),
                                         Index(value = ["clientMsgId"], unique = true)])
data class OutboxEntity(
  @PrimaryKey val id: String, val sessionId: String, val payload: String,
  val clientMsgId: String,                    // 幂等键，重复入队只发一次
  val state: Int,                             // PENDING | SENT | FAILED | CANCELLED
  val attempts: Int, val nextAttemptAt: Long, val createdAt: Long
)
```
> 待统一项：`docs/06 §3.3` 要求「所有待发消息先落 Room（`outbox` 表）」，而 `docs/03 §4.1` 的实体清单里**没有** `outbox`；本文件按 06 的口径把它列为 v1 正式表，`docs/03` 需补登记。

**索引理由（只写最关键的五条）**
- `message(sessionId, seq)`：唯一热路径。会话内拉页 = `WHERE sessionId=? AND seq<? ORDER BY seq DESC LIMIT n+1`，走索引直接定位，不排序不全扫。
- `message(sessionId, createdAt, id)`：外部时间轴 / 导出排序；`(createdAt,id)` 与 Web 端 `(timestamp,id)` 游标语义对齐，保证稳定顺序。
- `message(status, createdAt)`：重发与超时扫描是后台高频任务，不建索引即全表扫。
- `media_object(sha256)` 唯一索引：内容寻址的去重与引用计数都依赖它，也是孤儿文件判定的反查。
- `ledger_entry(idempotencyKey)` 唯一索引：把幂等从「应用层读-判-写」降级为数据库约束，并发重复领取直接 `UNIQUE` 冲突。

**明令禁止建索引的大字段**：`message.content`、书籍章节正文、`character.persona`、`memory_summary.summary`、`miniapp_kv.value`。它们只允许作为查询结果列，**不得**进入 `WHERE` / `ORDER BY` 的索引前缀。
> 前身：87 张表里 **11 个大文本字段**被列入索引（`messages.content`、`reader_chapters.content`、`sticker_items.imageUrl`），索引体积接近正文本身。

**外键与级联**：子表到父表一律 `onDelete = CASCADE`（`message_attachment → message → session → character`），并在 `AppDb` 打开时执行 `PRAGMA foreign_keys = ON`（Room 默认不开）。`media_object` **不设级联**：它由引用计数管理，只有 `refCount = 0` 且超过 **24h** 才允许物理删除。

### 4.5 分页硬规则（游标 + 页大小上限）

**游标编码（统一口径）**
```
cursor = base64url(JSON{"s": sessionId, "seq": 123456, "t": 1730000000000, "v": 1})
```
- 必须**同时**携带 `(seq, createdAt)`：`seq` 是主排序键（Android Room 的物理顺序），`createdAt` 用于与 Web 端 `(timestamp,id)` 语义对齐；`v` 为游标格式版本，遇到不认识的 `v` **直接拒绝（不猜测）**。
- 页大小：默认 **30**，**上限 50**，超出**直接抛错而不是截断**。
- 深翻必须带游标；**禁止** `LIMIT/OFFSET`。
- 禁止「先 `COUNT(*)` 再决定页数」——总数用 `session.messageCount` 冗余字段。

> 待统一项：`docs/04 §二 P1` 写「游标编解码 `(timestamp, id)`」，`docs/03 §5.1` 写 `(seq, createdAt)`；本文件以 03 的编码为物理实现、04 的 `(timestamp,id)` 为语义对齐基准，禁止两种编码并存。

**验收查询（唯一允许的热路径写法）**
```kotlin
@Query("""
  SELECT * FROM message
  WHERE sessionId = :sid AND deletedAt IS NULL
    AND (seq < :cursorSeq OR (:cursorSeq = 0 AND 1=1))
  ORDER BY seq DESC LIMIT :limit
""")
suspend fun pageMessages(sid: String, cursorSeq: Long, limit: Int): List<MessageEntity>
```
`pageMessages` 由 `MessageRepository.page()` 包裹：负责游标解码、`limit.coerceAtMost(50)` 与 `assertStorable` 前置检查。

**为什么禁止 OFFSET**：SQLite 的 OFFSET 必须先扫描并丢弃前 N 行，第 1000 页（每页 50）意味着先读 5 万行，耗时随页码线性增长——正是前身「进对话卡 1~3 秒」的成因。游标定位是 B-Tree `seek`，复杂度 `O(log n + pageSize)`，与页码无关。

**Web 端等价实现**：IndexedDB 同名 objectStore + 同名复合索引 `by_sessionId_seq`，游标语义与 Android **逐字段一致**，禁止 `getAll()` 后在内存排序。

### 4.6 迁移纪律

- **单 schema 声明**：`@Database(version = N, entities = [...])` 只有一处，`entities` 列表是唯一事实来源。
- **每个版本一个显式 `Migration`**：`MIGRATION_1_2`、`MIGRATION_2_3`…；**禁止** `fallbackToDestructiveMigration()`；**禁止** `AutoMigration` 用于删列 / 改类型。
- **每个 Migration 配一个测试**：`MigrationTestHelper` 用上一版导出的 schema JSON 建库，写入样本行，迁移后断言数据与索引都在。
- **禁止累积式 schema 声明**（不得出现 27 个并列的版本声明各自描述「这一版有哪些表」）。
- **schema 版本与 app 版本解耦**：app 版本（如 `2.4.1`）与 `dbVersion`（如 `7`）独立递增；备份 `manifest.json` 同时记录两者，导入时以 `dbVersion` 判兼容、`appVersion` 仅作提示。
- **破坏性变更流程**：① 新增影子表 → ② 双写一个版本 → ③ 迁移函数搬运并校验行数 → ④ 删除旧表（删列只能在两次发版之后）→ ⑤ 该迁移测试必须含「旧数据可读」断言。

> 前身：87 张表、**27 个 `version()` 声明**，只有 3 张表做对「父键 + 时间」复合索引，而最热路径 `messages` 恰恰没有。

### 4.7 备份与恢复（一级功能）

**ZIP 容器结构（两端唯一交换契约）**
```
backup-vp-20260101T1200.zip
  manifest.json           # 唯一入口
  tables/<table>.jsonl    # 每行一条记录，流式
  media/<sha path>        # 可选包含媒体
  checksums.txt
```
```json
{
  "format": "viewphone-backup",
  "formatVersion": 1,
  "dbVersion": 7,
  "appVersion": "2.4.1",
  "createdAt": 1767000000000,
  "device": "android",
  "tables": { "message": {"rows": 104233, "sha256": "..."}, "...": {} },
  "media": { "count": 5120, "bytes": 2147483648 },
  "totalSha256": "..."
}
```

**表注册表驱动（备份注册表）**
```kotlin
object BackupRegistry {
    val tables: List<TableSpec> = listOf(
        TableSpec("character", dependsOn = emptyList()),
        TableSpec("session", dependsOn = listOf("character")),
        TableSpec("message", dependsOn = listOf("session")),
        TableSpec("message_attachment", dependsOn = listOf("message", "media_object")),
        // 新增表必须在此登记
    )
}
```
**导出、清空、导入、容量统计四处全部读同一个注册表**，由它生成代码。
**CI 门禁**：反射比对 `@Database.entities` ↔ `BackupRegistry.tables`，集合不等即构建失败（两个方向都要判）。
> 前身：备份只覆盖 **43/87** 张表，桌面照片/壁纸正本、衣柜、快穿局 8 表、奇遇 6 表、工作台 5 表、购物 5 表**从未被导出**，换机即永久丢失；新增一张表要手抄四处，漏一处导致备份事务死锁。

**流式写盘**：全程 `ZipOutputStream` + 逐行写 JsonLine，写盘缓冲 **256KB**；**禁止** `JSON.stringify(整个库)`、禁止在内存里拼 40MB 字符串；`media` 大文件用 `copyTo` 分块搬运。

**导入顺序与三段式校验**（顺序严格按注册表 `dependsOn` 拓扑排序，单事务，`PRAGMA defer_foreign_keys = ON`）
- **第一段（预检，不写库）**：`manifest` 存在、`formatVersion` 已知、`dbVersion` ≤ 本机且能提供迁移路径；**未知表 → 硬失败**，不允许静默跳过。
- **第二段（完整性）**：逐表比对 `rows` 与 `sha256`；解压出的表集合与注册表必须**完全相等**（缺表或多余表都失败）。
- **第三段（提交）**：单事务内按拓扑序插入，事务结束前跑 `PRAGMA foreign_key_check`，**非空即回滚**。

**分块传输上限**：单次 RPC / 文件写入分片 ≤ **4MB**；**禁止**把一个整表 JSON 当一次 IPC 参数传（这正是前身「单次跨进程传输崩溃」的直接原因）。

**全表覆盖测试（四步）**
1. 每张表写入 ≥ **2 行**样本（含 1 行边界值：空串、超长文本、NULL 外键可选列）；
2. 导出 → 校验 `manifest.tables` 键集合 **==** 注册表集合；
3. 清空全库 → 导入 → 逐表比对 `COUNT(*)` 与全字段快照；
4. 追加断言：`media_object.refCount` 与 `message_attachment` 引用数一致（含 1 个媒体文件与 1 本 3 章的书）。

> 前身：导入零校验——无版本号/schema/校验和，`if (data.X)` 逐表 clear+bulkAdd，data 里没有的表**不清空** → 静默半恢复。

### 4.8 容量可见与配额

- **分开统计**：`structuredBytes = DB 文件 + -wal + -shm`；`mediaBytes = media/ 递归求和`。设置页**分别显示**，不合并成一个「已用空间」。
- **容量可视化**：横向双色条 + 分项 Top 10（按 `media_object.bytes` 聚类的 kind 排行）；刷新节流 **5 秒**，求和走后台线程。
- **超限必须可见报错**：任何写入捕获 `QuotaExceededError` / `SQLiteFullException` / `ENOSPC` → 弹出**阻断式 Dialog**，给出「清理缩略图 / 清理孤儿媒体 / 导出后删除」，并在 UI 保留「最近一次写入失败」记录。**`catch` 后不展示 = 否决。**
- **清理策略**：`_thumb` / `_cover` 可随时删除并从 `_orig` 重建（后台队列，限速）；孤儿回收 = 磁盘文件集合 − `media_object` 集合，且要求「文件 mtime > **24h**」才删；`refCount` 在写实体事务内 `+1`、删实体时 `-1`，`refCount = 0` 且超 24h 才物理删除。
- **Web 端补充**：首屏必须调用 `navigator.storage.persist()`（**拿不到不阻塞**）；拒绝时 UI 明确提示「浏览器可能在存储紧张时清除本站数据，请定期导出备份」；Chrome 下用 `navigator.storage.estimate()` 交叉核对配额；软阈值 **用量 80%** 触发清理提示。

> 前身：全库 **0 处** `navigator.storage.persist()`，且 `QuotaExceededError` 被静默吞掉，用户看到「保存成功」而照片其实没落盘。

### 4.9 跨端单一事实源（schema 生成 + CI 对齐）

- **单一事实来源**：`core/schema/schema.json` 定义实体、字段类型、索引、约束；由它**生成**：① Kotlin data class + Room 注解；② Web 侧 TypeScript 类型 + IndexedDB objectStore 与索引声明。
- 两端**不允许手写第二份定义**；生成产物纳入版本控制，CI 校验「生成的产物与 schema 一致」；**改字段必须改 `schema.json`，手写第二份 = CI 失败**。
- **字段级同一性**：字段名、类型语义、可空性两端一致；时间统一 **UTC 毫秒 `Long`/`number`**；金额统一**整数分**；主键统一 **UUIDv7 字符串**。
- **Web 端选型**：IndexedDB 承载结构化（与 Room 表一一对应，**同名 objectStore、同名复合索引**），OPFS 承载二进制（同一套内容寻址路径）。
- **同一份备份格式**：ZIP + `manifest.json` + `tables/*.jsonl`；`formatVersion` 由 schema 派生；两端 CI 使用**同一批 fixture ZIP** 做双向导入测试。

### 4.10 数据库选型（已冻结，不再讨论）

| 端 | 选型 | 理由 |
| :--- | :--- | :--- |
| Android | **Room + SQLite**（WAL，`enableMultiInstanceInvalidation()`） | Jetpack 官方、迁移工具体系成熟、与 Paging 3 / Flow 集成最好 |
| Web | **IndexedDB**（结构化）+ **OPFS**（二进制） | 浏览器唯一现实选择；与 Android 同构的仓储接口 |
| 两端一致性 | **`core/schema` 单一来源生成两侧实体 + CI 字段对齐测试** | 用「生成的契约」保证单一事实源，而不是用「同一份代码」勉强两端 |

**决策：不采用 SQLDelight。** 它「一套 SQL 双端共用」的优点真实存在，但代价是放弃 Room 的**迁移测试体系**与 IDE 支持，而本项目第一目标恰恰依赖「迁移必须可测」。
> 结论一句话：**用「生成的契约」保证单一事实源，而不是用「同一份代码」去勉强两端。**

### 4.11 数据自检（`vp doctor` / 设置页「数据诊断」）

一条命令输出可复制报告，覆盖 **7 项**：
1. **索引完整性**：读 `sqlite_master` 全部索引与 `PRAGMA index_list(<table>)`，与 `schema.json` 声明比对，输出缺失/多余清单；
2. **base64 残留扫描**：对大文本字段正则抽样（`^data:` 与 base64 密度），报命中表、行 id、字段名与长度；
3. **孤儿文件**：磁盘 − `media_object` 差集（含字节数）；
4. **悬空引用**：`message_attachment.mediaId` 不存在于 `media_object` 的行数（**必须为 0**）；
5. **超大字段**：任何文本字段 > 64KB 的 Top 50，按表聚合；
6. **慢查询清单**：内置语句白名单逐条跑 `EXPLAIN QUERY PLAN`，输出含 `SCAN` 或 `TEMP B-TREE` 的语句；release 构建下抽样校验热路径仍走索引；
7. **健康分**：以上 1/3/4 任一非空即判 `UNHEALTHY`，设置页显示红点。

### 4.12 数据类禁止速查（16 条，写代码前扫一眼）

| # | 永久禁止 | 前身事故理由 |
| :-- | :--- | :--- |
| 1 | 任何 base64 / data URL / 原始字节写入结构化库 | 缩略图 + 原图双份 base64 入同一条消息，收藏室再存一份 |
| 2 | 一个实体表有多个 schema 版本声明（累积式） | 87 表 / 27 个 `version()`，无人能说出真实 schema |
| 3 | 列表查询不建 `(父键, 排序键)` 复合索引 | 只有 3 张表做对，`messages` 热路径恰恰没有 |
| 4 | 用「全量 `toArray()` 拉内存再 `filter()`」替代索引 | **被立为规范**，进入即卡 1~3 秒 |
| 5 | 给大文本字段建索引 | 11 个大字段入索引，索引体积 ≈ 正文 |
| 6 | 手工维护多份表清单（导出/清空/导入/容量） | 43/87 覆盖，新增表漏抄致备份事务死锁 |
| 7 | 导入不做版本号 + 校验和 + 表集合校验 | 无校验、缺表不清空，静默半恢复 |
| 8 | 金额用浮点「元」+ `toFixed(2)` | 无整数分，累计误差与对账失败 |
| 9 | 幂等靠「读-判-写」 | 红包可重复领取 |
| 10 | 整章正文单字段存储、运行时正则切章 | 首屏解析阻塞主线程 |
| 11 | 不调用 `navigator.storage.persist()` | 全库 0 处调用 |
| 12 | 静默 `catch` 配额/写盘异常 | `QuotaExceededError` 被吞，「照片保存了却没有」 |
| 13 | 把整库/整表 JSON 当单次 IPC 参数 | 40MB 导出 + 跨进程传输崩溃 |
| 14 | 多套不同源持久化并存 | 主库 + 私有音乐库 + 127 个 `localStorage` 键 + 原生沙盒 |
| 15 | `LIMIT/OFFSET` 深翻与长列表全量渲染 | 第 1000 页卡顿、DOM 爆炸 |
| 16 | `fallbackToDestructiveMigration()` | 迁移失败即用户数据消失 |

**数据层验收标准汇总（一眼可查）**

| 项 | 标准 |
| :--- | :--- |
| 单条消息 | 库内不存二进制；`content` ≤ 64KB |
| 第 1000 页查询 | P95 < 50ms，走 `index_message_sessionId_seq` |
| 备份覆盖 | `manifest.tables` == `BackupRegistry` == `@Database.entities` |
| 导入 | 未知表硬失败；`foreign_key_check` 为空才提交 |
| 孤儿 / 悬空 | 悬空引用 = 0；孤儿可回收且清理不影响任何 UI |
| 容量超限 | 100% 有可见报错，无静默 catch |
| 跨端 | 同一 ZIP 双向导入成功 |

---

## 5. AI 内核要点

> 本项目**最核心的部分**（≥60% 工程量）。技术栈：纯 Kotlin（`shared/commonMain`），零 Android 依赖、零 DOM 依赖，**全部可在 JVM 单测里验证**；Android 与 Web 共用同一份实现。
> 旧版教训一句话：**照搬旧版产物（提示词）是对的，照搬旧版代码（实现方式）是错的。**

### 5.1 CompiledPrompt（编译产物即契约）

`CompiledPrompt` 是**语言中立的版本化 JSON**，落盘 `filesDir/context/<sessionId>/<turnIndex>.json`；Android 后台原生回复、Web 端、调试面板、golden 测试**消费同一份产物**，任何一方都不得自行拼提示词。

```kotlin
@Serializable
data class CompiledPrompt(
    val schemaVersion: Int,              // 产物结构版本；结构变更加 1
    val compilerVersion: String,         // 编译器语义版本，如 "2.3.0"
    val depthSpecVersion: Int,           // Depths 表版本；depth 调整必须 +1
    val sessionId: String,
    val turnIndex: Int,
    val mode: PromptMode,                // ONLINE / OFFLINE_THEATER / GROUP / PROACTIVE / SUMMARY
    val systemPrompt: String,            // 唯一真相，模型实际收到的 system
    val messages: List<CompiledMessage>, // 已含历史、插入位、多模态 part
    val segments: List<SegmentTrace>,
    val groupCharCounts: Map<String, Int>,
    val tokenEstimate: TokenEstimate,
    val trimDecisions: List<TrimDecision>,
    val directiveProtocolVersion: Int,   // 与模型约定的指令协议版本，v2 起 = 2
    val worldBookHits: List<WorldBookHit>,
    val builtAt: Long,
    val contentHash: String              // 由 segments 规范化后 SHA-256，用于缓存与 golden
)
```
- **版本号规则**：`schemaVersion` / `depthSpecVersion` 变更必须附迁移说明；`compilerVersion` 每次语义变更递增。
- `contentHash` 只对 `segments(id, depth, content)` 规范化后计算（剔除时间戳等易变字段），**同一 bundle 必须得到同一 hash**，否则缓存与 golden 无从谈起。
- `groupCharCounts`：按 `group` 汇总**启用段落的最终字符数**（替代旧版单一 `charCount`），一眼看出「是记忆太长还是世界书太长」。

### 5.2 depth 规范表（单一来源 `Depths.kt`）

| depth | id / group | 内容 | 来源 |
| :--- | :--- | :--- | :--- |
| -100000 | `disclaimer.top` | 法律免责置顶 | 常量 |
| -1000 | `disclaimer` | 安全底线 | 常量 |
| -950 | `offline.scenario` | 线下剧场场景设定 | 剧场 |
| -900 | `offline.rule` | 线下叙事准则 | 常量 |
| -800 | `identity.wall` | 角色身份墙 + 母语文化 + 防 OOC | Persona |
| -700 | `user.wall` | 用户人设 + 关系网 | UserMask / Relation |
| -600 | `memory.core` | 核心记忆（缓慢演化） | CoreMemory |
| -590 | `memory.recall.raw` | 原始对话向量召回（附 `daysAgo`） | Recall |
| -500 | `online.rule` | 线上实时通讯风格准则 | 常量 |
| -495 | `couples` | 情侣空间 | Couples |
| -490 | `env.sensors` | 电量/天气/位置/正在播放/歌单 | SensorSnapshot |
| -480 | `plot` | 主线剧情约束 | PlotConstraint |
| -475 | `blocked.a` / `blocked.b` | 拉黑态（两处，互斥启用） | Relation |
| -474 | `blocked.note` | 拉黑补充说明 | Relation |
| -470 | `today.state` | 当日日程/穿着/随身物/位置 | TodayState |
| -450 | `cap.multimedia` | 发图/语音/表情能力 | CapabilitySet |
| -430 | `cap.recall` | 撤回/引用能力 | 常量 |
| -420 | `cap.interaction` | 通话/主动行为 | 常量 |
| -400 | `time.aware` | 时间感知与间隔 | TimeAwareness |
| -100 | `tools.def` | 工具 / MCP 定义 | 运行时 |
| -90 | `cot` | CoT 强制格式 | 常量 |
| -85..-81 | `social.1..4` | 社交四段（主动/被@/冷场/收尾） | 常量 |
| **0** | `memory.summary` | 摘要记忆注入（**新增**显式位，旧版寄生在 -600） | Summaries |
| 锚点+order | `worldbook.*` | 世界书条目按 `atDepth` 落位 | WorldBookHit |
| -50 / -40 / -30 | `offline.time` / `offline.cot` / `offline.beautify` | **线下专用三段（必须存在）** | 常量 |
| ≥9990 | `append.*` | 固定追加段（心声/翻译/表情包/通话/查手机/线下格式墙） | Injected |

- **排序键**：`(depth ASC, group ASC, id ASC)`，**depth 越小越靠前**（与 SillyTavern 相反）。
- **相同 depth 禁止用注册顺序决定**顺序（旧版靠插入顺序隐性排序）。
- **所有 mode（含 GROUP / OFFLINE）必须走同一 CATALOG**，不许「群聊 builder 只 sort 不走 catalog」。
  > 前身：群聊 builder 未接入 CATALOG，群聊段落无法逐段开关；trace 只有单一字符数，调试靠人肉数段落。

### 5.3 指令协议（保留旧方括号字面格式）

**硬约定**：**保留前身已固化的方括号字面格式**（`[VOICE]` / `[SENDER:]` / `[PLAY_MUSIC]` / `[RED_ENVELOPE]` 等）。理由是降低人机摩擦——这些格式已被大量角色卡、世界书与提示词资料沿用，改语法等于让所有存量资料失效。**格式随 V1 发布即冻结**；如需演进，只能「新增别名 + 旧格式长期兼容」，不许直接改义。

**指令表（40 项，含 5 项新增 / 7 项改造；完整语义见 docs/07 §6.1）**

| 指令 | 状态 | 权威副作用 |
| :--- | :--- | :--- |
| `[SENDER:名]` | 保留 | 群聊分流，只改会话归属（领域用例） |
| `[VOICE:文本]` / `[VOICE]{duration,text}` | 修改 | 统一 `[VOICE:时长:文本]`，旧 JSON 形式仍解析（兼容层）；建语音气泡 |
| `[IMAGE:描述]` / `[MOMENT_IMAGE:描述]` | 保留 | 聊天图片卡 / 朋友圈配图；生图开关开启才调生图 |
| `[LOCATION:地点]` | 修改 | 收成 `[LOCATION:名称\|lat,lng?]` |
| `[TRANSFER:额]` / `[TRANSFER:人(额)]` | 保留 | **只建 pending 气泡**，不动账务 |
| `[RED_ENVELOPE:额:备注]` | 修改 | 合并旧版两套语法为一套 |
| `[RECEIVE_TRANSFER]` | 保留 | 改用户转账状态 + 系统灰字（领域用例 + 事务） |
| `[AGREE_PAY]` | 保留 | **唯一允许动余额的指令** → 校验余额 → 幂等键 → 事务记账 |
| `[PAY_FOR_ME]` / `[GIFT]` | 保留 | 建卡，不改账务 |
| `[QUOTE:id]` / `[MSG_ID:n]` | 保留 | 引用闭环：注入 `[MSG_ID]` → 模型回 `[QUOTE]`；**严禁复述原文** |
| `[SPLIT]` | 保留 | 强制分泡 |
| `[STATUS:json]` | 保留 | 心声；写 `status_history`（领域用例） |
| `[THOUGHT]…[/THOUGHT]` | 改造 | 与原生 `<think>` 统一为**同一 CoT 通道**，开关关闭时两者都不显示 |
| `[TRANS_JSON:json]` | 保留 | 译文挂气泡 |
| `[PLAY_MUSIC:index]` / `[STOP_MUSIC]` | 保留 | 原生播放器 |
| `[SET_ALARM:延迟:留言]` | 保留 | 系统闹钟 |
| `[BLUETOOTH_CMD:json]` | 保留 | 蓝牙（能力检测失败 → 降级为文本） |
| `[AUTO_CALL:voice\|video]` | 保留 | 拉起通话 |
| `[CHECK_PHONE]` | 保留 | 弹确认卡（**需用户二次确认**） |
| `[RECALL:id]` / `[RECALL]` | 保留 | 标记撤回 |
| `[POLL:主题(选项\|选项)]` / `[ANNOUNCE:标题(内容)]` | 保留 | 群投票 / 群公告 |
| `[MUTE:人(n)]` `[KICK:人]` `[TITLE:人(头衔)]` `[ADMIN:人(设/取)]` `[TRANSFER_OWNER:人]` | 保留 | 群管理，**全部经权限校验** |
| `[CALL_TOOL:json]` | 改造 | **必须**走 `ToolGateway` 白名单 + 超时 + 结果注入；模型伪造的 result 一律剥离 |
| `[MP_INVITE]` / `[HG_GIFT]` / `[WB_TOOL:json]` | 保留 | 小程序 / 礼物 / 世界书工具 |
| `[LIKE]` `[COMMENT]` `[SHARE]` | 保留 | 朋友圈互动 |
| `[SUMMARY]` | 保留 | 触发摘要（幂等键 = 会话 + 轮区间） |
| `【表情包：释义】` | 保留 | 贴纸 |
| `[REACT:emoji:id]` | **新增** | 表情反应 |
| `[TYPING:秒]` | **新增** | 显式「正在输入」时长 |
| `[REJECT_PAY:原因]` | **新增** | 角色拒收 |
| `[MEMO:文本]` | **新增** | 角色写给自己的一句话，入长期记忆候选 |
| `[MUTE_SELF:分钟]` | **新增** | 角色主动「先去忙」，配合主动行为限流 |
| `[DEBUG_RESULT]` 等模型自报结果 | **废弃** | 执行权在客户端，模型不得返回 result/status |

**注册与解析（唯一入口）**
```kotlin
sealed interface Directive { val raw: String; val sourceRange: IntRange }

interface DirectiveParser {
    val name: String
    val openToken: String                     // "[VOICE:" / "[VOICE]"
    fun parse(payload: String): Directive?    // null = 语法不合法 → 按纯文本
}

class DirectiveRegistry(private val parsers: Map<String, DirectiveParser>) {
    fun parseAll(text: String): DirectiveScanResult
    fun advance(chunk: String, eof: Boolean): List<DirectiveEvent>   // 流式增量
}
```
**权威副作用归属（硬契约，有测试）**：`[AGREE_PAY]`（余额）、群管理动作、`[TRANSFER_OWNER]`、`[RECEIVE_TRANSFER]`、撤回落地 —— **必须**走 `shared.domain.usecase` + 事务 + 幂等键；其余（气泡、卡片、播放、闹钟）只改 UI / 平台能力。**`directives` 层禁止引用任何 Repository。**

### 5.4 流式解析要求（增量状态机）

状态机：`TEXT → MAYBE_OPEN(缓冲 '<' / '[') → IN_NAME(名字匹配 trie) → IN_PAYLOAD(括号/引号平衡计数) → DONE | ABORT`

1. 遇 `[` 进 `MAYBE_OPEN` 开始缓冲，与已知 openToken 前缀比对（trie）。
2. 前缀不匹配 → **立即把缓冲当纯文本吐出**，回 `TEXT`（这就是「降级为纯文本」的实现点）。
3. `IN_PAYLOAD` 内维护 `depth`（`(` / `{` / `[` 各 +1，闭合 −1）且**引号内不计数**；`depth == 0` 且遇 token 结束符 → `DONE`。
4. **超长兜底**：缓冲 > **2048** 字仍不闭合 → 丢弃整段并按纯文本吐出，记 `directive.overflow` 指标。
5. **未闭合兜底**：`eof=true` 仍 `IN_PAYLOAD` → 同上丢弃，**绝不「补 `}`」**。
6. **超时丢弃**：一个指令从 `MAYBE_OPEN` 起超过 `directiveTimeoutMs = 8000` 未 `DONE` → 强制按文本定稿（防流卡死导致整轮不落库）。
7. 解析抛异常一律捕获 → 该段降级为纯文本，记 `directive.parse_error{name}`。

**流式与 UI 的背压（硬要求）**
- 气泡**按自然分句切分**，由指令扫描器产出的 `Directive` 边界驱动，**无固定延迟**。
- 上屏节流：`conflate` 到 **30fps** 合并帧；单气泡仅当文本增量 ≥ 1 字才触发重组。
- 打字机是**纯渲染动画**（Compose / CSS），不阻塞落库、不阻塞下一句解析。
- 落库时机：一条气泡**一旦定稿立即落库**（不等整轮结束），保证中途取消也保留已生成内容。

> 前身：解析靠正则 + 括号扫描在**多文件重复实现**，格式一变就靠「自愈补丁」（补 `}`、补 `</think>`、删伪造 result）吞错；流式路径每条气泡强制 `setTimeout 1000ms`，5 条回复要 5 秒。

### 5.5 三层记忆参数

**① 核心记忆（`-600`，缓慢演化）**
```kotlin
data class CoreMemorySnapshot(
    val selfCognition: String,   // 我是谁（现状/目的/变化）
    val relationToUser: String,  // 我眼中的用户、我们的关系
    val updatedAtTurn: Int,
    val revision: Int,
)
```
- **触发**：每 `coreEvolutionInterval = 50` 轮，或角色经历「重大事件」（拉黑解除、转账、剧情节点）后一轮。
- **输入**：上一版核心记忆 + 最近 **20** 轮 + 现有摘要 Top **10**。
- **输出**：结构化 JSON `{selfCognition, relationToUser, changeReason}`；**字段级 diff**：单次变更 ≤ 原文字符数 **25%**，超限则只接受新版本并把 diff 写入 `coreMemoryRevision` 表（可回滚、可查看历史）。
- **注入**：`depth = -600`，全量文本注入。
  > 前身：核心记忆直接覆盖写，无版本无 diff，改坏无法回退。

**② 摘要生成**
- **切片单位是「轮」**：连续 user 段 + 连续 char 段构成一轮，`roundIndex` 全局单调。
- **触发**：`(pendingRounds - bufferRounds) >= autoSummaryInterval`，默认 `interval = 10`、`buffer = 5`，**单次最多 100 轮**；`buffer` **永不丢弃**。
- **输出结构**：每条摘要 `{category: EMOTION|FACT|CORE, content, keywords[], startRound, endRound}`，一次生成可产出多条（按类别）。
- **落库**：`summaries(conversationId, startRound, endRound, category, content, keywords, vector, modelId, createdAt)`；向量维度**不固定**，**以 `modelId` 为键，不同 embedding 模型不混算余弦**。
- **注入**：`depth = 0`，按类别各取 TopK（默认**每类 2 条**），带 `daysAgo`。
  > 前身：向量维度取决于 `vec.length`、无 `modelId`，换模型后旧向量与新向量算余弦（静默错误）。

**③ 向量召回完整算法（参数为定值）**
```
输入：queryRounds = 最近 6 轮拼接文本
      λ_summary = 0.05，λ_raw = 0.5，λ_core = 0.001
      summaryThreshold = 0.55，rawThreshold = 0.50，TopK = 3
      rawSkipRounds = 5，分组配额 摘要 0.33 / 原文 0.33 / 核心 0.34

1. vec = embedding.embed(queryRounds)                    // 双通道
2. 通道A（在线）：在线 Embedding API；超时 8s 或非 2xx → 通道B
   通道B（本地）：ONNX all-MiniLM-L6-v2，384 维（Android）/ WASM（Web）
   注：本地优先可配置（offlineFirst），但两通道产出必须记录 modelId
3. 候选集：summaries(同 modelId) ∪ dialogueVectors(同 modelId)
4. 对每个候选：sim = cosine(vec, cand.vector)             // 纯余弦，非点积
5. age = now - cand.createdAt；day = age / 86400
   decay = exp(-λ * day)                                  // 摘要/原文/核心各用不同 λ
6. 阈值判定只用原始 sim：sim >= threshold(通道类型)        // ★ 绝不与 decay 混用
7. 排序分 score = sim * decay                              // 衰减仅参与排序
8. 分组配额：摘要 0.33 / 原文 0.33 / 核心 0.34，各取 TopK
9. 去重：同 startRound..endRound 区间已被摘要覆盖的原文轮次一律排除
   原文召回额外跳过最近 rawSkipRounds = 5 轮（避免与上下文重复）
10. 合并注入：摘要 → depth 0；原文 → depth -590（附 daysAgo 提示）
```
**核心不变式（写成单测）**：`threshold` 与 `decay` **永不混用**；一条 3 年前的记忆只要 `sim ≥ 0.50` 就必须能进候选，只是排序靠后。
> 前身：作者明确修过「久远记忆永不召回」的 bug（曾写成 `sim*decay >= threshold`）；混用会让记忆系统静默失效。**照搬该不变式 + 新增回归测试锁死。**

**记忆管理界面（`feature:memory`）**：查看/编辑/删除摘要与原文向量、手动触发重总结、每条记忆的召回次数统计、核心记忆版本历史与回滚、切换向量模型时的重建进度与「未迁移条目」标记。

### 5.6 上下文预算与分层裁剪（原则 + 具体值）

**真实 token 估算（唯一预算依据）**
```kotlin
class HeuristicTokenEstimator : TokenEstimator {
    override fun estimate(text: String): Int {          // 按 Unicode 码点逐段计权
        var t = 0.0
        for (cp in text.codePoints()) t += when {
            cp in 0x4E00..0x9FFF || cp in 0x3040..0x30FF ||
            cp in 0xAC00..0xD7AF || cp in 0x3000..0x303F ||
            cp in 0xFF00..0xFFEF -> 1.0                  // CJK / 全角标点 ≈ 1 token/字
            cp.code < 0x80 -> if (cp.isLetterOrDigit()) 0.25 else 0.34  // 英文 ≈ 4 char/token
            else -> 1.0                                  // emoji / 其它：保守计 1，宁多不少
        }
        return ceil(t).toInt() + 4                        // 每条消息固定开销
    }
}
```
**校准要求**：CI 内用离线 tokenizer（GPT 系 + Claude 系）对 **200 条**真实样本回归，断言 `|估算 − 真值| / 真值 ≤ 0.20`；超限即调权重。**UI 显示的数字必须来自同一实现。**
> 前身：`estTokens` 只在界面显示、从不参与裁剪，上下文里「完全没有 token 预算」。

**预算配置项（默认值，全部写死不许「按需配置」）**
`maxInputTokens = 32768`、`reserveOutputTokens = 1024`、`minKeepRounds = 6`、`recallTopK = 3`、`worldBookTokenCap = 2048`、`unitsPerCjkChar = 1.0`。

**输入预算的典型分配（`maxInputTokens = 32768` 时的参考口径，可调但必须进 trace）**

| 组成 | 预算量级 | 是否可裁 |
| :--- | :--- | :--- |
| S0 固定段（免责 + 身份墙 + 线上准则 + 时间感知 + 格式墙） | 约 1,500 ~ 3,000 tokens | **永不裁** |
| 核心记忆（`-600`） | ≤ 800 tokens | 不裁（超长视为配置错误） |
| 摘要注入（`depth = 0`，每类 2 条） | ≤ 1,200 tokens | S3 可裁（TopK 减半） |
| 原文召回（`-590`，TopK = 3） | ≤ 1,500 tokens | S1 先裁 |
| 世界书（非 constant 条目） | ≤ **2048** tokens（`worldBookTokenCap`） | S1 先裁 |
| 剧情 / 日程 / 传感器 / 能力段 | ≤ 1,200 tokens | S2 次裁 |
| 历史对话（`history`） | 剩余全部 | S4 最后裁（整轮丢弃，保底 6 轮） |
| 输出预留（`reserveOutputTokens`） | **1024** tokens | 不参与输入裁剪 |

**分层裁剪优先级**

| 级别 | 内容 | 策略 |
| :--- | :--- | :--- |
| **S0 永不裁** | `-100000/-1000` 免责、`-800` 身份墙、`-500` 线上准则、`-400` 时间感知、`append.*` 格式墙 | 触碰即视为**配置错误**，直接抛 `BudgetOverflowException` 并提示用户换更大上下文的模型 |
| **S1 先裁** | `memory.recall.raw`（召回原文）、`worldbook` 非 constant 条目 | 按条目粒度从低分到高分删；允许「裁剪半条」（截断到最近 N 字并加省略标记） |
| **S2 次裁** | `env.sensors`、`-450/-430/-420` 能力段、`-480` 剧情 | 整段删除，并在 trace 标记 |
| **S3 再裁** | `memory.summary`、summaries TopK | TopK 减半（3→2→1），最短的摘要优先保留 |
| **S4 最后裁** | `history` 消息 | 从最旧开始整体丢弃**整轮**（user+char 成对），**永不留半轮**；保底保留最近 `minKeepRounds = 6` 轮 |
| **绝对保底** | 历史 + S0 | 若仍超限 → **拒绝请求**并给出明确诊断，**绝不上截断过的半截提示词** |

```kotlin
data class TrimDecision(
    val level: TrimLevel, val target: String,
    val action: TrimAction,          // DROP / TRUNCATE / REDUCE_TOPK / DROP_ROUNDS
    val beforeTokens: Int, val afterTokens: Int, val reason: String,
)
```
`trimDecisions` 进 `CompiledPrompt`，调试面板按「省了多少 token」排序展示。
> 前身：上下文 `limit(10)` 与 UI 分页 30 条是两套口径，且裁剪不可见。**新增：裁剪决策可见、可单测。**

**上下文预算原则（三条）**：① 预算与裁剪**长在内核内**，UI 只能读取决策结果；② 估算器是唯一预算依据，UI 数字同源；③ 任何裁剪都必须进 `trimDecisions`，不可见 = 不允许。

### 5.7 多协议 LLM 客户端（要点）

```kotlin
interface LlmClient {
    fun stream(req: LlmRequest, profile: ApiProfile): Flow<LlmEvent>  // 冷流，collect 取消即断开
}
sealed interface LlmEvent {
    data class Delta(val text: String) : LlmEvent
    data class Reasoning(val text: String) : LlmEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int) : LlmEvent
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LlmEvent
    data class Finish(val reason: FinishReason) : LlmEvent     // STOP/LENGTH/FILTER/ERROR
    data class Failed(val error: LlmError) : LlmEvent
}
```
**四协议差异（`ApiProfile.protocol` 是真字段，编译进 `ProtocolAdapter`）**

| 维度 | OpenAI 兼容 | Anthropic Messages | Gemini generateContent | 自定义 |
| :--- | :--- | :--- | :--- | :--- |
| 端点 | `POST {base}/v1/chat/completions` | `POST {base}/v1/messages` | `POST {base}/v1beta/models/{m}:streamGenerateContent?alt=sse` | profile 模板 |
| 鉴权 | `Authorization: Bearer` | `x-api-key` + `anthropic-version: 2023-06-01` | `?key=` 或 `x-goog-api-key` | 模板 |
| system | `messages[0].role=system` | **顶层 `system` 字段** | `systemInstruction.parts[]` | 模板 |
| 角色 | `user/assistant/tool` | `user/assistant`（**无 system 角色**） | `user/model` | 模板 |
| 流式行 | `choices[0].delta.content` | `event: content_block_delta` + `delta.text` | `candidates[0].content.parts[0].text` | 模板 |
| 思维链 | `delta.reasoning_content` | `content_block_delta.thinking_delta` | `parts[].thought=true` | 可配 |
| 结束 | `data: [DONE]` | `message_stop` 事件 | `finishReason` + 流结束 | 可配 |
| 多模态 | `image_url` | `image` + `source.base64` | `parts[].inlineData` | 模板 |
| 工具调用 | `tool_calls[]` 增量拼接 | `tool_use` block 增量 | `functionCall` 整块 | 模板 |

**禁止**在客户端里出现任何硬编码 `/chat/completions`。
> 前身：`api_presets.protocol` 只在「拉模型列表」被读，真正请求永远硬编码 `${baseUrl}/chat/completions` + `Bearer` + `choices[0].delta.content`，Gemini 必失败、Claude 从未实现。

**SSE 解析**：`SseParser` 是纯函数状态机，喂 `ByteArray` 吐 `List<SseFrame>`；跨 chunk 半行、`\r\n`、`event:` 行、多行 `data:`、注释行全部处理。坏帧（JSON 解析失败）丢弃并计数，**连续 3 帧失败即中止**并归为 `ParseError`，不静默吞原因。

**超时 / 重试 / 错误分类**
```kotlin
data class RetryPolicy(
    val connectTimeoutMs: Long = 15_000, val firstByteTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 60_000, val maxAttempts: Int = 4,
    val backoffBaseMs: Long = 800, val backoffCapMs: Long = 20_000, val jitterRatio: Double = 0.3,
)
```
- **仅 `retryable` 且「首个内容 chunk 到达之前」才重试**（已上屏的流绝不重放，避免重复气泡）；退避 `min(base*2^n, cap) * (1 ± 0.3)`；`RateLimited` 优先遵循 `Retry-After`。
- **`ContextOverflow` 专门处理**：触发一次收紧预算的重编译（`maxInputTokens *= 0.7`）后重试一次；第二次仍溢出 → 失败并提示用户。
- **取消**：`flow` 的 collect 取消即关闭连接，全程 `CancellationException` 干净退出，**不留半条落库**。
- 错误分类：`Network` / `Timeout(phase)` / `Unauthorized` / `RateLimited(retryAfterMs)` / `ContentFiltered` / `ContextOverflow` / `ServerError(status ≥ 500 可重试)`。

**多模态降级**：`ApiProfile.supportsVision = false` 时，**在编译期**把图片段降级为文字描述段（`[用户发来一张图：<本地描述>]`），而不是发出去等 400。
> 前身：`fetchWithTimeout` 只包「抓分享链接 meta」和图片，**不包 `/chat/completions`**，网络挂起即永久挂起，零重试零超时。

### 5.8 世界书引擎

- **匹配方式（条目级四选一）**：`SUBSTRING`（默认，最短 **2 字**）/ `KEYWORD_INDEX`（推荐）/ `REGEX`（编译期校验 + 超时）/ `PROBABILITY`（**确定性种子** = `hash(sessionId, turnIndex, entryId)`，保证可测）。
- **KEYWORD_INDEX**：启动时建倒排索引 `keyword -> RoaringBitmap(entryIds)`；一次请求对最近 N 轮文本做 `indexOf` 命中并通过位图求并集，复杂度 `O(文本长度 + 命中数)`；**索引热驻内存**，世界书版本号变化时重建（版本号写在 bundle 里）。
- **插入位 5 种 + `at_depth` 语义**：`BEFORE_CHAR` / `AFTER_CHAR` / `BEFORE_EXAMPLES` / `AT_DEPTH`（system 段内绝对 depth）/ `AT_MESSAGE_DEPTH`（messages 中按「**倒数第 N 条**」插入，`N=0` 表示追加在最后一条之后）。
- **预算**：`constant = true` 的条目（绿字/作者注）**永不裁**；其余受 `worldBookTokenCap = 2048` 约束，按 `priority DESC, order ASC` 入选，超出部分进 trace 的 `droppedByBudget`。
- **性能红线**：禁止每请求 `toArray()` 全表扫描；禁止循环内线性查找。
  > 前身：匹配是纯 `indexOf` 子串且每请求全表拉取，`findRec` 线性查找构成 O(n²)。

### 5.9 主动行为与后台生成

- **调度**：WorkManager 每 **15 分钟**一个 tick，对每个角色做**纯函数决策**（`ProactivePolicy.shouldAct(state, now)` 无副作用、可单测）：日程事件到点、距上次对话 > 阈值、朋友圈冷场、来电时机。
- **去重**：`dedupeKey = hash(characterId, behaviorType, contentHash, 时间桶)`；主动行为落库前查唯一约束。
- **限流（「不许刷屏」）**：单角色主动消息 ≤ **1 次/30 分钟**、≤ **4 次/天**；主动朋友圈 ≤ **1 次/天**；主动来电 ≤ **1 次/天**；夜间 **23:30–07:30 静默**（除非用户在线且角色设了「夜猫子」）；单会话并发请求**恒为 1**。
- **离线原生回复**：Android 后台服务**复用同一份 `CompiledPrompt`**——读最近一次产物 + 增量追加新消息后局部重编译（同一 `PromptCompiler` 代码路径），**禁止**任何 Kotlin 版提示词特化实现。断网时退回本地模板（明确标注「离线模式」），**不伪造 AI 输出**。
  > 前身：离线兜底在 Kotlin 里另写了一套 prompt（双份实现必然分叉）；主动行为无统一限流，靠各模块自觉。

### 5.10 内核可测试性（纯函数边界）

**必须无副作用、无时钟、无随机、无 I/O**：`PromptCompiler.compile`、`TokenEstimator.estimate`、`Trimmer.trim`、`DirectiveRegistry.parseAll/advance`、`Recall.rank`、`Cosine`、`WorldBookEngine.match`、`ProactivePolicy.shouldAct`。时间与随机一律经参数注入（`now: Long`、`Random(seed)`）。

1. **Golden 测试**：用 Node 把前身 `app_prompts.js` 包桩跑起来，导出 `bundle → 期望 systemPrompt + 段序` 作为 oracle。**局限必须写进测试注释**：① 只覆盖单聊/群聊主路径，线下与工作台未接入 CATALOG；② 旧版本身有无意行为（`limit(10)`、同 depth 的隐式插入序），**对齐旧 bug 是错的**；③ 文案与 depth 一经有意改动，golden 必须**显式更新**并在 PR 说明理由，**禁止为了让测试变绿而改 golden**。
2. **Mock 向量回归**：手写 **20 组**固定向量，断言「排序 / 配额 / 去重 / 衰减不影响阈值」四条不变式；另加「跨 `modelId` 不混算」用例。
3. **流式解析边界用例清单（每条一个测试）**：指令跨 2/3/N chunk 切分；`[` 结尾后断开；`[RED_ENVE` + `LOPE]`；名字非任何指令前缀（应立即吐文本）；引号内含 `]`；括号不平衡；超长未闭合 > 2048；`eof` 未闭合；8000ms 超时丢弃；单 chunk 含 3 个指令；指令内嵌套逗号/冒号；emoji 与 CJK 混排切分；`\r\n` 与 `event:` 行；坏 JSON 帧连续 3 次；`[DONE]` 前后各有半帧。
4. **协议契约测试**：四协议各一份真实响应样本（离线录制）→ 断言 `LlmEvent` 序列完全一致。
5. **CI 门槛**：`shared` 单测全绿；`compiler` / `directives` / `memory` / `context` 行覆盖 **≥ 85%**；golden 变更必须在 PR 描述中声明；**禁止清单扫描测试**（`ForbiddenPatternsTest`）断言：`"/chat/completions"` 硬编码、`setTimeout|delay(1000)` 出现在 `client/` 与上屏路径、`limit(10)`/`take(10)` 形式的上下文硬编码、`toArray()`、手拼 `"{"`、`import android.`、同一指令名出现在两个 parser 文件。

---

## 6. Android 关键决策

### 6.1 进程结构与核心链路

```
┌────────────────────────────────────────────────────────────┐
│ 主进程 :app                                                  │
│  · MainActivity + Compose UI + 各 feature ViewModel          │
│  · 只做展示与输入；不做长耗时任务                              │
└───────────────┬────────────────────────────────────────────┘
                │ 跨进程：数据库为数据面（各自打开同一 SQLite，WAL）；AIDL 为控制面
┌───────────────▼────────────────────────────────────────────┐
│ 核心服务进程 :core（独立进程）                                │
│  · CoreService(ForegroundService, specialUse)：WebSocket/长轮询保活 │
│  · 入站消息处理：编译 Prompt → 调 LLM → 解析指令 → 落库 → 通知  │
│  · AlarmManager 链式看门狗（自续期）                          │
│  · WorkManager：非实时任务（备份、摘要补偿、清理、模型下载）    │
│  · ReplyReceiver：通知栏 RemoteInput 直达，不经过 UI 进程       │
└────────────────────────────────────────────────────────────┘
:remote（可选，0.2 再评估）：仅承载 ONNX 推理，隔离大内存峰值；内存不足时不启动
```

- **必须用独立进程**：① 满足 E2（UI 被系统回收后仍能收信并自动回复）；② UI 崩溃/被杀不拖垮后台；③ 一键全关的释放点集中在服务侧，可原子完成。
- **代价（必须认领）**：跨进程通信与调试复杂度，调试要挂两个进程。因此 `:core` **只允许依赖 `shared` + `core:data` + `core:platform`，禁止依赖任何 `feature:*` 与 `core:ui`**（Detekt 门禁）。
- **备选（若实测跨进程成本不可接受）**：退回同进程 + 前台服务，但**必须先改文档并重新评估 E2 的验收方式**。

> 前身：长轮询 `IlinkPoller` 与原生回信 `NativeReplyFallback` 与 WebView 同进程，网页心跳失效要靠 45s+6s 宽限才切换，进程被 WebView 崩溃带走则回信链路整体失效。

### 6.2 AIDL 控制面 + 数据库数据面

- **数据面**：数据库（两边各自打开同一 SQLite，**WAL 模式**，Room `Flow` 各自观察 + `enableMultiInstanceInvalidation()`），**不互传大对象**。
- **控制面**：AIDL 只传**命令与状态**（少量、强类型），因为「立即执行一次发送」这类需要低延迟 RPC：
```kotlin
class CoreBinder : ICoreControl.Stub() {
    override fun send(text: String, clientMsgId: String) = coreRuntime.enqueue(text, clientMsgId)
    override fun state(): CoreState = coreRuntime.snapshot()      // 小对象，可过 Binder
    override fun setBackgroundEnabled(enabled: Boolean) = coreRuntime.toggle(enabled)
}
// UI 端：数据面直接查 Room
val messages: Flow<List<Message>> = db.messageDao().observeRecent()
```
- **为什么不用纯 Room 共享**：不够——命令需要低延迟 RPC。**为什么不用纯 AIDL**：不够——消息历史/附件会撑爆 Binder（**1MB 事务上限**）。**为什么还要 WorkManager**：进程被系统回收后，已入队 Work 由系统持久化调度，是「最后一次自愈机会」。

**进程死亡后的恢复（4 条）**
1. `CoreService.onStartCommand` 返回 `START_STICKY`；`onTaskRemoved` 中重新 arm 下一次闹钟（**不启动不可见 Activity**）。
2. 所有待发消息先落 `outbox`（状态 `PENDING`），发送成功才置 `SENT`；因此**重启后必不丢消息**。
3. `:core` 启动时序：重建 Room → 领取 `PENDING` → 建 WebSocket → 注册闹钟链 → 发前台通知。
4. 每轮发送带 `clientMsgId` 做幂等，服务端去重。
   > 前身：补录队列只有 **100 条**、快照 6h TTL、每小时 20 条限流，且认领去重逻辑在网页侧；队列溢出即**静默丢弃**。本版改 Room 持久队列 + 幂等键。

### 6.3 保活分层（L0–L4）

| 层 | 手段 | 能解决什么 | 不能解决什么 |
| :-- | :--- | :--- | :--- |
| **L0** 前台服务 | `:core` 常驻 `foregroundServiceType` 服务 + 常驻通知 | 进程不在后台 LRU 里被优先杀；可长期持锁 | 用户手动「强行停止」、厂商清理器 |
| **L1** 调度 | 链式精确闹钟（**三级降级**）+ WorkManager 周期兜底 | Doze 下仍能被唤起、15min 级自愈 | force-stop 后闹钟被清空 |
| **L2** 用户授权 | 电池优化白名单、自启动、厂商后台白名单引导 | 显著降低被杀概率 | 各家开关路径不同，无法程序化保证 |
| **L3** 网络 | WebSocket 长连接（**心跳 20s，60s 无帧视为断线**，指数退避重连）+ 长轮询兜底 | 收消息的实时性 | 断网、Doze 网络冻结 |
| **L4** 兜底 | 下次启动时的补录队列重放 | 漏收消息最终不丢 | 无法保证「当时就回」 |

- **闹钟三级降级顺序**（踩过坑得到的正确顺序）：`setAlarmClock` → `setExactAndAllowWhileIdle` → `setAndAllowWhileIdle`（±15min，**文案写明「可能晚几分钟」**）；每次触发后重新 arm 下一次。
- **明确删除的两条旧手段**：**1×1 透明悬浮窗**（绕过 WebView 定时器节流；核心链路已原生化，不再需要）、**静音 `AudioTrack` 无限循环**（不能阻止 force-stop 也不能阻止 Doze，且让音频焦点与省电统计失真）。
- **最小必要保活原则**：保活的**唯一目的**是「消息到达时能及时处理」，不是「进程永不退出」；常驻通知必须**诚实**（说明用途 + 提供关闭入口）。
- **诚实边界**：部分国产 ROM 的 force-stop 会清除应用注册的**全部闹钟**，**任何应用内代码都无法挽回**；保活只能「提高存活概率」，不能承诺「必定到达」。这条必须写进产品文案。
  > 前身：旧版保活默认全开（1×1 透明悬浮窗 + 静音 `AudioTrack`），用户无从关闭；关闭时只停了一个定时器，闹钟/悬浮窗/WakeLock 全残留。

### 6.4 PlatformCapabilities 必须全异步

- **每个能力一个接口，返回 `Flow` 或 `suspend`；接口里不允许出现同步阻塞方法。**
- 接口层 `core:platform` 是**纯 Kotlin/JVM 模块，零 Android 依赖**，可在 JVM 单测跑 Fake；Android 实现集中在 `core:platform-android`，经 Hilt 绑定。
- **规则：业务代码只 import `core:platform` 的接口**；任何 `android.*` 出现在 `:core` 的 `main` 源集即构建失败（`check` 任务守住）。

```kotlin
interface PlatformCapabilities {
    val notifications: NotificationCapability; val alarms: AlarmCapability
    val connectivity: ConnectivityCapability;  val bluetooth: BluetoothCapability
    val media: MediaCapability;                val speech: SpeechCapability
    val overlay: OverlayCapability;            val files: FileCapability
    val capture: CaptureCapability;            val clipboard: ClipboardCapability
    val location: LocationCapability;          val battery: BatteryCapability
    val screen: ScreenCapability;              val haptics: HapticsCapability
    val inference: InferenceCapability;        val background: BackgroundCapability
    val permissions: PermissionCapability
}
interface NotificationCapability {
    suspend fun ensureChannels()
    suspend fun post(n: AppNotification): NotificationId
    fun replies(): Flow<InlineReply>              // 来自 RemoteInput
    suspend fun cancel(id: NotificationId)
}
interface AlarmCapability {
    suspend fun schedule(id: String, at: Instant, exact: Boolean): ScheduleResult
    suspend fun cancel(id: String); fun fires(): Flow<AlarmFire>; suspend fun cancelAll(): Int
}
interface ConnectivityCapability {
    val state: Flow<NetworkState>
    fun connect(url: String): Flow<SocketEvent>   // 自带心跳与退避重连
    suspend fun longPoll(url: String, holdSeconds: Int): PollResult
}
interface InferenceCapability {
    suspend fun isModelReady(): Boolean
    suspend fun ensureModel(onProgress: (Float) -> Unit): Boolean
    suspend fun embed(text: String): FloatArray   // 384 维
}
```
**Fake 实现（纯 JVM，必做）**：`FakeNotificationCapability`（记录 `post()` 序列）、`FakeAlarmCapability`（虚拟时钟 `advanceTo()` 触发 `fires()`）、`FakeConnectivityCapability`（脚本化断线/重连）。
> 前身：约 **110 个** `@JavascriptInterface` 全部同步，跑在 JavaBridge 线程，会冻结整个 WebView 的 JS 执行（作者自己在 `AndroidMcp.kt:446-453` 记录，却只补了 2 个异步变体）。

### 6.5 权限申请矩阵与首次引导

**结论：只申请「收得到消息、活得下去、你点了才用」三类权限，其余一律不申请。**

| 权限 | 用途（用户能懂的话） | 申请时机 | 被拒后的降级 |
| :--- | :--- | :--- | :--- |
| `INTERNET` | 和 AI 模型、云备份通话 | 随包 | 离线模板 + 标注「离线模式」，**不伪造 AI 输出** |
| `POST_NOTIFICATIONS` | 看到角色发来的消息 | **用户发出第一条消息之后** | 应用内横幅 + 兜底闹钟 + 未读汇总入口 |
| `FOREGROUND_SERVICE(+_SPECIAL_USE/_MEDIA_PLAYBACK/_DATA_SYNC)` | 让「收→回」在后台持续跑 / 放歌 | 随包 + Play 提交用途说明 | 无替代；服务由总开关关停 |
| `SCHEDULE_EXACT_ALARM` | 角色准时找你 / 设真闹钟 | 用户主动开「后台能力」时 | 三级降级（±15min，文案写明） |
| `RECEIVE_BOOT_COMPLETED` | 重启后自动恢复接收 | 随包 | 仅在「你打开 App 后」恢复调度 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 省电时别掐断接收 | 引导第 2 步 | 前台服务 + 闹钟照常，**到达率下降（不承诺）** |
| `WAKE_LOCK` | 处理消息那几秒别睡死 | 随包，按需 | 处理延迟增加；**禁止长期持有**（`onDestroy` 断言 `isHeld == false`） |
| `VIBRATE` | 新消息、闹钟震动 | 随包 | 无震动 |
| `ACCESS_COARSE_LOCATION` | 天气与「当前位置」卡片 | 用户点「用当前位置」（**手输城市是默认路径**） | 手输城市 |
| `SYSTEM_ALERT_WINDOW` | 桌面桌宠悬浮窗 | 用户主动开启桌宠时 | 桌宠 → 「通知卡片 + 页内桌宠」 |
| `BLUETOOTH_SCAN`（带 `neverForLocation`）/ `BLUETOOTH_CONNECT` | 发现并连接你的蓝牙设备 | 首次进蓝牙配对页 | 蓝牙置灰说明，其余不受影响 |
| `CAMERA` | 拍一张发给角色 | 用户点「拍摄」时 | 只能从相册（Photo Picker）选图 |
| `RECORD_AUDIO` | 发语音条（按住说话） | 首次长按「按住说话」 | 语音条不可用 → 只能打字，**不阻塞其他功能** |
| 通知使用权 | **仅**读「正在播放」与媒体控制 | 用户点「连接正在播放」时 | 该卡片不显示，**其余完全不受影响** |
| `ACCESS_FINE_LOCATION` | **原则不申请**；仅 ≤ Android 11 的 BLE 扫描需要 | 仅 ≤11 且主动配对时 | 蓝牙整体置灰并说明原因 |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` | **默认不申请**：改用 Photo Picker | 仅当本机 Photo Picker 不可用（`resolveActivity` 判空）才回退 | 提示「请在系统相册选好后分享到本应用」 |

**首次启动引导（4 步，每步可跳过，不请求任何权限就能开始用）**
- 步 0 冷启动：**什么都不申请**，文案「先挑一个角色，聊两句。」
- 步 1 **用户发出第一条消息之后**：申请 `POST_NOTIFICATIONS`，文案解释「不开的话只有你打开 App 才能看到未读」。
- 步 2 用户主动开「后台能力」：电池优化白名单 + `SCHEDULE_EXACT_ALARM` + 厂商自启指引（按 `Build.MANUFACTURER` 分发小米/华为/OPPO/vivo/魅族/三星/原生文案），**每项独立开关**，跳过时说明具体后果。
- 步 3 用到才问（**分散在功能入口**）：通知使用权、`SYSTEM_ALERT_WINDOW`、蓝牙、定位、相机/麦克风。
> 前身：厂商引导文案是有效资产（可直接沿用），但**保活默认全开、用户无从关闭**——好文案救不了坏默认。

### 6.6 一键全关（9 项原子释放）

`SettingsRepository.setBackgroundEnabled(false)` 必须**原子地**完成以下全部动作，任一失败都记录并可重试：
1. `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()` 关闭 `:core` 前台服务；
2. 取消全部已注册闹钟：遍历 `PendingIntent` 请求码集合逐个 `AlarmManager.cancel()`，并清空持久化的请求码表；
3. 取消全部 Work：`cancelUniqueWork("vp.core.periodic")`、`cancelUniqueWork("vp.core.outbox")`；
4. 关闭 `:core` 进程：`stopService(...)`，必要时 `Process.killProcess` 自身（**仅 `:core`**）；
5. 断开 WebSocket/长轮询，取消 OkHttp `Dispatcher`，置 `reconnectEnabled = false`；
6. 取消所有通知 `cancelAll()`，并删除本应用创建的 `core` 渠道以外全部渠道；
7. 移除悬浮窗 `removeViewImmediate()` 并把桌宠状态置 `DETACHED`；
8. 释放全部 `WakeLock`，`onDestroy` 断言 `isHeld == false`；
9. 解除动态注册的 `BroadcastReceiver`（屏幕、电量），并把 `outbox` 中 `PENDING` 的待发消息标记 `CANCELLED`（**下次启动不会偷偷发出**）。

**零残留验收（真机脚本，五条命令都要无输出）**
```bash
adb shell dumpsys alarm | grep -i <pkg>                    # 期望无输出
adb shell dumpsys power | grep -i -A2 <pkg>                # 期望无 PARTIAL_WAKE_LOCK
adb shell dumpsys notification --noredact | grep <pkg>     # 期望无活动通知
adb shell dumpsys window windows | grep <pkg>              # 期望无 overlay 窗口
adb shell ps -A | grep <pkg>                               # 期望无 :core 进程
```
用户可见进度必须是「已完成 7/9」这类**可核对**的进度，而不是转圈。
> 前身：关闭「保活」只停了一个定时器，闹钟、悬浮窗、WakeLock 全残留——**「看起来关了」比「没关」更糟**。

### 6.7 能力矩阵要点（P0 必须可用）

| 能力 | 优先级 | 实现要点 / 已知限制 |
| :--- | :--- | :--- |
| 前台服务保活 | P0 | `:core` 常驻服务，通知渠道 `IMPORTANCE_LOW` 且内容诚实可关；Android 14+ 必须声明 `foregroundServiceType` |
| 链式精确闹钟 | P0 | 三级降级 + 每次触发后重新 arm；国产 ROM force-stop 清空全部闹钟 |
| 开机自启 | P0 | `BootReceiver` 收 `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` 后重 arm 闹钟链与前台服务；国产 ROM 需手动开自启 |
| 电池优化白名单 | P0 | 跳 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；厂商二级开关跳不过去 |
| 通知（渠道/富通知） | P0 | 多渠道：`core`(LOW) / `message`(HIGH, BigText + action) / `media`(MediaStyle)；渠道重要性创建后不可由 App 上调 |
| **通知栏快捷回复 RemoteInput** | P0 | `RemoteInput` + `PendingIntent` 直达 `:core` 的 `ReplyReceiver`，**不经过 UI 进程**；`setAllowGeneratedReplies(true)` |
| 系统闹钟写入 | P1 | `AlarmClock.ACTION_SET_ALARM`；部分 ROM 无时钟应用，需 `resolveActivity` 判空 |
| 正在播放读取 | P1 | **只解析 `MediaStyle` / 含 `MediaSession.Token` 的媒体通知**，非媒体立即丢弃 |
| Media3 + MediaSession + 音频焦点 | P0 | `AudioFocusRequest` 用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 让路；焦点丢失必须暂停 |
| SAF 导入导出 | P0 | `ACTION_OPEN_DOCUMENT`/`CREATE_DOCUMENT` + 持久化 URI 权限，替代 `MANAGE_EXTERNAL_STORAGE` |
| FileProvider 分享 | P0 | authority `${applicationId}.fileprovider`；`file_paths.xml` **只暴露** `exports/` 与 `cache/share/` |
| 相机/相册 | P1 | 优先 `ACTION_PICK_IMAGES`（Photo Picker）零权限；拍照走 CameraX |
| 蓝牙 BLE + SPP | P1 | 后台扫描受 30s 限频；`neverForLocation` 会漏 beacon |
| 本地 ONNX 384 维 | P1 | 量化 `all-MiniLM-L6-v2`；模型按需下载到 `filesDir/models/`，**无模型必须降级**，禁止联网阻塞主链路 |
| WorkManager | P0 | `PeriodicWorkRequest` 最小 15min，`enqueueUniquePeriodicWork(REPLACE)` |
| 无障碍 | **不做** | 无「代操作其他 App」的正当需求；**不接受以无障碍换保活** |

### 6.8 明确不申请的权限

| 权限 / 能力 | 理由 |
| :--- | :--- |
| `MANAGE_EXTERNAL_STORAGE` | 过度授权，Play 仅允许文件管理/备份类核心功能；改用 SAF。前身越界防护只是**字符串前缀比对** |
| `USE_EXACT_ALARM` | 虽是普通权限，但 Play 只允许闹钟/日历类核心功能使用；消息类应用声明属违规风险 |
| `AccessibilityService`（无障碍） | Play 审核极严且可下架；**不接受以无障碍换保活** |
| `READ/WRITE_EXTERNAL_STORAGE`（旧） | 13+ 已废弃、10+ 受分区存储限制，保留只会触发冗余权限提示 |
| `ACCESS_BACKGROUND_LOCATION` / `QUERY_ALL_PACKAGES` / `PACKAGE_USAGE_STATS` / `REQUEST_INSTALL_PACKAGES` | 无正当需求，均属需专项说明的高敏能力 |
| `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` | 曲库走 SAF 目录授权；V1 无视频发送 |
| Doze 绕过手段（不可见窗口、静音音轨） | 保持「最小必要保活」，不欺骗系统调度 |

**审计清单（CI 或人工逐条勾选，20 条，命中即可能下架）**：`AndroidManifest.xml` 无 `MANAGE_EXTERNAL_STORAGE` / `USE_EXACT_ALARM` / `AccessibilityService` / 旧存储权限 / 后台定位等五类；`usesCleartextTraffic="false"` + `network_security_config`；源码与构建脚本无密钥字面量（`gitleaks` + `sk-`/`AIza`/长 base64 正则）；`HttpProxy` 白名单精确 host、`followRedirects=false`；jks / `keystore.properties` / `local.properties` 不在版本控制；签名只从 CI Secret 读；`file_paths.xml` 无 `<root-path>`/`<external-path>`；一键全关后五条 `dumpsys` 零残留；通知使用权非媒体通知立即丢弃；**0 处 `USING(true)`**；Web 端不存在 A/D 类能力的「点了没反应」入口。细节见 docs/05 §7。

### 6.9 安全基线（违反即门禁失败）

1. **签名与密钥**：构建脚本**禁止任何明文口令**；`keystore.properties` 必须在 `.gitignore`，仓库用 `gitleaks` CI 扫描；CI 从 Secret 注入 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，本地只用 debug 签名。
   > 前身：`app/build.gradle.kts:21,23` 明文弱口令 + `app/storyphone.jks` 被版本控制跟踪 → 签名密钥已泄露，新仓库第一步就是 `.gitignore` + **旧 jks 一律作废换新**。
2. **API Key 加密存储**：用 `EncryptedSharedPreferences`（MasterKey 在 Android Keystore，`setUserAuthenticationRequired(false)`）；禁止写 `BuildConfig` 明文、禁止进 Log；API Key 只显示**后 4 位**。
3. **原生 HTTP 代理白名单**：`AllowList.hosts` 精确匹配（禁后缀匹配，避免 `evil-api.viewphone.app`）、强制 `https`、`followRedirects = false`、禁止携带 `Cookie`/`Host`/`X-Forwarded-*`。
   > 前身：`sendNativeHttpRequest` 是任意 URL + 任意 header 的原生代理，等于把同源策略整个绕开（SSRF + 内网探测）。
4. **WebView 安全清单**（仅在沙箱需要时启用，且只加载本地资产）：`allowFileAccess=false`、`allowContentAccess=false`、`allowFileAccessFromFileURLs=false`、`allowUniversalAccessFromFileURLs=false`；`addJavascriptInterface` **仅暴露异步方法、数量 ≤ 20、前缀 `vp.`**；**核心界面不使用 WebView**。
5. **日志脱敏**：统一 `SafeLog`，正则脱敏手机号、邮箱、token、`Authorization` 头、消息正文（仅记长度与前 8 字符哈希）；release 关闭 `Log.v/d`。

### 6.10 测试与验收

**单元（JVM，每次提交）**：`:core` 决策状态机用 `FakeAlarmCapability` + `FakeConnectivityCapability` 覆盖「消息到达→决策→入 outbox→发送成功/失败重试」全分支；outbox 幂等（同一 `clientMsgId` 重复入队只发一次）；一键全关（`cancelAll()` 调用 1 次、`reconnectEnabled=false`、`PENDING` 全部转 `CANCELLED`）。

**仪器（`connectedAndroidTest`，夜间）**：`RemoteInput` 回复（直接调 `ReplyReceiver`，断言消息落 Room 且状态 `SENT`，不依赖 UI 自动化）；闹钟三级分支各跑一次；SAF 导入导出打桩（只写白名单目录）；蓝牙断线自动重连 3 次退避。

**真机手工清单（每次发版）**：冷启动引导 4 步全部可跳过；一键全关 + 五条 `dumpsys` 残留检查；桌宠显示/关闭后无 overlay；通知使用权开启后「正在播放」显示且日志确认**非媒体通知被丢弃**。

**端到端验收（E2 判据）**
```
前置：真机、已开通知/精确闹钟/免电池优化、已关闭「一键全关」
1. adb shell input keyevent KEYCODE_POWER           # 熄屏
2. 记录 t0；等待 30 分钟（期间不做任何交互）
3. 从服务端发送一条测试消息
4. 断言（30 分钟内，允许 ±90s 抖动）：
   a) 锁屏通知出现，展开可见 RemoteInput 输入框
   b) Room outbox 出现自动回复记录，状态 SENT，时间戳 − t0 ≤ 30min
   c) 服务端确认收到该回复，clientMsgId 唯一
5. 追加压力：adb shell am force-stop <pkg> 后再发一条
   → 期望：**收不到**（如实记录，不判失败），下次打开应用时补录队列重放成功
```
> 第 5 步的失败是**已知平台行为**，验收基线必须写明「force-stop 后依赖补录」，否则测试会长期红着并被忽视。

---

## 7. Web 端与 iOS 边界

### 7.1 一句话边界

> **手动触发可做，无人值守不可做。**
> 「阅读 / 编辑 / 管理 / 云同步 + 用户手动触发的一切」在 iOS 上可接近完整；**「无人值守的一切」（后台定时发消息、后台自动回复、锁屏投递）是 WebKit 架构边界而非投入问题**——必须用**云端定时 + Web Push（需已安装到主屏幕）**把「设备主动」改写成「服务端主动」。

**边界表（A = Android 独有｜B = 双端都有｜C = Web 受限｜D = Web 不可用）**

| 能力 | 类 | Web / iOS PWA 实况 | 缓解方案 |
| :--- | :-- | :--- | :--- |
| 拟真桌面 + Tier A 应用 | B | 功能对齐 | — |
| 定时主动发消息 | **D** | 页签与后台被冻结，**无可靠周期后台任务** | 云端 cron/队列代替本地定时，到点服务端写会话，下次打开即见 |
| 后台自动回复 | **D** | 后台冻结 → 无法自动回复 | 服务端代答；端侧私密逻辑改为「下次打开时补发」 |
| 锁屏通知投递 | C | iOS 16.4+ Web Push **仅「已添加到主屏幕」且已授权**时可用，无静默投递保证 | ① 引导安装 PWA；② 邮件/短信兜底；③ 打开时「未读汇总」补齐 |
| 通知栏快捷回复 | **D** | 无 RemoteInput，只能点开 | 通知文案一句话可读完，点击直达输入框并预聚焦 |
| 本地持久化 | C | IDB 有配额；Safari ITP 可能 **7 天未使用即清理**；`persist()` 支持有限 | ① 首屏申请 `persist()`（拿不到不阻塞）；② **E2E 加密云同步作权威副本** |
| 大文件 / 媒体 | C | 无任意文件系统访问；iOS 不支持 File System Access API | OPFS + IDB Blob，媒体外置，用完即 `revokeObjectURL` |
| 蓝牙 BLE | **D** | iOS Safari 不支持 Web Bluetooth | 隐藏 BLE 入口，提示「请在 Android 端配对」 |
| 系统闹钟直写 | **D** | Web 无系统闹钟 API | 日历 `.ics` 导入或通知提醒，文案明说差异 |
| 本地 ONNX 推理 | C | 只能 WASM 降级，内存/速度受限 | 小模型 WASM 预热；重模型走服务端；长任务拆片可中断 |
| 振动 | C | iOS 不支持 Vibration API | 视觉 + 短音效替代，**不当主反馈** |
| 后台音频 / 播报 | C | 切后台/锁屏可能被暂停 | 「打开时播放」；长音频服务端生成后点击播放 |
| 相机 / 图片上传 | B | `getUserMedia` / `<input capture>` 可用 | 上传前**客户端压缩 + 分片** |
| 云备份与恢复 | B | 同一份备份格式 | 由 `shared/data` 的备份格式单一来源生成 |

> 前身：旧版让同一套资产同时服务 WebView 与 PWA，又让 PWA 假装拥有原生能力——**边界的含糊直接变成信任损失**。
> **iOS/Web 完全没有的七个授权入口**（前台服务、精确闹钟、电池优化白名单、开机自启、通知使用权、悬浮窗、无障碍）**不得出现在 Web 端 UI 里**（不做「点了没反应」的按钮）。

### 7.2 Web 端技术栈与目录

- **构建**：Vite + TypeScript 5（**strict**、ESM），禁止无构建、无模块系统的裸脚本。
- **UI**：React + React Router（history + SW 回退）+ Tailwind CSS（消费同一份 token）；虚拟列表用 TanStack Virtual。
- **状态**：Zustand（UI 态，切片）+ TanStack Query（服务端/同步态）；**禁止业务状态散落成全局可变单例**。
- **样式**：CSS 变量设计令牌 + 组件化样式；**禁止以行内 `style=` 为主**；`!important` 数量必须为 **0**。
- **存储**：IndexedDB（`idb`）+ OPFS；**PWA**：`vite-plugin-pwa`（Workbox 生成清单）。
- **目录**：`app/`（入口、路由、Provider、错误边界）、`features/`（chat / pet / backup / settings）、`core-bridge/`（KMP 产物桥接，**唯一允许 import kotlin 输出的地方**）、`storage/`（idb schema、OPFS、迁移）、`sync/`（变更日志、冲突、重试）、`pwa/`（manifest、SW 注册、更新提示、安装引导）、`ui/`（设计令牌、通用组件）；`web/public/` 放 `icons/`（含 apple-touch-icon）与 `splash/`。
> 前身：84 个 JS / 105,984 行、18 个 CSS / 8,354 行、`index.html` 7,013 行含 **2,128 处行内 `style=`**、**399 处 `window.X =`**、**127 个 `localStorage` 键 / 558 个调用点**——全是「没有模块系统」的直接产物。

### 7.3 IndexedDB schema 与 OPFS

| 仓库 | 主键 | 索引 | 说明 |
| :--- | :--- | :--- | :--- |
| `message` | `seq`（自增，与 Android 同名字段语义一致） | `by_sessionId_seq`、`by_sessionId_createdAt_id`、`by_status_createdAt` | 消息正文，**不含二进制** |
| `session` | `id` | `by_characterId_lastMessageAt` | 会话元数据 |
| `media_object` | `sha256` | `by_kind_createdAt`、`by_refCount` | 媒体元信息（大小、mime、OPFS 路径） |
| `changeLog` | 自增 `seq` | `by_entity`、`by_pushed` | 离线变更日志，同步的真相来源 |
| `kv` | `key` | — | 只存**小**配置，**不是第二个 localStorage** |

**Object store 名与复合索引名必须与 Android 表名/索引名一一对应**（由 `core/schema/schema.json` 生成）。

- **媒体二进制一律不进 IDB 行内**，只存 OPFS 路径或 Blob 引用；二进制写 OPFS（`navigator.storage.getDirectory()`），不支持时回退 IDB Blob；渲染用 `URL.createObjectURL`，**组件卸载必须 `revokeObjectURL`**；统一 `mediaRef` 抽象（`opfs://` / `idb://` / 远端 URL）对上层透明。
- **配额管理**：启动与批量导入前 `navigator.storage.estimate()` 展示 `usage/quota`；主动申请 `persist()`（拿不到不阻塞）；软阈值（用量 **80%**）触发清理提示；**云上 E2E 密文副本是权威数据**。
- **同步与冲突**：离线写全部进 `changeLog`，联网后按序推送（FIFO + 幂等键）。消息类（只追加）用 LWW + 服务端时间戳即可；会话/实体元数据用 LWW + 变更日志并保留被覆盖版本；**当前不引入 CRDT**，但把 `changeLog` 设计成可升级为 CRDT op 的形状。
  > 前身：127 个 key / 558 个调用点把 `localStorage` 当主库；再叠加「整库 `JSON.stringify` → base64 → DEFLATE」（导出文本膨胀到 **40MB**）与 base64 图片入库，存储层直接失控。**Web 上 base64 是体积与内存的双重灾难。**

### 7.4 PWA 规范

- **Manifest**：`name` / `short_name` / `start_url` / `scope` / `display: standalone` / `theme_color` / `background_color` / `icons`（**192、512、512-maskable**）/ `orientation` / `lang: zh-Hans` / `shortcuts`。
- **iOS 特供（必须双份维护，iOS 不读 Manifest 的图标与启动图）**：`apple-touch-icon`（**180×180**）、`apple-mobile-web-app-capable`、`apple-mobile-web-app-status-bar-style: black-translucent`、`apple-mobile-web-app-title`、`apple-touch-startup-image`（配 `media` 多尺寸，**缺失会启动白屏**）、`viewport-fit=cover` + `env(safe-area-inset-*)`。
- **Service Worker**：**资源清单必须由构建产物生成**（`vite-plugin-pwa` / Workbox `injectManifest`），**禁止手工维护**；CI 校验「清单 vs 构建产物全集，差集非空即失败」。分层：App Shell 预缓存（version 化）→ 静态资源 stale-while-revalidate → API network-first（只缓存只读 GET）→ 媒体 cache-first + LRU 上限；导航统一 `navigateFallback` 保证离线可打开。
- **更新策略**：新 SW 就绪后**不静默 `skipWaiting`**，提示「发现新版本，点击刷新」；确认后 `postMessage(SKIP_WAITING)` → `controllerchange` → 刷新。
- **离线可用范围**：可打开 App Shell、浏览已缓存会话、撰写并排队发送（联网补发）、查看已缓存媒体、改本地设置。**不可用**：首次登录/注册、云端推理、云备份上传下载、未缓存媒体、Web Push 保证投递。
- **iOS 安装引导（iOS 无 `beforeinstallprompt`，必须手动引导）**：在用户完成一次有意义动作后触发（**不在首屏**）：①「把微光机装到桌面，才能收到通知」；②「点底部**分享** → 下滑选**添加到主屏幕** → 点**添加**」；③ 小字「未安装时 iOS 无法向网页推送通知」。必须可「不再提示」。
  > 前身：旧版 SW 清单**手工维护并漂移，漏掉 `index.html` 引用的 29 个脚本**，跨版本出现「旧 SW 当家把新引用打成 404」；同时 `fetch` 守卫让本地资源跳过 SW，而 `file://` 下 SW 根本不可注册——**等于装了个几乎不工作的 SW，还背上 404 风险**。

### 7.5 最小后端能力

**结论：需要后端，且是最小后端**——定时任务、Web Push、多端挤占、E2E 密文备份都需要一个在线的可信调度者；纯前端在 iOS 上无法满足 D 类能力。

| 能力 | 必需性 | 说明 |
| :--- | :--- | :--- |
| 鉴权与会话 | **必需** | 账号、设备管理、会话撤销 |
| 云备份存取 | **必需** | **服务端只见密文分片、不持密钥**（端到端加密） |
| 变更同步接口 | **必需** | 服务端做 LWW 归并 |
| 定时任务调度 | 可选（强烈建议） | 供 Web/iOS 补齐「无人值守」 |
| Web Push 发送 | 可选 | 仅已安装 PWA 有效 |
| 模型网关 | 可选 | Web 上跑重模型的唯一出路（**默认不代理 LLM 请求**） |
| 邮件/短信兜底 | 可选 | 通知兜底 |

- **不代理 LLM 请求**（除非用户自建网关）：API Key 由用户自己填，Android 存 Keystore，Web 存服务端会话或本地加密后存储；**绝不允许把 Key 硬编码进前端**。
- **账号与云备份按 `auth.uid()` 行级隔离**：RLS 一律 `USING (user_id = auth.uid())` + `WITH CHECK`，**禁止任何 `USING(true)`**；设备挤占由服务端登录时用**事务**执行 FIFO 驱逐 + 令牌吊销，前端不参与决策。
- **部署量级**：无状态容器化 API + 托管 Postgres + 对象存储 + 一个调度器；数千 MAU、备份总量 GB 级大致落在**每月几十美元**档；护栏：备份压缩后分片入库、冷数据转低频层、每用户设备配额。
- **一套分片策略、一个权威存储**（前身 GitHub 750,000 字节分片 + Supabase 4MB 分片并行，导出膨胀到 40MB）。
- **Web 端响应头（定值）**：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src 'self' blob:; connect-src 'self' https://api.<domain>; object-src 'none'; base-uri 'none'; frame-ancestors 'none'`，外加 `X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`、`Permissions-Policy` 白名单、HSTS。
- **禁止的加密把戏**：**禁止硬编码口令**（前身把流密钥口令写在 `index.html` 的 `atob('XVZTSFNISQ==')`——这不是加密，是把密钥和密文一起给攻击者）；**禁止 fail-open**（校验失败必须**阻断执行**）；**禁止自写流密码**，用 WebCrypto（AES-GCM），密钥由口令经 PBKDF2/Argon2 派生，**E2E 密钥永不离开客户端**；隐私说明须写「**丢失口令等于丢失数据**」。

### 7.6 共享内核的集成方式与唯一豁免条件

**唯一推荐路径：Kotlin/JS 产出 npm 包 + 薄封装**
```
shared/build/js/packages/viewphone-shared/     ← Kotlin/JS 产出（js(IR)）
        │  经 pnpm workspace 以本地依赖引用
        ▼
web/src/kernel/index.ts                        ← 薄封装：类型声明、错误转换、Promise 适配
web/src/apps/*                                 ← 业务直接调用封装后的 API
```
**规则：Web 端禁止用 TypeScript 重写任何编译器 / 解析器 / 记忆算法逻辑**——那份逻辑只能有一份实现（宪法 §二.1）。TS 侧只做三件事：读取 `CompiledPrompt` JSON、渲染 Trace 面板、把 bundle 序列化给编译器。

**集成约定（否则会很痛）**
1. 内核 API 只暴露**简单类型**（String / Number / Boolean / 数组 / 纯数据类），不把 Kotlin 集合类型暴露到 TS 侧。
2. 异步 API 一律返回 `Promise`（`suspend` 经 `@JsExport` + wrapper 转换）。
3. **内核导出面要窄**：只导出约 **10 个**入口（`compilePrompt`、`scanDirectives`、`recallMemory`、`matchWorldbook`、`encodeCursor`…），不做「什么都导出」。
4. 内核版本与 Web 端**同仓同版本**，一起发版。

**唯一豁免条件（必须逐字满足，否则不予合并）**
> 若集成在 **P0 验证阶段**被证明成本不可接受，唯一的例外路径是：**双实现 + CI 双向一致性测试**——同一批 **≥ 200 组 `ContextBundle`** 与 **≥ 50 组模型回复流式分片**，两端（Kotlin 与 TypeScript）输出**逐字节相同**；且必须写下 ADR 说明理由。**没有这道测试的双实现一律不予合并。**

**P0 就要做的集成验证**（风险 R1，不要等到 P6）：P0 阶段先做一个「hello world 级」KMP→JS 集成验证；不通过则**提前决策**走豁免路径。

> 待统一项：`docs/08 §2.3` 提出「混合分级」（schema/备份格式/提示词模板 → 两端各自实现；加解密/压缩/分片 → 共享），这与 `docs/02 §六`「内核只写一遍、唯一豁免需一致性测试」口径不一致；**本文件以 `docs/02 §六` 为准**，即默认单实现，豁免需满足上条。

### 7.7 iOS 真机验收清单（M = 必须｜B = 尽力｜N = 已知不支持｜[验证] = 需真机确认）

| # | 验收项 | 等级 |
| :-- | :--- | :--- |
| A1 | Safari 打开站点可登录并走通主流程 | M |
| A2 | 添加到主屏后 standalone 启动，图标与启动图正确、无地址栏 | M |
| A3 | 断网后仍能打开 App Shell 并浏览已缓存会话 | M |
| A4 | 发送消息（在线即时 / 离线排队后补发） | M |
| A5 | 图片上传（拍照 + 相册）并可回显 | M |
| A6 | 配额、`persist()` 返回值、**7 天未使用后数据是否被清**、OPFS 可用性 | M **[验证]** |
| A7 | Web Push 可订阅、可收到、点击直达会话；**未安装主屏时必须收不到**（负向断言） | B **[验证]** |
| A8 | 切后台 5 分钟回来状态未丢、连接自动恢复 | M |
| A9 | 后台/锁屏期间的「定时主动发消息」 | N（改由云端定时验证） |
| A10 | 安全区与动态字号：刘海、Home 条、放大字号下无遮挡 | M |
| A11 | 键盘弹出时输入框不被遮挡、页面可滚动 | M |
| A12 | 无振动反馈、已走视觉/音效降级且无报错 | N |
| A13 | BLE 入口在 iOS 上隐藏或提示不支持 | N |
| A14 | 媒体路径回退到 OPFS/IDB（FSA 缺失分支被走到） | M |
| A15 | 锁屏后后台音频 / 语音播报是否继续 | B **[验证]** |
| A16 | WASM 推理峰值内存下不被 Safari 回收 | B **[验证]** |
| A17 | 中文输入法候选与 emoji 输入正常 | M |
| A18 | iPad 分屏 / 横竖屏旋转不破 | B |

**必须真机实测后回填、本文不作支持性断言的 7 项**：① 存储配额与 ITP 7 天清理；② OPFS 可用性与大文件写入；③ Web Push 全链路（含「未安装主屏是否确实无法接收」）；④ 后台音频/播报；⑤ WASM(ONNX) 峰值内存上限；⑥ 后台冻结时间阈值（决定「补发」窗口与心跳设计）；⑦ `SharedArrayBuffer`/COEP 连带影响、`apple-touch-startup-image` 多尺寸、无 `beforeinstallprompt` 下引导转化。

### 7.8 双端一致性策略

**三个「同一份」（硬要求）**
1. **同一套实体 schema** —— `core/schema/schema.json` 生成 Kotlin 与 TS 两侧类型，**Web 端类型生成而非手写**；
2. **同一份备份格式** —— ZIP + `manifest.json` + `tables/*.jsonl`，两端互为可导入导出，`formatVersion` 由 schema 派生；
3. **同一套提示词编译产物** —— 由 `shared/ai/compiler` 单一来源产出，两端逐字节一致。

**golden 测试**：仓库维护 `golden/`（schema 样例、备份样例含空库/超大媒体/超长文本/时区/emoji 边界、提示词编译输入输出对、流式分片对）；Kotlin 侧 JVM 测试与 Web 侧 Vitest **读同一目录同一批文件**；任一端解析失败或输出不等即构建失败。

**允许且必须写进产品的不一致**：桌宠（系统悬浮窗 vs 页内 DOM）、定时/自动回复（端上 vs 云端）、通知快捷回复（支持 vs 无）、BLE/闹钟/振动（支持 vs 无）。差异要**显式、可测试、写进文案**。

---

## 8. 设计系统速查

> 单一事实源：`design/tokens.json` → 生成 **Compose Theme**（`core/ui/.../Theme.kt` + `Color.kt` + `Type.kt`）与 **Web CSS 变量**（`src/styles/tokens.css`）+ `tailwind.config.ts`。**两端禁止各自手写色值。**
> 生成物也进版本控制，**CI 校验「改 token 后重新生成无差异」**。

### 8.1 Token 核心数值表

**语义色（深色为默认）**

| Token | 深色 | 浅色 | 用途 |
| :--- | :--- | :--- | :--- |
| `bg-base` | `#0B0C0F` | `#F7F8FA` | 最底层背景 |
| `bg-surface` | `#14161A` | `#FFFFFF` | 卡片 / 面板 |
| `bg-surface-2` | `#1B1E24` | `#F1F3F6` | 次级面板、输入栏 |
| `bg-elevated` | `#22262E` | `#FFFFFF` | 弹层 / 抽屉 |
| `text-primary` | `#F2F4F8` | `#12141A` | 主文本 |
| `text-secondary` | `#A8AFBC` | `#5A6172` | 次要文本 / 时间戳 |
| `text-tertiary` | `#6E7686` | `#8A90A0` | 占位符 |
| `outline` | `rgba(255,255,255,.08)` | `rgba(0,0,0,.08)` | 发丝描边 |
| `accent` | `#6E9BFF` | `#3B6FE0` | 强调（可用户自定义） |
| `accent-glow` | `rgba(110,155,255,.35)` | `rgba(59,111,224,.25)` | 微光 |
| `success` / `warning` / `error` | `#3DD68C` / `#FFB13D` / `#FF5C5C` | 同族降亮 | 状态 |

**聊天专用色**

| Token | 值 | 用途 |
| :--- | :--- | :--- |
| `bubble-char` | `#22262E` | 角色气泡（左） |
| `bubble-user` | `#2E5BFF`（含 8% 微光） | 用户气泡（右） |
| `bubble-system` | 透明 + `text-tertiary` | 系统灰字（居中） |
| `bubble-withdrawn` | 透明 + 虚线描边 | 已撤回 |
| `unread-dot` | `error` + `accent-glow` | 未读红点 |
| `transfer-card` / `red-envelope` | 暖金 `#E8B45A` 系 | 转账 / 红包卡片 |

**形状 · 间距 · 字号 · 字重 · 行高**
```
radius:  xs=8  sm=12  md=16  lg=20  xl=28  full=999        (dp/px 1:1)
space :  2 4 8 12 16 20 24 32 40 48                          (4pt 栅格，禁止任意值)
type  :  caption 11 / footnote 12 / body 15 / headline 17 / title 20 / largeTitle 24 / display 32
weight:  regular 400 · medium 500 · semibold 600             (暗色下用 500/600 而非 700，避免发虚)
lineHeight: 1.35×（正文）/ 1.2×（标题）/ 1.15×（数字与计时）
font  :  中文优先系统字体（HarmonyOS Sans / 苹方 / 思源黑体）；数字与英文 Inter / SF Pro
```

**材质 · 阴影 · 动效**
```
blur    : sheet 24 · overlay 32 · hud 16                     alpha 0.6~0.8
shadow  : e1 0 1 2 rgba(0,0,0,.24) · e2 0 4 12 rgba(0,0,0,.32) · e3 0 12 32 rgba(0,0,0,.40)
motion  : fast 150ms · base 250ms · emphasis 350ms
spring  : dampingRatio 0.78 · stiffness 380（列表项入场 0.85 / 260）
z-index : 6 层常量 base / shell / app / overlay / sheet / hud（禁止任意值，前身手工排到 999999）
```

**强调色派生规则（用户自定义）**：输入一个 `H`（色相）→ 以 `accent` 的 OKLCH `L/C` 为基准生成 → **二分搜索调整 `L` 直到与 `bg-surface` 对比度 ≥ 4.5:1** → 派生 `accent-glow`（alpha 0.35）与 `on-accent`（按对比度自动取黑/白）。**禁止**直接让用户填任意色值。

**设计原则判据（5 条）**：P1 拟真优先（截图给非项目成员，第一反应是「这是手机截图」）· P2 微光是点缀（全屏辉光元素 ≤ 2 处；任一屏主色占比 ≤ 10%）· P3 一切走 Token（源码搜不到裸 `#RRGGBB`，lint 拦截；`!important` = 0）· P4 动效克制且可关（「减弱动态效果」开启后全部降为淡入淡出）· P5 双端同源（同一屏 Token 值逐项相同）。
> 前身：18 个 CSS 共 8,930 行、`!important` 164 次、硬编码色值 963 个、设计 token 只有 35 个定义、`index.html` 2,128 处行内 `style=`、z-index 手工排到 999999。

### 8.2 拟真外壳关键尺寸

| 元素 | 规格 |
| :--- | :--- |
| 手机外壳容器 | 圆角 **44**（设备形态）/ 全屏时圆角 **0**；安全区内边距 **上下 12 / 左右 16** |
| 状态栏 | 高 **44**；左：时间（`title`）；右：信号 + Wi-Fi + 电量（**18dp** 图标，间距 **6**） |
| 灵动岛 / 胶囊通知 | 居中，宽 **120** 高 **34**（空闲）/ 展开宽至 **320**、高 **44**；圆角 full |
| 图标网格 | **4 列**；行数由主题决定（薄秋 **7** 行 / 清透凉夏 **5** 行）；图标 **60×60** 圆角 **16**（M3 皮肤用圆形）；文字 **11** |
| Dock | 高 **78**；毛玻璃 blur **24** + `outline`；**4 个**图标；不显示文字 |
| 翻页指示器 | 底部居中，**6dp** 圆点，当前页 `accent`，其余 `text-tertiary` @40% |
| 小组件 | 时钟 **4×2** · 照片 **2×2** · 横幅 **4×3** · 搜索条 **4×1**；间距 **12**，圆角 **20** |
| 两态切换 | 桌面态（外壳+壁纸+网格）/ 应用全屏态（隐藏 Dock 与网格，保留状态栏）；转场用共享元素，**250ms** spring |
| 壁纸层 | 最底 z=0；毛玻璃层在壁纸之上、内容之下；**主题不得用 `!important` 覆盖用户壁纸** |
| 横屏 / 平板 | Tier A 应用仅竖屏；设置/阅读允许横屏；平板与桌面浏览器外壳居中，**最大宽 480**，两侧留暗背景，不做多列重排 |

> 前身：加一个桌面图标要改 **17 个注册点**、三处硬编码清单已漂移、主题 CSS 写成 JS 字符串无法 lint。本项目**桌面项由注册表驱动**，主题只是 Token 覆盖。

### 8.3 聊天界面关键规格

| 项 | 规格 |
| :--- | :--- |
| 列表 | `LazyColumn(reverseLayout = true)` 底部锚定；**物理节点上限 100**；游标分页每次 **30** 条 |
| 气泡 | 最大宽 **76%**；圆角 `lg`，靠尾侧下角改 `sm`；连续同人消息合并（间距 **4**，非连续 **12**） |
| 气泡尾巴 | 仅每组的最后一条显示；方向随发送者 |
| 时间分隔 | 同一天不重复；相邻间隔 > **5 分钟**显示时间；跨天显示「昨天/日期」 |
| 状态标记 | 发送中（转圈）· 已送达（单勾）· 已读（双勾）· 失败（红色感叹号 + 重试） |
| 语音条 | 高 **40**；宽度随秒数（最短 **80** / 最长 **220**）；波形 **24 根**柱；播放中柱体高亮 |
| 输入栏 | 高 **52**；左「+」扩展面板；文字/语音切换；发送按钮在输入非空时出现（**150ms** 缩放） |
| 扩展面板 | 红包 · 转账 · 位置 · 图片 · 表情包 · 语音通话 · 视频通话（六宫格，行高 **72**） |
| 引用块 | 气泡内顶部，左侧 **2dp** `accent` 竖线，灰底，最大 **3 行**省略 |
| Markdown | 支持粗体/斜体/行内代码/代码块/引用；**不支持 HTML**；渲染不得改变气泡宽度与换行 |
| 流式 | 打字机按 token 追加（**不做逐字**）；**自动滚底仅在用户未上滑时生效**；上滑后显示「回到底部」胶囊（右下，`elevated` + blur **16**） |
| 长按菜单 | 引用 · 复制 · 撤回 · 多选 · 重新生成 · 翻译（毛玻璃浮层，锚定气泡） |
| 心声卡片 | 独立于气泡的斜体卡片，`bg-surface-2`，左侧微光竖条 |

> 前身：每条气泡强制 `setTimeout(1000ms)`（5 条回复要 5 秒）；`appendMessageToDOM` 每次插入全表排序构成 O(N²)；气泡里塞 base64 图片。本项目**流式只更新一条草稿消息、节流落库、图片走文件引用**。

### 8.4 组件清单（`core:ui`，21 项，每项 6 态）

- **基础**：AppBar · TabBar · IconButton · PrimaryButton / SecondaryButton / TextButton · TextField / SearchField · Switch / Checkbox / Radio · Slider · Badge · Divider
- **容器**：Card · BottomSheet · ModalDialog · ActionSheet · Toast / Snackbar · EmptyState · Skeleton · ErrorRetryCard
- **复合**：Avatar（本地文件/占位/在线/模糊四态）· ChatBubble · MessageInput · MediaGrid · WalletCard

每项必须提供状态：`default / pressed / disabled / loading / empty / error`。**验收**：做一个「组件画廊」页面把所有组件与状态可视化陈列（P4 验收项）。
**图片资产三档**：orig（原尺寸，WebP q88，HEIF 仅作输入）· thumb（长边 256，WebP q72）· cover（长边 1024，WebP q80）。**图标**：栅格 24（线性，线宽 **1.75**）与 20（填充）；应用图标由注册表提供（禁止在 3 处各写一份）。**桌宠 9 状态名**必须是 `default / happy / sad / angry / hesitant / wash / eat / sleep / watch`（**这已是用户数据**）。

**动效 12 场景（关键值）**：应用打开/关闭 **250 / 200**（spring(0.78,380) / ease-in）；桌面翻页 **300** spring(0.85,300)；抽屉与弹层 **250** spring(0.80,350)；气泡入场 **180** ease-out + 8dp 上移；列表项错峰 **240**（间隔 30，最多 6 项）；图标按压 **120** scale 0.94；未读红点 **200** scale 0→1.15→1；打字指示器 **900** 循环；语音波形实时；来电呼吸环 **1600** 循环 alpha 0.15↔0.45；通知胶囊展开 **350** spring(0.82,320)；主题切换 **300** 交叉淡入（不做颜色插值动画）。「减弱动态效果」开启后全部改 **150ms** 淡入淡出，关闭循环类动效。

**无障碍与适配**：动态字号跟随系统，正文上限 **1.3×**；对比度正文 ≥ **4.5:1**、大字 ≥ **3:1**；RTL 镜像布局（气泡方向随语言而非仅随发送者）；iOS 安全区 `env(safe-area-inset-*)`，刘海/灵动岛区域不得放可点元素。

**视觉回归**：关键 **5 屏**（桌面 / 聊天 / 群聊 / 设置 / 我的）在两端各截一张图人工比对（M1~M3 足够；M4 起可选 Playwright 截图对比）。

---

## 9. 实施顺序与阶段门槛

### 9.1 里程碑总览

```
M1 ── 地基可信 ──────────► M2 ── 能聊天 ──────────► M3 ── 用得住 ──────────► M4 ── 上架级
   P0 + P1 + P2                P3 + P4                    P5 + P6(前半)              P6(后半) + P7
   约 5~6 周                   约 7~9 周                  约 8~13 周                 约 3~4 周
   "红线全部可验证"            "单聊跑通 + 后台活着"        "长期记忆 + 世界感"          "双端 + 云 + 发布"
```
估时口径：**单人 + AI 助手**，全职投入。**门槛未过不许进入下一阶段。**

| 里程碑 | 结束时你能做什么 | 不可妥协的验收 |
| :--- | :--- | :--- |
| **M1 地基可信** | 内核与数据层全部可单测；红线测试常绿 | 提示词 golden 通过；10 万条消息第 1000 页 < 50ms；备份全表覆盖通过；base64 写入被**生产路径**拦截 |
| **M2 能聊天** | 真机拟真桌面 + 单聊、流式回复、角色能设真闹钟、锁屏后仍能收到并自动回 | 锁屏 30 分钟端到端通过；长对话 5,000 条不掉帧；Trace 面板能解释「模型为什么这么说话」 |
| **M3 用得住** | 三层记忆生效；群聊/朋友圈/剧场/情侣空间/音乐/仪轨/桌宠/书城可用 | 记忆召回 E1 通过；每个 Tier B 模块交付一份《行为对照清单》逐条打勾（无「大概能用」） |
| **M4 上架级** | Android 出包 + Web PWA 上线 + 云同步/账号 | iOS 真机验收清单逐条过；双端同一份备份互导；CI 全绿并自动出包 |

### 9.2 P0–P7 一句话交付物

| 阶段 | 一句话交付物 | 验收门槛 | 约耗时 |
| :--- | :--- | :--- | :--- |
| **P0** 仓库与工程基线 | 仓库骨架 + `git init` 首次提交 + 版本锁定 + `shared` 空跑 + `core:*`/`feature:*` 空模块 + Detekt 规则 + CI + token 生成 + 密钥纪律 | `./gradlew build` 通过、CI 绿灯；**故意写一个跨 feature import 必须让 CI 变红**（证明规则真接上了） | 3 天 |
| **P1** 共享内核 ★最高价值 | `ai/`（compiler / directives / memory / context / worldbook / client）+ `domain/` + `data/pagination` + `economy` + golden 测试；**此阶段不写任何 UI** | `:shared:jvmTest` 全绿；`compiler`/`directives`/`memory` 覆盖 **≥ 85%**；20 组 bundle 与 oracle 逐字节一致；同一回复按 **1/2/3/7 字符**三种分片解析结果**完全相同**；200 轮历史 + 10 万字符世界书不超预算且 S0 段永不裁 | 2~3 周 |
| **P2** 数据层 | 五层存储 + 核心表与复合索引 + 写入守卫 + 游标分页 + 迁移 + **备份/恢复（一级功能）** + 容量可见报错 + `tools/diagnostics` | **四道红线全绿**（见 §1.2）；备份往返含 1 个媒体文件与 1 本 3 章的书；构造「故意漏索引 + 故意留一个 base64」的脏库，诊断能报 `UNHEALTHY` 并定位到表/行/字段 | 2~3 周 |
| **P3** 原生能力层 | `PlatformCapabilities` 全接口 + Android 实现 + **纯 JVM Fake**；保活 L0–L4 + 一键全关（9 项）+ 通知（含 RemoteInput）+ 精确闹钟 + Media3 + SAF/相机/蓝牙/剪贴板/定位/振动 + 本地 ONNX + 关键路径（收→编译→调 LLM→解析→落库→通知） | **锁屏 30 分钟端到端（E2）**；一键全关后五条 `dumpsys` 零残留；断网有明确分类与重试、恢复后自动补发；Fake 能让 P4 的 ViewModel 单测全绿 | 3~4 周 |
| **P4** 对话 MVP ★产品门面 | 拟真外壳 + **导航注册表** + 单聊（游标分页 / 流式打字机 / 长按菜单 / 全消息类型 / 搜索 / 美化）+ 角色档案与面具 + 世界书界面 + API 配置（四协议）+ **Trace 面板** + 设置 | 真机长对话 **5,000 条不掉帧**；内存常驻消息对象 < **100** 条；四种协议各跑通一次流式；Trace 能回答「模型为什么这么说话」；截图给非项目成员看第一反应是「这是手机截图」 | 4~5 周 |
| **P5** 长期记忆与世界感 | 三层记忆完整落地 + 记忆管理界面 + 角色主动行为（含去重与限流）+ Tier B 模块（群聊 → 朋友圈 → 剧场 → 情侣空间 → 音乐 → 仪轨 → 桌宠 → 书城） | **E1 判据**：注入 300 轮后问「我上次说的那个事」能答对，且 Trace 里能看到召回原文；每个 Tier B 模块行为对照清单逐条打勾；桌宠 9 状态名与前身一致 | 5~8 周 |
| **P6** Web / PWA 与云 | `shared` JS 产物接入 `web/src/kernel`（导出面 ≤ 10 入口）；React 外壳与 Tier A 应用；IndexedDB + OPFS；SW（**清单由构建产物生成**）；PWA manifest 与 iOS 安装引导；最小后端（鉴权 + E2E 密文备份 + 同步 + 可选定时/推送） | iOS 真机验收清单逐条过（已知不支持项必须写进产品说明）；双端同一份备份互导；**200 组 `ContextBundle` 两端逐字节相同** | 3~4 周 |
| **P7** 收尾与发布 | 性能与电量专项、隐私政策与权限文案、上架材料、CI 自动出包（debug/release 分流 + 签名走 Secret + artifact 保留）、崩溃遥测（可选 + 用户同意 + 脱敏）、发布与回滚预案 | A1–A20 审计清单全过；Data safety 表单与隐私政策条目与 App 内文案**逐字一致** | 2~3 周 |

**明确排除在 V1 之外**（防范围蔓延）：原生 iOS App、桌面端（Windows/macOS）、前身数据迁移器、小程序生态、工作台编码 Agent、快穿局、乙游、论坛、第三方 IM 协议对接、多人联机/云端角色共享。**要加回任何一项，先改 `docs/04` §三 并重排里程碑。**

### 9.3 测试策略（按层分配，不要都堆到最后）

| 层 | 测什么 | 工具 | 目标 |
| :--- | :--- | :--- | :--- |
| `shared` 内核 | 编译器 golden、指令流式边界、记忆算法、游标、金额 | JUnit5（`jvmTest`） | **覆盖率 ≥ 85%，占全部测试量 60%** |
| `core:data` | DAO 索引命中、迁移、备份往返、守卫负向 | Room in-memory + Robolectric | 红线测试 **100%** 覆盖 |
| `core:platform` | 能力接口契约 + Fake | JUnit5 | 每个接口至少一个 Fake 测试 |
| `feature:*` ViewModel | 状态流转、错误分支 | Turbine + 协程测试 | 关键路径 |
| UI | 聊天列表滚动、输入栏、长按菜单 | Compose UI Test | 门面级流程 |
| 真机 | 保活、通知、蓝牙、闹钟、媒体、悬浮窗、电量 | 手工清单 + `dumpsys` 脚本 | 每阶段一次 |
| Web | 内核一致性、存储、SW、PWA 安装 | Vitest + Playwright | M4 前跑通 |

### 9.4 Definition of Done（任务级，六条同时满足）

1. 代码通过编译、lint、单测；
2. 新逻辑有对应测试（核心算法必须有；UI 至少关键路径）；
3. 涉及数据层的，**表已在 `BackupRegistry` 注册**且迁移测试通过；
4. 涉及平台能力的，Fake 实现同步更新，ViewModel 单测不依赖真机；
5. 文档同步（改了什么、为什么、怎么验证）；
6. **没有引入 §2 的任何禁止项**（自查清单过一遍）。

### 9.5 风险登记册（提前认领）

| # | 风险 | 对策 |
| :-- | :--- | :--- |
| R1 | **KMP→JS 集成成本超预期**（类型桥接、调试体验） | **P0 就做 hello world 级集成验证**；不通过则提前决策走 §7.6 的豁免路径（不要等到 P6） |
| R2 | LLM 服务商协议漂移（流式格式、字段名变化） | 协议适配层 + 契约测试（录制真实响应做 fixture）+ 错误分类可诊断 |
| R3 | 国产 ROM force-stop 清闹钟 | **如实告知** + 引导白名单 + 服务端推送兜底；不承诺做不到的事 |
| R4 | iOS Safari 存储被清理（ITP 7 天） | 首屏申请 `persist()`、拒绝时明示风险、强制云备份提示、关键数据双写云端 |
| R5 | 范围蔓延（Tier C 诱惑） | §9.2 排除清单 + 「允许放弃」原则 |
| R6 | 单人维护 20 个应用 | 分层交付、Tier C 独立评估、模块边界与 lint 强制 |
| R7 | ONNX 模型体积与首启下载失败 | 优雅降级到在线 embedding；模型可离线导入 |
| R8 | 数据库迁移事故（升级后打不开） | 迁移测试 + 备份先行 + 灰度 + 可回滚 schema 策略 |
| R9 | Web 与 Android 行为分叉 | 内核单实现 + 200 组 bundle 一致性测试（M4 验收项） |
| R10 | 前身教训被遗忘、老毛病复发 | 每阶段开工前重读宪法 + 本文件；CI 把红线变成机器可判 |

### 9.6 节奏建议

1. **P1 + P2 做完之前，一行 UI 都不要写**——前身的教训是核心逻辑散进 UI 后永远无法回收。
2. 每阶段结束做一次「禁止清单对照自查」（§2 逐条过），比事后返工便宜得多。
3. 每阶段结束把该阶段学到的坑补回 `docs/`，**文档是资产，不是负担**。
4. 第一个可用的真机版本（M2）不要追求功能多，**要追求「数据与后台这两件事绝对可信」**——那才是这个项目与前身的分水岭。

---

## 10. 启动指令（可直接复制给新窗口的第一条消息）

```text
# 项目：微光机 ViewPhone（全新项目，从零开始，不是重构）

你是本项目的实施工程师。项目根目录 `D:\deepseek\viewphone`。

## 第一步（必做，不许跳过）
先完整读 `D:\deepseek\viewphone\CRITICAL.md`（《关键技术约定（合并版）》）——它是 docs/ 十份文档的
精简权威版，是你唯一必读的实施口径；冲突时以 `docs/00-CONSTITUTION.md` 为准，其次以 CRITICAL.md 为准。
读完后如需查细节，按 CRITICAL.md §11 的指针回查 `docs/` 对应章节（不要通读全部 docs）。

读完后先做三件事，做完停下来等我确认，**不要开始写业务功能**：
(a) 用你自己的话复述三条不可妥协的体验（E1/E2/E3）与四道测试红线（§1）；
(b) 列出你认为 CRITICAL.md 或 docs/ 中**互相矛盾或含糊**的地方，逐条提问；
(c) 给出 P0 阶段（仓库与工程基线）的具体执行计划：要创建哪些文件、跑什么命令、如何验证。

## 工作规则
- 每完成一个模块，输出：变更文件清单 + 关键设计决策 + 测试命令与**真实输出**（不许编造结果）。
- 遇到架构歧义：给 2~3 个方案对比（含取舍），让我决策，**不要擅自选择**。
- 不确定就说不确定。没验证过的事情不许声称已验证。
- 代码注释与文档用中文，变量名/API 用英文。
- 每阶段结束必须跑对应验收清单（CRITICAL.md §9），**未过不许进入下一阶段**。
- 任何想绕过禁止清单（CRITICAL.md §2）的"临时变通"，先来问我。

## 当前阶段：P0（仓库与工程基线，约 3 天）
目标产出：
1. 仓库骨架（目录结构见 CRITICAL.md §3.1）+ `git init` + 首次提交。
2. `gradle/libs.versions.toml` 锁定全部依赖版本（禁浮动版本）。
3. `shared` KMP 模块可空跑：`./gradlew :shared:jvmTest` 通过。
4. `core:*` / `core-service` / `feature:*` 空模块 + Detekt 自定义规则
   （跨 feature import、shared 内平台 API、文件>500 行、函数>80 行、禁止直调 System.currentTimeMillis）。
5. GitHub Actions CI：编译 + 单测 + lint。
6. `design/tokens.json` 生成 Compose Theme 与 Web CSS 变量（CI 校验重新生成无差异）。
7. 密钥纪律：签名读环境变量、`.gitignore` 覆盖 `*.jks`/`*.keystore`/`local.properties`、**不出现任何明文口令**。
8. **P0 附加项**：做一次「hello world 级」KMP→JS 集成验证（风险 R1，不许推迟到 P6），
   在 web/src/kernel 里成功调用一个导出的 shared 函数并打印结果。

验收（必须实际跑给我看）：
- `./gradlew build` 通过；CI 绿灯；
- 故意写一个跨 feature import，CI 必须变红（证明规则真的接上了）；
- KMP→JS 集成验证的可复现命令与真实输出。

现在开始 (a)(b)(c) 三步，然后等我确认再动手建仓库。
```

---

## 11. 名词与路径速查

### 11.1 前身代码在哪查玩法（只查、不照抄）

| 想查什么 | 去哪里看 |
| :--- | :--- |
| 某个玩法/应用怎么玩、入口在哪 | `D:\deepseek\xvshishiapk\app\src\main\assets\app_<模块名>.js`（`app_chat_group.js` 群聊、`app_chat_couples.js` 情侣空间、`app_quicktravel.js` 快穿） |
| 数据库表结构与字段 | `…\assets\db.js`（87 张表，**仅作字段参考**） |
| 提示词是怎么拼的 | `…\assets\app_prompts.js` + `app_context_manager.js` |
| 模型能输出哪些指令 | `…\assets\app_chat.js`（搜 `[` 开头的方括号标记） |
| 原生能力怎么调 | `…\app\src\main\java\com\story\phone\*.kt` |
| 某个功能为什么这么设计、踩过什么坑 | `开发日志.md`（项目根）与 `app\src\main\assets\开发日志.md`（**两版互补，都要查**） |
| 完整失败根因清单（六类根因 + 归因修正） | `D:\deepseek\Xvshishi_Rebuild_Proposal.md` §3 |
| 对话与内核考古（更细的证据） | `D:\deepseek\对话与AI内核考古报告.md` |

**三条纪律**：① 只借鉴「玩法与用户可感知的语义」，不借鉴实现；② **不迁移任何旧数据、不兼容任何旧格式**；③ 决定照搬某玩法前，先确认它符合本文件 §2 的禁止清单。

### 11.2 新工程关键路径与文件名

| 名称 | 路径 / 值 | 作用 |
| :--- | :--- | :--- |
| 工程根 | `D:\deepseek\viewphone` | 新仓库根（**尚未 `git init`**） |
| 本文件 | `D:\deepseek\viewphone\CRITICAL.md` | 实施窗口唯一必读（合并版） |
| 规划文档 | `D:\deepseek\viewphone\docs\00…09` + `README.md` | 细节来源；改需求先改这里 |
| 版本唯一来源 | `gradle\libs.versions.toml` | 禁浮动版本 |
| 共享内核 | `shared\src\commonMain\kotlin\com\viewphone\shared\…` | 唯一实现：编译/解析/记忆/预算/世界书/账务/分页/备份格式 |
| **深度的唯一来源** | `shared\…\ai\compiler\Depths.kt` | depth 常量表（§5.2） |
| 指令注册表 | `shared\…\ai\directives\DirectiveRegistry.kt` | 40 项指令 + 流式状态机 |
| 编译产物落盘 | `filesDir/context/<sessionId>/<turnIndex>.json` | `CompiledPrompt`（后台/Web/调试/golden 共用） |
| **schema 事实源** | `core\schema\schema.json` | 生成 Room 实体 + TS 类型 + IndexedDB 索引 |
| 备份注册表 | `core:data` 的 `BackupRegistry` | 导出/清空/导入/容量四处唯一来源 |
| Android 基础层 | `core:ui` / `core:data` / `core:platform`（接口）/ `core:platform-android`（实现） | §3.1、§6.4 |
| 核心服务进程 | `core-service`（进程名 `:core`） | CoreService + AIDL + 收→决策→回 |
| 设计 Token | `design\tokens.json` → `core\ui\…\Theme.kt`、`web\src\styles\tokens.css` | 双端同源（§8） |
| Web 内核桥 | `web\src\kernel\index.ts` | 唯一允许 import kotlin 产物的地方 |
| golden 夹具 | `golden\`（schema / 备份 / 提示词 / 流式分片） | Kotlin 与 Vitest 读同一批文件 |
| 诊断工具 | `tools\diagnostics`（`vp doctor`） | 7 项自检（§4.11） |
| CI | `.github\workflows\ci.yml` | 编译 + 单测 + lint + 迁移 + 出包 |

### 11.3 术语与标识速查

| 名词 | 含义 |
| :--- | :--- |
| `shared/` | KMP 共享内核**唯一**称呼（**不要**写成 `core:ai`；历史上 `core:*` 是被废弃的写法） |
| `CompiledPrompt` | 提示词编译产物，语言中立版本化 JSON，单一事实源 |
| `ContextBundle` | 编译器输入的不可变上下文容器（角色/面具/关系/记忆/世界书/时间/传感器/历史） |
| `depth` | 段落插入位置，**越小越靠前**；排序键 `(depth ASC, group ASC, id ASC)` |
| `SegmentProvider` | 每段一个纯函数 provider（`id` / `defaultDepth` / `group` / `render(ctx)`） |
| `DirectiveRegistry` | 指令注册表 + 流式增量状态机唯一入口 |
| `TenantProtection` / 主权防火墙 | 入库级防模型冒充用户的校验（照搬前身语义，「改造」实现） |
| `media://` | 二进制引用协议，映射到平台媒体根（Android `filesDir/media`，Web OPFS `media`） |
| `seq` | 消息全局单调自增主键，游标分页主排序键 |
| `clientMsgId` | 出站消息幂等键（`outbox` 唯一索引） |
| `idempotencyKey` | 账务幂等键，形如 `redpacket:<envelopeId>:<receiverId>`（唯一索引） |
| `BackupRegistry` | 备份/清空/导入/容量四处共用的表注册表 |
| `PlatformCapabilities` | 平台能力接口集合（全 `Flow`/`suspend`，零 Android 依赖） |
| `StorageGuard.assertStorable` | 二进制入库守卫，挂在**生产仓储调用点** |
| `E1 / E2 / E3` | 三条不可妥协的体验（§1.1） |
| `M1–M4` / `P0–P7` | 里程碑 / 阶段（§9） |
| `[验证]` | 需真机实测后回填，本文不作支持性断言（§7.7） |
| 待统一项 | 本文件在压缩过程中发现的 docs/ 内部冲突，已在对应章节标注；**未擅自修改 docs/**（见 §11.4） |

### 11.4 本文件对 docs/ 冲突的统一口径（不改 docs/，只在此声明）

| # | 冲突点 | 本文件统一口径 |
| :-- | :--- | :--- |
| 1 | 数据库选型（SQLDelight vs Room） | Android = **Room + SQLite**；Web = IndexedDB + OPFS；一致性靠 `core/schema` 生成 + CI 字段对齐；**不用 SQLDelight**（§4.10） |
| 2 | Android 进程结构（同进程 vs 独立进程） | **`:core` 独立进程** + AIDL 控制面 + 数据库数据面（WAL）；`:core` 禁依赖 `feature:*` 与 `core:ui`（§6.1、§6.2） |
| 3 | Web 端内核复用（KMP→JS vs TS 重写） | **禁止 TS 重写编译器/解析器/记忆算法**；唯一豁免 = 双实现 + CI 双向一致性测试（≥200 组 bundle + ≥50 组流式分片逐字节相同）（§7.6） |
| 4 | 共享内核命名（`shared/` vs `core:ai`） | 统一为 `shared/`；`core:*` 仅指 Android 基础层（`core:ui` / `core:data` / `core:platform`）（§3.1、§11.3） |
| 5 | 游标编码（`(seq, createdAt)` vs `(timestamp, id)`） | 物理实现用 `(seq, createdAt)`，语义对齐基准是 `(timestamp, id)`，**禁止两种编码并存**（§4.5） |
| 6 | `outbox` 表是否登记 | 列为 v1 正式表并纳入 `BackupRegistry`（§4.4） |
| 7 | 前台服务类型（`specialUse` vs `dataSync`） | 取 `specialUse` + 准备上架说明；`dataSync` 有每日时长上限，若需全天候则不适用（§6.5；原 docs/06 §9.3 仍标待确认） |
| 8 | `POST_NOTIFICATIONS` 申请时机 | 统一为「**用户发出第一条消息之后**」（§6.5） |
| 9 | `READ_MEDIA_IMAGES` 措辞 | 统一为「**默认不申请**，用 Photo Picker；仅当本机 Photo Picker 不可用时才回退申请」（§6.5） |
| 10 | Web UI 版本与状态库 | React **19** + Tailwind（消费同一份 token）+ Zustand 切片 + TanStack Virtual；React Router；`tsc --noEmit` 收口（§7.2） |
| 11 | `:platform-api` 命名 | 接口层叫 `core:platform`（纯 JVM），实现层叫 `core:platform-android`，`android.*` **只允许**出现在后者（§3.1、§6.4） |
| 12 | 内核集成策略（混合分级 vs 单实现） | 以「单实现 + 唯一豁免」为准（§7.6） |

---

**（完）**
