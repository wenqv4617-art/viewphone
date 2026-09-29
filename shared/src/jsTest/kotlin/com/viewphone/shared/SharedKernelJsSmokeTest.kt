package com.viewphone.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 共享内核的 JS 单测。
 *
 * 职责：**仅证明 JS target 的工具链跑得通** —— 即 `commonMain` 的同一份代码
 *       能被编译成 JS 并在 Node 上执行断言。
 *
 * ⚠️ 本文件不证明任何工程语义：
 *      - 它不是 Web 端一致性测试（那是 P6 的 200 组 ContextBundle 逐字节比对）；
 *      - 它不覆盖任何内核算法（编译器的 golden 测试从 P1 开始写）。
 *      因此这里只放一个冒烟断言，**不要**在此处扩展成"看起来像在测功能"的测试。
 */
class SharedKernelJsSmokeTest {

    @Test
    fun `同一份 commonMain 代码在 JS 上可断言`() {
        assertEquals("viewphone-shared/0.0.1-p0", SharedKernel.version())
    }
}
