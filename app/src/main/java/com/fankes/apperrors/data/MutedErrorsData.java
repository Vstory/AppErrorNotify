/*
 * AppErrorsTracking (api102 重构版) - 已忽略异常 APP 状态存储控制类 (Java 化)
 */
package com.fankes.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fankes.apperrors.bean.MutedErrorsAppBean;
import com.fankes.apperrors.hook.HookEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 已忽略异常 APP 状态存储控制类
 */
public class MutedErrorsData {

    /** RemotePreferences 组名 */
    public static final String PREFS_GROUP = "app_errors_mute";

    /** 本地 fallback 文件名 */
    private static final String LOCAL_PREFS_NAME = "com.fankes.apperrors_mute";

    /** 直到重新解锁 */
    private static final String KEY_UNTIL_UNLOCK = "until_unlock";
    /** 直到重新启动 */
    private static final String KEY_UNTIL_RESTART = "until_restart";

    /** 当前存储 */
    private static SharedPreferences prefs;

    /** system_server 内存镜像（性能，hook 热路径用） */
    private static Set<String> mutedErrorsIfUnlockApps = new HashSet<>();
    private static Set<String> mutedErrorsIfRestartApps = new HashSet<>();

    private static void log(String msg) {
        if (HookEntry.isReady()) HookEntry.getInstance().log(Log.INFO, HookEntry.TAG, msg, null);
    }

    /** system_server 初始化（RemotePreferences） */
    public static void init(SharedPreferences prefs) {
        MutedErrorsData.prefs = prefs;
        mutedErrorsIfUnlockApps = new HashSet<>(prefs != null && prefs.getStringSet(KEY_UNTIL_UNLOCK, null) != null
                ? prefs.getStringSet(KEY_UNTIL_UNLOCK, null) : new HashSet<String>());
        mutedErrorsIfRestartApps = new HashSet<>(prefs != null && prefs.getStringSet(KEY_UNTIL_RESTART, null) != null
                ? prefs.getStringSet(KEY_UNTIL_RESTART, null) : new HashSet<String>());
    }

    /** 模块 UI 初始化（本地 fallback） */
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    /** 模块 UI 连接 XposedService 后切换到远程存储 */
    public static void initService(io.github.libxposed.service.XposedService service) {
        prefs = service.getRemotePreferences(PREFS_GROUP);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    private static void persistUnlock() {
        if (prefs != null) prefs.edit().putStringSet(KEY_UNTIL_UNLOCK, mutedErrorsIfUnlockApps).apply();
    }
    private static void persistRestart() {
        if (prefs != null) prefs.edit().putStringSet(KEY_UNTIL_RESTART, mutedErrorsIfRestartApps).apply();
    }

    /** 忽略直到解锁 */
    public static void mutedErrorsIfUnlock(String packageName) {
        mutedErrorsIfUnlockApps.add(packageName);
        persistUnlock();
        log("Muted \"" + packageName + "\" until unlocks");
    }

    /** 忽略直到重启 */
    public static void mutedErrorsIfRestart(String packageName) {
        mutedErrorsIfRestartApps.add(packageName);
        persistRestart();
        log("Muted \"" + packageName + "\" until restarts");
    }

    /** 取消指定忽略 */
    public static void unmuteErrorsApp(MutedErrorsAppBean bean) {
        switch (bean.type) {
            case UNTIL_UNLOCKS:
                mutedErrorsIfUnlockApps.remove(bean.packageName);
                persistUnlock();
                log("Unmuted if unlocks errors app \"" + bean.packageName + "\"");
                break;
            case UNTIL_REBOOTS:
                mutedErrorsIfRestartApps.remove(bean.packageName);
                persistRestart();
                log("Unmuted if restarts errors app \"" + bean.packageName + "\"");
                break;
        }
    }

    /** 取消全部忽略 */
    public static void unmuteAllErrorsApps() {
        mutedErrorsIfUnlockApps.clear();
        mutedErrorsIfRestartApps.clear();
        persistUnlock();
        persistRestart();
        log("Unmute all errors apps --unlocks 0 --restarts 0");
    }

    /** 获取全部已忽略 APP 信息数组 */
    public static ArrayList<MutedErrorsAppBean> fetchMutedErrorsAppsData() {
        ArrayList<MutedErrorsAppBean> list = new ArrayList<>();
        if (!mutedErrorsIfUnlockApps.isEmpty())
            for (String pkg : mutedErrorsIfUnlockApps)
                list.add(new MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS, pkg));
        if (!mutedErrorsIfRestartApps.isEmpty())
            for (String pkg : mutedErrorsIfRestartApps)
                list.add(new MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_REBOOTS, pkg));
        return list;
    }

    /** 解锁后清空（USER_PRESENT 广播触发） */
    public static void clearIfUnlock() {
        mutedErrorsIfUnlockApps.clear();
        persistUnlock();
    }

    // ===== 属性访问（Kotlin 属性语法映射） =====
    public static Set<String> getMutedErrorsIfUnlockApps() { return mutedErrorsIfUnlockApps; }
    public static Set<String> getMutedErrorsIfRestartApps() { return mutedErrorsIfRestartApps; }

    private MutedErrorsData() {}
}
