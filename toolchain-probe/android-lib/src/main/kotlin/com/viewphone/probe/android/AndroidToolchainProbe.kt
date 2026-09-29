package com.viewphone.probe.android

/**
 * Android 工具链探针。
 *
 * 职责：提供一个不依赖 Android 运行时 API 的常量，用于验证 AGP 编译链路。
 */
object AndroidToolchainProbe {

    /** 探针标识。 */
    const val IDENTIFIER: String = "viewphone-android-toolchain-probe"
}
