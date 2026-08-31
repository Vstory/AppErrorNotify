/*
 * AppErrorsTracking (api102 重构版) - 全局配置存储控制类 (Java 化)
 */
package io.github.sky.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import io.github.sky.apperrors.data.enums.AppErrorsConfigType;

import java.util.Set;

/**
 * 全局配置存储控制类（api102 RemotePreferences，system_server 与模块 UI 同源）
 */
public class ConfigData {

    /** RemotePreferences 组名 */
    public static final String PREFS_GROUP = "app_errors_config";

    /** UI 本地 fallback 文件名 */
    private static final String LOCAL_PREFS_NAME = "io.github.sky.apperrors_preferences";

    // ===== 键值名称（与原版一致） =====
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

    /** 远程偏好（system_server / service 连接后的 UI） */
    private static SharedPreferences remotePrefs;

    /** UI 本地上下文（fallback） */
    private static Context uiContext;

    /** 当前生效存储 */
    private static SharedPreferences current() {
        if (remotePrefs != null) return remotePrefs;
        if (uiContext != null) return uiContext.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        return null;
    }

    /** system_server 初始化（RemotePreferences） */
    public static void init(SharedPreferences prefs) {
        remotePrefs = prefs;
    }

    /** 模块 UI 初始化（service 连接前 fallback 本地） */
    public static void init(Context context) {
        uiContext = context.getApplicationContext();
    }

    /** 模块 UI 连接 XposedService 后切换到远程存储 */
    public static void initService(io.github.libxposed.service.XposedService service) {
        remotePrefs = service.getRemotePreferences(PREFS_GROUP);
    }

    /** 刷新存储控制类（直读模式，占位兼容） */
    public static void refresh() {
    }

    // ===== 底层键值操作（internal） =====
    public static Set<String> getStringSet(String key) {
        SharedPreferences p = current();
        return p != null ? p.getStringSet(key, new java.util.HashSet<String>()) : new java.util.HashSet<String>();
    }

    public static void putStringSet(String key, Set<String> value) {
        SharedPreferences p = current();
        if (p != null) {
            try { p.edit().putStringSet(key, value).apply(); }
            catch (Throwable ignored) { /* system_server 只读 RemotePreferences：忽略 */ }
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
            catch (Throwable ignored) { /* system_server 只读 RemotePreferences：忽略 */ }
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
            catch (Throwable ignored) { /* system_server 只读 RemotePreferences：忽略 */ }
        }
    }

    // ===== 属性（UI 与 Host 共用；Kotlin 属性语法可映射） =====
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

    /** 全局错误显示类型（AppErrorsConfigType.ordinal；默认通知——本模块为通知版定位） */
    public static int getGlobalShowErrorsType() {
        return getInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, AppErrorsConfigType.NOTIFY.ordinal());
    }
    public static void setGlobalShowErrorsType(int value) {
        putInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, value);
    }

    /** 通知「忽略该应用」按钮：true=忽略直到重启（默认），false=忽略直到解锁 */
    public static boolean isMuteIgnoreUntilReboot() {
        return getBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, true);
    }
    public static void setMuteIgnoreUntilReboot(boolean value) {
        putBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, value);
    }

    private ConfigData() {}
}
