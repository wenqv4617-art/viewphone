package com.viewphone.shared

import com.viewphone.shared.api.echoTrimmed
import com.viewphone.shared.api.kernelVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 共享内核的 JS 单测。
 *
 * 职责：**仅证明 JS target 的工具链跑得通** —— 同一份 `commonMain` 代码
 *       能被编译成 JS 并在 Node 上执行断言，且 `api` 层的导出入口在 Kotlin 侧可调用。
 *
 * ⚠️ 本文件不证明任何工程语义：
 *      - 它不是 Web 端跨语言消费验证（真正的消费验证在 `web/probe-web`，用 TypeScript 跑）；
 *      - 它不覆盖任何内核算法（编译器的 golden 测试从 P1 开始写）。
 *      因此这里只放冒烟断言，**不要**扩展成"看起来像在测功能"的测试。
 */
class SharedKernelJsSmokeTest {

    @Test
    fun `同一份 commonMain 代码在 JS 上可断言`() {
        assertEquals("viewphone-shared/0.0.1-p0", SharedKernel.version())
    }

    @Test
    fun `api 导出入口在 Kotlin 侧可调用`() {
        assertEquals("viewphone-shared/0.0.1-p0", kernelVersion())
        assertEquals("hi", echoTrimmed("  hi  "))
    }
}
