package com.viewphone.shared

/**
 * 共享内核探针（**内部**实现，三端共用同一份）。
 *
 * 职责：P0 阶段证明 `commonMain` 的代码能被编译到 JVM / JS / Android。
 *       P1 起被真正的内核（ai/compiler、ai/directives、ai/memory…）取代。
 * 失败语义：这些函数不抛异常。
 *
 * 为什么这里没有 `@JsExport`（R1 实测结论，很重要）：
 *  - `@JsExport` 属于 `kotlin.js`，**在 commonMain 里不可用**
 *    （实测：`:shared:compileKotlinJvm` 报 `Unresolved reference 'JsExport'`）；
 *  - 即使可用，它也**不能**标注 `object` 成员
 *    （实测：`'@JsExport' is only allowed on files and top-level declarations.`）。
 *  ⇒ 因此本项目的固定写法是：
 *    **内核实现只有一份（commonMain）；对外的导出面是各平台的薄包装**
 *    （`jsMain` 的 `@JsExport` 顶层函数 / `jvmMain` 的普通顶层函数）。
 *    这既满足宪法 §二.1（单一实现），也满足 CRITICAL §7.6（导出面窄）。
 */
object SharedKernel {

    /** 内核版本标识，形如 `viewphone-shared/0.0.1-p0`。 */
    fun version(): String = "viewphone-shared/0.0.1-p0"

    /**
     * 去除首尾空白。
     *
     * @param input 任意字符串。
     * @return 去除首尾空白后的结果；空白输入返回空串。不抛异常。
     */
    fun echoTrimmed(input: String): String = input.trim()
}
