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
| Android Gradle Plugin | **9.0.1** | 构建日志 `:probe-android:assembleDebug` 成功 |
| Kotlin（JVM 插件 `org.jetbrains.kotlin.jvm`） | **2.4.20** | `:probe-jvm:compileKotlin` + `:probe-jvm:test` 成功 |
| JDK | **OpenJDK 21.0.10**（JetBrains JBR，`D:\AndroidStdio\jbr`） | `java -version` / `javac 21.0.10` |
| JUnit | **Jupiter 6.1.3** | `:probe-jvm:test` 产出 XML：`tests=1 failures=0 errors=0` |
| Kotlin JVM toolchain | **21** | `jvmToolchain(21)` 生效，无需额外下载 JDK |

## 三、Android SDK 现状

| 项 | 值 |
| :--- | :--- |
| SDK 根 | `C:\Users\MyAdmin\AppData\Local\Android\Sdk`（由环境变量 `ANDROID_HOME` 提供，**不进仓库**） |
| platform | `android-34`、`android-36`（本次下载补齐）、`android-36.1` |
| build-tools | `34.0.0`、`36.1.0`、`37.0.0`，以及 AGP 自动安装的 `36.0.0` |
| `compileSdk` | **36**（对应 `platforms/android-36`） |
| `minSdk` | **26**（P0 探针取值，尚未经产品决策确认） |
| 许可证 | `licenses/android-sdk-license` 已接受（AGP 自动装 Build-Tools 36 时验证通过） |

---

## 四、两处**必须记录**的环境坑与偏离

### 坑 1：Maven Central 把大 artifact 302 到 github.com，本机不可达

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
- **待办（属 P0 剩余项）**：CI runner 无此网络限制，但仓库选择需与镜像策略一致；P0 结束前需在 `ci.yml` 里复核。

### 坑 2：Gradle 9.3.0 的配置缓存与 Kotlin 插件 `KotlinCompile` 序列化冲突

- 现象：`Configuration cache state could not be cached: ... field __classpathSnapshotProperties__ of task ':probe-jvm:compileKotlin' of type KotlinCompile`。
- 处置：`gradle.properties` 中 `org.gradle.configuration-cache=false`，并写明原因与重新评估条件。
  （这意味着构建速度会有损失，属**已知折衷**，不是遗忘。）

### 偏离 1：平台 36 是手工补齐的，本机原本只有 `android-36.1`

- 事实：`android-36.1` 的 `source.properties` 写着 `AndroidVersion.ApiLevel=36.1`、`Platform.Version=16`；
  它**不是**主版本 36 的平台，`compileSdk = 36` 需要 `platforms/android-36`。
- 处置：下载 `platform-36_r02.zip`（65,878,410 字节）解压到 `platforms/android-36`。
  下载包内层多一层 `android-36/` 目录，已展平（否则 `source.properties` 位置不对，AGP 认不出）。
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
| 1 | KMP 插件（`org.jetbrains.kotlin.multiplatform`）2.4.20 + AGP 9.0.1 能否共存 | **未验证**（P0 下一步：`shared` 模块） |
| 2 | AGP 9 是否支持/要求 `compileSdkMinor` 概念 | **未验证** |
| 3 | AGP 9 是否有内置 Kotlin 支持（本次 Android 模块的 Kotlin 编译由 KGP 完成，非 AGP 内置） | **未验证** |
| 4 | Detekt 1.23.8 在 Gradle 9.3.0 上可用性、自定义规则 API 稳定性 | **未验证**（P0 第 3 步） |
| 5 | Compose BOM / Room / Ktor 等版本（尚未加入 `libs.versions.toml`） | **未加入**（P1/P2 需要时再加并实测） |
| 6 | JS 目标（KMP→JS hello world，B1 附加项） | **未开始** |
| 7 | `probe-android` 的 Kotlin 编译走的是 `kotlin.jvm` 插件而非 kotlin-android | 待 `shared` 落地时统一 |

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
