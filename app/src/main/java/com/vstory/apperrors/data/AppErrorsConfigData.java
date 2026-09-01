
package com.vstory.apperrors.data;

import com.vstory.apperrors.data.enums.AppErrorsConfigType;

import java.util.HashSet;
import java.util.Set;


public class AppErrorsConfigData {

    
    private static final String SHOW_ERRORS_DIALOG_APPS = "_show_errors_dialog_apps";
    
    private static final String SHOW_ERRORS_NOTIFY_APPS = "_show_errors_notify_apps";
    
    private static final String SHOW_ERRORS_TOAST_APPS = "_show_errors_toast_apps";
    
    private static final String SHOW_ERRORS_NOTHING_APPS = "_show_errors_nothing_apps";

    
    private static Set<String> showDialogApps = new HashSet<>();
    
    private static Set<String> showNotifyApps = new HashSet<>();
    
    private static Set<String> showToastApps = new HashSet<>();
    
    private static Set<String> showNothingApps = new HashSet<>();

    
    public static void refresh() {
        showDialogApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_DIALOG_APPS));
        showNotifyApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_NOTIFY_APPS));
        showToastApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_TOAST_APPS));
        showNothingApps = new HashSet<>(ConfigData.getStringSet(SHOW_ERRORS_NOTHING_APPS));
    }

    
    public static void migrateDialogConfigToGlobalIfNeeded() {
        refresh();
        if (!showDialogApps.isEmpty()) {
            showDialogApps.clear();
            saveAllData();
        }
        
        if (ConfigData.getGlobalShowErrorsType() == AppErrorsConfigType.DIALOG.ordinal()) {
            ConfigData.setGlobalShowErrorsType(AppErrorsConfigType.NOTIFY.ordinal());
        }
    }

    
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

    

    
    public static final String ACTION_CONFIG_CHANGED = "com.vstory.apperrors.action.CONFIG_CHANGED";

    
    public static void notifyConfigChanged(android.content.Context context) {
        try {
            context.sendBroadcast(new android.content.Intent(ACTION_CONFIG_CHANGED));
        } catch (Throwable ignored) {
        }
    }

    private AppErrorsConfigData() {}
}
