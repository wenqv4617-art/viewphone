# 06 · Android 原生能力与特权规划（ViewPhone / 微光机）

> 适用版本：ViewPhone 0.1（全新项目，无旧数据迁移）
> 技术栈：Kotlin + Jetpack Compose + Room + Coroutines/Flow（**核心界面不用 WebView 承载**）
> 配套：Web 端仅用于覆盖 iOS 与桌面浏览器，不承担 Android 后台行为（见 `08-WEB-AND-IOS.md`）。
>
> 本文的取舍依据来自旧版「叙事诗小手机」（Kotlin 壳 + WebView 混合，10 个 Kotlin 文件约 6,900 行）的实测结论。凡标注「旧版教训」「平台限制」处，均为可复现事实，不是推测。

---

## 0. 结论先行（五条硬决策）

1. **核心链路全部原生**：`WebView` 不承载核心界面，也不承载「收消息→决策→回消息」，后者放 `:core` 独立进程 + 前台服务 + `CoroutineWorker`。
   > 旧版教训：旧版 110 个 `@JavascriptInterface` 全部同步，跑在 JavaBridge 线程，会冻结整个 WebView 的 JS 执行（作者自己在 `AndroidMcp.kt:446-453` 记录，只补了 2 个异步变体）。同步桥一旦成为核心链路，保活再强也会被自己的桥堵死。
2. **保活做「分层最小必要」**：前台服务 + 链式精确闹钟 + 用户显式授权的自启/白名单，四件套里删掉悬浮窗与静音 `AudioTrack` 这两条「对抗系统调度」的手段。
   > 平台限制：部分国产 ROM 的 force-stop 会清除应用注册的**全部闹钟**，任何应用内代码都无法挽回（旧版日志结论）。因此保活只能"提高存活概率"，不能承诺"必定到达"。
3. **能力抽象为 `PlatformCapabilities`（接口 + `Flow`/`suspend`），禁止同步阻塞**，Android 实现只在 `:platform-android` 模块，纯 JVM 单测用 Fake。
4. **权限最小化 + 逐项可降级**：通知使用权只解析媒体类通知；`MANAGE_EXTERNAL_STORAGE` 不作为默认路径，改用 SAF。
   > 旧版教训：旧版 `NotificationListenerService` 解析了所有应用的通知（隐私问题），且以 `MANAGE_EXTERNAL_STORAGE` 做公共目录读写、越界防护只是字符串前缀比对，Play 上架与合规双重风险。
5. **安全基线是门禁不是建议**：签名与密钥走 Keystore/环境变量/CI Secret，`sendNativeHttpRequest` 类任意 URL 代理必须做域名白名单。
   > 旧版教训：`app/build.gradle.kts:21,23` 明文弱口令签名配置 + `app/storyphone.jks` 被版本控制跟踪；`sendNativeHttpRequest` 是任意 URL/任意 header 的原生 HTTP 代理（同源绕过 = SSRF）；`usesCleartextTraffic=true`。这四条在新项目里属于"不许重演"。

---

## 1. 能力矩阵

优先级定义：**P0** = 0.1 必须可用；**P1** = 0.2 补齐；**P2** = 排期可延；**不做** = 明确放弃并写明理由。

| 能力 | 是否实现 | 优先级 | 需要的权限 | 实现要点 | 已知平台限制 |
|---|---|---|---|---|---|
| 前台服务保活 | 是 | P0 | `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`（或更贴切的 `dataSync`） | `:core` 进程常驻服务，通知渠道 `IMPORTANCE_LOW` 且内容诚实可关 | Android 14+ 需声明 `foregroundServiceType` 并在清单写用途；`specialUse` 上架需说明 |
| 链式精确闹钟 | 是 | P0 | `SCHEDULE_EXACT_ALARM`（或 `USE_EXACT_ALARM`） | 三级降级：`setAlarmClock` → `setExactAndAllowWhileIdle` → `setAndAllowWhileIdle`；每次触发后重新 arm 下一次 | 国产 ROM force-stop 清空全部闹钟；`USE_EXACT_ALARM` 仅限闹钟/日历类应用 |
| 开机自启 | 是 | P0 | `RECEIVE_BOOT_COMPLETED` | `BootReceiver` 收到 `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` 后重新 arm 闹钟链与前台服务 | 国产 ROM 需用户手动开"自启动"，`BOOT_COMPLETED` 可能不下发 |
| 电池优化白名单 | 是 | P0 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导跳 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，拒绝则降级为前台服务+闹钟 | 厂商各自有二级开关（省电策略/后台运行），系统 Intent 跳不过去 |
| 通知（渠道/富通知） | 是 | P0 | `POST_NOTIFICATIONS`（13+ 运行时） | 多渠道：`core`(LOW) / `message`(HIGH, BigText + action) / `media`(MediaStyle) | 渠道重要性创建后不可由 App 上调，只能引导用户改 |
| **通知栏快捷回复 RemoteInput** | 是 | P0 | 同通知权限；通知使用权（可选增强） | `RemoteInput` + `PendingIntent` 直达 `:core` 的 `ReplyReceiver`，不经过 UI 进程 | 部分 ROM 收起输入框；需 `setAllowGeneratedReplies(true)` 才能被 Wear/车机识别 |
| 系统闹钟写入 | 是 | P1 | 无（`AlarmClock.ACTION_SET_ALARM`） | 用户在对话里说"明早 7 点"时，写系统闹钟而非仅应用内提醒 | 部分 ROM 无系统时钟应用，需 `resolveActivity` 判空 |
| 正在播放读取 | 是 | P1 | `BIND_NOTIFICATION_LISTENER_SERVICE`（通知使用权） | **只解析 `MediaStyle`/含 `MediaSession.Token` 的媒体类通知**，非媒体通知立即丢弃 | 用户授权页深、易被回收；不可读取未发通知的播放器 |
| 蓝牙 BLE + SPP | 是 | P1 | `BLUETOOTH_SCAN`/`CONNECT`（12+）、`BLUETOOTH`/`ADMIN`（≤11）、`ACCESS_FINE_LOCATION`（≤11 扫描） | BLE 扫描/GATT/特征读写/断线重连；经典 SPP 串口独立通道 | 后台扫描受 30s 限频；`neverForLocation` 会漏 beacon |
| Media3 后台播放 + MediaSession + 音频焦点 | 是 | P0 | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media3 `ExoPlayer` + `MediaSessionService`；`AudioFocusRequest` 用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 让路 | 音频焦点丢失必须暂停，否则被系统强杀 |
| TTS | 是 | P1 | 无 | `TextToSpeech` 封装成 `suspend` 的 `speak()`，用 `UtteranceProgressListener` 回调 | 需设备已装 TTS 引擎，中文引擎缺失时降级为纯通知 |
| 悬浮窗桌宠 | 是 | P1 | `SYSTEM_ALERT_WINDOW` | 原生 `ComposeView` 走 `WindowManager`，尺寸/DP 由原生计算 | 隐藏 5 分钟后 Blink 定时器被放大到 60s（旧版为此加了 1×1 透明悬浮窗，本版不照搬） |
| SAF 导入导出 | 是 | P0 | 无（`ACTION_OPEN_DOCUMENT`/`CREATE_DOCUMENT`） | 用 `DocumentFile` 与持久化 URI 权限，替代 `MANAGE_EXTERNAL_STORAGE` | SAF 树操作慢；无法直接拿绝对路径 |
| FileProvider 分享 | 是 | P0 | 无 | authority `${applicationId}.fileprovider`，`file_paths.xml` **白名单**只暴露 `exports/` 与 `cache/share/` | 暴露根目录即等于把私有目录交给任意 App |
| 相机/相册 | 是 | P1 | `CAMERA`、`READ_MEDIA_IMAGES`（13+，可用 Photo Picker 免权限） | 优先 `ACTION_PICK_IMAGES`（Photo Picker）零权限；拍照走 `CameraX` | 部分 ROM Photo Picker 由主模块提供，旧设备需回退 |
| 剪贴板 | 是 | P1 | 无（读需前台焦点，10+ 限制） | 写用 `ClipboardManager.setPrimaryClip`；读仅在前台且用户显式触发 | Android 10+ 后台读剪贴板直接拒绝并打日志 |
| 定位/天气 | 是 | P2 | `ACCESS_COARSE_LOCATION`（天气只需粗略） | 只取 `getLastKnownLocation` 粗定位；用户拒绝则手输城市 | 后台定位需 `ACCESS_BACKGROUND_LOCATION`，本版**不申请** |
| 电量/充电 | 是 | P0 | 无 | `BatteryManager` + `ACTION_BATTERY_CHANGED` 粘性广播，`Flow` 化 | 无 |
| 屏幕状态 | 是 | P0 | 无 | `PowerManager.isInteractive` + `ACTION_SCREEN_ON/OFF`（需动态注册） | 静态注册收不到屏幕广播 |
| 振动 | 是 | P0 | `VIBRATE` | `VibratorManager`（S+）/`Vibrator`（旧）双分支，`VibrationEffect` 波形 | 部分 ROM 静音模式吞振动 |
| 本地 ONNX 向量推理 | 是 | P1 | 无 | `onnxruntime-android` 跑量化 `all-MiniLM-L6-v2`（384 维）；模型按需下载到 `filesDir/models/` | 无模型必须降级（旧版降级为哈希向量），禁止联网阻塞主链路 |
| WorkManager 周期任务 | 是（新增） | P0 | 无 | `PeriodicWorkRequest` 最小 15min，唯一工作名 `enqueueUniquePeriodicWork(REPLACE)` | Doze 下顺延；不能当精确调度器用 |
| WebSocket/SSE 长连接保活 | 是（新增） | P0 | 无 | OkHttp `WebSocket` 在 `:core` 服务内；心跳 20s，60s 无帧视为断线，`Flow` 重连 + 指数退避 | 网络切换/Doze 必断，必须自愈；长轮询做兜底 |
| Widget / 快捷方式 | 是 | P2 | 无 | `AppWidgetProvider`（Glance 优先）+ `ShortcutManager` 动态快捷方式 | Widget 刷新受 `updatePeriodMillis` 最小 30min 限 |
| 无障碍（AccessibilityService） | **不做** | 不做 | 需用户手动开启无障碍 | Play 对其审核极严，且一旦被判定滥用会被下架；本项目无"代操作其他 App"的正当需求 | 开启后进程存活率显著提升，但**不接受**以无障碍换保活 |
| 通知使用权 | 是（受限用途） | P1 | 通知使用权 | 仅用于「正在播放」与「媒体控制」，代码层面过滤非媒体通知 | 授权入口深；用户可随时回收 |

---

## 2. 保活体系设计（本项目版）

### 2.1 分层与作用边界

| 层 | 手段 | 能解决什么 | 不能解决什么 |
|---|---|---|---|
| L0 前台服务 | `:core` 常驻 `foregroundServiceType` 服务 + 常驻通知 | 进程不在后台 LRU 里被优先杀；可长期持锁 | 用户手动「强行停止」、厂商清理器 |
| L1 调度 | 链式精确闹钟（三级降级）+ `WorkManager` 周期兜底 | Doze 下仍能被唤起（精确闹钟）、15min 级自愈 | force-stop 后闹钟被清空 |
| L2 用户授权 | 电池优化白名单、自启动、厂商后台白名单引导 | 显著降低被杀概率 | 各家开关路径不同，无法程序化保证 |
| L3 网络 | WebSocket 长连接 + 长轮询兜底 | 收消息的实时性 | 断网、Doze 网络冻结 |
| L4 兜底 | 下次启动时的补录队列重放 | 漏收消息最终不丢 | 无法保证"当时就回" |

**明确删除的两条旧手段**：
- **1×1 透明悬浮窗**：旧版用它规避"隐藏 5 分钟后 Blink 定时器被放大到 60s"。本版用 MediaSession/前台服务维持调度，不再用不可见悬浮窗欺骗系统。
  > 旧版教训：该手段绕过了 WebView 的定时器节流，但代价是常驻不可见窗口，用户无感知且商店审核有风险。核心链路已原生化，不再需要它。
- **静音 `AudioTrack` 无限循环**：本版不做。它是"防止音频子系统冻结"的偏方，且会让音频焦点与省电统计失真。
  > 平台限制：静音音轨不能阻止 force-stop，也不能阻止 Doze 网络冻结，收益与代价不匹配。

### 2.2 最小必要保活原则

- 保活的**唯一目的**是"消息到达时能及时处理"，不是"进程永不退出"。若某条手段不能直接提升消息到达率，就不加。
- 常驻通知必须**诚实**：文案说明"用于接收消息"，且提供关闭入口。
- 只申请 `SCHEDULE_EXACT_ALARM`（用户可撤销），**不申请 `USE_EXACT_ALARM`**。
  > 平台限制：`USE_EXACT_ALARM` 是普通权限但 Google Play 政策只允许闹钟/日历类核心功能使用；ViewPhone 是消息类应用，声明它属于政策违规风险，应走 `SCHEDULE_EXACT_ALARM` + 引导用户授权。

### 2.3 「一键全关」实现清单（必须彻底释放）

`SettingsRepository.setBackgroundEnabled(false)` 必须原子地完成以下全部动作，任一失败都记录并可重试：

1. `stopForeground(STOP_FOREGROUND_REMOVE)` 并 `stopSelf()` 关闭 `:core` 前台服务；
2. 取消全部已注册闹钟：遍历 `PendingIntent` 请求码集合逐个 `AlarmManager.cancel()`，并清空持久化的请求码表；
3. 取消全部 Work：`WorkManager.cancelUniqueWork("vp.core.periodic")`、`cancelUniqueWork("vp.core.outbox")`；
4. 关闭 `:core` 进程：`stopService(Intent(this, CoreService::class.java))`，必要时 `Process.killProcess` 自身（仅 `:core`）；
5. 断开 WebSocket/长轮询，取消 OkHttp `Dispatcher`，置 `reconnectEnabled=false`；
6. 取消所有通知：`NotificationManagerCompat.cancelAll()`，并删除 `core` 渠道以外本应用创建的全部渠道；
7. 移除悬浮窗：`WindowManager.removeViewImmediate()` 并把桌宠状态置为 `DETACHED`；
8. 释放全部 `WakeLock`：`PowerManager` 持有集合逐个 `release()`，并在 `onDestroy` 断言 `isHeld == false`；
9. 解除动态注册的 `BroadcastReceiver`（屏幕、电量），并把 `Room` 中 `scheduled=true` 的待发消息标记为 `CANCELLED`。

验收方式见 §7.3：关闭后用 `dumpsys alarm | grep <pkg>`、`dumpsys power | grep -i wake`、`dumpsys notification` 三条命令验证"零残留"。

### 2.4 对国产 ROM 的如实告知文案

> 首次引导与设置页展示（不夸大、不承诺）：
> 「不同厂商对后台应用的管理策略不同。部分机型在'强行停止'或系统清理后会清除本应用的全部定时任务与后台服务，**这些限制无法由应用绕过**。为保证消息及时到达，建议你：① 允许自启动；② 关闭本应用的电池优化；③ 在系统设置中把本应用设为'允许后台运行'。若你关闭后台能力，消息将在你下次打开应用时补发。」

---

## 3. 关键路径架构决策

### 3.1 推荐结论

**推荐：`:core` 独立进程 + AIDL/Binder 控制面 + Room 数据面（Flow 跨进程订阅）+ WorkManager 单例。**

| 决策 | 选择 | 理由 |
|---|---|---|
| 是否需要 `:core` 独立进程 | **需要** | UI 被杀（用户划掉 Recents）不应带走消息处理；独立进程可单独被前台服务保护 |
| 通信方式 | 控制面 AIDL、数据面 Room + Flow | AIDL 只传命令与状态（少量、强类型）；消息实体走 Room，避免大对象跨 Binder（1MB 事务上限） |
| 为什么不用纯 Room 共享 | 不够 | Room 天然支持多进程（`enableMultiInstanceInvalidation()`），但"立即执行一次发送"这类命令需要低延迟 RPC |
| 为什么不用纯 AIDL | 不够 | 消息历史、富文本、附件会撑爆 Binder；且 UI 需要可查询的历史 |
| 为什么还要 WorkManager | 兜底 | 进程被系统回收后，已入队的 Work 由系统持久化调度，是"最后一次自愈机会" |

### 3.2 进程结构

```
:app    (UI 进程)    Compose UI、Room 读、AIDL 客户端、SAF/相机等前台交互
:core   (常驻进程)   CoreService(前台服务) + WebSocket + 闹钟接收 + 发送队列 + 通知/RemoteInput 接收
:remote (可选,0.2)   仅承载 ONNX 推理，隔离大内存峰值；内存不足时不启动
```

> 旧版教训：旧版把长轮询 `IlinkPoller` 与原生回信 `NativeReplyFallback` 放在与 WebView 同进程，网页心跳失效后要靠 45s+6s 宽限判定才切换原生回信；进程一旦被 WebView 崩溃带走，回信链路整体失效。

### 3.3 进程死亡后的恢复

1. `CoreService.onStartCommand` 返回 `START_STICKY`；`onTaskRemoved` 中重新 arm 下一次闹钟（不启动不可见 Activity）。
2. 所有待发消息先落 Room（`outbox` 表，状态 `PENDING`），发送成功才置 `SENT`；因此**重启后必不丢消息**。
3. `:core` 启动时序：重建 Room → 领取 `PENDING` → 建 WebSocket → 注册闹钟链 → 发前台通知。
4. 每轮发送带 `clientMsgId` 做幂等，服务端去重。
   > 旧版教训：旧版补录队列只有 100 条、快照 6h TTL、每小时 20 条限流，且认领去重逻辑在网页侧；限制本身合理，但队列溢出即静默丢弃。本版改为 Room 持久队列 + 无上限（按磁盘）+ 幂等键。

### 3.4 与 UI 进程的通信

```kotlin
// core 端
class CoreBinder : ICoreControl.Stub() {
    override fun send(text: String, clientMsgId: String) = coreRuntime.enqueue(text, clientMsgId)
    override fun state(): CoreState = coreRuntime.snapshot()      // 小对象，可过 Binder
    override fun setBackgroundEnabled(enabled: Boolean) = coreRuntime.toggle(enabled)
}
// UI 端：数据面直接查 Room（多进程失效通知）
val messages: Flow<List<Message>> = db.messageDao().observeRecent()
```

---

## 4. 平台能力抽象层

### 4.1 设计原则

- 每个能力一个接口，返回 `Flow` 或 `suspend`；**接口里不允许出现同步阻塞方法**。
- 接口层是纯 Kotlin/JVM 模块（`:platform-api`），**零 Android 依赖**，因此可在 JVM 单测里跑 Fake。
- Android 实现集中在 `:platform-android`，通过 Hilt 绑定。

```kotlin
interface PlatformCapabilities {
    val notifications: NotificationCapability
    val alarms: AlarmCapability
    val connectivity: ConnectivityCapability
    val bluetooth: BluetoothCapability
    val media: MediaCapability
    val speech: SpeechCapability
    val overlay: OverlayCapability
    val files: FileCapability
    val capture: CaptureCapability
    val clipboard: ClipboardCapability
    val location: LocationCapability
    val battery: BatteryCapability
    val screen: ScreenCapability
    val haptics: HapticsCapability
    val inference: InferenceCapability
    val background: BackgroundCapability   // WorkManager
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
    suspend fun cancel(id: String)
    fun fires(): Flow<AlarmFire>
    suspend fun cancelAll(): Int
}

interface ConnectivityCapability {
    val state: Flow<NetworkState>
    fun connect(url: String): Flow<SocketEvent>   // WebSocket/SSE，自带心跳与退避重连
    suspend fun longPoll(url: String, holdSeconds: Int): PollResult
}

interface BluetoothCapability {
    val adapterState: Flow<AdapterState>
    fun scan(filters: List<ScanFilterSpec>): Flow<ScanResult>
    suspend fun connect(deviceId: String): BleSession
    suspend fun write(session: BleSession, characteristic: CharId, bytes: ByteArray)
}

interface MediaCapability {
    suspend fun play(item: MediaItem)
    suspend fun pause(); suspend fun next(); suspend fun previous()
    val nowPlaying: Flow<NowPlaying?>            // 只来自媒体类通知
    fun requestAudioFocus(): AudioFocusHandle
}

interface SpeechCapability { suspend fun speak(text: String, utteranceId: String): Unit }

interface OverlayCapability {
    suspend fun showPet(png: ByteArray, sizeDp: Int, anchor: Anchor): PetHandle
    suspend fun updateBubble(handle: PetHandle, text: String?)
    suspend fun hide(handle: PetHandle)
}

interface FileCapability {
    suspend fun export(name: String, mime: String, bytes: ByteArray): Uri
    suspend fun importDocument(mime: String): DocumentResult?
    fun share(uri: Uri, mime: String): ShareTicket
}

interface InferenceCapability {
    suspend fun isModelReady(): Boolean
    suspend fun ensureModel(onProgress: (Float) -> Unit): Boolean
    suspend fun embed(text: String): FloatArray          // 384 维
}

interface PermissionCapability {
    fun status(p: AppPermission): Flow<PermissionStatus>
    suspend fun request(p: AppPermission): PermissionStatus
    suspend fun openSettings(p: AppPermission)
}
```

### 4.2 Fake 与模块落位

- 纯 JVM 单测：`FakeNotificationCapability`（记录 `post()` 调用序列）、`FakeAlarmCapability`（虚拟时钟，`advanceTo()` 触发 `fires()`）、`FakeConnectivityCapability`（脚本化断线/重连）。

```kotlin
class FakeAlarmCapability(private val clock: TestClock) : AlarmCapability {
    private val jobs = mutableMapOf<String, Instant>()
    private val flow = MutableSharedFlow<AlarmFire>(extraBufferCapacity = 64)
    override suspend fun schedule(id: String, at: Instant, exact: Boolean): ScheduleResult {
        jobs[id] = at; return ScheduleResult.Scheduled(exact)
    }
    fun advanceTo(t: Instant) = jobs.filterValues { it <= t }.keys.forEach {
        flow.tryEmit(AlarmFire(it)); jobs.remove(it)
    }
    override fun fires(): Flow<AlarmFire> = flow
    // cancel / cancelAll 略
}
```

- 模块边界：
  - `:platform-api`（纯 JVM 接口 + 数据类）
  - `:platform-android`（Android 实现，含 `:platform-fake` 仅用于测试源集）
  - `:core`（业务：收→决策→回，依赖 `:platform-api`）
  - `:app`（Compose UI，依赖 `:core` 的 AIDL 客户端与 Room）
- 规则：**业务代码只 import `:platform-api`**，任何 `android.*` 出现在 `:core` 的 `main` 源集即为构建失败（用 `check` 任务守住）。

---

## 5. 权限申请与降级矩阵

| 权限 | 用途 | 申请时机 | 被拒后的降级路径 | 可跳转设置页 |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` | 消息与前台服务通知 | 首次进入主界面（13+） | 无通知，仅应用内横幅 + 兜底闹钟 | `ACTION_APP_NOTIFICATION_SETTINGS` |
| `SCHEDULE_EXACT_ALARM` | 精确唤醒 | 用户在设置里打开"后台能力"时 | 降级 `setAndAllowWhileIdle`（±15min 误差） | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| `RECEIVE_BOOT_COMPLETED` | 开机重建 | 随包声明，无需运行时 | 仅"打开应用后"恢复调度 | 无（厂商自启开关另见下） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 免电池优化 | 引导步骤 2 | 前台服务 + 闹钟，到达率下降 | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| `FOREGROUND_SERVICE(+_SPECIAL_USE/_MEDIA_PLAYBACK/_DATA_SYNC)` | 常驻与播放 | 随包声明 | 无替代，必须声明 | — |
| 通知使用权 | 正在播放、媒体控制 | 用户点"连接正在播放"时 | 该卡片不显示，其余功能不受影响 | `ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| `BLUETOOTH_SCAN/CONNECT` | BLE/SPP | 首次打开蓝牙配对页 | 蓝牙功能整体置灰，提示原因 | `ACTION_BLUETOOTH_SETTINGS` |
| `CAMERA` | 拍照回信 | 用户点"拍照" | 仅相册（Photo Picker） | `ACTION_APPLICATION_DETAILS_SETTINGS` |
| `READ_MEDIA_IMAGES` | 选图 | **不申请**，改用 Photo Picker | — | — |
| `VIBRATE` | 提醒 | 随包声明 | 无振动 | — |
| `ACCESS_COARSE_LOCATION` | 天气 | 用户点"用当前位置" | 手输城市（默认路径） | App 详情页 |
| `SYSTEM_ALERT_WINDOW` | 桌宠 | 用户开启桌宠时 | 桌宠不可用，改为通知卡片 | `ACTION_MANAGE_OVERLAY_PERMISSION` |
| `MANAGE_EXTERNAL_STORAGE` | — | **不申请** | SAF 导入导出 | — |

**首次启动引导流程**（4 步，可跳过任意步，随时在设置里补）：

1. 欢迎页 → 「开始使用」（不请求任何权限）；
2. 通知权限（`POST_NOTIFICATIONS`）→ 拒绝则展示"你将收不到提醒，建议开启"，可继续；
3. 后台能力页：电池优化白名单 + 精确闹钟（若适用）+ 厂商自启指引（按 `Build.MANUFACTURER` 展示小米/华为/OPPO/vivo/魅族/三星/原生 各自路径文案）→ 每项独立开关；
4. 可选能力页：通知使用权（正在播放）、悬浮窗（桌宠）、蓝牙、定位（天气），逐项按需申请，**不在启动时批量弹窗**。
   > 旧版教训：旧版的厂商引导文案（小米/华为/OPPO/vivo/魅族/三星）是有效资产，可直接沿用；但旧版把保活手段默认全开，用户无从关闭。本版每项都必须有显式开关。

---

## 6. 安全基线（硬性，违反即门禁失败）

1. **签名与密钥管理**
   - 禁止在 `build.gradle.kts` 出现任何明文口令；`keystore.properties` 必须在 `.gitignore`，且仓库用 `git-secrets`/`gitleaks` CI 扫描。
   - CI 从 Secret 注入 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；本地开发用 debug 签名。
   > 旧版教训：`app/build.gradle.kts:21,23` 明文弱口令 + `app/storyphone.jks` 被版本控制跟踪。新仓库第一步就是 `.gitignore` + 撤销旧 jks（视为已泄露）。
2. **API Key 加密存储**：服务端下发的 token 用 `EncryptedSharedPreferences`（MasterKey 在 Android Keystore，`setUserAuthenticationRequired(false)`），禁止写 `BuildConfig` 明文、禁止写 Log。
3. **原生 HTTP 代理域名白名单**
   ```kotlin
   interface HttpProxy {
       suspend fun request(req: ProxiedRequest): ProxiedResponse
   }
   object AllowList {
       // 精确 host 匹配 + 强制 https + 禁跳转到白名单外
       val hosts = setOf("api.viewphone.app", "cdn.viewphone.app")
       val allowedHeaders = setOf("authorization", "content-type", "x-client-id")
   }
   ```
   实现要点：只允许 `https`；`host` 必须精确等于白名单项（禁止后缀匹配，避免 `evil-api.viewphone.app`）；禁止携带 `Cookie`/`Host`/`X-Forwarded-*`；`followRedirects=false`。
   > 旧版教训：`sendNativeHttpRequest` 是任意 URL、任意 header 的原生 HTTP 代理，等于把同源策略整个绕开（SSRF + 内网探测）。
4. **WebView 安全配置清单**（仅在小程序沙箱需要时启用，且只加载本地资产）
   - `settings.javaScriptEnabled = true` 仅对本地沙箱页；`allowFileAccess=false`、`allowContentAccess=false`、`allowFileAccessFromFileURLs=false`、`allowUniversalAccessFromFileURLs=false`；
   - `usesCleartextTraffic=false`（清单级），网络访问全部走 §6.3 代理；
   - `addJavascriptInterface` **仅暴露异步方法**，数量 ≤ 20，命名前缀 `vp.`；核心界面不使用 WebView。
   > 旧版教训：`usesCleartextTraffic=true` + 110 个同步桥 + `file:///android_asset/` 加载，三者叠加使沙箱形同虚设。
5. **FileProvider 白名单**：`file_paths.xml` 只允许 `<files-path name="exports" path="exports/"/>` 与 `<cache-path name="share" path="share/"/>`；分享前把文件复制到这两个目录。禁止 `<root-path>`/`<external-path>`。
6. **日志脱敏**：统一 `SafeLog`，正则脱敏手机号、邮箱、token、`Authorization` 头、消息正文（仅记长度与前 8 字符哈希）；release 构建关闭 `Log.v/d`。

---

## 7. 测试与验收

### 7.1 单元（JVM，每次提交）
- `:core` 决策状态机：用 `FakeAlarmCapability` + `FakeConnectivityCapability` 覆盖「消息到达→决策→入 outbox→发送成功/失败重试」全分支。
- outbox 幂等：同一 `clientMsgId` 重复入队只发一次。
- 一键全关：断言 `Fake` 上 `cancelAll()` 被调用 1 次、`reconnectEnabled=false`、`outbox` 中 `PENDING` 全部转 `CANCELLED`。

### 7.2 仪器（`connectedAndroidTest`，夜间）
- `RemoteInput` 回复：用 `NotificationManager` 发通知后直接调用 `ReplyReceiver`，断言消息落 Room 且状态 `SENT`（不依赖 UI 自动化）。
- 闹钟降级：`AlarmManager` 三级分支各跑一次，断言 `PendingIntent` 可被 `cancel`。
- SAF 导入导出：`ACTION_CREATE_DOCUMENT` 用 `Intents` 打桩，断言只写到白名单目录。
- 蓝牙：用 `BluetoothGatt` 回环（需真机或 Mock 蓝牙），断言断线自动重连 3 次退避。

### 7.3 真机手工清单（每次发版）
1. 冷启动 → 引导 4 步全部可跳过；
2. 设置 → 一键全关 → 执行残留检查（见下）；
3. 桌宠显示/气泡/关闭后 `dumpsys window` 无本应用 overlay；
4. 通知使用权开启后，播放音乐 → 「正在播放」显示；查看日志确认**非媒体通知被丢弃**。

**零残留检查脚本**：
```bash
adb shell dumpsys alarm | grep -i <pkg>            # 期望无输出
adb shell dumpsys power | grep -i -A2 <pkg>        # 期望无 PARTIAL_WAKE_LOCK
adb shell dumpsys notification --noredact | grep <pkg>   # 期望无活动通知
adb shell dumpsys window windows | grep <pkg>      # 期望无 overlay 窗口
adb shell ps -A | grep <pkg>                       # 期望无 :core 进程
```

### 7.4 端到端验收：「锁屏 30 分钟仍能收到并自动回复」
```text
前置：真机、已开通知权限/精确闹钟/免电池优化、已关闭一键全关
1. adb shell input keyevent KEYCODE_POWER           # 熄屏
2. 记录 t0；等待 30 分钟（期间不做任何交互，不插拔 USB 调试的额外触发）
3. 从服务端发送一条测试消息
4. 断言（30 分钟内，允许一次 ±90s 抖动）：
   a) 锁屏通知出现，且展开可见 RemoteInput 输入框
   b) Room outbox 中出现自动回复记录，状态 SENT，时间戳 - t0 ≤ 30min
   c) 服务端确认收到该回复，clientMsgId 唯一
5. 追加压力：adb shell am force-stop <pkg> 后再发一条
   → 期望：**收不到**（如实记录，不判失败），下次打开应用时补录队列重放成功
```
> 平台限制：第 5 步的失败是已知平台行为，验收基线必须写明"force-stop 后依赖补录"，否则测试会长期红着并被忽视。

---

## 8. 照搬 vs 重做清单

### 8.1 可升级沿用（旧文件 → 新落位）

| 旧文件/职责 | 处理 | 一句理由 |
|---|---|---|
| `AndroidMcp.kt` 的**业务语义**（约 110 个方法背后要表达的能力清单） | 沿用语义，重写实现 | 能力清单是本项目最有价值的资产，但实现必须改为 `PlatformCapabilities` 的 `suspend`/`Flow` |
| `McpForegroundService.kt` | 沿用结构，重写 | 前台服务 + `START_STICKY` 的思路成立，改为 `:core` 独立进程与诚实通知 |
| `BootReceiver.kt` | 基本沿用小改 | 开机重建调度的逻辑与平台无关，只需改 arm 的闹钟链 |
| 闹钟三级降级代码（`setAlarmClock`→`setExactAndAllowWhileIdle`→`setAndAllowWhileIdle`） | 直接沿用 | 这是踩过坑后得到的正确降级顺序 |
| 厂商自启引导文案（小米/华为/OPPO/vivo/魅族/三星） | 直接沿用 | 文案经过实测，改由 `Build.MANUFACTURER` 分发 |
| 通知构造（`IMPORTANCE_HIGH` + BigText + action） | 沿用并增强 | 基础正确，本版补 `RemoteInput` 与渠道拆分 |
| `MediaPlayer` + `MediaSession` + MediaStyle + `mediaControlCommand` | **重做**为 Media3 | 旧版未用 Media3/ExoPlayer，自研控制面维护成本高 |
| `WorkbenchFileSystem` 的公共目录读写 | **重做**为 SAF | 它以 `MANAGE_EXTERNAL_STORAGE` + 字符串前缀比对做防护，安全与合规双重不可接受 |
| `IlinkPoller`（长轮询、35s hold/60s 超时、每轮续锁） | 沿用参数，改落 `:core` | 超时与 hold 参数是实测值，可直接用；承载位置必须换 |
| `NativeReplyFallback`（心跳失效切换原生回信、限流/去重/补录/TTL） | 沿用策略，重做队列 | 策略正确，但补录队列 100 条上限会静默丢消息，改为 Room 持久队列 + 幂等键 |
| BLE 扫描/GATT/断线重连 + SPP 串口 | 基本沿用 | 蓝牙部分与 UI 架构解耦，只需接口化 |
| `onnxruntime-android` + 量化 `all-MiniLM-L6-v2` + 无模型降级 | 沿用 | 已验证可行，模型按需下载到 `filesDir/models/` 的模式保留 |

### 8.2 必须重写（旧版教训不可复制）

| 旧实现 | 重写理由 |
|---|---|
| 110 个**同步** `@JavascriptInterface` | 同步桥冻结 WebView 的 JS 线程（`AndroidMcp.kt:446-453` 已记录该坑）；新架构核心链路不经 WebView |
| `sendNativeHttpRequest` 任意 URL/header 代理 | 等于绕过同源策略（SSRF）；必须换成域名白名单的 `HttpProxy` |
| `usesCleartextTraffic=true` | 明文流量可被中间人读取；新项目清单级关闭 |
| 明文弱口令签名 + jks 入库（`app/build.gradle.kts:21,23`） | 签名密钥已泄露，必须换新密钥并走 CI Secret |
| `NotificationListenerService` 解析全部通知 | 隐私越界；新实现只保留媒体类通知 |
| `MANAGE_EXTERNAL_STORAGE` + 字符串前缀越界防护 | 过度授权且防护不成立；改用 SAF |
| 1×1 透明悬浮窗 + 静音 `AudioTrack` 保活 | 对抗系统调度、用户无感知；核心链路原生化后不再需要 |
| 桌宠 `showDesktopPet(b64, sizeDp)` 单次过桥 | PNG base64 无尺寸校验，大图会打爆 Binder；改为 `ByteArray` + 尺寸上限 + 采样解码 |
| 零测试（CI 只跑 `assembleDebug`） | 保活与回信链路的失败模式全部在"锁屏很久之后"，没有自动化就是不可回归；新项目按 §7 建 CI 门禁 |
| 相机只靠 `<input type=file>` | 无原生相机控制、无 EXIF、无前台服务配合；改用 CameraX + Photo Picker |

---

## 9. 待确认项（需产品决策，不阻塞 0.1 开发）

1. 是否提供 `:remote` 推理进程：取决于低端机上 ONNX 内存峰值是否会拖垮 `:core`（建议先测量再定）。
2. 系统闹钟写入的触发口径：是"用户显式说'定闹钟'"还是"任何时间表达都写系统闹钟"（建议前者，避免污染用户系统闹钟）。
3. `specialUse` 还是 `dataSync` 作为前台服务类型：`dataSync` 有每日时长上限，若长连接需全天候，则选 `specialUse` 并准备上架说明材料。
