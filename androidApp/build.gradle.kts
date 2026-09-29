// ============================================================
// androidApp · Android 应用外壳（主端）
//
// 目标（第 1 轮）：能装能开 → 看到桌面 → 进设置页。
// 版本策略：只应用已被本仓库实测过的插件与依赖（见 gradle/libs.versions.toml 注释）。
// ============================================================

plugins {
    alias(libs.plugins.android.application)
    // 注意：**不要**应用 org.jetbrains.kotlin.android ——
    // AGP 9 内置 Kotlin 支持（android.builtInKotlin），两者同时应用会直接失败：
    //   "Failed to apply plugin 'org.jetbrains.kotlin.android' ... Remove the plugin"
    // 这与 DEC-010 记录的 KMP 情况同源。
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.viewphone.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.viewphone.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-p0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        debug {
            // 第 1 轮只出 debug 包；release 签名走 CI Secret（尚未接）
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)

    // 第 1 轮业务所需（catalog 早已列好，此处正式接入）
    implementation(libs.okhttp)                   // HTTP + SSE 流式
    implementation(libs.kotlinx.serialization.json) // 强类型 JSON，禁止手拼字符串

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
