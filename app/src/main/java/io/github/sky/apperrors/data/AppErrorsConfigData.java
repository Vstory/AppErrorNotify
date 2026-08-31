/*
 * AppErrorsTracking (api102 重构版) - 应用配置模版存储控制类 (Java 化)
 */
package io.github.sky.apperrors.data;

import io.github.sky.apperrors.data.enums.AppErrorsConfigType;

import java.util.HashSet;
import java.util.Set;

/**
 * 应用配置模版存储控制类
 */
public class AppErrorsConfigData {

    /** 显示错误对话框键值名称 */
    private static final String SHOW_ERRORS_DIALOG_APPS = "_show_errors_dialog_apps";
    /** 推送错误通知键值名称 */
    private static final String SHOW_ERRORS_NOTIFY_APPS = "_show_errors_notify_apps";
    /** 显示错误 Toast 键值名称 */
    private static final String SHOW_ERRORS_TOAST_APPS = "_show_errors_toast_apps";
    /** 什么也不显示键值名称 */
    private static final String SHOW_ERRORS_NOTHING_APPS = "_show_errors_nothing_apps";

    /** 显示错误对话框的 APP 包名数组 */
    private static Set<String> showDialogApps = new HashSet<>();
    /** 推送错误通知的 APP 包名数组 */
    private static Set<String> showNotifyApps = new HashSet<>();
    /** 显示错误 Toast 的 APP 包名数组 */
    private static Set<String> showToastApps = new HashSet<>();
    /** 什么也不显示的 APP 包名数组 */
    private static Set<String> showNothingApps = new HashSet<>();

    /** 刷新存储控制类 */
    public static void refresh() {
        showDialogApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_DIALOG_APPS));
        showNotifyApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_NOTIFY_APPS));
        showToastApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_TOAST_APPS));
        showNothingApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_NOTHING_APPS));
    }

    /**
     * 一次性迁移：旧"显示错误对话框"配置 → 跟随全局。
     * 本模块 v1.9(42) 起为纯通知版，DIALOG 能力已移除；
     * 升级后首次打开模块 UI（service 连接成功、RemotePreferences 可写）时调用，
     * 清空废弃的 showDialogApps → 旧配置自动变为未配置（GLOBAL 跟随全局）。
     * 幂等：set 为空时直接跳过。
     * ⚠️ 必须在 UI 进程调用（system_server 侧 RemotePreferences 只读，写无效）
     */
    public static void migrateDialogConfigToGlobalIfNeeded() {
        refresh();
        if (!showDialogApps.isEmpty()) {
            showDialogApps.clear();
            saveAllData();
        }
        // 全局显示类型若仍为废弃的 DIALOG → 重置为通知（纯通知版默认）
        if (ConfigData.getGlobalShowErrorsType() == AppErrorsConfigType.DIALOG.ordinal()) {
            ConfigData.setGlobalShowErrorsType(AppErrorsConfigType.NOTIFY.ordinal());
        }
    }

    /**
     * 获取当前 APP 显示错误的类型是否为 [type]
     * @param type 当前类型
     * @param packageName 当前 APP 包名 - 不填为全局配置
     */
    public static boolean isAppShowingType(AppErrorsConfigType type, String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()) {
            switch (type) {
                case GLOBAL:
                    return !showDialogApps.contains(packageName) && !showNotifyApps.contains(packageName)
                            && !showToastApps.contains(packageName) && !showNothingApps.contains(packageName);
                case DIALOG: return showDialogApps.contains(packageName);
                case NOTIFY: return showNotifyApps.contains(packageName);
                case TOAST: return showToastApps.contains(packageName);
                case NOTHING: return showNothingApps.contains(packageName);
            }
            return false;
        }
        return ConfigData.getGlobalShowErrorsType() == type.ordinal();
    }

    /**
     * 写入当前 APP 显示错误的类型
     * @throws IllegalStateException 如果 packageName 为空 type 为 GLOBAL
     */
    public static void putAppShowingType(AppErrorsConfigType type, String packageName) {
        if ((packageName == null || packageName.trim().isEmpty()) && type == AppErrorsConfigType.GLOBAL)
            throw new IllegalStateException("You can't still specify the \"follow global config\" type when saving the global config");
        if (packageName != null && !packageName.trim().isEmpty()) {
            switch (type) {
                case GLOBAL:
                    showDialogApps.remove(packageName); showNotifyApps.remove(packageName);
                    showToastApps.remove(packageName); showNothingApps.remove(packageName);
                    break;
                case DIALOG:
                    showDialogApps.add(packageName); showNotifyApps.remove(packageName);
                    showToastApps.remove(packageName); showNothingApps.remove(packageName);
                    break;
                case NOTIFY:
                    showDialogApps.remove(packageName); showNotifyApps.add(packageName);
                    showToastApps.remove(packageName); showNothingApps.remove(packageName);
                    break;
                case TOAST:
                    showDialogApps.remove(packageName); showNotifyApps.remove(packageName);
                    showToastApps.add(packageName); showNothingApps.remove(packageName);
                    break;
                case NOTHING:
                    showDialogApps.remove(packageName); showNotifyApps.remove(packageName);
                    showToastApps.remove(packageName); showNothingApps.add(packageName);
                    break;
            }
            saveAllData();
        } else {
            ConfigData.setGlobalShowErrorsType(type.ordinal());
        }
    }

    private static void saveAllData() {
        ConfigData.putStringSet(SHOW_ERRORS_DIALOG_APPS, showDialogApps);
        ConfigData.putStringSet(SHOW_ERRORS_NOTIFY_APPS, showNotifyApps);
        ConfigData.putStringSet(SHOW_ERRORS_TOAST_APPS, showToastApps);
        ConfigData.putStringSet(SHOW_ERRORS_NOTHING_APPS, showNothingApps);
    }

    // ===== 配置变更广播（UI → system_server：保存后通知 hook 立即 refresh，避免等下次崩溃才生效） =====

    /** 广播 action：UI 保存应用配置模板后通知 system_server 刷新 */
    public static final String ACTION_CONFIG_CHANGED = "io.github.sky.apperrors.action.CONFIG_CHANGED";

    /** UI 保存配置后调用：广播 → system_server 收到后立即 AppErrorsConfigData.refresh() */
    public static void notifyConfigChanged(android.content.Context context) {
        try {
            context.sendBroadcast(new android.content.Intent(ACTION_CONFIG_CHANGED));
        } catch (Throwable ignored) {
        }
    }

    private AppErrorsConfigData() {}
}
