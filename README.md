# AppErrorNotify（异常通知）

> **Android 异常跟踪 LSPosed 模块（通知版）**：拦截应用崩溃 / ANR，以 **系统通知** 方式展示异常，并记录历史、导出分享、按应用配置。基于 [AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking) 改造，基于现代 **libxposed API 102**。

> ⚠️ 本项目为 **AppErrorNotify（异常通知）**，是原 [AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking) 的 Java 改造版，以 **LSPosed API 102** 方式实现。原项目（Kotlin 版）见 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking)。

## 与原项目的差异

本项目基于原项目 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking) 改造，以 **LSPosed API 102** 方式实现，在部分细节上与原项目略有差异：

| 维度 | 原项目 | 本项目 |
|------|--------|--------|
| **异常展示方式** | 系统错误对话框（弹窗） | **系统通知**（点击进记录列表，通知按钮可查看详情 / 忽略该应用） |
| **原生弹窗处理** | 拦截并替换为自定义对话框 | 拦截原生错误弹窗，改为通知展示 |
| **上传策略** | 原项目策略 | 已调整 |
| **主界面设置** | 含对话框相关选项（防误触 / 始终显示重新打开 / Material3 风格） | 已移除，保留通知与记录相关选项 |
| **代码** | Kotlin | **纯 Java** 重构（0 .kt，44 Java 文件 + ViewBinding） |

## 功能特性

- **崩溃 / ANR 拦截**：拦截系统错误对话框（API ≤ R 直接取消，API > R 拦截 `ErrorDialogController`），不再弹出原生白色气泡
- **通知展示**：异常发生时发送系统通知（Channel `APPS_ERRORS`，高优先级），标题含应用名 + 崩溃原因，反复崩溃按 pid 覆盖同一条通知
- **通知快捷操作**：
  - 点击通知主体 → 打开异常历史记录列表
  - 通知 **「忽略该应用」** 按钮 → 忽略该应用异常（直到重启）
  - 通知 **「查看信息」** 按钮 → 直达该异常详情页
- **异常历史记录**：记录从模块启动以来的全部异常，支持查看、导出、分享（文本 / 文件）、清空
- **忽略列表**：手动忽略异常的应用管理，重启后自动清空
- **按应用配置模板**：为每个应用单独配置异常时的展示方式（通知 / Toast / 静默 / 跟随全局，纯通知版**不弹窗**）
- **显示过滤**：仅显示前台应用异常 / 仅显示主进程异常（后台进程仍记录但不打扰）
- **桌面图标显隐**：可在桌面隐藏 / 恢复模块图标
- **调试日志**：内置 Logger 调试日志界面，方便排查

## 技术栈

- 纯 Java（0 Kotlin）+ ViewBinding
- AGP 9.2.1 + Gradle 9.5
- libxposed API 102（`io.github.libxposed:api/service` 本地 jar）
- 作用域：`system_server`（scope.list = `android`）

## 构建

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 依赖 `android.jar`（android-34+）与本地 libxposed jar，构建环境需可访问 SDK。

## 环境要求

- minSdk 26 / targetSdk 37
- LSPosed 1.9+（支持 API 102）

## 安装使用

1. LSPosed 中启用模块，勾选系统框架（`system_server`）
2. 重启系统（或重启 system_server）使模块生效
3. 触发任意应用崩溃 → 收到"异常跟踪"通知

## 分支说明

| 分支 | 内容 |
|------|------|
| `master` | ✅ 当前 Java 版（纯 Java / 0 .kt，libxposed api102） |
| `kotlin` | 原 Kotlin 版归档（module-app / demo-app 结构） |

## License

本项目为 [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking) 的 Java 改造版（衍生作品），沿用原项目 **GNU AGPL-3.0** 开源协议。

- Copyright (C) 2017 Fankes Studio (qzmmcn@163.com)
- Copyright (C) 2026 Vstory (Java rework)

完整协议见 [LICENSE](LICENSE)。
