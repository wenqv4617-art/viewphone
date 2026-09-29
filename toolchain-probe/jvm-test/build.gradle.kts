// ============================================================
// ⚠️ 非生产模块（toolchain-probe / jvm-test）
// 用途：证明「Kotlin JVM 插件 + JUnit5 + JDK21」链路真实可用。
// 生产模块禁止依赖本模块（根 build.gradle.kts 强制校验）。
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
