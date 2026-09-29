package com.viewphone.shared.api

import com.viewphone.shared.SharedKernel

/**
 * 共享内核对 **JavaScript / TypeScript** 的公开入口（唯一出口）。
 *
 * 设计依据：
 *  - `CRITICAL §3.1`：共享内核有「唯一公开入口」，Web 端只经 `web/src/kernel`
 *    这一层薄封装消费内核；
 *  - `CRITICAL §7.6`：导出面 ≤ 10 个入口，因此逐个登记，不做"什么都导出"。
 *
 * 为什么这个文件在 `jsMain`（R1 实测约束，见 DEC-011）：
 *  `@JsExport` 属 `kotlin.js`，**commonMain 里不可用**；且不能标注 `object` 成员。
 *  ⇒ 内核实现留在 commonMain（独一份），导出面按平台各写一层**薄包装**。
 *
 * 导出面登记（当前 2 / 10）：
 *  1. `kernelVersion()`
 *  2. `echoTrimmed(input)`
 */
@JsExport
public fun kernelVersion(): String = SharedKernel.version()

/**
 * 回显并规范化一个字符串（R1 验证用的最小「有真实计算」的入口）。
 *
 * @param input 任意字符串。
 * @return 去除首尾空白后的结果；空白输入返回空串。不抛异常。
 */
@JsExport
public fun echoTrimmed(input: String): String = SharedKernel.echoTrimmed(input)
