# 微光机 ViewPhone · 工程架构与模块边界

> 依据：`00-CONSTITUTION.md` 第二、三条。本文档确定**代码放哪里、谁能依赖谁、数据怎么流**。
> 实施时若发现某条规则不适用，**先改本文档再写代码**，不允许"先破例后补文档"。

---

## 一、仓库结构（Monorepo）

```
D:\deepseek\viewphone\
├── docs\                                  # 本目录：所有规划与规范（先读 00-CONSTITUTION）
├── settings.gradle.kts
├── build.gradle.kts
├── gradle\libs.versions.toml              # ★ 唯一版本来源，禁浮动版本
├── gradle.properties
│
├── shared\                                # ★ KMP 共享内核（唯一实现，可编译到 JVM/Android 与 JS）
│   ├── build.gradle.kts                   #   targets: androidTarget + jvm + js(IR)
│   └── src\
│       ├── commonMain\kotlin\com\viewphone\shared\
│       │   ├── ai\
│       │   │   ├── compiler\              # 提示词编译器：depths / segments / templates / artifact
│       │   │   ├── directives\            # 指令协议：注册表 + 各解析器 + 流式增量状态机
│       │   │   ├── memory\                # 三层记忆：摘要生成/向量召回/余弦/时间衰减/去重
│       │   │   ├── context\               # ContextBundle / 预算 / 分层裁剪
│       │   │   ├── worldbook\             # 世界书匹配引擎（索引化，非全表扫描）
│       │   │   └── client\                # LLM 抽象 + 四协议适配 + SSE 解析 + 重试/超时/取消
│       │   ├── domain\                    # 实体 + 校验 + 领域用例接口 + 事件
│       │   │   ├── model\                 # Character / Persona / Conversation / Message / Memory / ...
│       │   │   ├── repository\            # 仓储接口（实现由平台提供）
│       │   │   └── usecase\               # 纯逻辑用例（可单测）
│       │   ├── data\                      # 分页游标 / 排序键 / 备份格式 (manifest·注册表·校验和)
│       │   ├── economy\                   # 金额（Long 分）、账务规则、红包/转账幂等
│       │   └── util\                      # 时间/格式化/文本/ID 等（唯一实现）
│       ├── androidMain\kotlin\...         # actual：平台能力、文件存储、SQLite 驱动、ONNX 调用
│       ├── jsMain\kotlin\...              # actual：OPFS/IndexedDB、fetch、WebCrypto
│       └── commonTest\kotlin\...          # ★ 内核 golden 与算法测试（占测试量 80%）
│
├── androidApp\                            # Android 壳：DI 装配、导航图、Application、MainActivity
│   └── src\main\kotlin\com\viewphone\app\
│
├── core\                                  # Android 侧基础能力（不含业务）
│   ├── ui\                                # Design System：Theme/Token/通用组件（气泡、抽屉、Toast…）
│   ├── data\                              # Room 数据库、DAO、仓储实现、DataStore、媒体文件仓库
│   ├── platform\                          # ★ 唯一可以 import android.* 的业务无关层（设备能力）
│   └── testing\                           # 测试工具、Fake 平台实现
│
├── feature\                               # 每个功能自闭环（UI + ViewModel + 用例编排）
│   ├── desktop\      # 拟真桌面、Dock、图标网格、小组件、主题皮肤
│   ├── chat\         # ★ 单聊（核心）
│   ├── character\    # 角色档案、用户面具、关系网、世界书管理
│   ├── memory\       # 记忆库查看/编辑/重总结
│   ├── group\        # 群聊
│   ├── theater\      # 线下剧场 / 赴约 / 深谈
│   ├── moments\      # 朋友圈
│   ├── couples\      # 情侣空间
│   ├── music\        # 音乐
│   ├── reader\       # 书城
│   ├── ritual\       # 仪轨（四维状态）
│   ├── pet\          # 桌宠（悬浮窗）
│   ├── settings\     # 设置、API 配置、备份恢复、调试面板(Trace)
│   ├── forum\        # （Tier C）
│   ├── checkphone\   # （Tier C）
│   ├── quicktravel\  # （Tier C）
│   ├── heartgame\    # （Tier C）
│   ├── shopping\     # （Tier C）
│   ├── workbench\    # （Tier C）
│   └── miniapp\      # 小程序宿主 + 真隔离沙箱
│
├── web\                                   # Web / PWA（TypeScript）
│   ├── package.json                       # pnpm
│   ├── vite.config.ts
│   └── src\
│       ├── kernel\                        # ★ 消费 shared 的 JS 产物（见 §六）
│       ├── apps\                          # 与 Android feature 一一对应的应用
│       ├── shell\                         # 拟真外壳（React 版）
│       ├── storage\                       # IndexedDB + OPFS
│       └── sw\                            # Service Worker（清单由构建产物生成）
│
└── tools\                                 # 脚本：token 生成、golden 更新、备份往返测试、诊断
```

**关键取舍：为什么 `shared` 是**单模块 + 多源集**，而 Android 侧拆多模块**
- `shared` 追求"改一处、两端同时生效"，模块越少越省事；它的源集天然就是平台隔离边界。
- Android 侧拆 `feature:*` 是为了**编译隔离与增量构建**（20 个应用若在一个模块里，改一行要全量重编，且无法用 `api/implementation` 强制边界）。
- 前身教训：全部代码平铺在一个 assets 目录、无构建步骤，导致"一个 5000 行的脚本无法定位、也无法分别做语法检查"。

---

## 二、依赖方向（编译期强制）

```
androidApp ──► feature:* ──► core:ui ──┐
                    │                  ├──► shared ──► (无上游)
                    └──► core:data ──► core:platform
                                              │
web ──► shared(js 产物)                  core:platform 是唯一可 import android.* 的层
```

**硬性规则**
1. `shared` **不得依赖任何 Android API、任何 React/DOM**（`commonMain` 里出现 `android.` 或 `document.` 即编译失败）。平台差异用 `expect/actual`。
2. `feature` **之间零直接依赖**。需要协作时：
   - 只读共享数据 → 通过 `shared.domain.repository` 接口；
   - 事件通知（如"角色发了新消息"要给桌宠加红点）→ 通过 `shared.domain.event` 的 `SharedFlow`。
3. `core:data` 不得依赖任何 `feature`；`core:platform` 不得依赖 `core:data`。
4. `core:ui` 不得包含业务逻辑（不许出现"会话""角色"这类概念），只放通用视觉组件。
5. 上层可以依赖下层的**接口**，永不能依赖下层的**实现类**（实现经 Hilt 注入）。

**强制手段（CI 必须跑）**
- Gradle 的 `api` / `implementation` 严格区分，防止传递依赖泄露。
- Detekt 自定义规则：① 禁止跨 feature import；② 禁止 `shared/commonMain` 出现平台 API；③ 单文件 > 500 行报错；④ 单函数 > 80 行报错；⑤ 禁止 `System.currentTimeMillis()` 直调（必须走注入的 `Clock`，便于测试）。
- 禁止 `object` 单例持有可变状态（Detekt 规则 + code review）。

---

## 三、核心数据流

### 3.1 前台发送一条消息（Happy path）
```
① feature:chat 输入栏 → ChatViewModel.send(text)
② shared.domain.usecase.SendMessage
     ├─ 落库：Message(status=SENDING)  ──► core:data MessageRepository (Room)
     ├─ 组装 ContextBundle（角色/面具/关系/记忆/世界书/时间感知/传感器）
     ├─ shared.ai.compiler.PromptCompiler.compile(bundle) ──► CompiledPrompt
     │     · 产物写入 files/context/<conversationId>.json（供后台/离线复用，宪法 §二.1）
     └─ shared.ai.client.LlmClient.stream(compiledPrompt, profile)
③ 流式分片 ──► shared.ai.directives.StreamingDirectiveScanner（增量状态机）
     ├─ 纯文本增量 ──► Message(status=STREAMING) 逐段更新（节流写入，不是每个字符写一次库）
     └─ 完整指令 ──► Directive 强类型对象
④ 用例执行指令副作用（仅领域层可改数据）：
     · 建气泡类 → MessageRepository
     · 账务类   → economy.WalletUseCase（事务 + 唯一约束，见 §五.3）
     · 设备类   → core:platform（播放音乐 / 设闹钟 / 蓝牙）
     · 记忆类   → shared.ai.memory（写摘要 / 写向量）
⑤ 落库完成 ──► Room 发 Flow 变更 ──► UI 自动刷新（不需要手写 DOM 刷新）
⑥ 收尾：触发自动摘要判定、更新会话 lastMessageAt、必要时发通知
```

> 前身教训：旧版在"逐条气泡上屏"里塞了 `setTimeout(1000ms)`，5 条回复要 5 秒；且流式期间反复操作 DOM。本项目**流式只更新内存中的一条消息草稿，节流落库**，UI 用状态驱动。

### 3.2 后台收到消息并自动回复（关键路径，不依赖 UI）
```
① core:platform 常驻前台服务内的长轮询/推送连接收到入站消息
② 立即落库（Message，senderType=REMOTE）──► Room
③ 读取该会话的 CompiledPrompt（若缺失或过期 → 直接在本进程内重编译，因为编译器就在 shared 里）
④ LlmClient.call(compiledPrompt) —— 纯 Kotlin，无需 UI
⑤ 指令解析与副作用（同 §3.1 ④）
⑥ 发系统通知（含通知栏快捷回复 RemoteInput）
⑦ UI 若在运行，经 Room Flow 自动看到新消息
```
**这条链路的全部代码在 `shared` + `core:platform` 内，不经过任何 ViewModel、不经过 UI 进程。**

> 前身教训：旧版后台自动回复要靠"网页把拼好的 prompt 存成快照，原生照抄"，作者自述"原生不可能复刻那一整套…硬复刻必然分叉"。本项目从架构上消除了这个问题——**编译器就在共享核心里，原生与 UI 调的是同一个函数**。

### 3.3 Web 端同一条链路
```
① web/src/kernel 调用 shared 的 JS 产物（同一份编译器/解析器/记忆算法）
② 存储走 IndexedDB（结构化）+ OPFS（二进制）；接口与 Android 同构，实现不同
③ 无后台能力 → 打开时补发/补回；服务端定时任务 + Web Push 兜底（见 08-WEB-AND-IOS.md）
```

---

## 四、各层职责边界（"这个代码该放哪"决策表）

| 你要写的东西 | 放哪 | 说明 |
| :--- | :--- | :--- |
| 提示词片段文案、depth 常量 | `shared/ai/compiler` | 唯一来源 |
| 一个新的模型输出标记解析 | `shared/ai/directives` | 注册到 `DirectiveRegistry` |
| 记忆召回算法改动 | `shared/ai/memory` | 必须补单测 |
| 一个新的业务实体 | `shared/domain/model` | 含校验，双端共用 |
| 一个新页面 | `feature:<app>` | 自闭环，含自己的 ViewModel |
| 一个通用气泡/抽屉/Toast | `core:ui` | 不含业务 |
| Room 表/DAO 改动 | `core:data` + `docs/03-DATA-MODEL.md` | 必须补 Migration 与备份注册 |
| 调用蓝牙/闹钟/通知/悬浮窗 | `core:platform` | 只暴露接口，不暴露 android 类型 |
| 一个新的 Android 屏幕适配 | `feature:*` 的 Compose 代码 | |
| 一个新的 React 组件 | `web/src/apps/*` 或 `web/src/shell` | |
| 一个跨端共用的工具函数 | `shared/util` | **禁止两端各写一份** |

> 前身教训：同一个"头像解析"函数有 5 份实现、`escapeHtml` 有 8 份——因为当年没有任何"放哪"的约定。

---

## 五、几个必须写死的设计决定

### 5.1 数据库技术选型（**已冻结，不再讨论**）
| 端 | 选型 | 理由 |
| :--- | :--- | :--- |
| Android | **Room + SQLite** | Jetpack 官方、迁移工具体系成熟、与 Paging 3/Flow 集成最好 |
| Web | **IndexedDB**（结构化）+ **OPFS**（二进制） | 浏览器唯一现实选择；与 Android 同构的仓储接口 |
| 两端一致性 | **由 `core/schema` 单一事实来源生成两侧实体**，字段级同一性（UTC 毫秒 / 整数分 / UUIDv7 字符串），CI 校验生成物与声明一致 | 替代"一份 SQL 双端共用"的幻想，同时不放弃单一事实源 |

**决策**：不采用 SQLDelight。
- 优点（一套 SQL 双端共用）真实存在，但**代价是放弃 Room 的迁移测试体系与 IDE 支持**，而本项目的数据第一目标恰恰依赖"迁移必须可测"。
- 风险（两端实体漂移）由 `03-DATA-MODEL.md` §9 的**生成式 schema + CI 字段对齐测试**消除：改字段必须改 `core/schema/schema.json`，两侧代码由生成器产出，**手写第二份 = CI 失败**。
> 结论一句话：**用"生成的契约"保证单一事实源，而不是用"同一份代码"去勉强两端。**

### 5.2 时间与随机源必须可注入
所有 `Clock`、`Random`、`UuidGenerator` 经接口注入。理由：时间感知（角色会算"距上次聊天多久"）是本产品核心逻辑，**必须能在测试里拨表**。
> 前身教训：全仓直调 `Date.now()`，导致时间感知逻辑完全无法测试。

### 5.3 账务必须事务化 + 幂等
- 金额一律 `Long`，单位**分**。
- 所有余额变动走单一入口 `WalletUseCase`，内部 `transaction { }`，流水表带唯一业务键（如 `redEnvelopeId + claimerId`）防重复领取。
- 存储层用唯一索引兜底，而不是靠"先读后判再写"。
> 前身教训：金额是浮点「元」+ `toFixed(2)`；红包领取靠读-判-写、无唯一约束、无事务，并发双击可重复领取。

### 5.4 通知与后台的边界
- **入站消息处理**与**通知展示**都在服务侧完成，UI 只负责"点通知后定位到会话"。
- 通知必须支持**通知栏直接回复**（RemoteInput）。
> 前身教训：通知栏没有快捷回复（全仓 0 命中），用户必须先点开 App。

### 5.5 导航
- Android 用 Navigation Compose，路由表**由注册表驱动**（一处声明：路由 id + 图标 + 标题 + 是否在桌面显示 + 是否在 Dock）。
> 前身教训：加一个桌面图标要改 **17 个注册点**，三处硬编码清单已漂移（某个应用在 A 清单有、B 清单无）。

### 5.6 诊断与可观测性（第一天就建）
- `tools/diagnostics`：索引齐全性检查、base64 残留扫描、孤儿媒体文件统计、超大字段 TOP-N、慢查询清单、备份往返自检。
- 设置页内置"开发者诊断"面板（可导出报告）。
> 前身教训：没有任何自检工具，问题只能靠用户反馈"卡了"来发现。

---

## 六、`shared` 如何被 Web 端消费

**方案（唯一推荐）**：Kotlin/JS 产出 npm 包，`web/src/kernel` 做一层薄封装。**规则：Web 端禁止用 TypeScript 重写任何编译器/解析器/记忆算法逻辑**——那份逻辑只能有一份实现（宪法 §二.1）。
> 若集成在 P0 验证阶段被证明成本不可接受，唯一的例外路径是：**双实现 + CI 双向一致性测试**（同一批 ≥200 组 `ContextBundle` 与 ≥50 组模型回复分片，两端输出逐字节相同），且必须写下 ADR 说明理由。**没有这道测试的双实现一律不予合并。**
```
shared/build/js/packages/viewphone-shared/     ← Kotlin/JS 产出
        │  经 pnpm workspace 以本地依赖引用
        ▼
web/src/kernel/index.ts                        ← 薄封装：类型声明、错误转换、Promise 适配
web/src/apps/*                                 ← 业务直接调用封装后的 API
```
**要遵守的约定（否则集成会很痛）**
1. 内核 API 只暴露**简单类型**（String/Number/Boolean/数组/纯数据类），不暴露 Kotlin 集合类型到 TS 侧。
2. 异步 API 一律返回 `Promise`（`suspend` 经 `@JsExport` + wrapper 转换）。
3. **内核导出面要窄**：只导出约 10 个入口（`compilePrompt`、`scanDirectives`、`recallMemory`、`matchWorldbook`、`encodeCursor`…），不做"什么都导出"。
4. 内核版本与 Web 端**同仓同版本**，一起发版。

**备选方案（若集成成本过高）**：内核在 Web 端用 TypeScript 重写一遍，但**必须**：
- 共用同一套 golden 测试文件（用旧版输出与 Android 端输出双向对齐）；
- 在 CI 里跑"双实现一致性测试"，输入 200 组真实 ContextBundle，断言两端编译产物逐字节相同。
> 这是宪法 §二.1 的**唯一豁免路径**，且必须带着一致性测试才允许。

---

## 七、Android 进程与任务结构（与 `06-NATIVE-ANDROID.md` 配套）

```
┌────────────────────────────────────────────────────────────┐
│ 主进程 :app                                                  │
│  · MainActivity + Compose UI + 各 feature ViewModel          │
│  · 只做展示与输入；不做长耗时任务                              │
└───────────────┬────────────────────────────────────────────┘
                │ 跨进程：数据库为数据面（各自打开同一 SQLite，WAL）；AIDL 为控制面
┌───────────────▼────────────────────────────────────────────┐
│ 核心服务进程 :core（独立进程）                                │
│  · ForegroundService(specialUse)：长轮询/SSE 连接保活          │
│  · 入站消息处理：编译 Prompt → 调 LLM → 解析指令 → 落库 → 通知  │
│  · AlarmManager 链式看门狗（自续期）                          │
│  · WorkManager：非实时任务（备份、摘要补偿、清理、模型下载）    │
└────────────────────────────────────────────────────────────┘
```
- **是否用独立进程**：**用**。`:core` 独立进程承载关键路径（收消息→决策→回消息），理由：① 满足 E2「关掉 App、UI 被系统回收后仍能收信并自动回复」；② UI 崩溃/被杀不拖垮后台；③ 一键全关的释放点集中在服务侧，可原子完成。
  - 代价（必须认领）：跨进程通信与调试复杂度、调试时要挂两个进程。因此 `:core` **只允许依赖 `shared` + `core:data` + `core:platform`，禁止依赖任何 `feature` 与 `core:ui`**（Detekt 门禁）。
  - 备选（若实测发现跨进程成本不可接受）：退回同进程 + 前台服务，但**必须先改本文档并重新评估 E2 的验收方式**。
- **UI 与服务的通信**：**数据面**走数据库（两边各自打开同一 SQLite，WAL 模式，Room 的 `Flow` 各自观察，不互传大对象）；**控制面**走 AIDL（命令：立即检查新消息 / 刷新连接 / 一键全关 / 查询心跳状态），事件经 AIDL 回调 + 数据库双写兜底。

---

## 八、代码规范要点（写进 CI）

1. Kotlin 官方风格 + `ktlint`；Web 端 `eslint` + `prettier` + `tsc --noEmit`。
2. **禁止 `!!`**（除非附注释说明为何不可能为 null）。
3. **禁止空 catch**。捕获必须做三件事之一：处理、降级、向上抛；只写日志 = 禁止。
   > 前身教训：`catch(e){}` 遍布关键路径，一处把表名拼错导致接口永久静默返回空数组，无人发现。
4. 所有跨层数据用强类型；禁止用 JSON 字符串在层间传递。
5. 公开 API 必须有 KDoc，说明职责、参数、失败语义。
6. 提交信息用中文，按 `feat:/fix:/refactor:/docs:/test:/chore:` 分组。
