# AppErrorNotify

> **🌐 English · [简体中文](README.md)**

An Android crash tracking module (LSPosed / EDXposed) that intercepts app crashes, surfaces them as **system notifications**, records their history, and lets you configure the presentation per app.

Rebuilt in pure Java on [libxposed API 102](https://github.com/LSPosed/LSPosed) (upstream: [KitsunePie/AppErrorsTracking](https://github.com/KitsunePie/AppErrorsTracking), originally Kotlin + YukiHookAPI).

> ⚠️ **Package change**: since v1.15 the package name is `io.github.vstory.apperrors` (a brand new app; it does **not** share data with the old `com.vstory.apperrors`). **Upgrading users must re-enable the module in LSPosed**, check **system_server** in scope, and may uninstall the old package.

## ✨ Features

### 🔔 Crash Interception & Notifications

- **Crash interception**: the stock white error dialog no longer appears when an app crashes (the module takes over the `AppErrors` / `ErrorDialogController` chain).
- **System notification**: high-priority notification whose title includes the app name and crash reason. Tapping the notification itself opens the crash history; tapping its **"View info"** action jumps straight to that crash's detail page.
- **No spam**: repeated crashes of the same app replace the same notification instead of stacking up.

### 📋 Crash History

- Automatically records every crash since the module started (including Native crashes); newest is listed on top.
- **Group collapse**: identical crashes (same app + same exception + same throw location; Native by same abort message) are collapsed into one group shown as `×N`; tap to expand each record. Single records are not collapsed.
- **Only this app**: long-press a record → menu "Only show this app" to filter the history; tap the filter bar to cancel.
- Long-press menu: view detail / app info / only this app / delete this record.
- Toolbar buttons: **statistics report** / clear all / export all.

### 🧾 Crash Detail

- **App info**: app name / package / version / user ID / CPU ABI / targetSdk / minSdk.
- **Exception info**: exception message / exception type / throwing file / throwing class / throwing method / line number / record time.
- **Crash page**: where the crash happened — the foreground activity (e.g. `MainActivity`) for foreground crashes, or **"Background"** for background crashes, so you can tell whether it crashed while the user was actively using it.
- **Stack trace**: one-tap copy / export to file / share / print to logcat; optional no-wrap (horizontal scrolling) mode for selecting the whole trace.
- Every info row is **tap-to-copy** (label + value, or value only).

### 📊 Statistics Report (icon on the history page toolbar)

- Card-style report: crash count / total apps / apps involved / crash ratio / most-crashed app / most common crash type.

### 🎛 Home-screen Toggles

| Toggle / entry | Effect |
|---|---|
| Show errors only in foreground apps | When on, only foreground crashes notify you; background crashes are still recorded silently |
| Show errors only in main-process apps | Same, but limited to main-process crashes |
| Ignore behavior | Choose whether newly ignored (muted) apps stay muted until "device reboot" or "device unlock" |
| App config template | Set the crash presentation per app (below) |
| Muted apps | View and un-mute ignored apps |
| Quick Settings tile | System quick-settings tile that opens the crash history directly |
| Hide launcher icon | The module stays reachable from inside LSPosed / EDXposed |

### ⚙️ Per-app Presentation

Choose how each app's crashes are presented: **follow global / system notification / Toast / silent**. Silent apps are still recorded, just not shown.

### 🛡 Crash-storm Circuit Breaker (anti-reboot)

If the same app crashes repeatedly within a short window (crash storm), the module automatically **force-stops the app** and pauses its notifications to stop the storm from dragging down the system (system-wide crash loops can reboot the device); a notice with a one-tap resume is posted meanwhile. This protection is **always active** and is **not affected** by display filters / mutes.

### 🌐 Misc

- **UI language switch**: the UI follows the system language; when the system is Chinese, tap the home-screen title **5 times** to toggle between 中文 and English (crash notification text follows too).
- **Debugging**: built-in log viewer; the "Debug Setting" toggle controls verbosity (when on, crash-time stack details and other `[DEBUG]` logs are emitted, and can be printed to logcat / exported).

## ⚠️ Scope & Notes

- **Scope**: the module runs **only in the `system_server` process**; on first enable, check `system_server` in LSPosed and **reboot** (or restart `system_server`). Later upgrades can use LSPosed "Hot reload" to apply immediately.
- **Crash chain only**: the module hooks the Android `AppErrors` (app crash) chain — **no ANR handling**; it does not inject into normal app processes.
- **No dialogs**: presentation is a system notification (default) / Toast / silent — no dialog popups, no floating windows.
- **Circuit-breaker side effect**: during a crash storm the crashing app is auto force-stopped (even if muted) — that is the active protection against system reboot loops.
- Package-change note above; module data / notification history are isolated per package.

## Installation

1. Enable the module in [LSPosed](https://github.com/LSPosed/LSPosed), and check **system_server** in scope
2. Reboot (or restart `system_server`) to activate
3. Trigger an app crash → receive a crash notification; open the module to browse history and details

> Latest version & downloads: see [GitHub Releases](https://github.com/Vstory/AppErrorNotify/releases).

## License

**GNU AGPL-3.0** (upstream Copyright (C) 2017 Fankes Studio; 2026 Vstory Java rework). See [LICENSE](LICENSE).
