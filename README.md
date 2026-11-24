# CrashStormTest

> **本分支 `crashstorm-test` 存放的是测试用 App，不是 AppErrorNotify 模块本体。**
> 与 `master`（模块代码）**没有共同历史**——本分支是一个**孤儿分支（orphan branch）**，
> 内容只有这个测试 App 自己的代码，检出的工作树里不会出现模块源码。

## 这是什么

`CrashStormTest` 是 **AppErrorNotify** 的**实机测试工具 App**，用来在真机上制造可控的崩溃，
以验证模块的两条核心能力：

| 验证项 | 用什么按钮 | 期望结果 |
|---|---|---|
| 崩溃通知 | 单次崩溃 | 应收到 **1 条**崩溃通知 |
| 崩溃时页面归因（前台） | 前台崩溃 | 模块详情应显示 **崩溃时页面 = MainActivity** |
| 崩溃时页面归因（后台） | 后台崩溃（点后按 Home） | 模块详情应显示 **崩溃时页面 = 后台** |
| 通知收敛 | 温和风暴 | 同一应用反复崩溃**只替换同一条**通知，不堆积 |
| 自动抑制（熔断） | 温和风暴 | 30 秒内 ≥3 次 → **force-stop + 清通知 + 弹「已自动暂停」说明通知** |
| 手动恢复 | 点说明通知里的「恢复通知」 | 计数归零 |
| 自动恢复 | 什么都不做 | 平静 **3 分钟**自动解除 |

> ⚠️ **若点完「后台崩溃」不按 Home 键**，3 秒到点时页面仍在最前 → 这次仍会被记成**前台崩溃**。

## 多进程残留测试（`:worker` 进程）

本 App 除主进程外还带一个 `:worker` 进程（`WorkerService`，Manifest 里 `android:process=":worker"`），
用来验证「一个进程崩溃后，同应用的其它进程是否残留」。

`ps -A | grep com.vstory.test.crashstorm` 在运行时应看到两行：

```
com.vstory.test.crashstorm          ← 主进程，跑本页面
com.vstory.test.crashstorm:worker   ← 子进程，WorkerService
```

| 验证项 | 用什么按钮 | 期望结果 |
|---|---|---|
| 建立多进程态 | 拉起 worker | `ps` 里出现 `:worker` |
| 主进程崩 → 子进程残留 | 主进程崩溃 | 主进程消失；`:worker` 是否存活即是否「残留」 |
| 子进程崩 → 主进程受影响 | worker 崩溃 | 仅 `:worker` 消失，主进程应不受影响 |
| 存活模式对比 | 切换 worker 存活模式 | `STICKY`：子进程被杀后可能被系统重拉（**pid 变**）；`NOT_STICKY`：死透（**pid 消失**） |

设计约束（决定了测试是否有意义）：

- `WorkerService.onBind` **恒返回 null** —— 刻意不建 binder 连接。一旦与主进程有连接，主进程死时
  AMS 会连带清理该服务，就测不到「残留」了。
- 判读残留前**必须先确认当前存活模式**：`STICKY` 下 pid 变化可能是被系统重新拉起，不等于残留。


## ⚠️ 使用警告：完整风暴曾导致系统死机

「完整风暴（连崩 12 次）」由真机实证**曾把系统的 `crash_dump` 链路拖垮，导致设备死机**。

- **日常验证一律用「温和风暴（连崩 4 次）」**：第 3 次即触发熔断 `force-stop`，已足够闭环。
- **完整风暴仅供压力场景，高风险，慎用。**

## 工程信息

| 项 | 值 |
|---|---|
| 包名 / applicationId | `com.vstory.test.crashstorm` |
| 应用名 | CrashStorm |
| versionCode / versionName | CI 构建时由 `github.run_number` 注入：`run_number + 1000` / `1.1+ci.<run_number>`（保证覆盖安装不被拒）；本地不带 `-PciRun` 时为 2 / 1.1 |
| minSdk / targetSdk / compileSdk | 26 / **31** / 37 |
| 语言 | Java 17（无 AndroidX 依赖） |
| 进程 | 主进程 + `:worker` |

**为什么 `targetSdk` 钉在 31**：规避通知的**运行时权限**，并让 `SCHEDULE_EXACT_ALARM` 精确闹钟**自动授予**——
自复活风暴靠精确闹钟驱动，这两点在更高 targetSdk 上会变成需要用户手动授权的额外前置。

**实现要点**：崩溃 = 线程抛 `RuntimeException`（走 `system_server` 的 AppErrors 崩溃路径；
主进程与 `:worker` 都抛在主线程）；风暴 = 崩溃前先排好一个 `setExactAndAllowWhileIdle` 闹钟，
崩溃后由该闹钟把 Activity 拉起来，等 `onWindowFocusChanged` 确认界面真正可见再崩下一次
（保证走「前台崩溃」路径）。

## 构建

**走 CI，本地不编译**：push 到本分支（或手动 dispatch）即触发 `.github/workflows/build-crashstorm.yml`，
产出带固定密钥签名的 release 包，发到固定 tag `crashstorm-ci` 的滚动测试页（保留最新 5 个）。

```bash
git push origin crashstorm-test
```

签名密钥与主应用共用同一套 GitHub Secrets（`KEY_BASE64` / `SIGNING_*`），因此两个包签名一致、可互相覆盖安装。
⚠️ 若设备上装过**别的签名**的 CrashStorm，需先卸载再装 CI 包（版本号虽递增，签名不同仍会拒装）。

## 注意事项

- 这是**测试工具**，不随 AppErrorNotify 发布，也不进模块的 Release 产物。
- 崩溃日志与通知的观察入口在 **AppErrorNotify 模块本体**（其详情页），本 App 只负责「制造崩溃」。
- 请勿在**日常使用的主力机**上跑完整风暴。
