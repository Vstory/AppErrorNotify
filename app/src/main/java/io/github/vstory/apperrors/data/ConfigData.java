
package io.github.vstory.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import io.github.vstory.apperrors.data.enums.AppErrorsConfigType;

import java.util.Set;


public class ConfigData {

    
    public static final String PREFS_GROUP = "app_errors_config";

    
    private static final String LOCAL_PREFS_NAME = "io.github.vstory.apperrors_preferences";

    
    private static final String KEY_SHOW_DEVELOPER_NOTICE = "_show_developer_notice";
    private static final String KEY_ENABLE_MATERIAL3_STYLE_DIALOG = "_enable_material3_style_dialog";
    private static final String KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT = "_enable_only_show_errors_in_front";
    private static final String KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN = "_enable_only_show_errors_in_main";
    private static final String KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS = "_enable_always_shows_reopen_app_options";
    private static final String KEY_ENABLE_APP_CONFIG_TEMPLATE = "_enable_app_config_template";
    private static final String KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG = "_enable_prevent_misoperation_for_dialog";
    private static final String KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE = "_disable_auto_wrap_error_stack_trace";
    private static final String KEY_SHARE_WITH_FILE = "_share_with_file";
    private static final String KEY_GLOBAL_SHOW_ERRORS_TYPE = "_global_show_errors_type";
    private static final String KEY_MUTE_IGNORE_UNTIL_REBOOT = "_mute_ignore_until_reboot";
    private static final String KEY_ENABLE_DEBUG = "_enable_debug";

    
    private static SharedPreferences remotePrefs;

    
    private static Context uiContext;

    
    private static SharedPreferences current() {
        if (remotePrefs != null) return remotePrefs;
        if (uiContext != null) return uiContext.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        return null;
    }

    
    public static void init(SharedPreferences prefs) {
        remotePrefs = prefs;
    }

    
    public static void init(Context context) {
        uiContext = context.getApplicationContext();
        
        AppErrorsConfigData.refresh();
    }

    
    public static void initService(io.github.libxposed.service.XposedService service) {
        remotePrefs = service.getRemotePreferences(PREFS_GROUP);
        
        AppErrorsConfigData.refresh();
    }

    
    public static void refresh() {
    }

    
    public static Set<String> getStringSet(String key) {
        SharedPreferences p = current();
        return p != null ? p.getStringSet(key, new java.util.HashSet<String>()) : new java.util.HashSet<String>();
    }

    public static void putStringSet(String key, Set<String> value) {
        SharedPreferences p = current();
        if (p != null) {
            try { p.edit().putStringSet(key, value).apply(); }
            catch (Throwable ignored) {  }
        }
    }

    public static int getInt(String key, int def) {
        SharedPreferences p = current();
        return p != null ? p.getInt(key, def) : def;
    }

    public static void putInt(String key, int value) {
        SharedPreferences p = current();
        if (p != null) {
            try { p.edit().putInt(key, value).apply(); }
            catch (Throwable ignored) {  }
        }
    }

    public static boolean getBoolean(String key, boolean def) {
        SharedPreferences p = current();
        return p != null ? p.getBoolean(key, def) : def;
    }

    public static void putBoolean(String key, boolean value) {
        SharedPreferences p = current();
        if (p != null) {
            try { p.edit().putBoolean(key, value).apply(); }
            catch (Throwable ignored) {  }
        }
    }

    
    public static boolean isShowDeveloperNotice() {
        return getBoolean(KEY_SHOW_DEVELOPER_NOTICE, true);
    }
    public static void setShowDeveloperNotice(boolean value) {
        putBoolean(KEY_SHOW_DEVELOPER_NOTICE, value);
    }

    public static boolean isEnableMaterial3StyleAppErrorsDialog() {
        return getBoolean(KEY_ENABLE_MATERIAL3_STYLE_DIALOG, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
    }
    public static void setEnableMaterial3StyleAppErrorsDialog(boolean value) {
        putBoolean(KEY_ENABLE_MATERIAL3_STYLE_DIALOG, value);
    }

    public static boolean isEnableOnlyShowErrorsInFront() {
        return getBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT, false);
    }
    public static void setEnableOnlyShowErrorsInFront(boolean value) {
        putBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT, value);
    }

    public static boolean isEnableOnlyShowErrorsInMain() {
        return getBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN, false);
    }
    public static void setEnableOnlyShowErrorsInMain(boolean value) {
        putBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN, value);
    }

    public static boolean isEnableAlwaysShowsReopenAppOptions() {
        return getBoolean(KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS, false);
    }
    public static void setEnableAlwaysShowsReopenAppOptions(boolean value) {
        putBoolean(KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS, value);
    }

    public static boolean isEnableAppConfigTemplate() {
        return getBoolean(KEY_ENABLE_APP_CONFIG_TEMPLATE, false);
    }
    public static void setEnableAppConfigTemplate(boolean value) {
        putBoolean(KEY_ENABLE_APP_CONFIG_TEMPLATE, value);
    }

    public static boolean isEnablePreventMisoperation() {
        return getBoolean(KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG, false);
    }
    public static void setEnablePreventMisoperation(boolean value) {
        putBoolean(KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG, value);
    }

    public static boolean isDisableAutoWrapErrorStackTrace() {
        return getBoolean(KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE, false);
    }
    public static void setDisableAutoWrapErrorStackTrace(boolean value) {
        putBoolean(KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE, value);
    }

    public static boolean isShareWithFile() {
        return getBoolean(KEY_SHARE_WITH_FILE, false);
    }
    public static void setShareWithFile(boolean value) {
        putBoolean(KEY_SHARE_WITH_FILE, value);
    }

    
    public static int getGlobalShowErrorsType() {
        return getInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, AppErrorsConfigType.NOTIFY.ordinal());
    }
    public static void setGlobalShowErrorsType(int value) {
        putInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, value);
    }

    
    public static boolean isMuteIgnoreUntilReboot() {
        return getBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, true);
    }
    public static void setMuteIgnoreUntilReboot(boolean value) {
        putBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, value);
    }

    
    public static boolean isEnableDebug() {
        return getBoolean(KEY_ENABLE_DEBUG, false);
    }
    public static void setEnableDebug(boolean value) {
        putBoolean(KEY_ENABLE_DEBUG, value);
    }

    private ConfigData() {}
}
