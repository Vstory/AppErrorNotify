# AppErrorNotify

拦截应用崩溃 / ANR，以**系统通知**展示并记录历史的 Android 异常跟踪模块。

基于上游 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)，用 **libxposed API 102** 纯 Java 重构（原版 Kotlin + YukiHookAPI）。

> ⚠️ **包名变更**：自 v1.15 起包名改为 `io.github.vstory.apperrors`（全新应用，与旧包名 `com.vstory.apperrors` 不共享数据）。**升级用户需在 LSPosed 中重新启用模块**并勾选 **system_server**，旧包名版本可卸载。

## 安装

1. [LSPosed](https://github.com/LSPosed/LSPosed) 启用本模块，勾选 **system_server**
2. 重启系统生效
3. 触发应用崩溃 → 收到「异常跟踪」通知

## 界面语言切换

模块默认跟随系统语言显示。系统为中文时，若希望模块界面**主动切换为英文**：

1. 打开模块主界面（LSPosed 中点击模块条目进入）
2. **连续点击顶部标题文本 5 次** → 界面语言在中文 / 英文间切换
3. 再次连点 5 次可切回

> 说明：切换仅作用于模块界面与**崩溃通知文案**（崩溃通知默认跟随系统语言，切换后跟随所选语言），不影响系统其它应用；该操作仅标题文本区域响应，不影响页面内其它点击。

## License

**GNU AGPL-3.0**（上游 Copyright (C) 2017 Fankes Studio；2026 Vstory Java 重构）。详见 [LICENSE](LICENSE)。
