// ============================================================
// 工具链探针 · 最小 Android Library 模块
// 目的：证明 AGP 9.0.1 + compileSdk 36 + JDK21 这条链路在本机可真实编译。
//       P0 完成后本模块可删除。
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
