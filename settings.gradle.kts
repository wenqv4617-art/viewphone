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
    // 为什么不是 FAIL_ON_PROJECT_REPOS（实测三组合后的结论，别改回去）：
    //   Kotlin/JS 插件为获取 Node 分发包，会往**项目级** repositories 注入
    //   `https://nodejs.org/dist`。三种配置的实测结果：
    //     ① FAIL_ON_PROJECT_REPOS → 直接拒绝注入：
    //        "...repository 'Distributions at https://nodejs.org/dist' was added by unknown code"
    //     ② PREFER_SETTINGS       → 更糟：注入的仓库存在时 **settings 仓库被整个忽略**，
    //        解析器链只剩 [maven, maven2, Google, MavenRepo]，Node 分发无处可寻，
    //        报错 "Could not find org.nodejs:node:24.16.0"（见 .buildlogs/node-debug.log）
    //     ③ PREFER_PROJECT（当前）→ settings 仓库对 Maven 依赖仍生效（kwargs 见下），
    //        Node 分发由 KGP 注入的仓库解析。
    // 取舍（如实记录）：约束由"编译期强制"降级为"约定 + review"——
    //   所有 Maven 依赖仍集中在这里声明，模块内不得自建仓库；
    //   但这条现在**没有**构建期护栏了。见 docs/DECISIONS.md DEC-008。
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
    }
}

rootProject.name = "viewphone"

// ---- 模块清单（按需创建：用到哪个建哪个，不预先铺空骨架）----

// 共享内核：唯一实现，两端共用（CRITICAL §3.1）。jvm / js / android 三 target 均已通过。
include(":shared")

// Android 应用外壳（主端）
include(":androidApp")

// ⚠️ 非生产模块：仅用于验证工具链真实可用，不承载业务，不参与交付包。
//    生产模块禁止依赖它（根 build.gradle.kts 强制校验）。
//    归宿：core:testing 落地后并入它，不删除。
include(":toolchain-probe:jvm-test")
include(":toolchain-probe:android-lib")
