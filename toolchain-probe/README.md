// ============================================================
// ⚠️ 非生产模块（toolchain-probe）
// 用途：证明本机/CI 的工具链（JDK21 + AGP + Kotlin JVM + JUnit5）真实可用，
//       不承载任何业务逻辑，不参与交付包。
// 约束：生产模块禁止依赖本模块（根 build.gradle.kts 有强制校验）。
// 归宿：core:testing 落地后并入它，**不删除**。
// 内含两个子模块：
//   - jvm-test    ：纯 JVM Kotlin + JUnit5（证明单测链路）
//   - android-lib ：最小 Android Library（证明 AGP + compileSdk 链路）
// ============================================================
