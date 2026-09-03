# AppErrorNotify

拦截应用崩溃 / ANR，以**系统通知**展示并记录历史的 Android 异常跟踪模块。

基于上游 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)，用 **libxposed API 102** 纯 Java 重构（原版 Kotlin + YukiHookAPI）。

## 相比上游新增

- **纯通知版**：上游崩溃可按应用配置「对话框 / 通知 / Toast」展示；本模块去掉该配置与弹窗页（`AppErrorsDisplayActivity`），崩溃 / ANR 一律只发系统通知
- **调试日志 UI 开关**：新增调试页（`DebugActivity`），调试日志由界面开关运行时控制，正式版亦可随时开关，无需重启
- **Native 崩溃堆栈高亮**：详情页定位 `signal` 行（如 `signal 11 (SIGSEGV)`）红色加粗，崩溃原因一眼可见
- **Markdown 代码块复制**：复制完整报告 / 堆栈自动 ``` 包裹，粘贴到聊天 / 文档保持格式
- **详情字段点击复制**：详情页每个信息字段（异常信息 / 类型 / 文件名 / 抛出类·方法·行号 / 时间 / 版本 / 包名）点击即复制，点标签则连标签一并复制（原版只能整份复制）

其余（异常历史、忽略列表、按应用配置、前台 / 主进程过滤、隐藏图标、语言切换、磁贴）与上游一致。

## 版本

`v1.14.73`（versionCode 73 / versionName 1.14）· libxposed API 102 · minSdk 26 / targetSdk 37

## 安装

1. [LSPosed](https://github.com/LSPosed/LSPosed) 启用本模块，勾选 **system_server**
2. 重启系统生效
3. 触发应用崩溃 → 收到「异常跟踪」通知

## License

**GNU AGPL-3.0**（上游 Copyright (C) 2017 Fankes Studio；2026 Vstory Java 重构）。详见 [LICENSE](LICENSE)。
