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
| Kotlin JVM toolchain | **21** | `jvmToolchain(21)` 生效，无需额外下载 JDK |
| Gradle 配置缓存 | **开启且已验证可复用** | `Reusing configuration cache.`（见 `docs/DECISIONS.md` DEC-001） |

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

### 偏离 2：`.gitignore` 的一处真实 bug 已修

`!gradle/wrapper/gradle-wrapper.jar` 是死代码：Gradle 会忽略「位于被忽略目录内」的负例规则。
已改为 `!gradle/wrapper/` 前缀式负例，并写入注释防止回退。
**验证方法**：`git check-ignore -v gradle/wrapper/gradle-wrapper.jar` 退出码非 0 = 未被忽略。
（若此处失效，CI 会缺 wrapper jar，`./gradlew` 直接不可用。）

---

## 五、尚未验证的事项（不许当成已验证）

| # | 事项 | 状态 |
| :-- | :--- | :--- |
| 1 | KMP 插件（`org.jetbrains.kotlin.multiplatform`）2.4.20 + AGP 9.0.1 共存 | **部分验证**：`jvm()` target 已通过；`androidTarget()` **未验证**（step 3） |
| 2 | AGP 9 是否支持/要求 `compileSdkMinor` 概念 | **未验证** |
| 3 | AGP 9 是否有内置 Kotlin 支持 | **有实测线索**：`toolchain-probe:android-lib` 未应用任何 Kotlin 插件，AGP 仍编译了 `.kt`。**未做对照实验**，不作为结论 |
| 4 | Detekt 1.23.8 在 Gradle 9.3.0 上可用性、自定义规则 API 稳定性 | **未验证**（P0 第 3 步） |
| 5 | Compose BOM / Room / Ktor 等版本 | **未加入** `libs.versions.toml`（需要时再加并实测） |
| 6 | `js(IR)` target（P0 step 2）+ KMP→JS hello world（B1 附加项） | **未开始** |
| 7 | JUnit5 与 KMP `commonTest` 的组合方式 | **未验证**（当前只用 `jvmTest` + kotlin-test） |
| 8 | 本机 `cmdline-tools` 缺失，`sdkmanager` 命令尚不可用 | **待补装**（见 §3.1） |

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
