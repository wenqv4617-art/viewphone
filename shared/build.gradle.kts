// ============================================================
// shared · KMP 共享内核（宪法 §二.1：唯一实现，两端共用同一份代码）
//
// P0 阶段策略（Q2 口径）：**一次只加一个 target**，每步立即验证。
//   step 1 : jvm()            ← 已通过（:shared:jvmTest 绿）
//   step 2 : js               ← 当前这一步
//   step 3 : androidTarget()  ← 失败不阻塞（P4 才真正被 androidApp/feature 依赖）
// 理由：KMP 单个 target 配置失败可能拖垮整个模块，逐一引入才能定位问题。
// ============================================================

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // ---- step 1：纯 JVM（内核算法与 golden 测试的宿主）----
    jvm()

    // ---- step 2：JS（Web 端消费同一份内核的唯一途径，宪法 §二.1）----
    // 注意：Kotlin 2.4.20 起 `js(IR) { }` 已废弃（编译器类型选择被移除，2.6 删掉），
    //      正确写法就是 `js { }`。这是编译器实测给出的弃用警告，本文件照此修正。
    js {
        nodejs()
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
