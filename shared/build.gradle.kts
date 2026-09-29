// ============================================================
// shared · KMP 共享内核（宪法 §二.1：唯一实现，两端共用同一份代码）
//
// P0 阶段策略（Q2 口径）：**一次只加一个 target**，每步立即验证。
//   step 1 : jvm()            ← 已通过（:shared:jvmTest 绿）
//   step 2 : js               ← 已通过（:shared:jsTest 绿）
//   step 3 : androidLibrary   ← 已通过（compileAndroidMain + AAR 落盘）
//   step 4 : JS 产物可被 TS 消费 ← 当前这一步（R1 决策点）
// 理由：KMP 单个 target 配置失败可能拖垮整个模块，逐一引入才能定位问题。
// ============================================================

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 起，KMP 的 Android target 必须用这个专用插件：
    //   'com.android.library' 与 'org.jetbrains.kotlin.multiplatform' 自 AGP 9.0 起**不兼容**
    //   （实测报错见 docs/DECISIONS.md DEC-010），官方推荐即本插件。
    // 注意：这里**不带版本**（版本已在根 build.gradle.kts 用 apply false 固定）。
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    // ---- step 1：纯 JVM（内核算法与 golden 测试的宿主）----
    jvm()

    // ---- step 2：JS（Web 端消费同一份内核的唯一途径，宪法 §二.1）----
    // 注意：Kotlin 2.4.20 起 `js(IR) { }` 已废弃（编译器类型选择被移除，2.6 删掉），
    //      正确写法就是 `js { }`。这是编译器实测给出的弃用警告，本文件照此修正。
    js {
        nodejs()
        // R1 实测（DEC-011）：Kotlin/JS 默认产 **UMD/CJS**，在 ESM 工程里
        // `import { com } from '@viewphone/shared'` 会直接失败：
        //   SyntaxError: The requested module ... does not provide an export named 'com'
        // Web 端是 ESM 世界，因此必须显式切到 ES modules 输出。
        useEsModules()
        // 「TS 侧能否拿到类型」是 R1 的验收项之一，必须显式开启：
        // 默认不生成 .d.ts，TS 就只能弱类型调用（验收要求如实记录这种情况）。
        generateTypeScriptDefinitions()
        // 产出可分发的库产物（production 变体），供 Web 端消费。
        binaries.library()
    }

    // ---- step 3：Android target（androidApp / feature:* 消费内核的途径）----
    // 用 `androidLibrary {}` 而不是旧的 `androidTarget()`：
    // AGP 9 + KMP 的官方 DSL 就是这个，`androidTarget()` 属已被取代的旧路径。
    androidLibrary {
        namespace = "com.viewphone.shared"
        compileSdk = 36
        minSdk = 26 // 见 docs/DECISIONS.md DEC-002
    }

    jvmToolchain(21)

    sourceSets {
        // 教训（DEC-007）：KMP **不会**自动注入 kotlin-test。
        // 每新增一个 test 源集都必须显式声明，jvm 已实测，js 同样必须。
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// 只对 JVM 测试任务指定 JUnit Platform。
// 不能写成 tasks.withType<Test>() —— KotlinJsTest 虽然也继承 Test，
// 但 JS 侧用的是 Kotlin 自带的测试框架，不该被强加 JUnit Platform 配置。
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// ============================================================
// Node 版本锁定（口径：不能今天 24.16、明天 CI 拿到别的版本）
//
// KGP 的默认 Node 版本会随插件版本变化，必须在构建脚本里写死。
// 写法依据（实测）：`NodeJsEnvSpec`（继承 `EnvSpec`）的
//   `version: Property<String>` 是**非废弃**的现代入口；
//   NodeJsRootExtension 上的 `nodeVersion` 已被 @Deprecated。
//   扩展名 `kotlinNodeJsSpec` 由 Gradle 报错里的
//   "Currently registered extension names: [...]" 直接列出，非猜测。
// 变更此值时必须同步更新 docs/P0-TOOLCHAIN-BASELINE.md 与 CI 配置。
// ============================================================
extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>("kotlinNodeJsSpec") {
    version = "24.16.0"
}
