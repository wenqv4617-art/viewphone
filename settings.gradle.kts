// ============================================================
// 微光机 ViewPhone · Gradle 设置
// 规则：依赖与插件仓库只在这里声明（模块内不得各自声明 repositories）。
// ============================================================

pluginManagement {
    repositories {
        // 顺序即优先级。镜像放前面是**实测结论**，不是偏好：
        // 本机访问 repo.maven.apache.org 时，kotlin-compiler-embeddable 会被 302
        // 重定向到 github.com/releases/download（约 58MB），而 github.com:443 在本机超时
        // → 首次构建必然失败（见 .buildlogs/build1.log）。
        // 阿里云 public 同时代理 Central，google 镜像代理 Google Maven，两者均已实测可达。
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 官方源兜底：镜像缺件时仍能命中（可能需要能连通 github 的网络）
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 禁止子模块自建仓库 —— 保证「唯一版本来源 + 唯一仓库来源」
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
    }
}

rootProject.name = "viewphone"

// ---- 模块清单（P0 逐步加入）----

// 共享内核：唯一实现，两端共用（CRITICAL §3.1）。P0 只开 jvm target。
include(":shared")

// ⚠️ 非生产模块：仅用于验证工具链真实可用，不承载业务，不参与交付包。
//    生产模块禁止依赖它（根 build.gradle.kts 强制校验）。
//    归宿：core:testing 落地后并入它，不删除。
include(":toolchain-probe:jvm-test")
include(":toolchain-probe:android-lib")
