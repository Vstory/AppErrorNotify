/*
 * AppErrorsTracking (api102 重构版) - 已忽略异常 APP 状态存储控制类 (Java 化)
 */
package com.fankes.apperrors.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.fankes.apperrors.bean.MutedErrorsAppBean;

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
        // ⚠️ 不能用 HookEntry.log()：HookEntry 只在 system_server 注入时存在，
        //    UI 进程加载 HookEntry 类会 NoClassDefFoundError → 模块自身崩溃
        android.util.Log.i("AppErrorsTracking", msg != null ? msg : "");
    }

    /** system_server 初始化（内存模式——原版就只存 system_server 内存，不持久化！
     *  ⚠️ 不能用 RemotePreferences 写：system_server 里 getRemotePreferences() 返回只读实现
     *      （LSPosedRemotePreferences.edit() 抛 UnsupportedOperationException）
     *      原版 YukiHookAPI 的 mutedErrorsIfUnlockApps 就是纯内存 Set，解锁/重启后自动清空） */
    public static void init(SharedPreferences prefs) {
        MutedErrorsData.prefs = null;   // 内存模式：不持有 prefs，persist* 变为 no-op
        mutedErrorsIfUnlockApps = new HashSet<>();
        mutedErrorsIfRestartApps = new HashSet<>();
    }

    /** 模块 UI 初始化（本地 fallback，可写） */
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    /** 模块 UI 连接 XposedService 后切换到远程存储（UI 进程的 RemotePreferences 可写） */
    public static void initService(io.github.libxposed.service.XposedService service) {
        prefs = service.getRemotePreferences(PREFS_GROUP);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    private static void persistUnlock() {
        if (prefs != null) {
            try { prefs.edit().putStringSet(KEY_UNTIL_UNLOCK, mutedErrorsIfUnlockApps).apply(); }
            catch (Throwable ignored) { /* system_server 内存模式：忽略 */ }
        }
    }
    private static void persistRestart() {
        if (prefs != null) {
            try { prefs.edit().putStringSet(KEY_UNTIL_RESTART, mutedErrorsIfRestartApps).apply(); }
            catch (Throwable ignored) { /* system_server 内存模式：忽略 */ }
        }
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

    // ===== 广播通道（UI ↔ system_server 同步；system_server 侧为内存权威，UI 经广播读写） =====

    /** 广播 action 常量（与 system_server FrameworkHooker receiver 约定） */
    public static final String ACTION_GET_MUTED = "com.fankes.apperrors.action.GET_MUTED";
    public static final String ACTION_MUTED_RESULT = "com.fankes.apperrors.action.MUTED_RESULT";
    public static final String ACTION_MUTE_ERROR = "com.fankes.apperrors.action.MUTE_ERROR";
    public static final String ACTION_UNMUTE_ERROR = "com.fankes.apperrors.action.UNMUTE_ERROR";
    public static final String ACTION_UNMUTE_ALL = "com.fankes.apperrors.action.UNMUTE_ALL";
    public static final String EXTRA_MUTED = "muted";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_BEAN = "bean";

    /** UI 请求忽略某应用（通知按钮 / 展示页入口 → system_server 内存 Set 生效） */
    public static void requestMute(Context context, String packageName) {
        try {
            context.sendBroadcast(new Intent(ACTION_MUTE_ERROR).putExtra(EXTRA_PACKAGE, packageName));
        } catch (Throwable ignored) {
        }
    }

    /** UI 请求取消忽略指定应用 */
    public static void requestUnmute(Context context, MutedErrorsAppBean bean) {
        try {
            context.sendBroadcast(new Intent(ACTION_UNMUTE_ERROR).putExtra(EXTRA_BEAN, bean));
        } catch (Throwable ignored) {
        }
    }

    /** UI 请求取消全部忽略 */
    public static void requestUnmuteAll(Context context) {
        try {
            context.sendBroadcast(new Intent(ACTION_UNMUTE_ALL));
        } catch (Throwable ignored) {
        }
    }

    /**
     * UI 读取：经广播从 system_server 拉取忽略列表（system_server 侧是内存权威；UI 侧本地 Set 可能不同步）
     * @param context UI Context
     * @param callback 收到后的回调（可能在非主线程）
     */
    public static void fetchFromSystemServer(final Context context, final Runnable callback) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_MUTED_RESULT);
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                    }
                    Object extra = intent != null ? intent.getSerializableExtra(EXTRA_MUTED) : null;
                    if (extra instanceof ArrayList) {
                        ArrayList<?> raw = (ArrayList<?>) extra;
                        mutedErrorsIfUnlockApps = new HashSet<>();
                        mutedErrorsIfRestartApps = new HashSet<>();
                        for (Object o : raw) {
                            if (o instanceof MutedErrorsAppBean) {
                                MutedErrorsAppBean bean = (MutedErrorsAppBean) o;
                                if (bean.type == MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS)
                                    mutedErrorsIfUnlockApps.add(bean.packageName);
                                else
                                    mutedErrorsIfRestartApps.add(bean.packageName);
                            }
                        }
                    }
                    if (callback != null) callback.run();
                }
            };
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            context.sendBroadcast(new Intent(ACTION_GET_MUTED));
        } catch (Throwable t) {
            if (callback != null) callback.run();
        }
    }

    private MutedErrorsData() {}
}
