# 08 · Web 端与 iOS 覆盖线（ViewPhone / 微光机）

> 定位：Web 端的使命是**覆盖 iOS 用户**（本项目不做原生 iOS）。本文是实现规范，不是概述。
> 诚实前提：iOS Web 上「手动触发的一切」可以接近完整，「无人值守的一切」（后台定时发消息、后台自动回复、锁屏投递）是 **WebKit 架构边界而非投入问题**，必须用云端定时 + Web Push（需安装到主屏幕）把「设备主动」改写成「服务端主动」。
> 依据：`00-CONSTITUTION.md` §三、§五；`02-ARCHITECTURE.md` §六（共享内核如何被 Web 消费）。

主端是 **Android 原生 App（Kotlin + Compose + Room）**，**不做原生 iOS App**。Web 端的唯一战略目的是**兼容 iOS**——让 iOS 用户通过浏览器 / PWA 拿到尽可能完整的体验。

第一原则是**诚实**：说清 Web 能做到什么、做不到什么，以及 iOS（Safari / WebKit）上差在哪、怎么缓解。约束：**不迁移旧版数据，全新开始**；共享内核走 KMP（纯 Kotlin 的 `core:*` 可编译到 JS）。

**结论**：iOS 上"阅读 / 编辑 / 管理 / 云同步 + 手动触发的一切"可接近完整；**"无人值守的一切"（后台定时发消息、后台自动回复、锁屏投递）在当前 WebKit 上无法可靠实现**，必须用**云端定时 + Web Push（需安装到主屏幕）**把"设备主动"改写为"服务端主动"。

> 平台限制：iOS 上"App 在后台仍能自己动"不是工程投入能弥补的，是 WebKit 的架构边界。

---

## 1. 产品定位与能力边界表

**A = Android 独有｜B = 双端都有｜C = Web 受限（可降级）｜D = Web 不可用（须替代）**。

| 能力 | 类 | Web / iOS PWA 实况 | 缓解方案 |
|---|---|---|---|
| 悬浮窗桌宠 | A | 无系统级悬浮窗，只能页内 DOM | 降级为页内桌宠 + 拖拽 + 双击；跨 App 陪伴只承诺 Android |
| 定时主动发消息 | D | 页签与后台被冻结，**无可靠周期后台任务** | **云端 cron/队列代替本地定时**，到点服务端写会话，下次打开即见 |
| 后台自动回复 | D | 后台冻结 → 无法自动回复 | 服务端代答；端侧私密逻辑改为"**下次打开时补发**" |
| 锁屏通知投递 | C | iOS 16.4+ Web Push **仅在"已添加到主屏幕"**且已授权时可用，无静默投递保证 | ① 引导安装 PWA；② **邮件/短信兜底**；③ 打开时"未读汇总"补齐 |
| 通知快捷回复 | A | 无 RemoteInput，只能点开 | 通知文案一句话可读完，点击直达输入框并预聚焦 |
| 本地持久化 | C | IDB 有配额；**Safari ITP 对脚本可写存储可能 7 天未使用即清理**；`persist()` 支持有限 | ① 申请 `persist()`（拿不到不阻塞）；② **E2E 加密云同步作权威副本** |
| 大文件 / 媒体 | C | 无任意文件系统访问；**iOS 不支持 File System Access API** | **OPFS + IDB Blob**，媒体外置，用完即 `revokeObjectURL` |
| 蓝牙 BLE | D | **Web Bluetooth 在 iOS Safari 不支持** | 隐藏 BLE 入口，提示"请在 Android 端配对" |
| 系统闹钟直写 | D | Web 无系统闹钟 API | 改用**日历 `.ics` 导入**或通知提醒，文案明说差异 |
| 本地 ONNX 推理 | C | 只能 **WASM 降级**，内存/速度受限，iOS 内存压力下易被回收 | 小模型 WASM 并预热；**重模型走服务端**；长任务拆片可中断 |
| 振动反馈 | C | **iOS 不支持 Vibration API** | **视觉 + 短音效**替代；调用前特性检测，不当主反馈 |
| 后台音频 / 播报 | C | 后台播放受限，切后台/锁屏可能被暂停 | 改为"打开时播放"；长音频服务端生成、点击播放 |
| 相机 / 图片上传 | B | `getUserMedia` / `<input capture>` 可用 | 上传前**客户端压缩 + 分片**，避免撑爆配额 |
| 深色模式 / 安全区 / 字号 | B | `prefers-color-scheme`、`env(safe-area-inset-*)` | 必须处理刘海安全区与动态字号 |
| 云备份与恢复 | B | 同一份备份格式 | 由 `core:backup` 单一来源生成 |
| 账号 / 多设备挤占 | B | 同一后端 | 见第 5 节 |

> 旧版教训：旧版 PWA 下把桌宠做成"网页 DOM + 拖拽 + 双击"却仍宣称与真机一致——**边界必须写进产品文档，而不是宣传语。**

---

## 2. Web 端架构

### 2.1 技术栈（明确选型）

**构建** Vite + TypeScript（ESM），禁止无构建、无模块系统的裸脚本；**UI** React 18 + TS，React Router（history + SW 回退）；**状态** Zustand（UI 态）+ TanStack Query（服务端/同步态），禁止业务状态散落成全局可变单例；**样式** CSS Modules + CSS 变量设计令牌，禁止以行内 `style=` 为主；**存储** IndexedDB（`idb`）+ OPFS；**PWA** `vite-plugin-pwa`（Workbox 生成清单）。

> 旧版教训：84 个 JS / 105,984 行、18 个 CSS / 8,354 行、`index.html` 7,013 行含 **2,128 处行内 `style=`**、**399 处 `window.X =`**、**127 个 localStorage 键 / 558 个调用点**——全是"没有模块系统"的直接产物，这就是本节的立项理由。

### 2.2 目录结构

`web/src/` 下：`app/`（入口、路由、Provider、错误边界）、`features/`（chat / pet / backup / settings）、`core-bridge/`（KMP 产物桥接，唯一允许 import kotlin 输出的地方）、`storage/`（idb schema、OPFS、迁移）、`sync/`（变更日志、冲突、重试）、`pwa/`（manifest、SW 注册、更新提示、安装引导）、`ui/`（设计令牌、通用组件）；`web/public/` 放 `icons/`（含 apple-touch-icon）与 `splash/`。

### 2.3 与 KMP 内核集成（成本对比与推荐）

**方案 A：KMP → Kotlin/JS，TS 消费 `core:*` 产物。** `core:model/backup/prompt` 用 `js(IR)` 产出 JS，配 `kotlinx.serialization` 的 TS 声明或薄 `.d.ts`。成本：必须自建桥接层（产物带自身 runtime、类型不会自动变成好用的 TS 类型、`Long`/`Duration`/错误模型需显式转换），**包体积显著增加**。收益：schema、备份格式、提示词产物天然一致。

**方案 B：只共享"契约 + golden 测试"，两端各自实现。** 把 schema、备份格式、提示词模板定义为语言中立规范（JSON Schema / 模板文本 / 样例数据），Kotlin 与 TS 各写实现，跑同一组 golden。成本：逻辑写两遍，但都是母语惯用写法，调试与体积可控。

**推荐：混合分级。** schema / 备份格式 / 提示词模板 → **B**（纯数据，跨语言成本极低）；加解密、压缩、分片等**必须逐字节一致**的算法 → **A**；UI 邻近状态与网络编排 → 各自实现。

> 结论：**不要为"共享"把整个内核编译到 JS**；默认 B，仅对逐字节必须一致的模块启用 A，并禁止 KMP 产物无声膨胀首屏体积。

---

## 3. 存储与离线

### 3.1 IndexedDB schema

| 仓库 | 主键 | 索引 | 说明 |
|---|---|---|---|
| `messages` | `id` | `by_conversation_createdAt`、`by_status` | 消息正文，**不含二进制** |
| `conversations` | `id` | `by_updatedAt` | 会话元数据 |
| `entities` | `id` | `by_type_updatedAt`、`by_dirty` | 统一实体表，`dirty` 标记待同步 |
| `changeLog` | 自增 `seq` | `by_entity`、`by_pushed` | 离线变更日志，同步的真相来源 |
| `mediaMeta` | `id` | `by_message` | 媒体元信息（大小、mime、opfs 路径） |
| `kv` | `key` | — | 只存**小**配置，**不是第二个 localStorage** |

媒体二进制一律不进 IDB 行内，只存 OPFS 路径或 Blob 引用。

> 旧版教训：127 个 key / 558 个 localStorage 调用点把 localStorage 当主库；再叠加"整库 `JSON.stringify` → base64 → DEFLATE"（实测导出文本膨胀到 **40MB**）与 base64 图片入库，存储层直接失控。**Web 上 base64 是体积与内存的双重灾难。**

### 3.2 OPFS 与媒体外置

二进制写 **OPFS**（`navigator.storage.getDirectory()`），不支持时回退 IDB Blob。渲染时 `URL.createObjectURL`，**组件卸载必须 `revokeObjectURL`**。统一 `mediaRef` 抽象（`opfs://` / `idb://` / 远端 URL）对上层透明。

> 平台限制：**OPFS 在 Safari 的可用性与配额行为必须在真机验证**；代码走"能力检测 + 回退"，不得假定可用。

### 3.3 配额管理

启动与批量导入前 `navigator.storage.estimate()` 展示 `usage/quota`；主动申请 `navigator.storage.persist()`（拿不到不阻塞）；软阈值（用量 80%）触发清理提示；**云上 E2E 密文副本是权威数据**。

> 平台限制：ITP"7 天未使用可能清理"与 `persist()` 效果均版本相关（**需真机验证**），**不能把"数据只在这台 iPhone 上"当产品承诺**。

### 3.4 同步与冲突解决

离线写全部进 `changeLog`，联网后按序推送（FIFO + 幂等键）。**消息类（只追加）用 LWW + 服务端时间戳即可；会话/实体元数据用 LWW + 变更日志并保留被覆盖版本；当前不引入 CRDT**，但把 `changeLog` 设计成可升级为 CRDT op 的形状，为将来多端协同编辑预留。

---

## 4. PWA 规范

**Manifest**：`name` / `short_name` / `start_url` / `scope` / `display: standalone` / `theme_color` / `background_color` / `icons`（192、512、512-maskable）/ `orientation` / `lang: zh-Hans` / `shortcuts`。

**iOS 特供**（iOS 不读 Manifest 的图标与启动图，必须双份维护）：`apple-touch-icon`（180×180）、`apple-mobile-web-app-capable`、`apple-mobile-web-app-status-bar-style: black-translucent`、`apple-mobile-web-app-title`、`apple-touch-startup-image`（配 `media` 多尺寸，缺失会启动白屏）、`viewport-fit=cover` + `env(safe-area-inset-*)`。

**Service Worker**：**资源清单必须由构建产物生成**（`vite-plugin-pwa` / Workbox `injectManifest`），禁止手工维护。分层：App Shell 预缓存（version 化）→ 静态资源 stale-while-revalidate → API network-first（只缓存只读 GET）→ 媒体 cache-first + LRU 上限；导航统一 `navigateFallback` 保证离线可打开。

> 旧版教训：旧版 SW 清单**手工维护并漂移，漏掉 `index.html` 引用的 29 个脚本**，跨版本出现"旧 SW 当家把新引用打成 404"；同时 `fetch` 守卫让 http(s) 下所有本地资源**跳过 SW**，而 APK 走 `file:///android_asset/`、file:// 下 SW 根本不可注册——**等于装了个几乎不工作的 SW，还背上 404 风险**。新版必须产物生成清单 + CI 校验（清单 vs 构建产物全集，差集非空即失败）。

**更新策略**：新 SW 就绪后**不静默 `skipWaiting`**，提示"发现新版本，点击刷新"；确认后 `postMessage(SKIP_WAITING)` → `controllerchange` → 刷新。

**离线可用范围**：可打开 App Shell、浏览已缓存会话、撰写并排队发送（联网补发）、查看已缓存媒体、改本地设置。不可用：首次登录/注册、云端推理、云备份上传下载、未缓存媒体、Web Push 保证投递。

**iOS 安装引导**（iOS **无 `beforeinstallprompt`**，必须手动引导）：在用户完成一次有意义动作后触发，而非首屏。①「把微光机装到桌面，才能收到通知」；②「点底部**分享** → 下滑选**添加到主屏幕** → 点**添加**」；③ 小字「未安装时 iOS 无法向网页推送通知」。必须可"不再提示"。

> 平台限制：iOS Web Push 的前提是"已添加到主屏幕"，**安装引导不是增长手段，而是功能开关**。

---

## 5. 安全与鉴权

**账号体系**：旧版为 Supabase Auth（邮箱账密）+ `user_devices` 表做"2 台设备 FIFO 挤占" + Realtime 强踢，`app_backups` 建表 SQL 对 anon 开放 `FOR ALL USING(true)`。建议：① 自建后端 + 标准 OIDC/OAuth2，或继续用 Supabase 但 **RLS 一律按 `auth.uid()` 收敛**（`USING (user_id = auth.uid())` + `WITH CHECK`），**禁止任何 `USING(true)` 的 anon 策略**；② 设备挤占由服务端登录时用**事务**执行 FIFO 驱逐 + 令牌吊销，前端不参与决策；③ 激活码核销必须在服务端事务内完成。

> 旧版教训：`FOR ALL USING(true)` 是一条能把产品信任度清零的 SQL；FIFO 挤占暴露在前端同样可绕。**授权判断永远在服务端。**

**API Key 绝不进前端**：密钥只存服务端，Web 只调自家网关（鉴权、配额、限流、审计、错误码翻译）；CI 扫描产物与源码的密钥模式（`sk-`、`AIza`、长 base64），命中即失败。

**响应头**：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src 'self' blob:; connect-src 'self' https://api.<domain>; object-src 'none'; base-uri 'none'; frame-ancestors 'none'`，外加 `X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`、`Permissions-Policy` 白名单、HSTS。仅在 WASM/线程需要 `SharedArrayBuffer` 时才考虑 COEP（**会影响媒体与外链，需评估**）。供应链：锁文件入库、`npm ci`、Dependabot/Renovate、SBOM、关键依赖 pin。

**Web 端绝不能做的加密把戏**：**禁止硬编码口令**（旧版把流密钥口令写在 `index.html` 的 `atob('XVZTSFNISQ==')`——这不是加密，是把密钥和密文一起发给攻击者）；**禁止 fail-open**（旧版完整性校验只保护 1 个文件且失败只"叠 UI"、脚本照常运行，**校验失败必须阻断执行**）；**禁止自写流密码**，用 WebCrypto（AES-GCM 等），密钥由口令经 PBKDF2/Argon2 派生，**E2E 密钥永不离开客户端**。

---

## 6. 云同步与后端

**结论：需要后端，且是最小后端。** 定时任务、Web Push、多端挤占、E2E 密文备份都需要一个在线的可信调度者；纯前端在 iOS 上无法满足第 1 节的 D 类能力。

**最小能力清单**：鉴权与会话（必需）、云备份存取（必需，**服务端只见密文分片、不持密钥**）、变更同步接口（必需，服务端做 LWW 归并）、定时任务调度（可选但强烈建议）、Web Push 发送（可选，仅已安装 PWA 有效）、模型网关（可选，Web 上跑重模型的唯一出路）、邮件/短信兜底（可选）。

**部署与成本量级**：无状态容器化 API + 托管 Postgres + 对象存储 + 一个调度器；数千 MAU、备份总量 GB 级大致落在**每月几十美元**档，主要变量是备份存储与出网流量。护栏：备份压缩后分片入库、冷数据转低频层、每用户设备配额。

> 旧版教训：GitHub 备份 750,000 字节分片 + Supabase 4MB 分片两套并行，导出文本膨胀到 40MB。新版**一套分片策略、一个权威存储**。

**数据归属与隐私**：数据归用户所有，提供一键明文导出与一键彻底删除（含分片与对象存储）；E2E 下服务端**无法**读取内容（写进隐私说明，并提醒"**丢失口令等于丢失数据**"）；明确列出哪些数据在端、哪些在云、哪些发给第三方。

---

## 7. iOS 验收清单（真机逐条可测）

**M = 必须通过｜B = 尽力而为｜N = 已知不支持**；**[验证]** = 需真机确认。

| # | 验收项 | 等级 | 判定方法 |
|---|---|---|---|
| A1 | Safari 打开站点可登录并走通主流程 | M | 真机全流程 |
| A2 | 添加到主屏后 standalone 启动，图标与启动图正确、无地址栏 | M | 主屏图标 + 冷启动观察 |
| A3 | 断网后仍能打开 App Shell 并浏览已缓存会话 | M | 飞行模式冷启动 |
| A4 | 发送消息（在线即时 / 离线排队后补发） | M | 断网发送 → 联网后服务端收到 |
| A5 | 图片上传（拍照 + 相册）并可回显 | M | `<input capture>` 与相册两条路径 |
| A6 | 配额、`persist()` 返回值、**7 天未使用后数据是否被清**、OPFS 可用性 | M **[验证]** | 记录 `estimate()`；放置 8 天复测 |
| A7 | Web Push 可订阅、可收到、点击直达会话 | B **[验证]** | iOS 16.4+；**未安装主屏时必须收不到**（负向断言） |
| A8 | 切后台 5 分钟回来状态未丢、连接自动恢复 | M | 手动计时 |
| A9 | 后台/锁屏期间的"定时主动发消息" | N | 记为不支持，改由云端定时验证 |
| A10 | 安全区与动态字号：刘海、Home 条、放大字号下无遮挡 | M | 调大字号后逐屏检查 |
| A11 | 键盘弹出时输入框不被遮挡、页面可滚动 | M | 真机软键盘实测 |
| A12 | 无振动反馈、已走视觉/音效降级且无报错 | N | 控制台无 `navigator.vibrate` 异常 |
| A13 | BLE 入口在 iOS 上隐藏或提示不支持 | N | 检查 UI 分支 |
| A14 | 媒体路径回退到 OPFS/IDB（FSA 缺失分支被走到） | M | iOS 上验证回退 |
| A15 | 锁屏后后台音频 / 语音播报是否继续 | B **[验证]** | 锁屏 2 分钟观察 |
| A16 | WASM 推理峰值内存下不被 Safari 回收 | B **[验证]** | 真机长时压测 |
| A17 | 中文输入法候选与 emoji 输入正常 | M | 真机输入法 |
| A18 | iPad 分屏 / 横竖屏旋转不破 | B | iPad 真机旋转 |

### 7.1 必须在真机验证的不确定项（独立清单）

以下在本文中**一律不作支持性断言**，须实测后回填：

1. **存储配额与 ITP 清理**（A6）：7 天未使用清理是否命中本应用；`persist()` 的实际返回值；`estimate()` 的配额量级。
2. **OPFS 可用性**（A6）：iOS Safari 是否支持 `getDirectory()`、配额与清理范围、大文件写入是否抛异常——**决定媒体层是否必须走 IDB Blob 主路径**。
3. **Web Push 全链路**（A7）：已安装主屏时的订阅成功率、投递时延、是否存在静默投递、**"未安装主屏是否确实无法接收"**。
4. **后台音频 / 播报**（A15）：锁屏与切后台后的持续能力与中断点。
5. **WASM（ONNX）资源上限**（A16）：峰值内存、是否被 Safari 回收、可跑的模型规模上限。
6. **后台冻结时间阈值**（A8/A9）：页签被冻结的具体时长（随 iOS 版本与低电量模式变化）——决定"补发"窗口与心跳设计。
7. **其他 WebKit 细节**：`SharedArrayBuffer` 与 COEP 的连带影响；`apple-touch-startup-image` 的多尺寸适配；无 `beforeinstallprompt` 下引导链路的实际转化。

---

## 8. 与 Android 端的一致性策略

**三个"同一份"（硬要求）**：① **同一套实体 schema**——由 `core:model` 规范文件生成 Kotlin 与 TS 两侧类型，Web 端类型**生成而非手写**；② **同一份备份格式**——`core:backup` 定义容器格式（版本号、分片大小、压缩算法、字段顺序、时间戳精度），两端互为可导入导出；③ **同一套提示词编译产物**——由 `core:prompt` 单一来源产出。

**golden 测试**：仓库维护 `golden/`（schema 样例、备份样例含空库/超大媒体/超长文本/时区/emoji 边界、提示词编译输入输出对）；Kotlin 侧 JVM 测试与 Web 侧 Vitest **读同一目录同一批文件**；任一端解析失败或输出不等即构建失败。备份格式的**逐字节一致**由 2.3 方案 A 保证，其余由方案 B 的契约测试保证。

**允许且必须写进产品的不一致**：桌宠（系统悬浮窗 vs 页内 DOM）、定时/自动回复（端上 vs 云端）、通知快捷回复（支持 vs 无）、BLE/闹钟/振动（支持 vs 无）。差异要显式、可测试、写进文案。

> 旧版教训：旧版让同一套前端资产同时服务 WebView 与 PWA，又让 PWA 假装拥有原生能力——结果两端都不可信。

---

## 9. 排期与门禁

1. 搭 `web/`（Vite + React + TS）骨架与设计令牌；CI 接入密钥扫描与 `npm ci`。
2. 接入 `vite-plugin-pwa`，产物生成 precache 清单 + "清单 vs 产物"差集校验。
3. 实现 IDB schema 与 OPFS 媒体层（含 `revokeObjectURL` 生命周期检查）。
4. 落地 `core:model` / `core:backup` / `core:prompt` 规范文件与 `golden/`，两端测试接入。
5. 最小后端：鉴权 + E2E 密文备份 + 同步接口；RLS 全部按 `auth.uid()` 收敛。
6. iOS 安装引导 + Web Push 订阅链路；准备邮件兜底通道。
7. 走完第 7 节清单，**A6 / A7 / A15 / A16 真机结果回填本文档**。

> 诚实度声明：凡未真机验证的浏览器行为一律标注 **[验证]** 并集中于 7.1，**不作支持性断言**；凡"Web 不可用"的能力均给出替代方案与用户可见文案要求，**不允许静默失败**；本文不迁移旧版任何数据，旧版实测数字仅作反面教材引用。
