# AppErrorsTracking (Java Port)

为 APP 的崩溃对话框增加更多功能，修复被定制 ROM 删除的崩溃对话框，给 Android 开发者最好的体验。

> ⚠️ 本项目是 **AppErrorsTracking 的 Java 化改造版**（Kotlin → 纯 Java），基于现代 **libxposed API 102**。
> 原项目（Kotlin 版，含完整历史）见：**https://github.com/KitsunePie/AppErrorsTracking**

## 分支说明

| 分支 | 内容 |
|------|------|
| `master` | ✅ 当前 Java 版（纯 Java / 0 .kt，libxposed api102） |
| `kotlin` | 原 Kotlin 版归档（module-app / demo-app 结构） |

## 功能

- 拦截系统崩溃 / ANR 对话框，自定义展示
- 错误记录 + 静默列表 + 通知 + 快捷操作
- 按应用维度配置，支持桌面图标显隐等

## 技术栈

- 纯 Java（0 Kotlin / 44 Java 文件）+ ViewBinding
- AGP 9.2.1 + Gradle 9.5
- libxposed API 102（`io.github.libxposed:api/service` 本地 jar）
- 作用域：`system_server`（scope.list = `android`）

## 构建

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

## 环境要求

- minSdk 26 / targetSdk 37
- LSPosed 1.9+（支持 api102）

## License

继承原项目开源协议（详见原仓库 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)）。
