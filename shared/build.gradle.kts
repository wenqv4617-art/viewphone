// ============================================================
// shared · KMP 共享内核（宪法 §二.1：唯一实现，两端共用同一份代码）
//
// P0 阶段策略（Q2 口径）：**一次只加一个 target**，每步立即验证。
//   step 1 : jvm()            ← 当前已在（唯一门槛：:shared:jvmTest 必须绿）
//   step 2 : js(IR)           ← step 1 通过后再加
//   step 3 : androidTarget()  ← 失败不阻塞（P4 才真正被 androidApp/feature 依赖），
//                               完整报错记录为待解决项
// 理由：KMP 单个 target 配置失败可能拖垮整个模块，逐一引入才能定位问题。
//
// 测试依赖：本轮先用 `src/jvmTest` + kotlin-test（KMP 默认的 JUnit 风格）。
// commonTest 与 JUnit5 的组合待 step 2 之后单独引入并实测（未验证项，不预先声明）。
// ============================================================

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// 测试依赖：显式声明，KMP **不会**自动注入 kotlin-test。
// 实测教训：只写 `jvm()` 时，`src/jvmTest` 会被识别（任务名即 :shared:jvmTest），
// 但 `kotlin.test.*` 解析不到 → `Unresolved reference 'test'`。
// 这里用 sourceSets 的命名访问器（KMP 2.x 推荐写法），不依赖生成的 accessor。
kotlin {
    // ---- step 1：纯 JVM（内核算法与 golden 测试的宿主）----
    jvm()

    jvmToolchain(21)

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
