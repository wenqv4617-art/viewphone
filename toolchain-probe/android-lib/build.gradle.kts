// ============================================================
// ⚠️ 非生产模块（toolchain-probe / android-lib）
// 用途：证明「AGP + compileSdk 36 + JDK21」链路真实可用。
// 生产模块禁止依赖本模块（根 build.gradle.kts 强制校验）。
//
// 实测事实：本模块未应用任何 Kotlin Android 插件，AGP 自身即编译了 Kotlin 源码。
// 这与 CRITICAL §11.4 第 3 条（Web 内核复用）、§3.3 的模块边界无关，
// 但属「AGP 9 是否内置 Kotlin 支持」这一未验证项的实测线索，见 docs/DECISIONS.md。
// ============================================================

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.viewphone.probe.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
