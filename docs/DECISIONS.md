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
