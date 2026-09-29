// ============================================================
// 工具链探针 · 纯 JVM Kotlin 模块
// 目的：在建立业务模块之前，先证明「Gradle + Kotlin JVM 插件 + JDK21 + 单测」
//      这条链路在本机可真实编译并跑通测试。P0 完成后本模块可删除。
// ============================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
