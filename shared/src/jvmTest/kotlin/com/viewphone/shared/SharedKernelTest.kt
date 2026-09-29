package com.viewphone.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 共享内核的 JVM 单测。
 *
 * 职责：证明「commonMain 的代码可以在普通 JVM 单测里跑，不需要模拟器」
 *       —— 这是宪法 §四.1 的地基，也是 P1 内核 ≥85% 覆盖率的前提。
 */
class SharedKernelTest {

    @Test
    fun `内核版本可被 JVM 单测断言`() {
        assertEquals("viewphone-shared/0.0.1-p0", SharedKernel.version())
    }
}
