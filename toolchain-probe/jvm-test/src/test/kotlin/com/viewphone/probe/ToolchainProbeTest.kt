package com.viewphone.probe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 工具链探针测试。
 *
 * 职责：证明 JUnit 5 在 Kotlin JVM 模块里可运行（这是 P1 内核 85% 覆盖率的地基）。
 */
class ToolchainProbeTest {

    @Test
    fun `探针可被单测调用`() {
        assertEquals("viewphone-toolchain-probe", ToolchainProbe.identify())
    }
}
