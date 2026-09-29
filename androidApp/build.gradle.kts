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
        // 每次发新版都必须 +1，否则系统不允许覆盖安装更新
        versionCode = 2
        versionName = "0.2.0-p1"
    }

    /**
     * 固定签名（重要：这是"能覆盖安装"的关键）。
     *
     * 为什么把 keystore 提交进仓库：
     *  - 之前用 AGP 自动生成的 debug 签名，**每台机器/每次 CI 构建都不同**，
     *    导致用户已安装的包无法被新包覆盖（INSTALL_FAILED_UPDATE_INCOMPATIBLE）。
     *  - 提交一个**固定口令的 debug keystore** 后，所有构建签名一致，可直接覆盖安装。
     *  - 口令 `android` 是公开的、且仅用于 debug 包；**正式发布签名必须走 CI Secret**，
     *    不得复用这份（见宪法 §三.6）。
     */
    signingConfigs {
        create("fixedDebug") {
            storeFile = file("viewphone-debug.keystore")
            storePassword = "android"
            keyAlias = "viewphone"
            keyPassword = "android"
        }
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
        release {
            isMinifyEnabled = false
            // 暂时复用固定 debug 签名，保证"同一份包能互相覆盖安装"；
            // 正式上架前替换为 CI Secret 里的 release 签名。
            signingConfig = signingConfigs.getByName("fixedDebug")
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
