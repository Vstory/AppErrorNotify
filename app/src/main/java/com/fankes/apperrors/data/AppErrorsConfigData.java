/*
 * AppErrorsTracking (api102 重构版) - 应用配置模版存储控制类 (Java 化)
 */
package com.fankes.apperrors.data;

import com.fankes.apperrors.data.enums.AppErrorsConfigType;

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

    private AppErrorsConfigData() {}
}
