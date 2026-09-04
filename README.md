# AppErrorNotify

拦截应用崩溃 / ANR，以**系统通知**展示并记录历史的 Android 异常跟踪模块。

基于上游 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)，用 **libxposed API 102** 纯 Java 重构（原版 Kotlin + YukiHookAPI）。

## 🛡 崩溃风暴自动抑制（v1.15 新增）

当同一应用在 **30 秒内崩溃 ≥ 3 次**（崩溃风暴）时，模块自动**强制停止崩溃源应用**并**暂停其崩溃通知**——掐断带自复活闹钟的崩溃循环，避免 `crash_dump` 转储风暴拖垮系统（真机实测可致 zygote 卡死重启）。

- 抑制期间该应用崩溃静默记录，不打扰
- 说明通知带「恢复通知」按钮，可手动恢复
- 平静 3 分钟自动解除；抑制期间再崩自动顺延
- 被手动忽略（muted）的应用同样参与风暴熔断，仅静默提示

> ⚠️ **升级提醒**：v1.14.73 及更早版本**未处理崩溃风暴**，存在被高频崩溃应用拖垮系统的风险，请升级到 v1.15.74+。

## 相比上游新增

- **崩溃风暴自动抑制**：见上（上游无此能力）
- **纯通知版**：上游崩溃可按应用配置「对话框 / 通知 / Toast」展示；本模块去掉该配置与弹窗页，崩溃 / ANR 一律只发系统通知
- **调试日志 UI 开关**：新增调试页，调试日志由界面开关运行时控制，正式版亦可随时开关，无需重启
- **Native 崩溃堆栈高亮**：详情页定位 `signal` 行（如 `signal 11 (SIGSEGV)`）红色加粗，崩溃原因一眼可见
- **Markdown 代码块复制**：复制完整报告 / 堆栈自动 ``` 包裹，粘贴到聊天 / 文档保持格式
- **详情字段点击复制**：详情页每个信息字段点击即复制，点标签则连标签一并复制（原版只能整份复制）

其余（异常历史、忽略列表、按应用配置、前台 / 主进程过滤、隐藏图标、语言切换、磁贴）与上游一致。

## 版本

`v1.15.74`（versionCode 74 / versionName 1.15）· libxposed API 102 · minSdk 26 / targetSdk 37

> ⚠️ **包名变更**：自 v1.15 起包名改为 `io.github.vstory.apperrors`（全新应用，与旧包名 `com.vstory.apperrors` 不共享数据）。**升级用户需在 LSPosed 中重新启用模块**并勾选 **system_server**，旧包名版本可卸载。

## 安装

1. [LSPosed](https://github.com/LSPosed/LSPosed) 启用本模块，勾选 **system_server**
2. 重启系统生效
3. 触发应用崩溃 → 收到「异常跟踪」通知

## License

**GNU AGPL-3.0**（上游 Copyright (C) 2017 Fankes Studio；2026 Vstory Java 重构）。详见 [LICENSE](LICENSE)。
