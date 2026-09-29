// ============================================================
// ⚠️ 非生产模块（toolchain-probe / jvm-test）
// 作用：证明 Kotlin 编译器在本机/CI 的 JDK21 上真的跑起来了。
// 生产模块禁止依赖本模块（根 build.gradle.kts 强制校验）。
// ============================================================

package com.viewphone.probe

/**
 * JVM 工具链探针。
 *
 * 职责：提供一个可被单测断言的纯函数，用于验证构建链路真实可用。
 * 失败语义：本函数不抛异常。
 */
object ToolchainProbe {

    /** 返回探针标识，用于断言编译产物确实来自本模块。 */
    fun identify(): String = "viewphone-toolchain-probe"
}
