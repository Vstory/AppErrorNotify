package io.github.vstory.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import io.github.vstory.apperrors.data.enums.AppErrorsConfigType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public static final long FIRST_BIND_WAIT_MS = 500;

    /** 只写镜像文件，不进 remote */
    private static final String KEY_MIRROR_SEEDED = "__mirror_seeded";

    /** 连续没等到绑定的次数（只在镜像文件） */
    private static final String KEY_BIND_WAIT_MISSES = "__bind_wait_misses";

    /** 失手上限：到它就不再等（无框架设备上止损） */
    private static final int BIND_WAIT_MAX_MISSES = 2;

    private static final Object BIND_LOCK = new Object();

    /** remote（权威） */
    private static volatile SharedPreferences remotePrefs;

    /** 本地镜像（service 未绑定时读它） */
    private static volatile SharedPreferences localPrefs;

    private static Context uiContext;

    /** 未绑定期间的写入，绑定后回灌 remote */
    private static final Map<String, Object> pendingWrites = new ConcurrentHashMap<>();

    /** 当前生效的读取源 */
    private static SharedPreferences reader() {
        SharedPreferences remote = remotePrefs;
        if (remote != null) return remote;
        return localPrefs;
    }

    public static void init(SharedPreferences prefs) {
        remotePrefs = prefs;
    }

    public static void init(Context context) {
        uiContext = context.getApplicationContext();
        localPrefs = uiContext.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        remotePrefs = null;
        AppErrorsConfigData.refresh();
    }

    public static void initService(io.github.libxposed.service.XposedService service) {
        remotePrefs = service.getRemotePreferences(PREFS_GROUP);
        flushPendingToRemote();
        syncRemoteIntoMirror();
        AppErrorsConfigData.refresh();
        resetBindWaitMisses();
        synchronized (BIND_LOCK) {
            BIND_LOCK.notifyAll();
        }
    }

    public static void refresh() {
    }


    public static boolean isMirrorSeeded() {
        SharedPreferences local = localPrefs;
        return local != null && local.getBoolean(KEY_MIRROR_SEEDED, false);
    }

    public static boolean shouldAwaitFirstBind() {
        SharedPreferences local = localPrefs;
        if (local == null) return false;
        return local.getInt(KEY_BIND_WAIT_MISSES, 0) < BIND_WAIT_MAX_MISSES;
    }

    public static void noteBindWaitMiss() {
        SharedPreferences local = localPrefs;
        if (local == null) return;
        try {
            local.edit()
                    .putInt(KEY_BIND_WAIT_MISSES, local.getInt(KEY_BIND_WAIT_MISSES, 0) + 1)
                    .commit();
        } catch (Throwable ignored) { /* 计数失败仅影响止损，忽略 */ }
    }

    private static void resetBindWaitMisses() {
        SharedPreferences local = localPrefs;
        if (local == null) return;
        try {
            local.edit().putInt(KEY_BIND_WAIT_MISSES, 0).commit();
        } catch (Throwable ignored) { /* 忽略 */ }
    }

    public static boolean awaitFirstBind(long timeoutMs) {
        if (remotePrefs != null) return true;
        // 用 nanoTime 而非 SystemClock：这段要能脱离 Android 跑离机自检
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1_000_000L;
        synchronized (BIND_LOCK) {
            while (remotePrefs == null) {
                long remainNanos = deadline - System.nanoTime();
                if (remainNanos <= 0) return false;
                try {
                    BIND_LOCK.wait(Math.max(1L, remainNanos / 1_000_000L), (int) (remainNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }


    private static void flushPendingToRemote() {
        SharedPreferences remote = remotePrefs;
        if (remote == null || pendingWrites.isEmpty()) return;
        try {
            SharedPreferences.Editor editor = remote.edit();
            for (Map.Entry<String, Object> entry : pendingWrites.entrySet())
                putObject(editor, entry.getKey(), entry.getValue());
            editor.commit();
            pendingWrites.clear();
        } catch (Throwable ignored) { /* 写失败：保留 pending */ }
    }

    private static void syncRemoteIntoMirror() {
        SharedPreferences local = localPrefs;
        SharedPreferences remote = remotePrefs;
        if (local == null || remote == null) return;
        try {
            Map<String, ?> all = remote.getAll();
            SharedPreferences.Editor editor = local.edit();
            for (Map.Entry<String, ?> entry : all.entrySet())
                putObject(editor, entry.getKey(), entry.getValue());
            // remote 为空也要标记已播种，否则每次冷启动都白等
            editor.putBoolean(KEY_MIRROR_SEEDED, true);
            editor.commit();
        } catch (Throwable ignored) { /* 镜像同步失败不影响主流程（下次绑定再试） */ }
    }

    @SuppressWarnings("unchecked")
    private static void putObject(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }


    private interface EditorOp {
        void put(SharedPreferences.Editor editor);
    }

    // 必须 commit()：RemotePreferences.apply() 会丢
    private static void write(String key, Object pendingValue, EditorOp op) {
        SharedPreferences local = localPrefs;
        if (local != null) {
            try {
                SharedPreferences.Editor editor = local.edit();
                op.put(editor);
                editor.commit();
            } catch (Throwable ignored) { /* 镜像写失败不影响主流程 */ }
        }
        SharedPreferences remote = remotePrefs;
        if (remote != null) {
            try {
                SharedPreferences.Editor editor = remote.edit();
                op.put(editor);
                editor.commit();
            } catch (Throwable ignored) { /* system_server 侧 RemotePreferences 只读：忽略 */ }
        } else if (local != null) {
            pendingWrites.put(key, pendingValue);
        }
    }

    public static Set<String> getStringSet(String key) {
        SharedPreferences p = reader();
        return p != null ? p.getStringSet(key, new java.util.HashSet<String>()) : new java.util.HashSet<String>();
    }

    public static void putStringSet(String key, Set<String> value) {
        if (value == null) return;
        final Set<String> copy = new java.util.HashSet<>(value);
        write(key, copy, editor -> editor.putStringSet(key, copy));
    }

    public static int getInt(String key, int def) {
        SharedPreferences p = reader();
        return p != null ? p.getInt(key, def) : def;
    }

    public static void putInt(String key, int value) {
        write(key, value, editor -> editor.putInt(key, value));
    }

    public static boolean getBoolean(String key, boolean def) {
        SharedPreferences p = reader();
        return p != null ? p.getBoolean(key, def) : def;
    }

    public static void putBoolean(String key, boolean value) {
        write(key, value, editor -> editor.putBoolean(key, value));
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

    /** 全局显示类型（默认通知） */
    public static int getGlobalShowErrorsType() {
        return getInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, AppErrorsConfigType.NOTIFY.ordinal());
    }
    public static void setGlobalShowErrorsType(int value) {
        putInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, value);
    }

    /** true = 忽略直到重启 */
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
