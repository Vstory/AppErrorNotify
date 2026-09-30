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

## ⚠️ 使用警告：完整风暴曾导致系统死机

「完整风暴（连崩 12 次）」由真机实证**曾把系统的 `crash_dump` 链路拖垮，导致设备死机**。

- **日常验证一律用「温和风暴（连崩 4 次）」**：第 3 次即触发熔断 `force-stop`，已足够闭环。
- **完整风暴仅供压力场景，高风险，慎用。**

## 工程信息

| 项 | 值 |
|---|---|
| 包名 / applicationId | `com.vstory.test.crashstorm` |
| 应用名 | CrashStorm |
| versionCode / versionName | 2 / 1.0 |
| minSdk / targetSdk / compileSdk | 26 / **31** / 36 |
| 语言 | Java 17（无 AndroidX 依赖） |

**为什么 `targetSdk` 钉在 31**：规避通知的**运行时权限**，并让 `SCHEDULE_EXACT_ALARM` 精确闹钟**自动授予**——
自复活风暴靠精确闹钟驱动，这两点在更高 targetSdk 上会变成需要用户手动授权的额外前置。

**实现要点**：崩溃 = 主线程抛 `RuntimeException`（走 `system_server` 的 AppErrors 前台崩溃路径）；
风暴 = 崩溃前先排好一个 `setExactAndAllowWhileIdle` 闹钟，崩溃后由该闹钟把 Activity 拉起来，
等 `onWindowFocusChanged` 确认界面真正可见再崩下一次（保证走「前台崩溃」路径）。

## 构建

```bash
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/
```

无第三方依赖、无 AndroidX，用标准 Android SDK 即可离线构建（需 JDK 17）。

## 注意事项

- 这是**测试工具**，不随 AppErrorNotify 发布，也不进模块的 Release 产物。
- 崩溃日志与通知的观察入口在 **AppErrorNotify 模块本体**（其详情页），本 App 只负责「制造崩溃」。
- 请勿在**日常使用的主力机**上跑完整风暴。
