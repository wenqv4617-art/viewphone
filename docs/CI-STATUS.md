# CI 状态与门禁口径

> 更新：P0 第 1 天。**本文是"门禁到底在哪"的唯一口径，其他文档与本文件冲突时以本文件为准。**

---

## 一、当前状态：**云端 CI 未启用，本地 verify 是唯一门禁**

| 项 | 状态 |
| :--- | :--- |
| 本仓库 git remote | **无**（`git remote -v` 为空）→ 云端 workflow **不会被触发** |
| `.github/workflows/ci.yml` | **已写好，但是最小骨架，尚未生效**（不要把它当门禁） |
| **当前真正的门禁** | **`pnpm verify`**（本地，立刻生效，不依赖网络与 GitHub） |
| pre-push 钩子 | 可安装：`pnpm hooks:install`（**只作用于本仓库**） |

**为什么先做本地门禁**：门禁的价值是"每次提交都跑"。依赖远端可达才有红灯，
等于在最需要它的时候（没网、远端挂了、还没建远端）没有门禁。

### 怎么用

```bash
pnpm verify              # 跑完全部门禁（编译 + 双单测 + 内核 + 类型 + 真实调用 + 仓库膨胀）
pnpm verify:fast         # 只跑快关卡：typecheck + probe + bloat
pnpm hooks:install       # 装成 pre-push（等价于 git config --local core.hooksPath .githooks）
pnpm hooks:uninstall     # 卸载
pnpm bloat               # 只跑仓库膨胀门禁
```

**应急跳过**：`git push --no-verify`（请在事后补跑，不要常态使用）。

### 门禁包含哪些关卡

`tools/verify/verify.mjs` 是唯一入口，顺序执行、**全部跑完再汇总**，任一失败即非 0 退出：

| # | 关卡 | 证明什么 |
| :-- | :--- | :--- |
| 1 | Gradle `build` + `:shared:jvmTest` + `:shared:jsTest` + JS 生产库 | 三端编译与两条单测链路真实可用 |
| 2 | 内核装配 + **导出面机器校验** | `.d.mts` 的实际导出集合 == 装配层声明集合（防手写漂移） |
| 3 | `tsc --noEmit`（web） | TS 拿到**强类型**，不是弱类型调用 |
| 4 | `probe`（web） | TS **真的 import 并调用**内核且断言返回值正确 |
| 5 | 仓库膨胀门禁 | 构建产物/依赖/缓存/密钥未入库；单文件与全仓体积在限内 |

失败时每一关的完整输出落在 `.buildlogs/verify/<关卡>.log`（该目录已 gitignore）。
若失败特征匹配 **Node 分发包下载失败**，verify 会明确打印这一判断与处理顺序，
避免被误读成代码问题。

---

## 二、远端落地后要做的事（启用云端 CI 的检查清单）

**第一步**：`git remote add origin <url>` 并首次 push，然后在 GitHub 上确认 workflow 跑起来。

**必须逐项核对**（缺一项就会出现"本地绿、云端红"或"两套构建"）：

1. **同版本**：Gradle 9.3.0（wrapper 锁定）/ AGP 9.0.1 / Kotlin 2.4.20 / **JDK 21**。
   本机用的是 Android Studio 自带 **JBR21**，云端**不是** —— 因此 `ci.yml` 里显式
   `actions/setup-java@v4` + `temurin` + `21`。**不要**依赖 runner 预装 JDK 的版本。
2. **同镜像策略**：`settings.gradle.kts` 是「aliyun 优先 + 官方兜底」。
   云端**沿用同一份文件**，`ci.yml` 里**不做任何仓库覆盖**。
   ⚠️ 明确否定的做法：本地用镜像、云端用官方 —— 那等于两套构建，
   出问题时本地无法复现（这也是把镜像策略写进 `settings.gradle.kts` 而不是本机配置的原因）。
3. **`ANDROID_HOME` 必须显式设置**：`local.properties` 被 gitignore，云端拿不到 `sdk.dir`。
   `ci.yml` 里由 `android-actions/setup-android` 导出，并加了显式校验步骤
   （`test -n "$ANDROID_HOME"` + 校验 `platforms/android-36/source.properties`）。
4. **`platforms;android-36` 用 `sdkmanager` 声明式安装**，与本地同一条命令；
   不允许手工解压/展平目录（见基线文档 §3.1 与偏离 1）。
5. **Node 分发包**：从 `nodejs.org` 下载（版本锁定 `24.16.0`）。
   这是**未声明的外部依赖**，首个 CI run 会拉约 50MB。
   `ci.yml` 里先跑 `node tools/ci/check-node-dist-reachable.mjs --version 24.16.0`，
   让它"失败得易读"。

   > **明确不做**：改成自建分发或第三方镜像。理由：会引入新的维护面与信任面，
   > 且破坏"本地与云端同源"。
6. **pnpm 用 `--frozen-lockfile`**：`pnpm-lock.yaml` 已入库，云端不得就地改动依赖版本。
7. **缓存**：Gradle（`~/.gradle/caches|wrapper|nodejs`）与 pnpm store。
   注意 `~/.gradle/nodejs` 也要缓存 —— 否则每个 run 都会重下 Node 分发包。

## 三、本机与云端的差异（已知且已认领）

| 项 | 本机 | 云端 | 影响 |
| :--- | :--- | :--- | :--- |
| JDK 来源 | Android Studio JBR 21.0.10 | Temurin 21 | 均满足 `jvmToolchain(21)`；**JDK 大版本一致**，补丁版本可能不同 |
| `cmdline-tools` / `sdkmanager` | **缺失**（见基线 §3.4） | 由 setup-android 提供 | 本机平台 36 是 AGP 自动安装 + 一次手工补齐；**云端走声明式**，已在文档登记为差异 |
| 镜像可达性 | 依赖 aliyun 镜像（本机无法直连 github） | 通常两者都可达 | 由于策略一致，两边都会先试镜像、失败再走官方 |

## 四、未做（明确留给后续，不要误以为已完成）

- 云端 CI 的**首次实跑验证**（没有 remote，无法验证 `ci.yml` 的正确性 ——
  本文件不对未跑过的工作流作任何"已可用"的断言）。
- Detekt 5 条规则（`CRITICAL §3.3`）：**尚未实现**，属 P0 后续步骤。
  当前生效的模块边界门禁只有一条：生产模块不得依赖 `toolchain-probe`（见 DEC-009）。
- 迁移测试、出包（debug/release 分流 + 签名走 Secret）：属 P2/P7。
