# 实施决策记录（ADR）

> 规则：**只记录已经拍板、并且有证据支撑的决定。** 未验证的猜测一律不写进这里（写在 `docs/P0-TOOLCHAIN-BASELINE.md` 的「未验证」表里）。
> 每条决策必须写明：背景 / 证据 / 结论 / 复查点。没有复查点的决策视为不完整。

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

## DEC-008｜仓库解析模式改为 `PREFER_PROJECT`（**护栏降级，必须知悉**）

- **日期**：P0 第 1 天（step 2）
- **状态**：已生效，**代价已认领**
- **背景**：加入 `js` target 后，`:kotlinNodeJsSetup` 需要解析 `org.nodejs:node:24.16.0`。
  Kotlin/JS 插件为此会往**项目级** `repositories` 注入 `https://nodejs.org/dist`。
- **三种配置的实测结果**（不是推测）：

  | # | `repositoriesMode` | 结果 |
  | :-- | :--- | :--- |
  | ① | `FAIL_ON_PROJECT_REPOS` | 构建失败：`repository 'Distributions at https://nodejs.org/dist' was added by unknown code`。把这个仓库声明进 settings **无效**，冲突点是"项目级仓库是否存在" |
  | ② | `PREFER_SETTINGS` | **更糟**：注入存在时 settings 仓库被整个忽略，解析器链只剩 `[maven, maven2, Google, MavenRepo]`，Node 分发无处可寻 → `Could not find org.nodejs:node:24.16.0`（证据 `.buildlogs/node-debug.log`），**镜像同时失效** |
  | ③ | `PREFER_PROJECT` | 通过：settings 仓库对 Maven 依赖仍生效（`build2.log` 起 Aliyun 一直在用），Node 分发由 KGP 注入的仓库解析 |

  另查证：KGP 2.4.20 **没有**"关闭 Node 下载"的 Gradle 属性/环境变量
  （jar 内可搜到的开关只有 `kotlin.js.yarn` 等，见 `.buildlogs` 的检索结果），
  所以"复用宿主 Node"这条路也无法保住①。
- **结论**：采用 ③。**"所有仓库集中在 settings、模块内不得自建仓库"这条约束，
  由编译期强制降级为「约定 + review」** —— 该降级已在此明确记录，不隐瞒。
  Maven 依赖仍然集中在 `settings.gradle.kts`，实际行为未变。
- **复查点**：
  1. 若将来 KGP 提供"关闭 Node 下载"的官方开关，或提供把 Node 分发加入 settings 的机制，
     **立即恢复 `FAIL_ON_PROJECT_REPOS`**，并把本条标记为已偿还。
  2. review 时必须人工确认没有模块自建 `repositories {}`（现在没有构建期兜底了）。
  3. 升级 Kotlin 版本时复查本条的三种配置是否仍成立。

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
