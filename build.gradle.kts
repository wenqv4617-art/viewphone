// ============================================================
// 微光机 ViewPhone · 根构建脚本
// 只声明插件版本（apply false），不在这里应用任何插件。
// ============================================================

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

// ============================================================
// 模块边界强制校验（宪法 §二.4「feature 之间零直接依赖」的地基）
//
// 规则：**生产模块禁止依赖 toolchain-probe**。
// 理由：探针模块存在的意义是「证明工具链可用」，一旦被生产代码引用，
//       它就会漂移成事实上的公共库，而它不在 CRITICAL §3.1 的交付结构里。
// 失败语义：配置阶段直接抛错，构建无法进行。
// ============================================================

val nonProductionProjects = setOf(
    ":toolchain-probe:jvm-test",
    ":toolchain-probe:android-lib",
)

subprojects {
    if (path in nonProductionProjects) return@subprojects

    // 版本目录的别名 → 模块路径（探针被 catalog 引用时也要拦住）
    val forbidden = listOf(
        "toolchain-probe:jvm-test",
        "toolchain-probe:android-lib",
    )

    afterEvaluate {
        configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                val target = dependency as? ProjectDependency ?: return@forEach
                if (target.path in nonProductionProjects) {
                    throw GradleException(
                        "模块边界违规：生产模块 $path 依赖了非生产模块 ${target.path}。" +
                            "该模块仅用于验证工具链，core:testing 落地后会被并入。" +
                            "请改用 core:testing 等正式模块。",
                    )
                }
            }
        }

        // 兜底：字符串写法（例如 implementation(project(":toolchain-probe:jvm-test"))）
        configurations.forEach { configuration ->
            configuration.dependencies
                .filterIsInstance<ExternalModuleDependency>()
                .forEach { dependency ->
                    if (forbidden.any { dependency.name.contains(it) }) {
                        throw GradleException(
                            "模块边界违规：生产模块 $path 依赖了非生产模块（${dependency.name}）。",
                        )
                    }
                }
        }
    }
}
