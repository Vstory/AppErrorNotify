# AppErrorNotify（异常通知）

> **Android 异常跟踪 LSPosed 模块（通知版）**：拦截应用崩溃 / ANR，以 **系统通知** 方式展示异常，并记录历史、导出分享、按应用配置。基于现代 **libxposed API 102** 实现。

## 功能特性

- **崩溃 / ANR 拦截**：拦截系统错误对话框（API ≤ R 直接取消，API > R 拦截 `ErrorDialogController`），不再弹出原生白色气泡
- **通知展示**：异常发生时发送系统通知（Channel `APPS_ERRORS`，高优先级），标题含应用名 + 崩溃原因，反复崩溃按 pid 覆盖同一条通知
- **通知快捷操作**：
  - 点击通知主体 → 打开异常历史记录列表
  - 通知 **「忽略该应用」** 按钮 → 忽略该应用异常（直到重启）
  - 通知 **「查看信息」** 按钮 → 直达该异常详情页
- **异常历史记录**：记录从模块启动以来的全部异常，支持查看、导出、分享（文本 / 文件）、清空
- **异常详情信息卡**：展示崩溃应用包名、版本名(代码)、异常信息、类型、文件名、抛出类/方法、行号、记录时间；支持点击复制（标签+值 / 仅值）与堆栈单独复制
- **忽略列表**：手动忽略异常的应用管理，重启后自动清空
- **按应用配置模板**：为每个应用单独配置异常时的展示方式（通知 / Toast / 静默 / 跟随全局，纯通知版**不弹窗**）
- **显示过滤**：仅显示前台应用异常 / 仅显示主进程异常（后台进程仍记录但不打扰）
- **界面语言切换**：系统中文环境下，点击主界面标题文本 **5 次** → 界面语言中↔英互切；崩溃通知文案跟随切换后的语言
- **桌面图标显隐**：可在桌面隐藏 / 恢复模块图标
- **调试日志**：内置 Logger 调试日志界面，方便排查
- **快速设置磁贴**：提供 Quick Settings 快捷开关

## 技术栈

- **纯 Java** 实现（0 Kotlin 源码）+ ViewBinding
- libxposed **Modern API 102**（`api` 框架提供 compileOnly，`interface`/`service` 模块自带）
- AGP **9.2.1** + Gradle **9.5.0**
- 作用域：**`system_server`** 系统框架进程（scope.list = `android`）
- minSdk **26** / targetSdk **37** / compileSdk **37**

## 构建

> ⚠️ **构建必须使用 JDK17**（arm64 环境 `/usr/lib/jvm/java-21-openjdk-arm64` 为 JRE-only，无 javac，直接 `./gradlew` 会报错）。

推荐使用项目根目录的 **`build.sh`**（自动设置 JDK17，唯一构建入口）：

```bash
./build.sh          # 构建 release APK → app/build/outputs/apk/release/app-release.apk
./build.sh clean    # 清理构建产物
```

也可以直接调用 Gradle（需自行保证 JAVA_HOME 为 JDK17）：

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 依赖 `android.jar` 与本地 libxposed jar（`app/libs/libxposed/`），构建环境需可访问 Android SDK。

## 安装使用

1. **LSPosed** 中启用本模块，勾选系统框架（**`system_server`**）
2. 重启系统（或重启 `system_server`）使模块生效
3. 触发任意应用崩溃 → 收到「异常跟踪」通知

## 模块信息

- 包名：`io.github.sky.apperrors`
- 模块入口：`io.github.sky.apperrors.hook.HookEntry`（extends `XposedModule`）
- 当前版本：`v1.13(69)`（versionCode 69 / versionName 1.13）

## License

本项目为 **GNU AGPL-3.0** 开源协议。

- Copyright (C) 2026 Vstory (Java rework)

完整协议见 [LICENSE](LICENSE)。
