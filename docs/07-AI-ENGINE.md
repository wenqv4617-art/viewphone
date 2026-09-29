# 07 · AI 对话引擎与上下文工程（ViewPhone / 微光机）

> 这是本项目**最核心的部分**（占 60% 以上工程量）。
> 技术栈：纯 Kotlin（KMP `commonMain`），零 Android 依赖、零 DOM 依赖，**全部可在 JVM 单测里验证**；Android 与 Web 共用同一份实现。
> 三类标记：**【照搬】**沿用旧版已验证设计 · **【改造】**保留语义、重做实现 · **【新增】**旧版没有。
> 唯一实现位置：`shared/ai/`（`shared/src/commonMain/kotlin/com/viewphone/shared/ai/`）。
> 依据：`00-CONSTITUTION.md` §一、§二；`04-ROADMAP.md` §二 P1。
>
> 旧版教训：**照搬旧版产物（提示词）是对的，照搬旧版代码（实现方式）是错的。** 本文通篇按此切分。

---

## 1. 内核边界与模块

```
core/ai/src/commonMain/kotlin/com/viewphone/ai/
├── api/         AiEngineApi.kt            // 模块唯一公开入口
├── compiler/    PromptCompiler.kt, Depths.kt, SegmentCatalog.kt, CompiledPrompt.kt
├── directives/  DirectiveRegistry.kt, Directive.kt, StreamingDirectiveScanner.kt
├── memory/      CoreMemory.kt, Summarizer.kt, Recall.kt, VectorIndex.kt
├── context/     ContextBundle.kt, Budget.kt, Trimmer.kt, TokenEstimator.kt
├── client/      LlmClient.kt, Protocol.kt, SseParser.kt, Retry.kt, ErrorTaxonomy.kt
├── embedding/   EmbeddingProvider.kt, OnlineEmbedding.kt, LocalOnnxEmbedding.kt, Cosine.kt
├── worldbook/   WorldBookEngine.kt, MatchIndex.kt
└── proactive/   ProactivePolicy.kt        // 纯决策函数（无调度器）
```

**属于内核**：纯函数式编译/裁剪/召回/解析、协议适配、SSE 状态机、时间衰减排序、世界书匹配索引。
**不属于内核**：`android.*`、`java.io.File`、`Room`、`WorkManager`、UI 类型；直接网络 I/O（走 `expect/actual` 或注入接口）；事务与账务写入（属 `core:domain`）。

> 旧版教训：`app_context_manager.js` 的 `estTokens` 只服务 UI 展示、真实预算在另一个模块里，导致内核无预算能力。**预算与裁剪必须长在内核内，UI 只能读取决策结果。**

### 1.1 编译产物即契约

`CompiledPrompt` 是语言中立的版本化 JSON，落盘 `filesDir/context/<sessionId>/<turnIndex>.json`，Android 后台原生回复、Web 端、调试面板、golden 测试**消费同一份产物**，任何一方都不得自行拼提示词。

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

**版本号规则**：`schemaVersion`/`depthSpecVersion` 变更必须附迁移说明；`compilerVersion` 每次语义变更递增；`contentHash` 只对 `segments(id, depth, content)` 规范化后计算（剔除时间戳等易变字段），**同一 bundle 必须得到同一 hash**，否则缓存与 golden 无从谈起。
**【改造】** 从此只有一份实现，离线端读产物。

---

## 2. 上下文模型 `ContextBundle`

```kotlin
@Immutable
data class ContextBundle(
    val mode: PromptMode,
    val persona: Persona,                    // 角色身份墙：名/年龄/职业/母语/性格/禁忌
    val userMask: UserMask,                  // 用户人设：昵称/称呼偏好/自述
    val relations: List<Relation>,           // 关系网 + 好感度 + 拉黑三态
    val coreMemory: CoreMemorySnapshot,      // -600
    val summaries: List<MemorySummary>,      // 摘要记忆（三类）
    val recalledDialogues: List<RecalledTurn>, // -590 原文召回（已带 daysAgo）
    val worldBookHits: List<WorldBookHit>,   // 世界书，按自身 depth 插入
    val plot: PlotConstraint?,               // -480 主线剧情
    val todayState: TodayState?,             // -470 日程/穿着/随身物/位置
    val timeAwareness: TimeAwareness,        // -400 模拟时钟、距上次对话间隔
    val sensors: SensorSnapshot?,            // -490 电量/天气/位置/正在播放/歌单
    val capabilities: CapabilitySet,         // 多模态：能否出图/TTS/生视频/工具
    val group: GroupContext?,                // 群成员/禁言/管理员/投票
    val switches: SegmentSwitches,           // 段落开关 + enabledOverrides/depthOverrides
    val history: List<HistoryTurn>,          // 已落库对话（含 [MSG_ID] 锚点）
    val injected: List<FixedAppend>,         // 固定追加段（心声/翻译/表情包格式墙）
)
```

**不可变约定**：全部字段 `val`、集合为只读视图，构造后不得被任何模块修改；调整一律走返回新实例的纯函数（`bundle.withSwitches(...)`），禁止交给 UI 做可变缓存。

> 旧版教训：旧版在回复生成里对同一份 prompt 字符串连续原地拼接 7 次（心声/翻译/小程序/通话/查手机/回溯），顺序耦合、无法单测。**【改造】** 全部下沉为 bundle 字段 + catalog 段落。

---

## 3. 提示词编译器

### 3.1 depth 规范表（单一来源 `Depths.kt`）

| depth | id / group | 内容 | 来源 |
|---|---|---|---|
| -100000 | `disclaimer.top` | 法律免责置顶 | 常量 |
| -1000 | `disclaimer` | 安全底线 | 常量 |
| -950 | `offline.scenario` | 线下剧场场景设定 | 剧场 |
| -900 | `offline.rule` | 线下叙事准则 | 常量 |
| -800 | `identity.wall` | 角色身份墙 + 母语文化 + 防 OOC | Persona |
| -700 | `user.wall` | 用户人设 + 关系网 | UserMask/Relation |
| -600 | `memory.core` | 核心记忆（缓慢演化） | CoreMemory |
| -590 | `memory.recall.raw` | 原始对话向量召回 | Recall |
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
| -100 | `tools.def` | 工具/MCP 定义 | 运行时 |
| -90 | `cot` | CoT 强制格式 | 常量 |
| -85..-81 | `social.1..4` | 社交四段（主动/被@/冷场/收尾） | 常量 |
| 0 | `memory.summary` | 摘要记忆注入（**【新增】**显式位，旧版寄生在 -600） | Summaries |
| 锚点+order | `worldbook.*` | 世界书条目按 `atDepth` 落位 | WorldBookHit |
| -50 / -40 / -30 | `offline.time/cot/beautify` | 线下专用 | 常量 |
| ≥9990 | `append.*` | 固定追加段（心声/翻译/表情包/通话/查手机/线下格式墙） | Injected |

**排序键**：`(depth ASC, group ASC, id ASC)`，**depth 越小越靠前**（与 SillyTavern 相反）。相同 depth **禁止**用注册顺序决定——旧版靠插入顺序隐性排序。

> 旧版教训：群聊 builder 未接入 CATALOG（只 sort），导致群聊段落无法逐段开关。**【改造】** 所有 mode（含 GROUP/OFFLINE）必须走同一 CATALOG。

### 3.2 注册与覆盖

```kotlin
interface SegmentProvider {                      // 每段一个 provider，纯函数
    val id: String
    val defaultDepth: Int
    val group: String
    fun render(ctx: ContextBundle): String?      // null = 该 ctx 下不产出
}

class PromptCompiler(
    private val catalog: SegmentCatalog,
    private val estimator: TokenEstimator,
    private val trimmer: Trimmer,
) {
    fun compile(ctx: ContextBundle, budget: Budget, overrides: CompileOverrides): CompiledPrompt
}

data class CompileOverrides(
    val enabledOverrides: Map<String, Boolean> = emptyMap(),
    val depthOverrides: Map<String, Int> = emptyMap(),
)
```

`groupCharCounts`：按 `group` 汇总启用段落的**最终字符数**（替代旧版 `charCount` 单值），一眼看出"是记忆太长还是世界书太长"。

> 旧版教训：trace 只有字符数，无法定位膨胀来源，调试靠人肉数段落。**【新增】** 分组字符统计。

### 3.3 端侧共用（KMP → JS）

`core:ai` 全部代码在 `commonMain`；Web 端通过 Kotlin/JS 编译产物（`core-ai.js`）调用同一份 `PromptCompiler`，**禁止在 TypeScript 里重写任何编译逻辑**。TS 侧只做三件事：读取 `CompiledPrompt` JSON、渲染 Trace 面板、把 bundle 序列化给编译器。

> 旧版教训：JS 与 Kotlin 两份提示词实现必然分叉。**【新增】** 用编译目标共享，而非"人工同步"。

---

## 4. 上下文预算与裁剪

### 4.1 真实 token 估算（**【新增】**）

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
    // messages 计入时逐条累加 4；tool 定义按 JSON 原文估算
}
```

**校准要求**：CI 内用离线 tokenizer（GPT 系 + Claude 系）对 200 条真实样本做回归，断言 `|估算-真值|/真值 ≤ 0.20`；超限即调权重。估算器是**唯一预算依据**，UI 显示的数字必须来自同一实现。

> 旧版教训：`estTokens` 只在界面显示、从不参与裁剪，上下文里"完全没有 token 预算"。**【新增】** 估算即预算。

### 4.2 分层裁剪优先级

| 级别 | 内容 | 策略 |
|---|---|---|
| **S0 永不裁** | `-100000/-1000` 免责、`-800` 身份墙、`-500` 线上准则、`-400` 时间感知、`append.*` 格式墙 | 触碰即视为配置错误，直接抛 `BudgetOverflowException` 并提示用户换更大上下文模型 |
| **S1 先裁** | `memory.recall.raw`（召回原文）、`worldbook`（非 constant 条目） | 按条目粒度从低分到高分删，允许"裁剪半条"（截断到最近 N 字并加省略标记） |
| **S2 次裁** | `env.sensors`、`-450/-430/-420` 能力段、`-480` 剧情 | 整段删除，并在 trace 标记 |
| **S3 再裁** | `memory.summary`、`summaries` TopK | TopK 减半（3→2→1），最短的摘要优先保留 |
| **S4 最后裁** | `history` 消息 | 从最旧开始整体丢弃**整轮**（user+char 成对），永不留半轮；保底保留最近 `minKeepRounds=6` 轮 |
| **绝对保底** | 历史 + S0 | 若仍超限 → 拒绝请求并给出明确诊断，**绝不上截断过的半截提示词** |

**预算配置项**：`maxInputTokens`（默认 32768）、`reserveOutputTokens`（默认 1024）、`minKeepRounds=6`、`recallTopK=3`、`worldBookTokenCap`（默认 2048）、`unitsPerCjkChar=1.0`。

```kotlin
data class TrimDecision(
    val level: TrimLevel, val target: String,
    val action: TrimAction,          // DROP / TRUNCATE / REDUCE_TOPK / DROP_ROUNDS
    val beforeTokens: Int, val afterTokens: Int, val reason: String,
)
```

`trimDecisions` 进 `CompiledPrompt`，调试面板按"省了多少 token"排序展示。

> 旧版教训：上下文 `limit(10)` 与 UI 分页 30 条是两套口径，且裁剪不可见。**【新增】** 裁剪决策可见、可单测。

---

## 5. 多协议 LLM 客户端

```kotlin
interface LlmClient {
    fun stream(req: LlmRequest, profile: ApiProfile): Flow<LlmEvent>  // 冷流，collect 取消即断开
}

sealed interface LlmEvent {
    data class Delta(val text: String) : LlmEvent
    data class Reasoning(val text: String) : LlmEvent      // reasoning_content / thinking
    data class Usage(val inputTokens: Int, val outputTokens: Int) : LlmEvent
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LlmEvent
    data class Finish(val reason: FinishReason) : LlmEvent // STOP/LENGTH/FILTER/ERROR
    data class Failed(val error: LlmError) : LlmEvent
}
```

### 5.1 四协议差异表（**【新增】**，旧版只实现第一列）

| 维度 | OpenAI 兼容 | Anthropic Messages | Gemini generateContent | 自定义 |
|---|---|---|---|---|
| 端点 | `POST {base}/v1/chat/completions` | `POST {base}/v1/messages` | `POST {base}/v1beta/models/{m}:streamGenerateContent?alt=sse` | profile 模板 |
| 鉴权 | `Authorization: Bearer` | `x-api-key` + `anthropic-version: 2023-06-01` | `?key=` 或 `x-goog-api-key` | 模板 |
| system | `messages[0].role=system` | **顶层 `system` 字段** | `systemInstruction.parts[]` | 模板 |
| 消息角色 | `user/assistant/tool` | `user/assistant`（**无 system 角色**） | `user/model` | 模板 |
| 流式行 | `data: {choices[0].delta.content}` | `event: content_block_delta` + `data: {delta.text}` | `data: {candidates[0].content.parts[0].text}` | 模板 |
| 思维链 | `delta.reasoning_content` | `content_block_delta.thinking_delta` | `parts[].thought=true` | 可配 |
| 结束 | `data: [DONE]` | `message_stop` 事件 | `finishReason` + 流结束 | 可配 |
| 多模态 | `content:[{type:"image_url",image_url:{url}}]` | `content:[{type:"image",source:{type:"base64",...}}]` | `parts:[{inlineData:{mimeType,data}}]` | 模板 |
| 工具调用 | `tool_calls[]` 增量拼接 | `tool_use` block 增量 | `functionCall` 整块 | 模板 |

`ApiProfile.protocol` 是**真字段**：编译进 `ProtocolAdapter`，由适配器决定端点、鉴权、请求体、解析器。**禁止**在客户端里出现任何硬编码 `/chat/completions`。

> 旧版教训：`api_presets.protocol` 只在"拉模型列表"被读，真正请求永远硬编码 `${baseUrl}/chat/completions` + `Bearer` + `choices[0].delta.content`，Gemini 官方必失败、Claude 从未实现。**【新增】** 协议适配器 + 每协议一组契约测试。

### 5.2 SSE 解析

`SseParser` 是纯函数状态机：喂 `ByteArray`，吐 `List<SseFrame>`；跨 chunk 的半行、`\r\n`、`event:` 行、多行 `data:`、注释行全部处理。坏帧（JSON 解析失败）丢弃并计数，连续 3 帧失败即中止并归为 `ParseError`，不静默吞掉原因。

> 旧版教训：解析靠 `data: ` 前缀字符串切分，格式一变就靠"自愈补丁"（补 `}`、补 `</think>`、删伪造 status）；补丁本身是新 bug 来源。**【改造】** 状态机 + 显式失败计数。

### 5.3 超时 / 重试 / 错误分类 / 取消

```kotlin
data class RetryPolicy(
    val connectTimeoutMs: Long = 15_000, val firstByteTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 60_000, val maxAttempts: Int = 4,
    val backoffBaseMs: Long = 800, val backoffCapMs: Long = 20_000, val jitterRatio: Double = 0.3,
)

sealed interface LlmError {
    val retryable: Boolean
    data class Network(val msg: String, override val retryable: Boolean = true) : LlmError
    data class Timeout(val phase: String, override val retryable: Boolean = true) : LlmError
    data class Unauthorized(val status: Int) : LlmError { override val retryable = false }
    data class RateLimited(val retryAfterMs: Long?) : LlmError { override val retryable = true }
    data class ContentFiltered(val raw: String) : LlmError { override val retryable = false }
    data class ContextOverflow(val reportedLimit: Int?) : LlmError { override val retryable = false }
    data class ServerError(val status: Int) : LlmError { override val retryable = status >= 500 }
}
```

- **重试策略**：仅 `retryable` 且**首个内容 chunk 到达之前**才重试（已经上屏的流绝不重放，避免重复气泡）；退避 = `min(base*2^n, cap) * (1 ± jitter)`；`RateLimited` 优先遵循 `Retry-After`。
- **`ContextOverflow` 专门处理**：触发一次**收紧预算的重编译**（`maxInputTokens *= 0.7`）后重试一次；第二次仍溢出 → 失败并提示用户。
- **取消**：`flow` 的 collect 取消即关闭连接，全程 `CancellationException` 干净退出，不留半条落库。

> 旧版教训：`fetchWithTimeout` 只包"抓分享链接 meta"和图片，**不包 `/chat/completions`**，网络挂起即永久挂起，零重试零超时。**【新增】** 超时三档 + 分类退避。

### 5.4 多模态与降级

图片以 `MediaRef(uri, mime, bytes)` 传入；适配器按协议转 `image_url` / `base64 source` / `inlineData`。`ApiProfile.supportsVision=false` 时，**在编译期**把图片段降级为文字描述段（`[用户发来一张图：<本地描述>]`），而不是发出去等 400。

> 旧版教训：能力开关与真实请求脱节，模型不支持图片时报错发生在运行期用户面前。**【新增】** 编译期降级。

### 5.5 流式与 UI 的背压

- 气泡**按自然分句切分**，由指令扫描器产出的 `Directive` 边界驱动，**无固定延迟**。
- 上屏节流：`conflate` 到 30fps 的合并帧；单气泡仅当文本增量 ≥ 1 字才触发重组。
- 打字机效果是**纯渲染动画**（Compose `AnimatedVisibility` / 前端 CSS），不阻塞落库、不阻塞下一句解析。
- 落库时机：一条气泡**一旦定稿立即落库**（不等整轮回复结束），保证中途取消也保留已生成内容。

> 旧版教训：流式落库前每条气泡强制 `setTimeout 1000ms`，5 条回复要 5 秒；且"纯追加"导致 DOM 线性增长。**【改造】** 指令边界驱动 + 节流渲染 + 定稿即落库。

---

## 6. 指令协议 v2

### 6.1 指令表（40 项，含 5 项新增 / 7 项改造）

| 指令 | 状态 | 语义与权威副作用 |
|---|---|---|
| `[SENDER:名]` | 保留 | 群聊分流；只改会话归属（领域用例） |
| `[VOICE:文本]` / `[VOICE]{duration,text}` | 修改 | 统一为 `[VOICE:时长:文本]`，旧 JSON 形式仍解析（兼容层）；建语音气泡 |
| `[IMAGE:描述]` / `[MOMENT_IMAGE:描述]` | 保留 | 聊天图片卡 / 朋友圈配图；生图开关开启才调生图 |
| `[LOCATION:地点]` | 修改 | 收成 `[LOCATION:名称\|lat,lng?]` |
| `[TRANSFER:额]` / `[TRANSFER:人(额)]` | 保留 | **只建 pending 气泡**，不动账务 |
| `[RED_ENVELOPE:额:备注]` | 修改 | 合并旧版两套语法为一套 |
| `[RECEIVE_TRANSFER]` | 保留 | 改用户转账状态 + 系统灰字（领域用例 + 事务） |
| `[AGREE_PAY]` | 保留 | **唯一允许动余额的指令** → `core:domain` 用例：校验余额→幂等键→事务记账 |
| `[PAY_FOR_ME]` / `[GIFT]` | 保留 | 建卡，不改账务 |
| `[QUOTE:id]` / `[MSG_ID:n]` | 保留 | 引用闭环：注入 `[MSG_ID]` → 模型回 `[QUOTE]`；严禁复述原文 |
| `[SPLIT]` | 保留 | 强制分泡 |
| `[STATUS:json]` | 保留 | 心声；写 `status_history`（领域用例） |
| `[THOUGHT]…[/THOUGHT]` | 改造 | 与原生 `<think>` 统一为**同一 CoT 通道**，开关关闭时两者都不显示 |
| `[TRANS_JSON:json]` | 保留 | 译文挂气泡 |
| `[PLAY_MUSIC:index]` / `[STOP_MUSIC]` | 保留 | 原生播放器 |
| `[SET_ALARM:延迟:留言]` | 保留 | 系统闹钟 |
| `[BLUETOOTH_CMD:json]` | 保留 | 蓝牙（能力检测失败则降级为文本） |
| `[AUTO_CALL:voice\|video]` | 保留 | 拉起通话 |
| `[CHECK_PHONE]` | 保留 | 弹确认卡（需用户二次确认） |
| `[RECALL:id]` / `[RECALL]` | 保留 | 标记撤回 |
| `[POLL:主题(选项\|选项)]` / `[ANNOUNCE:标题(内容)]` | 保留 | 群投票/公告（领域用例） |
| `[MUTE:人(n)]` `[KICK:人]` `[TITLE:人(头衔)]` `[ADMIN:人(设/取)]` `[TRANSFER_OWNER:人]` | 保留 | 群管理，**全部经权限校验** |
| `[CALL_TOOL:json]` | 改造 | **必须**走 `ToolGateway` 白名单 + 超时 + 结果注入；模型中伪造的 result 一律剥离 |
| `[MP_INVITE]` / `[HG_GIFT]` / `[WB_TOOL:json]` | 保留 | 小程序/礼物/世界书工具 |
| `[LIKE]` `[COMMENT]` `[SHARE]` | 保留 | 朋友圈互动 |
| `[SUMMARY]` | 保留 | 触发摘要（幂等键 = 会话 + 轮区间） |
| `【表情包：释义】` | 保留 | 贴纸 |
| `[REACT:emoji:id]` | **新增** | 表情反应，取代旧版把反应塞进正文的做法 |
| `[TYPING:秒]` | **新增** | 显式"正在输入"时长，替代 UI 猜 |
| `[REJECT_PAY:原因]` | **新增** | 角色拒收；旧版只能沉默 |
| `[MEMO:文本]` | **新增** | 角色写给自己的一句话，入长期记忆候选 |
| `[MUTE_SELF:分钟]` | **新增** | 角色主动"先去忙"，配合主动行为限流 |
| `[DEBUG_RESULT]` 等模型自报执行结果 | **废弃** | 执行权在客户端，模型不得返回 result/status |

### 6.2 注册与解析

```kotlin
sealed interface Directive { val raw: String; val sourceRange: IntRange }

interface DirectiveParser {
    val name: String
    val openToken: String                     // "[VOICE:" / "[VOICE]"
    /** 已确定拿到的完整载荷，返回 null 表示"语法不合法，按纯文本" */
    fun parse(payload: String): Directive?
}

class DirectiveRegistry(private val parsers: Map<String, DirectiveParser>) {
    fun parseAll(text: String): DirectiveScanResult
    /** 流式增量：喂入任意切分的 chunk，输出已定稿事件 */
    fun advance(chunk: String, eof: Boolean): List<DirectiveEvent>
}
```

### 6.3 流式增量状态机（**【新增】** 旧版无此物）

状态：`TEXT → MAYBE_OPEN(缓冲 '<'/ '[') → IN_NAME(名字匹配 trie) → IN_PAYLOAD(括号/引号平衡计数) → DONE | ABORT`。

1. 遇到 `[` 进入 `MAYBE_OPEN`，开始缓冲；与已知 openToken 前缀比对（trie）。
2. 前缀不匹配 → 立即把缓冲当**纯文本**吐出，回到 `TEXT`（这是"降级为纯文本"的实现点）。
3. `IN_PAYLOAD` 内维护 `depth`（`(`/`(`/`{`/`[` +1，闭合 -1）与**引号内不计数**；`depth==0` 且遇 token 结束符 → `DONE`。
4. **超长兜底**：缓冲 > 2048 字仍不闭合 → 丢弃整段并以纯文本吐出，记 `directive.overflow` 指标。
5. **未闭合兜底**：`eof=true` 仍 `IN_PAYLOAD` → 同上丢弃，绝不"补 `}`"。
6. **超时丢弃**：一个指令从 `MAYBE_OPEN` 起超过 `directiveTimeoutMs=8000` 未 DONE → 强制按文本定稿（防止流卡死导致整轮不落库）。
7. 解析抛异常一律捕获 → 该段降级为纯文本，记 `directive.parse_error{name}`。

> 旧版教训：解析靠正则 + 括号扫描在**多文件重复实现**，格式一变就靠"自愈补丁"（补 `}`、补 `</think>`、删伪造 result）吞掉错误。**【改造】** 单一状态机，显式失败指标，禁止任何"猜测式修补"。

**权威副作用归属（硬契约，有测试）**：`[AGREE_PAY]`（余额）、群管理动作、`[TRANSFER_OWNER]`、`[RECEIVE_TRANSFER]`、撤回落地 —— **必须**走 `core:domain` 用例 + 事务 + 幂等键；其余（气泡、卡片、播放、闹钟）只改 UI/平台能力。`directives` 层**禁止**引用任何 Repository。

---

## 7. 记忆三层

### 7.1 核心记忆演化（**【改造】**）

```kotlin
data class CoreMemorySnapshot(
    val selfCognition: String,   // 我是谁（现状/目的/变化）
    val relationToUser: String,  // 我眼中的用户、我们的关系
    val updatedAtTurn: Int,
    val revision: Int,
)
```

- **触发**：每 `coreEvolutionInterval = 50` 轮，或角色经历"重大事件"（拉黑解除、转账、剧情节点）后一轮。
- **输入**：上一版核心记忆 + 最近 20 轮 + 现有摘要 Top10。
- **输出**：结构化 JSON `{selfCognition, relationToUser, changeReason}`；**字段级 diff**：单次变更 ≤ 原文字符数 25%，超限则只接受新版本并把 diff 写入 `coreMemoryRevision` 表（可回滚、可查看历史）。
- **注入**：`-600`，全量文本注入（这就是它慢速演化的意义）。

> 旧版教训：核心记忆直接覆盖写，无版本、无 diff，改坏了无法回退；且注入位只有 -600 一处、与摘要混在一起。**【改造】** 版本化 + 字段级 diff + 独立注入位。

### 7.2 摘要生成

- **切片单位是"轮"**：连续的 user 段 + 连续的 char 段构成一轮，`roundIndex` 全局单调。
- **触发（照搬旧版语义）**：`(pendingRounds - bufferRounds) >= autoSummaryInterval`，默认 `interval=10`、`buffer=5`，单次最多 `100` 轮。`buffer` 永不丢弃。
- **输出结构**：每条摘要 `{category: EMOTION|FACT|CORE, content, keywords[], startRound, endRound}`，一次生成可产出多条（按类别）。
- **落库**：`summaries(conversationId, startRound, endRound, category, content, keywords, vector, modelId, createdAt)`，向量维度**不固定**：以 `modelId` 为键，不同 embedding 模型不混算余弦。
- **注入**：`depth=0` 位，按类别各取 TopK（默认每类 2 条），带 `daysAgo`。

> 旧版教训：摘要在"轮"切片上正确，但向量维度取决于 `vec.length`、无 modelId，换模型后旧向量会与新向量算余弦（静默错误）。**【改造】** 向量必须带 `modelId + dim`。

### 7.3 向量召回完整算法

```
输入：queryRounds = 最近 6 轮拼接文本；λ_summary=0.05，λ_raw=0.5，λ_core=0.001
      summaryThreshold=0.55，rawThreshold=0.50，TopK=3

1. vec = embedding.embed(queryRounds)          // 双通道
2. 通道A(在线)：在线 Embedding API；超时 8s 或非 2xx → 通道B
   通道B(本地)：ONNX all-MiniLM-L6-v2，384 维（Android）/ WASM（Web）
   注：本地优先可配置（offlineFirst），但两通道产出必须记录 modelId
3. 候选集：summaries(同 modelId) ∪ dialogueVectors(同 modelId)
4. 对每个候选：sim = cosine(vec, cand.vector)        // 纯余弦，非点积
5. age = now - cand.createdAt；day = age/86400
   decay = exp(-λ * day)                             // 摘要/原文/核心各用不同 λ
6. 阈值判定只用原始 sim：sim >= threshold(通道类型)
7. 排序分 score = sim * decay                        // 衰减仅参与排序
8. 分组配额：摘要 0.33 / 原文 0.33 / 核心 0.34（三角权重），各取 TopK
9. 去重：同 startRound..endRound 区间已被摘要覆盖的原文轮次一律排除
   原文召回额外跳过最近 rawSkipRounds=5 轮（避免与上下文重复）
10. 合并注入：摘要 → depth 0；原文 → depth -590（附 daysAgo 提示）
```

**核心不变式（写成单测）**：`threshold` 与 `decay` 永不混用；一条 3 年前的记忆只要 `sim≥0.50` 就必须能进候选，只是排序靠后。

> 旧版教训：作者明确修过"久远记忆永不召回"的 bug（曾写成 `sim*decay>=threshold`）；threshold 与 decay 混用会让记忆系统静默失效。**【照搬】** 该不变式 + **【新增】** 回归测试锁死。

### 7.4 记忆管理界面（`feature:memory`）

查看/编辑/删除摘要与原文向量（**【新增】** 旧版只能看不能改）、手动触发重总结、每条记忆的召回次数统计、核心记忆版本历史与回滚、切换向量模型时的重建进度与"未迁移条目"标记。

---

## 8. 世界书引擎（**【改造】**）

- **匹配方式（四选一/条目级）**：`SUBSTRING`（默认，最短 2 字）/ `KEYWORD_INDEX`（推荐）/ `REGEX`（编译期校验，带超时）/ `PROBABILITY`（**确定性种子** = `hash(sessionId, turnIndex, entryId)`，保证可测）。
- **KEYWORD_INDEX**：启动时建倒排索引 `keyword -> RoaringBitmap(entryIds)`；一次请求对最近 N 轮文本做 `indexOf` 命中并通过位图求并集，复杂度 O(文本长度 + 命中数)。**索引热驻内存**，世界书版本号变化时重建（版本号写在 bundle 里）。
- **插入位 5 种 + `at_depth` 语义**：`BEFORE_CHAR` / `AFTER_CHAR` / `BEFORE_EXAMPLES` / `AT_DEPTH`（在 system 段内的绝对 depth）/ `AT_MESSAGE_DEPTH`（在 messages 中按"**倒数第 N 条**"插入，`N=0` 表示追加在最后一条之后）。旧版只有后两种真实可用，前三种是显式新增的等价能力。
- **预算**：`constant=true` 的条目（绿字/作者注）**永不裁**；其余受 `worldBookTokenCap`（默认 2048）约束，按 `priority DESC, order ASC` 入选，超出部分进 trace 的 `droppedByBudget`。
- **性能红线**：禁止每请求 `toArray()` 全表扫描；禁止循环内线性查找（旧版 `findRec` 反复调用成 O(n²)）。

> 旧版教训：匹配是纯 `indexOf` 子串且每请求全表拉取，`findRec` 线性查找构成 O(n²)。**【改造】** 倒排索引 + 版本化缓存 + 预算。

---

## 9. 主动行为与后台生成

- **调度**：WorkManager 每 15 分钟一个 tick，对每个角色做**纯函数决策**（`ProactivePolicy.shouldAct(state, now)` 无副作用、可单测）：日程事件到点、距上次对话 > 阈值、朋友圈冷场、来电时机。
- **去重**：`dedupeKey = hash(characterId, behaviorType, contentHash, 时间桶)`；一次主动行为落库前查唯一约束。
- **限流（"不许刷屏"）**：单角色主动消息 ≤ 1 次/30 分钟、≤ 4 次/天；主动朋友圈 ≤ 1 次/天；主动来电 ≤ 1 次/天；夜间 `23:30–07:30` 静默（除非用户在线且角色设了"夜猫子"）；单会话并发请求恒为 1。
- **离线原生回复**：Android 后台服务**复用同一份 `CompiledPrompt`**——它读最近一次的产物 + 增量追加新消息后局部重编译（同一 `PromptCompiler` 代码路径），**禁止**任何 Kotlin 版提示词特化实现。断网时退回本地模板（明确标注"离线模式"），不伪造 AI 输出。

> 旧版教训：离线兜底在 Kotlin 里另写了一套 prompt（双份实现必然分叉）；主动行为无统一限流，靠各模块自觉。**【改造】** 单实现 + 策略对象 + 硬限流。

---

## 10. 可测试性

**纯函数边界（必须无副作用、无时钟、无随机、无 I/O）**：`PromptCompiler.compile`、`TokenEstimator.estimate`、`Trimmer.trim`、`DirectiveRegistry.parseAll/advance`、`Recall.rank`、`Cosine`、`WorldBookEngine.match`、`ProactivePolicy.shouldAct`。所有时间与随机通过参数注入（`now: Long`、`Random(seed)`）。

1. **Golden 测试**：用 Node 把旧版 `app_prompts.js` 包桩跑起来，导出 `bundle → 期望 systemPrompt + 段序` 作为 oracle。
   **局限（必须写进测试注释）**：① 只覆盖单聊/群聊主路径，线下与工作台未接入 CATALOG，覆盖不全；② 旧版本身有无意的行为（如 `limit(10)`、深度相同的隐式插入序），**对齐旧 bug 是错的**；③ 文案与 depth 一旦有意改动，golden 必须显式更新并在 PR 说明理由，**禁止为了让测试变绿而改 golden**。
2. **Mock 向量回归**：手写 20 组固定向量，断言排序、配额、去重、衰减不影响阈值这四条不变式；另加"跨 modelId 不混算"用例。
3. **流式解析边界用例清单（每条一个测试）**：指令跨 2/3/N chunk 切分；`[` 结尾后断开；`[RED_ENVE` + `LOPE]`；名字非任何指令前缀（应立即吐文本）；引号内含 `]`；括号不平衡；超长未闭合 >2048；`eof` 未闭合；8000ms 超时丢弃；单 chunk 含 3 个指令；指令内嵌套逗号/冒号；emoji 与 CJK 混排切分；`\r\n` 与 `event:` 行；坏 JSON 帧连续 3 次；`[DONE]` 前后各有半帧。
4. **协议契约测试**：四协议各一份真实响应样本（离线录制）→ 断言 `LlmEvent` 序列完全一致。
5. **CI 门槛**：`core:ai` 单测全绿；`compiler/directives/memory/context` 行覆盖 ≥ 85%；golden 变更必须在 PR 描述中声明；Detekt 自定义规则（`core:ai` 内出现 `import android.`/`java.io.File`/`okhttp` 即失败；单文件 ≤ 500 行、单函数 ≤ 80 行）；**禁止清单扫描测试**（见 §11 的可自动断言项）。

> 旧版教训：内核逻辑与 UI/DB 混写在一个 10,328 行文件里，无任何单测，回归全靠手点。**【新增】** 纯函数边界 + CI 门槛。

---

## 11. 反面清单（本项目永久禁止）

1. **禁止硬编码上下文条数**（如只发最近 10 条）而 UI 另按 30 条渲染。→ 旧版：LLM `limit(10)` vs DOM 分页 30，两套口径。
2. **禁止硬编码端点/鉴权/字段路径**（`/chat/completions`、`Bearer`、`choices[0].delta.content`）。→ 旧版：`protocol` 是死字段，Gemini 必失败、Claude 未实现。
3. **禁止对话请求无超时**。→ 旧版：`fetchWithTimeout` 不包 `/chat/completions`，挂起即永久挂起。
4. **禁止在流式渲染路径上加固定延迟**（`setTimeout 1000ms`/气泡）。→ 旧版：5 条回复要 5 秒。
5. **禁止每请求全表扫描 / 循环内线性查找**。→ 旧版：世界书每请求 `toArray()`，`findRec` 构成 O(n²)。
6. **禁止重复实现指令解析或出站清洗**。→ 旧版：多文件重复正则 + "自愈补丁"。
7. **禁止同一提示词存在两份语言实现**。→ 旧版：JS 与 Kotlin 双份，作者自陈必然分叉。
8. **禁止字符串拼 JSON / 手写序列化**（一律 kotlinx.serialization）。→ 旧版：手拼 JSON + 补 `}` 兜底。
9. **禁止模型自报执行结果**（`[DEBUG_RESULT]`/`result`/`status` 一律剥离）。→ 旧版：必须靠清洗补丁删伪造字段。
10. **禁止 `Float`/`Double` 表示金额**（一律 `Long` 分）；**禁止金额指令只建 pending 气泡还宣称"扣款"**。→ 旧版：`[AGREE_PAY]` 是唯一真实记账点。
11. **禁止在 `DirectiveParser` 内访问 Repository / DB / 发起网络**。→ 旧版：解析与副作用耦合在 `app_chat.js`。
12. **禁止 `catch{}` 静默吞解析/网络错误**（必须归类 + 计数 + 上报）。→ 旧版：自愈补丁掩盖格式漂移。
13. **禁止把上下文/提示词全文常驻内存缓存**（LRU 保留最近 24 轮全文那种）。→ 旧版：`MAX_KEEP=24` 长文本常驻。
14. **禁止 base64 图片进任何持久化或上下文传输层**。→ 旧版：40MB → `TransactionTooLargeException`。
15. **禁止上线前未跑四协议契约测试与 golden 测试**。

---

**附：可自动断言的禁止项**（写进 `ForbiddenPatternsTest`，源码文本扫描）
`"/chat/completions"` 硬编码 · `setTimeout|delay(1000)` 出现在 `client/` 与 UI 上屏路径 · `limit(10)`/`take(10)` 形式的上下文硬编码 · `toArray()` 在所有 provider 内 · `JSONObject`/手拼 `"{"` 在 `core:ai` · `import android.` 在 `core:ai` · 同一指令名出现在两个 parser 文件。
