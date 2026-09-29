package com.viewphone.shared.api

import com.viewphone.shared.SharedKernel

/**
 * 共享内核对 **JVM / Android** 的公开入口。
 *
 * 与 `jsMain` 的同名 API 一一对应：内核实现只有一份（commonMain 的 `SharedKernel`），
 * 这里只是平台侧的薄包装。这样 JVM 单测与 JS 消费面能覆盖同一组入口名，
 * 不会出现"两端各有一份算法"的情况（宪法 §二.1）。
 *
 * 导出面登记（与 jsMain 保持一致，当前 2 / 10）：
 *  1. `kernelVersion()`
 *  2. `echoTrimmed(input)`
 */

/** 内核版本标识，形如 `viewphone-shared/0.0.1-p0`。 */
public fun kernelVersion(): String = SharedKernel.version()

/**
 * 回显并规范化一个字符串。
 *
 * @param input 任意字符串。
 * @return 去除首尾空白后的结果；空白输入返回空串。不抛异常。
 */
public fun echoTrimmed(input: String): String = SharedKernel.echoTrimmed(input)
