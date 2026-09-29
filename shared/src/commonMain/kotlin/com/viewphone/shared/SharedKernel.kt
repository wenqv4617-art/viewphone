package com.viewphone.shared

/**
 * 共享内核探针。
 *
 * 职责：P0 阶段仅用于证明 `commonMain` 的代码能被编译并在 JVM 上被单测执行。
 *       P1 起本文件将被真正的内核（ai/compiler、ai/directives、ai/memory…）取代。
 * 失败语义：本函数不抛异常。
 */
object SharedKernel {

    /**
     * 返回内核版本标识。
     *
     * @return 形如 `viewphone-shared/0.0.1-p0` 的版本字符串，用于构建与 CI 断言。
     */
    fun version(): String = "viewphone-shared/0.0.1-p0"
}
