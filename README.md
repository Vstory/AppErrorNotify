# AppErrorNotify

拦截应用崩溃 / ANR，以**系统通知**展示并记录历史的 Android 异常跟踪模块。

基于上游 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)，用 **libxposed API 102** 纯 Java 重构（原版 Kotlin + YukiHookAPI）。

> ⚠️ **包名变更**：自 v1.15 起包名改为 `io.github.vstory.apperrors`（全新应用，与旧包名 `com.vstory.apperrors` 不共享数据）。**升级用户需在 LSPosed 中重新启用模块**并勾选 **system_server**，旧包名版本可卸载。

## 安装

1. [LSPosed](https://github.com/LSPosed/LSPosed) 启用本模块，勾选 **system_server**
2. 重启系统生效
3. 触发应用崩溃 → 收到「异常跟踪」通知

## License

**GNU AGPL-3.0**（上游 Copyright (C) 2017 Fankes Studio；2026 Vstory Java 重构）。详见 [LICENSE](LICENSE)。
