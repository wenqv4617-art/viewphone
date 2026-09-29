# P0 · 工具链基线（实测记录）

> 本文件只记录**真实跑过并拿到输出**的事实。未验证的一律标「未验证」，不写推测。
> 采集时间：P0 第 1 天　｜　采集机器：本机 Windows 11 + Android Studio（见下）
> 关联：`CRITICAL.md §9.2 P0`、`docs/04-ROADMAP.md §二 P0`

---

## 一、结论：本机首次构建已成功

| 项 | 值 |
| :--- | :--- |
| 验收命令 | `./gradlew build` |
| 结果 | **BUILD SUCCESSFUL in 19s**（69 tasks: 33 executed, 9 from cache, 27 up-to-date） |
| 退出码 | `0` |
| 首次可编译命令 | `./gradlew :probe-jvm:test :probe-android:assembleDebug` → **BUILD SUCCESSFUL in 1m 29s**（27 tasks executed） |

**这条基线就是 B3 决策要的「以本机首次构建成功为准」。**

---

## 二、已实测通过的版本组合

| 组件 | 版本 | 证据 |
| :--- | :--- | :--- |
| Gradle | **9.3.0** | `gradle --version` 输出 + `gradle-wrapper.properties` 的 `distributionUrl` |
| Android Gradle Plugin | **9.0.1** | 构建日志 `:toolchain-probe:android-lib:assembleDebug` 成功 |
| Kotlin（JVM 插件 `org.jetbrains.kotlin.jvm`） | **2.4.20** | `:toolchain-probe:jvm-test:test` 成功 |
| Kotlin（KMP 插件 `org.jetbrains.kotlin.multiplatform`） | **2.4.20** | `:shared:jvmTest` **BUILD SUCCESSFUL**（P0 step 1 门槛） |
| JDK | **OpenJDK 21.0.10**（JetBrains JBR，`D:\AndroidStdio\jbr`） | `java -version` / `javac 21.0.10` |
| JUnit | **Jupiter 6.1.3** | `:toolchain-probe:jvm-test:test` XML：`tests=1 failures=0 errors=0` |
| kotlin-test（KMP 默认） | 随 Kotlin 2.4.20 | `:shared:jvmTest` XML：`tests=1 failures=0 errors=0` |
| Kotlin JS（`js { nodejs() }`） | **2.4.20 已通过** | `:shared:jsTest` → `jsNodeTest` **BUILD SUCCESSFUL in 3m 46s** |
| Node 运行环境 | 由 KGP 管理并**锁定 `24.16.0`** | 在 `shared/build.gradle.kts` 写死；见 §2.6 |
| Kotlin JVM toolchain | **21** | `jvmToolchain(21)` 生效，无需额外下载 JDK |
| Gradle 配置缓存 | **开启且已验证可复用** | `Reusing configuration cache.`（见 `docs/DECISIONS.md` DEC-001） |
| R1：KMP→JS 被 TypeScript 真实消费 | **可行**（10 项断言 + 类型检查通过） | 见 `docs/DECISIONS.md` **DEC-011** 与 §2.7 |

### 2.1 `:shared:jvmTest` 实测输出（P0 step 1 门槛）

```
> Task :shared:compileKotlinJvm
> Task :shared:jvmJar
> Task :shared:compileTestKotlinJvm
> Task :shared:jvmTest

BUILD SUCCESSFUL in 11s
5 actionable tasks: 3 executed, 2 up-to-date
Configuration cache entry stored.
```

真实测试报告（`shared/build/test-results/jvmTest/TEST-com.viewphone.shared.SharedKernelTest.xml`）：

```
测试类: SharedKernelTest[jvm]
tests=1 failures=0 errors=0 skipped=0 time=0.027s
  - 内核版本可被 JVM 单测断言()[jvm]  0.016s
```

> **实测教训（必须记住）**：KMP **不会**自动注入 `kotlin-test`。只写 `jvm()` 时
> `src/jvmTest` 目录会被识别（任务名就是 `:shared:jvmTest`），但 `kotlin.test.*` 解析不到
> （`Unresolved reference 'test'`）。必须在 `sourceSets { jvmTest.dependencies { implementation(kotlin("test")) } }`
> 里显式声明。首次失败日志见 `.buildlogs/shared-jvmtest.log`。

### 2.2 模块边界门禁自证（探针模块不可被生产模块依赖）

```
# 故意让 :shared 依赖 :toolchain-probe:jvm-test
FAILURE: Build failed with an exception.
* What went wrong:
A problem occurred configuring project ':shared'.
> 模块边界违规：生产模块 :shared 依赖了非生产模块 :toolchain-probe:jvm-test。
  该模块仅用于验证工具链，core:testing 落地后会被并入。请改用 core:testing 等正式模块。
BUILD FAILED in 3s
=== exit code: 1 ===
```
撤销违规后 `:shared:jvmTest` 立即恢复 `BUILD SUCCESSFUL`。
> 效力边界见 `docs/DECISIONS.md` **DEC-009**：这条门禁与 `CRITICAL §3.3` 的 5 条 Detekt 规则是两件事，
> Detekt 规则**尚未实现**（P0 第 3 步）。

### 2.3 step 2：`js` target 加入后的实测（P0 step 2 门槛）

命令：`./gradlew :shared:jvmTest :shared:jsTest`
```
> Task :shared:compileKotlinJs
> Task :shared:compileTestKotlinJs
> Task :shared:compileTestDevelopmentExecutableKotlinJs
> Task :shared:jsTestTestDevelopmentExecutableCompileSync
> Task :kotlinNodeJsSetup
> Task :kotlinYarnSetup
> Task :kotlinNpmInstall
> Task :shared:jsNodeTest
> Task :shared:jsTest

BUILD SUCCESSFUL in 3m 46s
=== exit code: 0 ===
```
> 注意：`jsTest` 是聚合任务，**真实结果 XML 在 `jsNodeTest` 目录下**
> （`:shared:jsTest` 的 `test-results/jsTest` 目录是空的，别找错地方）。

测试报告的真实路径与内容：

| 源集 | 报告文件 | tests | failures | errors |
| :--- | :--- | :--- | :--- | :--- |
| jvm | `shared/build/test-results/jvmTest/TEST-com.viewphone.shared.SharedKernelTest.xml`（类 `SharedKernelTest[jvm]`） | 1 | 0 | 0 |
| js | `shared/build/test-results/jsNodeTest/TEST-jsNodeTest.com.viewphone.shared.SharedKernelJsSmokeTest.xml`（类 `jsNodeTest.com.viewphone.shared.SharedKernelJsSmokeTest`） | 1 | 0 | 0 |

用例名：`内核版本可被 JVM 单测断言()[jvm]`、`同一份 commonMain 代码在 JS 上可断言[js, node]`。

**JS 产物确实落盘**（以下为实测路径与大小，Windows）：

```
shared/build/compileSync/js/test/testDevelopmentExecutable/kotlin/viewphone-shared.js        1,493 B   ← 内核 JS 产物
shared/build/compileSync/js/test/testDevelopmentExecutable/kotlin/viewphone-shared-test.js   3,425 B   ← 测试 JS 产物
shared/build/compileSync/js/test/testDevelopmentExecutable/kotlin/kotlin-stdlib.js         620,362 B
shared/build/compileSync/js/test/testDevelopmentExecutable/kotlin/kotlin-test.js            18,486 B
shared/build/tmp/jsPublicPackageJson/package.json
shared/build/tmp/jsTestPublicPackageJson/package.json
```
`shared/build/compileSync/`、`shared/build/klib/`、`shared/build/kotlin/` 均为 JS target 生成。

**配置缓存三态（加 js target 后的复查，履行 DEC-001 复查点）**：

| 状态 | 命令 | 结果 |
| :--- | :--- | :--- |
| 写入 | 删 `.gradle/configuration-cache` 后 `:shared:jvmTest :shared:jsTest --no-build-cache` | `BUILD SUCCESSFUL` + `Configuration cache entry stored.` |
| 复用 | 再次执行 | **`Reusing configuration cache.`** + `Configuration cache entry reused.` |

> JS 相关的两条坑（`js(IR)` 已废弃；仓库模式与 `FAIL_ON_PROJECT_REPOS` 冲突）见
> `docs/DECISIONS.md` DEC-008，以及 `shared/build.gradle.kts` 内的注释。

### 2.4 实测教训：KMP 的 test 源集必须显式声明 `kotlin-test`

见 `docs/DECISIONS.md` **DEC-007**（可复用条目，后续每个模块都照此办理）。
一句话：`src/jvmTest` 目录会被识别，但 `kotlin.test.*` 不会自动可用 —— 不声明必报
`Unresolved reference 'test'`。加 `js` 后同理，故 `jsTest.dependencies` 也必须写。

### 2.6 Node 版本锁定（口径：不能今天 24.16、明天 CI 拿到别的版本）

KGP 自带的默认 Node 版本**随插件版本变化**，因此已在构建脚本里写死：

```kotlin
// shared/build.gradle.kts
extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>("kotlinNodeJsSpec") {
    version = "24.16.0"
}
```

- 扩展名 `kotlinNodeJsSpec` 取自 Gradle 报错中列出的
  "Currently registered extension names"（实测，非猜测）。
- **不要**用 `NodeJsRootExtension.nodeVersion` —— 它已被 `@Deprecated`。
- 变更此值是**三处联动**：`shared/build.gradle.kts` → 本文件 → `ci.yml`（尚未落地）。
- 为什么必须锁：`:shared:jsTest` 与 `:shared:compileProductionLibraryKotlinJs` 都依赖
  `:kotlinNodeJsSetup`；不锁就等于"CI 与本机跑的不是同一个运行时"。

### 2.7 R1 结论：KMP→JS **可行**（详见 `docs/DECISIONS.md` DEC-011）

验收命令与真实结果（三条都 exit 0）：

```bash
node web/probe-web/scripts/build-kernel.mjs      # 装配内核到 web/.kernel
pnpm -C web/probe-web run typecheck              # tsc --noEmit → 通过（强类型）
pnpm -C web/probe-web run probe                  # 真实 import + 调用 → 10 项断言通过
pnpm -C web/probe-web run bundle                 # 体积实测
```

**内核 JS 体积（验收项 4）—— 并更正一处此前的误导性数字：**

| 口径 | 字节 | KiB | gzip |
| :--- | :--- | :--- | :--- |
| esbuild bundle（未压缩，含 stdlib） | 27,128 | 26.5 | 5.6 KiB |
| esbuild bundle（**压缩**，含 stdlib） | **9,756** | **9.5** | **3.7 KiB** |
| 内核产物逐文件合计（未压缩，不含 .map） | 30,308 | 29.6 | — |

> ⚠️ **更正**：此前简报里提到的"kotlin-stdlib 就 620KB"来自
> `compileSync/js/**test**/testDevelopmentExecutable/kotlin/kotlin-stdlib.js`，
> 那是 **dev 测试运行器**的拼接产物，**不是生产库产物**。
> 真实生产产物 + tree-shaking 后是 **gzip 3.7 KiB** 量级。用 620KB 做首屏预算会严重高估。
> 逐文件占比：stdlib 28,936 B（95.5%）、`viewphone-shared.mjs` 1,248 B（4.1%）、dom-api-compat 124 B。

**导出面（验收项 5）**：当前 **2 个**（`kernelVersion`、`echoTrimmed`），上限 10。
ESM 下导出是**扁平顶层具名导出**，没有命名空间；命名空间写法由装配层 adapter 提供（形态 C）。
调用语法评价与最脆环节（手写 `.d.mts` 需三处同步）见 DEC-011 §11.5。

## 三、Android SDK 现状与**可复现安装**（要求 ②）

| 项 | 值 |
| :--- | :--- |
| SDK 根 | `C:\Users\MyAdmin\AppData\Local\Android\Sdk`（由环境变量 `ANDROID_HOME` 提供，**不进仓库**） |
| platform | `android-34`、**`android-36`（本仓库要求，见下）**、`android-36.1` |
| build-tools | `34.0.0`、`36.1.0`、`37.0.0`，以及 AGP 自动安装的 `36.0.0` |
| `compileSdk` | **36**（对应 `platforms/android-36`） |
| `minSdk` | **26**（已确认，见 `docs/DECISIONS.md` DEC-002） |
| 许可证 | `licenses/android-sdk-license` 已接受 |

### 3.1 声明式安装（**唯一认可的方式**）

`platforms;android-36` 必须通过 `sdkmanager` 声明式安装，**不许依赖手工解压/展平目录**：

```bash
sdkmanager --install "platforms;android-36"
```

- 本地与 CI **使用同一条命令、同一个镜像、同一个版本**，避免行为分叉（要求 ②③）。
- CI 侧由 `android-actions/setup-android` 提供 `sdkmanager`；**本机目前没有 `cmdline-tools`**
  （`%LOCALAPPDATA%\Android\Sdk` 下无 `cmdline-tools` 目录），
  因此本机也是靠 AGP 自动下载补齐的。**待办**：本机补装 `cmdline-tools`，让本地与 CI 完全同路。
- 校验方式（不依赖任何手工步骤）：

  ```bash
  grep -E '^AndroidVersion.ApiLevel=36$' "$ANDROID_HOME/platforms/android-36/source.properties"
  ```

### 3.2 命令与镜像必须双端一致（要求 ③）

| 项 | 本机 | CI 要求 |
| :--- | :--- | :--- |
| Gradle | 9.3.0（wrapper 锁定） | 同 |
| AGP | 9.0.1 | 同 |
| Kotlin | 2.4.20 | 同 |
| JDK | JBR 21.0.10 | **必须 JDK 21**（不得用 17 跑出不同行为） |
| 依赖仓库顺序 | 镜像 → 官方兜底（`settings.gradle.kts`） | **同顺序**（见 `docs/DECISIONS.md` DEC-003） |

> `ci.yml` 落地时必须逐项核对上表，并在本条记录核对结果。

---

## 四、两处**必须记录**的环境坑与偏离

### 坑 1：Maven Central 把大 artifact 302 到 github.com，本机不可达

> 已固化为决策：见 `docs/DECISIONS.md` DEC-003（含 CI 一致性复查点）。

- 现象：`kotlin-compiler-embeddable:2.4.20`（约 58MB）从 `repo.maven.apache.org` 被重定向到
  `github.com/JetBrains/kotlin/releases/download/...`，而 **github.com:443 在本机连接超时** → 首次构建失败。
  原始错误见 `.buildlogs/build1.log`。
- 实测对照（真实 HEAD 请求）：

  | 源 | 结果 |
  | :--- | :--- |
  | `maven.aliyun.com/repository/public` | **200**，同一 artifact 58,593,245 字节 |
  | `maven.aliyun.com/repository/google` | **200**（AGP POM 可达） |
  | `repo1.maven.org` 同一 artifact | 302 → github.com（不可用） |

- 处置：`settings.gradle.kts` 中**镜像优先、官方源兜底**，并已写明原因。

### 坑 2：配置缓存（**结论已更正**）

首轮误判为「Gradle 9.3.0 与 Kotlin 插件 `KotlinCompile` 的配置缓存不兼容」，并一度关闭配置缓存。
经四次复测（含真·从零编译 + 缓存复用）确认：**那是依赖下载失败造成的连带报错，不是插件缺陷**。
配置缓存现已**开启**并验证可写入、可从零编译、可复用。

> 完整证据链、结论与复查点见 `docs/DECISIONS.md` **DEC-001**（不可只在本文件省略）。

### 偏离 1：平台 36 的安装方式**已修正为声明式**（要求 ②）

- 事实：`android-36.1` 的 `source.properties` 写着 `AndroidVersion.ApiLevel=36.1`、`Platform.Version=16`；
  它**不是**主版本 36 的平台，`compileSdk = 36` 需要 `platforms/android-36`。
- **首轮做法（已废弃）**：手工下载 `platform-36_r02.zip` 并手工展平目录。
  手工步骤不可复现、会与 CI 分叉，且本机 `cmdline-tools` 缺失导致 `sdkmanager` 尚不可用。
- **修正后的口径**：统一用 `sdkmanager --install "platforms;android-36"`（见 §3.1），
  本地与 CI 同命令、同镜像、同版本。校验用 `source.properties` 里的 `AndroidVersion.ApiLevel`，
  不依赖任何人工判断。
- **未验证**：`compileSdk = 36` 能否直接用 `android-36.1` 这个次版本平台（未实测，不作断言）。

### 3.3 CI 上**必须**设置 `ANDROID_HOME`（否则第一次云端构建必失败）

仓库里**没有** `local.properties`（按宪法 §三.6 被 `.gitignore` 排除），
所以 AGP 拿 SDK 路径只有两条途径：

| 途径 | 本机 | CI |
| :--- | :--- | :--- |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` 环境变量 | 已用（见 §六） | **必须显式设置** |
| `local.properties` 的 `sdk.dir` | 可选（本机没建） | **禁止**（不能入库，也不该在 CI 里生成） |

> ⚠️ **CI 硬性要求：`ANDROID_HOME` 必须在 workflow 里设置**（由 `android-actions/setup-android`
> 导出，或显式 `echo "ANDROID_HOME=$ANDROID_SDK_ROOT" >> $GITHUB_ENV`）。
> 缺了它，AGP 会在配置阶段直接失败：`SDK location not found. Define a valid SDK location with an
> ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local.properties file`。
> 这条必须在第一次推送 `ci.yml` 时就写对，不能等它红了再补。

### 3.4 本机 `cmdline-tools` 缺口（已知、不绕过）

- 现状：本机 SDK 下**没有 `cmdline-tools`**，所以 `sdkmanager` 命令在本机暂时跑不了；
  平台 36 当时是靠 AGP 自动安装 + 一次手工下载补齐的。
- **决定**：**不为了"本机一致"而改用不可复现的方式**。CI 一律走 `sdkmanager`；
  本机缺口单独补装 `cmdline-tools` 后，本地也回到同一条命令。
  在此之前，本地与 CI 的 SDK 安装路径承认存在差异，并**记录在案**（不当成已解决）。

### 偏离 2：`.gitignore` 的一处真实 bug 已修

`!gradle/wrapper/gradle-wrapper.jar` 是死代码：Gradle 会忽略「位于被忽略目录内」的负例规则。
已改为 `!gradle/wrapper/` 前缀式负例，并写入注释防止回退。
**验证方法**：`git check-ignore -v gradle/wrapper/gradle-wrapper.jar` 退出码非 0 = 未被忽略。
（若此处失效，CI 会缺 wrapper jar，`./gradlew` 直接不可用。）

---

## 五、尚未验证的事项（不许当成已验证）

| # | 事项 | 状态 |
| :-- | :--- | :--- |
| 1 | KMP 插件（`org.jetbrains.kotlin.multiplatform`）2.4.20 + AGP 9.0.1 共存 | **已验证**：jvm / js / android 三个 target 全部编译通过（见 §2.5） |
| 2 | AGP 9 是否支持/要求 `compileSdkMinor` 概念 | **未验证**（`compileSdk = 36` 用 `platforms/android-36` 已够用） |
| 3 | AGP 9 是否有内置 Kotlin 支持 | **已证实**：AGP 9 报错中点名 `android.builtInKotlin`，且它与 KMP 互斥（DEC-010） |
| 4 | Detekt 1.23.8 在 Gradle 9.3.0 上可用性、自定义规则 API 稳定性 | **未验证**（P0 第 3 步，5 条规则尚未实现） |
| 5 | Compose BOM / Room / Ktor 等版本 | **未加入** `libs.versions.toml`（需要时再加并实测） |
| 6 | `js` target 与 KMP→JS hello world（B1 附加项） | js target **已完成**；`web/src/kernel` 调用验证**未开始** |
| 7 | JUnit5 与 KMP `commonTest` 的组合方式 | **未验证**（当前用 `jvmTest` + kotlin-test、`jsTest` + kotlin-test） |
| 8 | 本机 `cmdline-tools` 缺失，`sdkmanager` 命令尚不可用 | **待补装**（见 §3.1、§3.4） |

### 2.5 step 3：Android target 加入后的实测（三个 target 并存）

```
> Task :shared:compileAndroidMain
> Task :shared:bundleAndroidMainAar
> Task :shared:assembleAndroidMain
BUILD SUCCESSFUL in 18s
112 actionable tasks: 102 executed, 4 from cache, 6 up-to-date
```
`./gradlew :shared:jvmTest :shared:jsTest build` → `exit code: 0`。

三份产物并存（实测路径与大小）：

| target | 产物 |
| :--- | :--- |
| android | `shared/build/outputs/aar/shared.aar`（1,590 B）、`shared/build/classes/kotlin/android/main/com/viewphone/shared/SharedKernel.class`（848 B） |
| jvm | `shared/build/classes/kotlin/jvm/main/com/viewphone/shared/SharedKernel.class`（848 B） |
| js | `shared/build/compileSync/js/test/testDevelopmentExecutable/kotlin/viewphone-shared.js`（1,493 B）+ `.map` |

测试报告：jvm `tests=1 failures=0 errors=0`；js `tests=1 failures=0 errors=0`。

配置缓存三态（三 target 加入后复查，履行 DEC-001 复查点）：
写入 `Configuration cache entry stored.` → 复用 **`Reusing configuration cache.`**。

> Android target 的旧 DSL（`com.android.library` + `androidTarget()`）**已被 AGP 9 封死**，
> 完整报错与最终可用写法见 `docs/DECISIONS.md` **DEC-010** ——
> 这一条同时证实了上表中第 3 项的「AGP 9 内置 Kotlin 支持」。

---

## 六、环境变量与机器相关配置约定

仓库**不含**任何机器路径。本机需要的三个变量（每次新 shell 或写入系统环境变量）：

```powershell
$env:JAVA_HOME    = "D:\AndroidStdio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path         = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

JDK 绑定写在**本机** `%USERPROFILE%\.gradle\gradle.properties`：`org.gradle.java.home=D:/AndroidStdio/jbr`。
回退方案：安装 Temurin 17 后改这一行。

> 本机另有两个已实测的小坑（与本仓库配置无关，记录备用）：
> ① `pnpm` 经 PowerShell 会被执行策略拦截，需 `cmd /c pnpm ...`；
> ② `adb` 不在 PATH（`platform-tools` 目录存在）。

---

## 七、仓库膨胀门禁（CI 必跑）

命令：`node tools/ci/check-repo-bloat.mjs`（加 `--json` 输出机器可读结果）

它把"构建产物 / 依赖目录 / Gradle 缓存 / 密钥文件不得入库"从人工体检变成机器守住的规则。
两类检查，任一失败即非 0 退出：

| 检查 | 内容 |
| :--- | :--- |
| 路径黑名单 | `build/`、`node_modules/`、`.gradle/`、`.buildlogs/`、`dist/`、`local.properties`、`keystore.properties`、`*.jks`、`*.keystore`、`*.apk/*.aab/*.dex`、`*.class/*.aar/*.jar`（仅放行 `gradle/wrapper/gradle-wrapper.jar`）、`*.log`、`*.tsbuildinfo`、`web/.kernel/`、`.bundle-out/` |
| 体积上限 | 单文件 ≤ **1 MiB**；全仓跟踪体积 ≤ **8 MiB** |

**当前基线（实测）**：跟踪文件 **36** 个，合计 **520,993 字节 = 0.50 MiB**，无违规（exit 0）。

**门禁自证**（证明它真的会红，不是写了没接）：
```
# 强制暂存 web/.kernel 后
node tools/ci/check-repo-bloat.mjs
  ✗ 发现 8 处违规：
    - web/.kernel/kotlin/kotlin-kotlin-stdlib.mjs.map  (22262 字节)  原因：内核装配产物（由脚本生成，不入库）
    - web/.kernel/kotlin/viewphone-shared.mjs  (1248 字节)  原因：内核装配产物（由脚本生成，不入库）
    ... （逐条列出）
=== exit code: 1 ===
# 撤销暂存后恢复 exit code: 0
```

**顺带修掉的一个真实缺陷**：本轮之前 `web/.kernel/` **并没有**被 `.gitignore` 忽略
（只靠门禁兜住）。现已加入 `.gitignore`，双保险；已验证
`git check-ignore -v web/.kernel/package.json` 命中，且 `pnpm-lock.yaml` /
`kotlin-js-store/yarn.lock` 两个 lockfile **正常入库**（它们是可复现安装的前提，不能忽略）。
