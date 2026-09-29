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

// ---- 模块清单（P0 逐步加入；先只放工具链探针，确认基线后再加业务空模块）----
include(":probe-jvm")
include(":probe-android")
