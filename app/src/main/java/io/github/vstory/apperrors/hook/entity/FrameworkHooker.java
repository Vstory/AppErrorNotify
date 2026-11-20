package io.github.vstory.apperrors.hook.entity;

import android.app.ActivityManager;
import android.app.ApplicationErrorReport;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.util.ArrayMap;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;

import io.github.vstory.apperrors.R;
import io.github.vstory.apperrors.bean.AppErrorsInfoBean;
import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.AppErrorsRecordData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.data.MutedErrorsData;
import io.github.vstory.apperrors.data.enums.AppErrorsConfigType;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.ui.activity.errors.AppErrorsDetailActivity;
import io.github.vstory.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;
import io.github.vstory.apperrors.utils.tool.Debug;
import io.github.vstory.apperrors.utils.tool.ModuleLogger;
import io.github.vstory.apperrors.wrapper.BuildConfigWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedModule;

public class FrameworkHooker {

    private static final String TAG = "AppErrorNotify";

    private static XposedModule module;

    /** 必须用它加载 com.android.server.*，不能用 Class.forName 默认加载器 */
    private static ClassLoader systemServerClassLoader;

    private static Context hostContext;

    private static final List<HookHandle> hookHandles = new ArrayList<>();

    private static final List<String> hookSummary = new ArrayList<>();
    private static int hookOkCount = 0;
    private static int hookSkipCount = 0;
    private static int hookFailCount = 0;

    private static final int NOTIFICATION_ID = 0xAE;
    private static final int SUPPRESS_NOTICE_ID = 0xAF;
    private static final String ACTION_RESUME_SUPPRESS = "io.github.vstory.apperrors.action.RESUME_SUPPRESS";
    private static final String EXTRA_APP_NAME = "app_name";
    private static final long BURST_WINDOW_MS = 30_000L;
    private static final int BURST_LIMIT = 3;
    private static final long SUPPRESS_QUIET_MS = 3 * 60_000L;
    private static final Map<String, ArrayDeque<Long>> crashWindowTimes = new HashMap<>();
    private static final Map<String, Long> autoSuppressedUntil = new HashMap<>();

    private static void log(int level, Object msg, Throwable e) {
        if (module != null) {
            module.log(level, TAG, msg != null ? msg.toString() : "", e);
            String p;
            switch (level) {
                case Log.ERROR: p = "E"; break;
                case Log.INFO: p = "I"; break;
                case Log.DEBUG: p = "D"; break;
                default: p = "I"; break;
            }
            ModuleLogger.log(p, TAG, msg != null ? msg.toString() : "", e);
        }
    }

    private static void logError(Object msg, Throwable e) { log(Log.ERROR, msg, e); }
    private static void logError(Object msg) { log(Log.ERROR, msg, null); }
    private static void logInfo(Object msg) { log(Log.INFO, msg, null); }
    private static void logDebug(Object msg) {
        if (!ConfigData.isEnableDebug()) return;
        String m = msg != null ? msg.toString() : "";
        Debug.d(TAG, m);
        ModuleLogger.log("D", TAG, m, null);
    }

    // 崩溃 hook 跑在 AMS 持锁线程上，副作用必须投到专用后台线程，防 system_server 死锁
    private static volatile Handler bgHandler;

    private static Handler ensureBgHandler() {
        Handler h = bgHandler;
        if (h == null) {
            synchronized (FrameworkHooker.class) {
                if (bgHandler == null) {
                    try {
                        HandlerThread t = new HandlerThread("AppErrorNotify-Bg",
                                Process.THREAD_PRIORITY_BACKGROUND);
                        t.start();
                        bgHandler = new Handler(t.getLooper());
                    } catch (Throwable ignored) {
                        bgHandler = null;
                    }
                }
                h = bgHandler;
            }
        }
        return h;
    }

    private static void runOnBg(Runnable r) {
        try {
            Handler h = ensureBgHandler();
            if (h != null) {
                h.post(r);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            r.run();
        } catch (Throwable ignored) {
        }
    }


    private static Class<?> classOf(String... names) {
        for (String name : names) {
            if (systemServerClassLoader != null) {
                try {
                    return Class.forName(name, false, systemServerClassLoader);
                } catch (Throwable ignored) {
                }
            }
            try {
                ClassLoader boot = Class.forName("android.app.ActivityThread").getClassLoader();
                if (boot != null && boot != FrameworkHooker.class.getClassLoader()) {
                    try {
                        return Class.forName(name, false, boot);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Class<?> classOfRequired(String... names) {
        Class<?> c = classOf(names);
        if (c == null) throw new IllegalStateException("Class not found: " + names[0]);
        return c;
    }

    private static Object getField(Object owner, String name) {
        Class<?> clazz = owner != null ? owner.getClass() : null;
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void setField(Object owner, String name, Object value) {
        Class<?> clazz = owner != null ? owner.getClass() : null;
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                f.set(owner, value);
                return;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static Object invokeMethod(Object owner, String name, Object... args) {
        Class<?> clazz = owner != null ? owner.getClass() : null;
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        while (clazz != null) {
            try {
                Method m = clazz.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m.invoke(owner, args);
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method methodOf(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method methodOfParamCount(Class<?> clazz, String name, int paramCount) {
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Constructor<?> constructorOf(Class<?> clazz, Class<?>... paramTypes) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(paramTypes);
            c.setAccessible(true);
            return c;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Constructor<?> constructorOfParamCount(Class<?> clazz, int paramCount) {
        try {
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == paramCount) {
                    c.setAccessible(true);
                    return c;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    private static Class<?> UserControllerClass() { return classOf("com.android.server.am.UserController"); }
    private static Class<?> AppErrorsClass() { return classOfRequired("com.android.server.am.AppErrors"); }
    private static Class<?> AppErrorDialogClass() { return classOfRequired("com.android.server.am.AppErrorDialog"); }
    private static Class<?> AppErrorDialog_DataClass() { return classOfRequired("com.android.server.am.AppErrorDialog$Data"); }
    private static Class<?> ProcessRecordClass() { return classOfRequired("com.android.server.am.ProcessRecord"); }
    private static Class<?> ActivityManagerServiceClass() { return classOf("com.android.server.am.ActivityManagerService"); }
    private static Class<?> ActivityTaskManagerService_LocalServiceClass() { return classOf("com.android.server.wm.ActivityTaskManagerService$LocalService"); }
    private static Class<?> PackageListClass() { return classOf("com.android.server.am.ProcessRecord$PackageList", "com.android.server.am.PackageList"); }
    private static Class<?> ErrorDialogControllerClass() { return classOf("com.android.server.am.ProcessRecord$ErrorDialogController", "com.android.server.am.ErrorDialogController"); }

    /** 不能用 getResourcesForApplication：system_server 里资源表不全，getString 会抛 NotFoundException */
    private static android.content.res.Resources moduleResources() {
        Context context = hostContext;
        if (context == null) return null;
        ApplicationInfo ai = module != null ? module.getModuleApplicationInfo() : null;
        if (ai == null) return null;
        try {
            String apkPath = ai.sourceDir;
            if (apkPath == null) return null;
            android.content.res.AssetManager am = context.getResources().getAssets();
            try {
                android.content.res.AssetManager am2 = (android.content.res.AssetManager) android.content.res.AssetManager.class.getConstructor().newInstance();
                java.lang.reflect.Method addPath = android.content.res.AssetManager.class.getMethod("addAssetPath", String.class);
                int cookie = (Integer) addPath.invoke(am2, apkPath);
                if (cookie == 0) return null;
                android.content.res.Configuration cfg = new android.content.res.Configuration(context.getResources().getConfiguration());
                try {
                    int langMode = io.github.vstory.apperrors.utils.tool.LanguageData.getMode();
                    if (langMode == io.github.vstory.apperrors.utils.tool.LanguageData.MODE_ENGLISH) {
                        cfg.setLocale(java.util.Locale.ENGLISH);
                    } else if (langMode == io.github.vstory.apperrors.utils.tool.LanguageData.MODE_CHINESE) {
                        cfg.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);
                    }
                } catch (Throwable ignored) { /* 读偏好失败则跟随系统 */ }
                return new android.content.res.Resources(am2, context.getResources().getDisplayMetrics(), cfg);
            } catch (Throwable t) {
                return context.getPackageManager().getResourcesForApplication(ai);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Context getSystemServerContext() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread", false, systemServerClassLoader);
            java.lang.reflect.Method cur = atClass.getDeclaredMethod("currentActivityThread");
            Object at = cur.invoke(null);
            if (at == null) return null;
            java.lang.reflect.Field f = atClass.getDeclaredField("mSystemContext");
            f.setAccessible(true);
            Object ctx = f.get(at);
            return ctx instanceof Context ? (Context) ctx : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureHostContext(Context context) {
        if (hostContext != null) return;
        hostContext = context;
        if (!LocaleFactoryKt.isLocaleInitialized()) {
            LocaleFactoryKt.attachLocale(() -> {
                android.content.res.Resources r = moduleResources();
                return r != null ? r : context.getResources();
            });
        }
        try {
            AppErrorsRecordData.init(context);
        } catch (Throwable t) {
            logError("error records init failed\n  " + t, t);
        }
        registerLifecycle(context);
        registerErrorChannel(context);
    }

    static final String ACTION_GET_ERRORS = "io.github.vstory.apperrors.action.GET_ERRORS";
    static final String ACTION_ERRORS_RESULT = "io.github.vstory.apperrors.action.ERRORS_RESULT";
    static final String ACTION_GET_ERROR_DETAIL = "io.github.vstory.apperrors.action.GET_ERROR_DETAIL";
    static final String ACTION_ERROR_DETAIL_RESULT = "io.github.vstory.apperrors.action.ERROR_DETAIL_RESULT";
    static final String ACTION_GET_ERRORS_PAGE = "io.github.vstory.apperrors.action.GET_ERRORS_PAGE";
    static final String ACTION_ERRORS_PAGE_RESULT = "io.github.vstory.apperrors.action.ERRORS_PAGE_RESULT";
    static final String EXTRA_RECORD_KEY = "record_key";
    static final String EXTRA_OFFSET = "offset";
    static final String EXTRA_LIMIT = "limit";
    static final String ACTION_CLEAR_ERRORS = "io.github.vstory.apperrors.action.CLEAR_ERRORS";
    static final String ACTION_REMOVE_ERROR = "io.github.vstory.apperrors.action.REMOVE_ERROR";
    static final String EXTRA_ERRORS = "errors";
    static final String EXTRA_BEAN = "bean";
    static final String ACTION_GET_APP_TOTAL = "io.github.vstory.apperrors.action.GET_APP_TOTAL";
    static final String ACTION_APP_TOTAL_RESULT = "io.github.vstory.apperrors.action.APP_TOTAL_RESULT";
    static final String EXTRA_APP_TOTAL = "total_apps";
    static final String ACTION_GET_LOGS = ModuleLogger.ACTION_GET_LOGS;
    static final String ACTION_LOGS_RESULT = ModuleLogger.ACTION_LOGS_RESULT;
    static final String EXTRA_LOGS = ModuleLogger.EXTRA_LOGS;
    static final String ACTION_GET_MUTED = MutedErrorsData.ACTION_GET_MUTED;
    static final String ACTION_MUTED_RESULT = MutedErrorsData.ACTION_MUTED_RESULT;
    static final String ACTION_MUTE_ERROR = MutedErrorsData.ACTION_MUTE_ERROR;
    static final String ACTION_UNMUTE_ERROR = MutedErrorsData.ACTION_UNMUTE_ERROR;
    static final String ACTION_UNMUTE_ALL = MutedErrorsData.ACTION_UNMUTE_ALL;
    static final String EXTRA_MUTED = MutedErrorsData.EXTRA_MUTED;
    static final String EXTRA_PACKAGE = MutedErrorsData.EXTRA_PACKAGE;

    private static volatile boolean errorChannelRegistered = false;
    private static volatile BroadcastReceiver errorChannelReceiver;
    private static volatile BroadcastReceiver lifecycleReceiver;

    public static boolean isBroadcastChannelRegistered() {
        return errorChannelRegistered;
    }

    /**
     * 热重载前注销注册到 system_server 的 external callbacks。
     * API 102 要求 onHotReloading 返回 true 之前完成注销：不注销则旧 filter 与旧 onReceive
     * 永久驻留，改 ERROR_CHANNEL_ACTIONS 后热重载不生效，只能重启进程恢复。
     */
    public static void unregisterExternalCallbacks() {
        Context ctx = getSystemServerContext();
        // 先摘引用再注销，避免异常路径下把引用留在字段里导致后续重复注销失败
        BroadcastReceiver errorReceiver = errorChannelReceiver;
        errorChannelReceiver = null;
        BroadcastReceiver lifeReceiver = lifecycleReceiver;
        lifecycleReceiver = null;
        if (ctx != null) {
            if (errorReceiver != null) {
                try {
                    ctx.unregisterReceiver(errorReceiver);
                } catch (Throwable t) {
                    logDebug("error records channel: unregister skipped: " + t);
                }
            }
            if (lifeReceiver != null) {
                try {
                    ctx.unregisterReceiver(lifeReceiver);
                } catch (Throwable t) {
                    logDebug("unregister lifecycle skipped: " + t);
                }
            }
        }
        errorChannelRegistered = false;
        errorChannelRetry = 0;
        logDebug("external callbacks unregistered for hot reload");
    }

    /**
     * 注册模块到 system_server 的 external callbacks。
     * 冷启动走 ensureHostContext，热重载后 static 已复位且框架不重放 onSystemServerStarting，
     * 必须在新代码里重新调用一次，否则新 filter 永远不会装上。
     */
    public static void registerExternalCallbacks(Context context) {
        registerErrorChannel(context);
        registerLifecycle(context);
    }

    private static final int ERROR_CHANNEL_RETRY_MAX = 20;

    /**
     * 通道 action 清单，filter 注册与 onReceive 分支的唯一来源。
     * 2026-11-20 详情与分页两条通道漏在了 filter 之外，请求被 IntentFilter 静默丢弃：
     * onReceive 一次都没进，UI 只能干等超时，表现为详情白屏、堆栈永远为空。
     */
    private static final String[] ERROR_CHANNEL_ACTIONS = {
            ACTION_GET_ERRORS,
            ACTION_GET_ERRORS_PAGE,
            ACTION_GET_ERROR_DETAIL,
            ACTION_CLEAR_ERRORS,
            ACTION_REMOVE_ERROR,
            ACTION_GET_APP_TOTAL,
            ACTION_GET_LOGS,
            ACTION_GET_MUTED,
            ACTION_MUTE_ERROR,
            ACTION_UNMUTE_ERROR,
            ACTION_UNMUTE_ALL,
            ACTION_RESUME_SUPPRESS,
            AppErrorsConfigData.ACTION_CONFIG_CHANGED,
    };

    /**
     * 回传记录：摘要模式正常远小于事务上限；万一仍超限，对数切半重发，而不是让 system_server 抛
     * TransactionTooLargeException。切到 1 条仍失败即放弃（UI 侧有自己的超时兜底）。
     */
    private static void sendRecordsResult(Context ctx,
                                          java.util.List<io.github.vstory.apperrors.bean.AppErrorsInfoBean> payload) {
        java.util.List<io.github.vstory.apperrors.bean.AppErrorsInfoBean> data = payload;
        while (true) {
            try {
                Intent result = new Intent(ACTION_ERRORS_RESULT);
                result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                result.putExtra(EXTRA_ERRORS, new java.util.ArrayList<>(data));
                ctx.sendBroadcast(result);
                return;
            } catch (Throwable t) {
                if (!isTransactionTooLarge(t) || data.size() <= 1) {
                    logDebug("error records channel: send records failed \"--count " + data.size() + "\" --" + t);
                    return;
                }
                data = new java.util.ArrayList<>(data.subList(0, data.size() / 2));
                logDebug("error records channel: payload too large, retry with " + data.size() + " records");
            }
        }
    }

    /** Binder 事务超限会被 BinderProxy 包成 RuntimeException，必须沿 cause 链找 */
    private static boolean isTransactionTooLarge(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof android.os.TransactionTooLargeException) return true;
            String m = c.getMessage();
            if (m != null && m.contains("TransactionTooLarge")) return true;
        }
        return false;
    }

    public static void registerErrorChannel(Context context) {
        if (errorChannelRegistered) return;
        errorChannelRegistered = true;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    try {
                        if (ACTION_GET_ERRORS.equals(action)) {
                            // 热重载会分裂内存态，故每次从磁盘重读，否则 UI 会拉到空列表
                            // 只回摘要：完整 bean 的堆栈逐条可达几十 KB，200 条累加必撑爆 Binder 事务
                            // （system_server 里抛 TransactionTooLargeException 代价极大），完整记录走详情/分页
                            java.util.List<io.github.vstory.apperrors.bean.AppErrorsInfoBean> payload =
                                    AppErrorsRecordData.summaryListFromFiles();
                            sendRecordsResult(ctx, payload);
                            logDebug("error records channel: sent " + payload.size() + " summaries to UI");
                        } else if (ACTION_GET_ERRORS_PAGE.equals(action)) {
                            int offset = Math.max(intent.getIntExtra(EXTRA_OFFSET, 0), 0);
                            // 钳一页上限：UI 传 0 或巨值都会把整批记录塞进一次事务
                            int limit = Math.min(Math.max(intent.getIntExtra(EXTRA_LIMIT, 0), 1), 50);
                            java.util.List<io.github.vstory.apperrors.bean.AppErrorsInfoBean> page =
                                    AppErrorsRecordData.fullPageFromFiles(offset, limit);
                            Intent result = new Intent(ACTION_ERRORS_PAGE_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_ERRORS, new java.util.ArrayList<>(page));
                            ctx.sendBroadcast(result);
                            logDebug("error records channel: sent " + page.size() + " full records to UI --offset " + offset);
                        } else if (ACTION_GET_ERROR_DETAIL.equals(action)) {
                            String key = intent.getStringExtra(EXTRA_RECORD_KEY);
                            io.github.vstory.apperrors.bean.AppErrorsInfoBean bean = AppErrorsRecordData.findByKey(key);
                            Intent result = new Intent(ACTION_ERROR_DETAIL_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            if (bean != null) result.putExtra(EXTRA_BEAN, bean);
                            ctx.sendBroadcast(result);
                            logDebug("error records channel: sent record detail \""
                                    + key + "\" --found " + (bean != null));
                        } else if (ACTION_CLEAR_ERRORS.equals(action)) {
                            AppErrorsRecordData.clearAll();
                            logDebug("error records channel: cleared all records from UI");
                        } else if (ACTION_REMOVE_ERROR.equals(action)) {
                            String key = intent.getStringExtra(EXTRA_RECORD_KEY);
                            if (key != null && !key.isEmpty()) {
                                AppErrorsRecordData.removeByKey(key);
                            } else {
                                Object bean = FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_BEAN);
                                if (bean instanceof io.github.vstory.apperrors.bean.AppErrorsInfoBean)
                                    AppErrorsRecordData.remove((io.github.vstory.apperrors.bean.AppErrorsInfoBean) bean);
                            }
                            logDebug("error records channel: removed one record from UI");
                        } else if (ACTION_GET_APP_TOTAL.equals(action)) {
                            int total = 0;
                            try {
                                java.util.List<android.content.pm.PackageInfo> all =
                                        ctx.getPackageManager().getInstalledPackages(0);
                                for (android.content.pm.PackageInfo pi : all) {
                                    String pkg = pi != null ? pi.packageName : null;
                                    if (pkg != null && !pkg.equals("android")
                                            && !pkg.equals(BuildConfigWrapper.APPLICATION_ID)) total++;
                                }
                            } catch (Throwable ignored) {
                            }
                            Intent result = new Intent(ACTION_APP_TOTAL_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_APP_TOTAL, total);
                            ctx.sendBroadcast(result);
                            logDebug("Stats channel: sent total apps " + total + " to UI");
                        } else if (ACTION_GET_LOGS.equals(action)) {
                            Intent result = new Intent(ACTION_LOGS_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_LOGS, new java.util.ArrayList<>(ModuleLogger.allData()));
                            ctx.sendBroadcast(result);
                            logDebug("Log channel: sent " + ModuleLogger.allData().size() + " logs to UI");
                        } else if (ACTION_GET_MUTED.equals(action)) {
                            Intent result = new Intent(ACTION_MUTED_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_MUTED, MutedErrorsData.fetchMutedErrorsAppsData());
                            ctx.sendBroadcast(result);
                            logDebug("Mute channel: sent " + MutedErrorsData.fetchMutedErrorsAppsData().size() + " muted apps to UI");
                        } else if (ACTION_MUTE_ERROR.equals(action)) {
                            String pkg = intent.getStringExtra(EXTRA_PACKAGE);
                            if (pkg != null && !pkg.isEmpty()) {
                                if (ConfigData.isMuteIgnoreUntilReboot()) {
                                    MutedErrorsData.mutedErrorsIfRestart(pkg);
                                    logDebug("Mute channel: muted \"" + pkg + "\" until restart");
                                } else {
                                    MutedErrorsData.mutedErrorsIfUnlock(pkg);
                                    logDebug("Mute channel: muted \"" + pkg + "\" until unlock");
                                }
                            }
                        } else if (ACTION_UNMUTE_ERROR.equals(action)) {
                            Object bean = FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_BEAN);
                            if (bean instanceof io.github.vstory.apperrors.bean.MutedErrorsAppBean)
                                MutedErrorsData.unmuteErrorsApp((io.github.vstory.apperrors.bean.MutedErrorsAppBean) bean);
                            logDebug("Mute channel: unmuted one app");
                        } else if (ACTION_UNMUTE_ALL.equals(action)) {
                            MutedErrorsData.unmuteAllErrorsApps();
                            logDebug("Mute channel: unmuted all apps");
                        } else if (ACTION_RESUME_SUPPRESS.equals(action)) {
                            resumeSuppressIfSuppressed(ctx, intent.getStringExtra(EXTRA_PACKAGE),
                                    intent.getStringExtra(EXTRA_APP_NAME));
                        } else if (AppErrorsConfigData.ACTION_CONFIG_CHANGED.equals(action)) {
                            AppErrorsConfigData.refresh();
                            if (LocaleFactoryKt.isLocaleInitialized()) {
                                LocaleFactoryKt.attachLocale(() -> {
                                    android.content.res.Resources r = moduleResources();
                                    return r != null ? r : null;
                                });
                            }
                            logDebug("config sync channel: refreshed config template");
                        } else {
                            // filter 已按 ERROR_CHANNEL_ACTIONS 注册，走到这里说明某条分支没进清单
                            logError("error records channel: unhandled action " + action);
                        }
                    } catch (Throwable t) {
                        logError("error records channel: handle failed\n  " + t);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            for (String channelAction : ERROR_CHANNEL_ACTIONS) filter.addAction(channelAction);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            errorChannelReceiver = receiver;
            logDebug("error records channel: receiver registered");
            errorChannelRetry = 0;
        } catch (Throwable t) {
            errorChannelRegistered = false;
            // 启动早期系统服务未 publish，同步注册会 NPE，故延迟重试
            boolean amsNotReady = t instanceof NullPointerException
                    && String.valueOf(t.getMessage()).contains("registerReceiverWithFeature");
            if (amsNotReady) {
                logInfo("error records channel: register deferred (AMS not ready at boot), retry in 3s ("
                        + (errorChannelRetry + 1) + "/" + ERROR_CHANNEL_RETRY_MAX + ")");
            } else {
                logError("error records channel: register failed\n  " + t);
            }
            if (context != null && errorChannelRetry < ERROR_CHANNEL_RETRY_MAX) {
                errorChannelRetry++;
                final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.postDelayed(() -> registerErrorChannel(context), 3000L);
            }
        }
    }

    private static int errorChannelRetry = 0;

    private static void registerLifecycle(Context context) {
        if (lifecycleReceiver != null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    MutedErrorsData.clearIfUnlock();
                    logDebug("User present, cleared muted errors until unlocks");
                } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                    if (LocaleFactoryKt.isLocaleInitialized()) {
                        LocaleFactoryKt.attachLocale(() -> {
                            android.content.res.Resources r = moduleResources();
                            return r != null ? r : (ctx != null ? ctx.getResources() : null);
                        });
                        logDebug("Locale changed, refreshed module locale");
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            context.registerReceiver(receiver, filter);
        lifecycleReceiver = receiver;
    }

    private static class AppErrorsProcessData {
        private final Object errors;
        private final Object proc;
        private final Object resultData;
        private final String foregroundActivity;

        AppErrorsProcessData(Object errors, Object proc, Object resultData) {
            this.errors = errors;
            this.proc = proc;
            this.resultData = resultData;
            this.foregroundActivity = captureForegroundActivity(proc);
        }

        String foregroundActivity() {
            return foregroundActivity;
        }

        Object pkgList() {
            Object list = proc != null ? invokeMethod(proc, "getPkgList") : null;
            if (list == null && proc != null) list = getField(proc, "pkgList");
            return list;
        }

        int pkgListSize() {
            Object list = pkgList();
            if (list != null) {
                Object size = invokeMethod(list, "size");
                if (size instanceof Integer) return (Integer) size;
            }
            Object rawPkgList = proc != null ? getField(proc, "pkgList") : null;
            if (rawPkgList instanceof ArrayMap) return ((ArrayMap<?, ?>) rawPkgList).size();
            return -1;
        }

        int pid() {
            if (proc == null) return 0;
            Object v = getField(proc, "mPid");
            if (!(v instanceof Integer)) v = getField(proc, "pid");
            return v instanceof Integer ? (Integer) v : 0;
        }

        int userId() {
            if (proc == null) return 0;
            Object v = getField(proc, "userId");
            return v instanceof Integer ? (Integer) v : 0;
        }

        ApplicationInfo appInfo() {
            if (proc == null) return null;
            Object v = getField(proc, "info");
            return v instanceof ApplicationInfo ? (ApplicationInfo) v : null;
        }

        String processName() {
            if (proc == null) return "";
            Object v = getField(proc, "processName");
            return v instanceof String ? (String) v : "";
        }

        String packageName() {
            ApplicationInfo info = appInfo();
            return info != null && info.packageName != null ? info.packageName : processName();
        }

        boolean isActualApp() {
            return pkgListSize() == 1 && appInfo() != null;
        }

        boolean isMainProcess() {
            return packageName().equals(processName());
        }

        boolean isBackgroundProcess() {
            Object ams = errors != null ? getField(errors, "mService") : null;
            Object userController = ams != null ? getField(ams, "mUserController") : null;
            if (userController == null) return false;
            Object ids = invokeMethod(userController, "getCurrentProfileIds");
            if (!(ids instanceof int[])) ids = invokeMethod(userController, "getCurrentProfileIdsLocked");
            if (ids instanceof int[]) {
                int[] arr = (int[]) ids;
                for (int id : arr) {
                    if (id != userId()) return true;
                }
            }
            return false;
        }

        boolean isRepeatingCrash() {
            if (resultData == null) return false;
            Object v = getField(resultData, "repeating");
            return v instanceof Boolean && (Boolean) v;
        }
    }

    private static String captureForegroundActivity(Object proc) {
        try {
            if (proc == null) {
                logDebug("⑥ capture: proc=null (skip)");
                return null;
            }
            Object wpc = getField(proc, "mWindowProcessController");
            if (wpc == null) wpc = invokeMethod(proc, "getWindowProcessController");
            if (wpc == null) {
                logDebug("⑥ capture: mWindowProcessController not found (skip)");
                return null;
            }
            Object hasVisible = invokeMethod(wpc, "hasVisibleActivities");
            if (!(hasVisible instanceof Boolean)) {
                logDebug("⑥ capture: hasVisibleActivities unavailable (skip)");
                return null;
            }
            if (!(Boolean) hasVisible) {
                logDebug("⑥ capture: no visible activity -> background");
                return "";
            }
            Object acts = getField(wpc, "mActivities");
            if (acts instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) acts;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object r = list.get(i);
                    if (r == null) continue;
                    Object vis = invokeMethod(r, "isVisible");
                    if (vis instanceof Boolean && !(Boolean) vis) continue;
                    String name = foregroundShortName(r);
                    if (name != null) {
                        logDebug("⑥ capture: foreground=" + name);
                        return name;
                    }
                }
                if (!list.isEmpty()) {
                    String name = foregroundShortName(list.get(list.size() - 1));
                    if (name != null) {
                        logDebug("⑥ capture: foreground(fallback top)=" + name);
                        return name;
                    }
                }
                logDebug("⑥ capture: visible flag set but no ActivityRecord name resolved -> background");
            } else {
                logDebug("⑥ capture: mActivities not a List -> background");
            }
            return "";
        } catch (Throwable t) {
            logDebug("⑥ capture: exception -> skip\n  " + t);
            return null;
        }
    }

    private static String foregroundShortName(Object activityRecord) {
        try {
            Object cn = getField(activityRecord, "mActivityComponent");
            if (cn != null) {
                Object sn = invokeMethod(cn, "getShortClassName");
                if (sn instanceof String) {
                    String s = ((String) sn).trim();
                    if (!s.isEmpty()) return s.startsWith(".") ? s.substring(1) : s;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object sc = getField(activityRecord, "shortComponentName");
            if (sc instanceof String) {
                String v = ((String) sc).trim();
                int slash = v.lastIndexOf('/');
                if (slash >= 0) v = v.substring(slash + 1);
                return v.startsWith(".") ? v.substring(1) : v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // 熔断必须先于展示过滤执行：后台/非主进程/被忽略应用的崩溃同样要统计，否则风暴漏网
    private static final int BURST_NONE = 0;
    private static final int BURST_TRIGGERED = 1;
    private static final int BURST_SILENT = 2;

    private static int burstSuppressState(String pkg, int userId, Context context) {
        try {
            long now = System.currentTimeMillis();
            Long deadline = autoSuppressedUntil.get(pkg);
            if (deadline != null) {
                if (now < deadline) {
                    autoSuppressedUntil.put(pkg, now + SUPPRESS_QUIET_MS);
                    return BURST_SILENT;
                }
                autoSuppressedUntil.remove(pkg);
            }
            ArrayDeque<Long> times = crashWindowTimes.get(pkg);
            if (times == null) {
                times = new ArrayDeque<>();
                crashWindowTimes.put(pkg, times);
            }
            while (!times.isEmpty() && now - times.peekFirst() > BURST_WINDOW_MS) times.pollFirst();
            times.addLast(now);
            if (times.size() >= BURST_LIMIT) {
                autoSuppressedUntil.put(pkg, now + SUPPRESS_QUIET_MS);
                crashWindowTimes.remove(pkg);
                cancelAppNotification(context, pkg);
                forceStopCrashSource(context, pkg, userId);
                logInfo("Burst suppress: \"" + pkg + "\" crashed " + BURST_LIMIT + " times within "
                        + (BURST_WINDOW_MS / 1000) + "s -> app force-stopped, notifications auto-paused");
                return BURST_TRIGGERED;
            }
        } catch (Throwable t) {
            logError("Burst suppress check failed\n  " + t);
        }
        return BURST_NONE;
    }

    private static void forceStopCrashSource(Context context, String pkg, int userId) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            // API 26+ 用 forceStopPackageAsUser 双参版
            java.lang.reflect.Method m = null;
            try {
                m = ActivityManager.class.getMethod("forceStopPackageAsUser", String.class, int.class);
            } catch (NoSuchMethodException asUserMissing) {
                m = ActivityManager.class.getMethod("forceStopPackage", String.class);
            }
            if (m.getParameterTypes().length == 2) {
                m.invoke(am, pkg, userId);
            } else {
                m.invoke(am, pkg);
            }
            logInfo("Burst suppress: force-stopped \"" + pkg + "\" via " + m.getName()
                    + " (userId=" + userId + ") to stop the crash storm");
        } catch (Throwable t) {
            logError("force-stop of crash source failed\n  " + t);
        }
    }

    private static void cancelAppNotification(Context context, String pkg) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(pkg, NOTIFICATION_ID);
        } catch (Throwable ignored) {
        }
    }

    private static void cancelSuppressNotice(Context context, String pkg) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(pkg, SUPPRESS_NOTICE_ID);
        } catch (Throwable ignored) {
        }
    }

    private static void postSuppressNotice(Context context, String pkg, String appName) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                manager.createNotificationChannel(new NotificationChannel("APPS_ERRORS",
                        LocaleFactoryKt.getLocale().getAppName(), NotificationManager.IMPORTANCE_HIGH));
            Intent resumeIntent = new Intent(ACTION_RESUME_SUPPRESS);
            resumeIntent.putExtra(EXTRA_PACKAGE, pkg);
            resumeIntent.putExtra(EXTRA_APP_NAME, appName);
            PendingIntent resumePi = PendingIntent.getBroadcast(context, 3, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = new Notification.Builder(context, "APPS_ERRORS")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setColor(0xFFFF6200)
                    .setContentTitle(LocaleFactoryKt.getLocale().getCrashAutoSuppressNoticeTitle())
                    .setContentText(LocaleFactoryKt.getLocale().getCrashAutoSuppressNoticeContent(appName))
                    .setStyle(new Notification.BigTextStyle().bigText(
                            LocaleFactoryKt.getLocale().getCrashAutoSuppressNoticeContent(appName)))
                    .addAction(0, LocaleFactoryKt.getLocale().getCrashAutoSuppressResume(), resumePi)
                    .setDefaults(0);
            manager.notify(pkg, SUPPRESS_NOTICE_ID, builder.build());
        } catch (Throwable t) {
            logError("failed to post suppression notice\n  " + t);
        }
    }

    private static void resumeSuppressIfSuppressed(Context context, String pkg, String appName) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            boolean wasSuppressed = autoSuppressedUntil.remove(pkg) != null;
            crashWindowTimes.remove(pkg);
            cancelSuppressNotice(context, pkg);
            if (wasSuppressed) {
                FunctionFactoryKt.toastLong(context, LocaleFactoryKt.getLocale().getCrashAutoSuppressResumed(appName, pkg));
                logInfo("Burst suppress: manually resumed notifications for \"" + pkg + "\"");
            }
        } catch (Throwable t) {
            logError("Resume suppress failed\n  " + t);
        }
    }

    private static void handleShowAppErrorUi(AppErrorsProcessData d, Context context) {
        String appName;
        ApplicationInfo info = d.appInfo();
        if (info != null) {
            String n = FunctionFactoryKt.appNameOf(context, info.packageName);
            appName = n.trim().isEmpty() ? info.packageName : n;
        } else {
            appName = d.packageName();
        }

        String appNameWithUserId = d.userId() != 0 ? appName + " (" + LocaleFactoryKt.getLocale().userId(d.userId()) + ")" : appName;

        String errorTitle = d.isRepeatingCrash()
                ? LocaleFactoryKt.getLocale().aerrRepeatedTitle(appNameWithUserId)
                : LocaleFactoryKt.getLocale().aerrTitle(appNameWithUserId);

        boolean muted = MutedErrorsData.getMutedErrorsIfUnlockApps().contains(d.packageName())
                || MutedErrorsData.getMutedErrorsIfRestartApps().contains(d.packageName());
        boolean isSelf = BuildConfigWrapper.APPLICATION_ID.equals(d.packageName());
        // 无条件前置：被忽略/后台/非主进程的崩溃同样计入熔断
        if (!isSelf) {
            int burstState = burstSuppressState(d.packageName(), d.userId(), context);
            if (burstState == BURST_TRIGGERED) {
                if (!muted) postSuppressNotice(context, d.packageName(), appName);
                return;
            }
            if (burstState == BURST_SILENT) return;
        }
        if ((d.isBackgroundProcess() || !FunctionFactoryKt.isAppCanOpened(context, d.packageName()))
                && ConfigData.isEnableOnlyShowErrorsInFront()) return;
        if (!d.isMainProcess() && ConfigData.isEnableOnlyShowErrorsInMain()) return;
        if (muted) return;

        if (BuildConfigWrapper.APPLICATION_ID.equals(d.packageName())) {
            FunctionFactoryKt.toast(context, "AppErrorNotify has crashed, please see the log in console");
            logError("AppErrorNotify crashed itself\n  see Android Runtime Exception on console");
        } else if (!ConfigData.isEnableAppConfigTemplate()) {
            sendCrashNotification(context, d, appName, errorTitle);
        } else {
            AppErrorsConfigData.refresh();
            AppErrorsConfigType type = resolveAppShowType(d.packageName());
            logDebug("App config template: \"" + d.packageName() + "\" -> " + type.name());
            switch (type) {
                case TOAST:
                    FunctionFactoryKt.toast(context, errorTitle);
                    break;
                case NOTHING:
                    break;
                case NOTIFY:
                default:
                    sendCrashNotification(context, d, appName, errorTitle);
                    break;
                case GLOBAL:
                    AppErrorsConfigType global = AppErrorsConfigType.values()[ConfigData.getGlobalShowErrorsType()];
                    switch (global) {
                        case DIALOG:
                            sendCrashNotification(context, d, appName, errorTitle);
                            break;
                        case TOAST:
                            FunctionFactoryKt.toast(context, errorTitle);
                            break;
                        case NOTHING:
                            break;
                        case NOTIFY:
                        default:
                            sendCrashNotification(context, d, appName, errorTitle);
                    }
            }
        }
    }

    private static AppErrorsConfigType resolveAppShowType(String packageName) {
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, packageName)) return AppErrorsConfigType.NOTIFY;
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, packageName)) return AppErrorsConfigType.TOAST;
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTHING, packageName)) return AppErrorsConfigType.NOTHING;
        return AppErrorsConfigType.GLOBAL;
    }

    private static void sendCrashNotification(Context context, AppErrorsProcessData d, String appName, String errorTitle) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            String channelId = "APPS_ERRORS";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                manager.createNotificationChannel(new NotificationChannel(channelId,
                        LocaleFactoryKt.getLocale().getAppName(), NotificationManager.IMPORTANCE_HIGH));

            android.graphics.drawable.Icon icon;
            android.content.res.Resources res = moduleResources();
            Drawable dIcon = res != null ? FunctionFactoryKt.drawableOf(res, R.drawable.ic_notify) : null;
            if (dIcon != null) {
                icon = Icon.createWithBitmap(toBitmap(dIcon));
            } else {
                icon = Icon.createWithResource(context, android.R.drawable.stat_notify_error);
            }

            io.github.vstory.apperrors.bean.AppErrorsInfoBean bean = null;
            for (io.github.vstory.apperrors.bean.AppErrorsInfoBean b : AppErrorsRecordData.allData) {
                if (b.pid == d.pid()) { bean = b; break; }
            }
            if (bean == null) {
                ApplicationInfo ai = d.appInfo();
                bean = io.github.vstory.apperrors.bean.AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                        ai != null ? ai.packageName : d.packageName(), null);
            }

            Intent listIntent = AppErrorsRecordActivity.Companion.intent();
            listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent contentPi = PendingIntent.getActivity(context, 0, listIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent detailIntent = new Intent();
            detailIntent.setComponent(new ComponentName(BuildConfigWrapper.APPLICATION_ID,
                    AppErrorsDetailActivity.class.getName()));
            detailIntent.putExtra("app_errors_info_extra", bean);
            detailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent detailPi = PendingIntent.getActivity(context, 1, detailIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = new Notification.Builder(context, channelId)
                    .setSmallIcon(icon)
                    .setColor(0xFFFF6200)
                    .setAutoCancel(true)
                    .setContentTitle(errorTitle)
                    .setContentText(LocaleFactoryKt.getLocale().getAppErrorsTip())
                    .setContentIntent(contentPi)
                    .addAction(0, LocaleFactoryKt.getLocale().getNotificationViewInfo(), detailPi)
                    .setDefaults(Notification.DEFAULT_ALL);
            manager.notify(d.packageName(), NOTIFICATION_ID, builder.build());
            if (autoSuppressedUntil.containsKey(d.packageName())) {
                postSuppressNotice(context, d.packageName(), appName);
            }
        } catch (Throwable t) {
            logError("failed to post crash notification\n  " + t);
        }
    }

    private static Bitmap toBitmap(Drawable drawable) {
        try {
            int w = Math.max(drawable.getIntrinsicWidth(), 1);
            int h = Math.max(drawable.getIntrinsicHeight(), 1);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void handleAppErrorsInfo(AppErrorsProcessData d, Context context, ApplicationErrorReport.CrashInfo info) {
        ApplicationInfo appInfo = d.appInfo();
        if (BuildConfigWrapper.APPLICATION_ID.equals(d.packageName())) {
            if (info != null) {
                logError("AppErrorNotify crashed itself, stackTrace:\n" + info.stackTrace, null);
            }
        }
        AppErrorsInfoBean bean = AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                appInfo != null ? appInfo.packageName : null, info);
        String fg = d.foregroundActivity();
        if (fg != null && !fg.isEmpty()) {
            bean.setRunningActivity(fg);
        } else if (fg != null) {
            bean.setBackgroundCrash(true);
        }
        AppErrorsRecordData.add(bean);
        String pkg = d.packageName();
        String crashKind = d.isRepeatingCrash() ? "keeps stopping" : "has stopped";
        StringBuilder sb = new StringBuilder();
        sb.append("Crash report: \"").append(pkg).append("\" ").append(crashKind);
        sb.append("\n  pid:      ").append(d.pid());
        if (!pkg.equals(d.processName())) {
            sb.append("\n  process:  \"").append(d.processName()).append("\"");
        }
        if (d.userId() != 0) {
            sb.append("\n  user:     ").append(d.userId());
        }
        logInfo(sb.toString());
        if (ConfigData.isEnableDebug()) {
            String exClass = info != null ? info.exceptionClassName : null;
            String exMsg = info != null ? info.exceptionMessage : null;
            StringBuilder details = new StringBuilder();
            details.append("Crash details: process=\"").append(d.processName()).append('"')
                   .append(" user=").append(d.userId())
                   .append(" repeating=").append(d.isRepeatingCrash());
            if (exClass != null) details.append(" exception=").append(exClass);
            if (exMsg != null && !exMsg.trim().isEmpty()) details.append(" msg=\"").append(exMsg).append('"');
            logDebug(details.toString());
            if (info != null && info.stackTrace != null && !info.stackTrace.isEmpty()) {
                logDebug("Crash stackTrace:\n" + info.stackTrace);
            }
        }
    }

    public static void install(XposedModule module, ClassLoader systemServerClassLoader) {
        FrameworkHooker.module = module;
        if (systemServerClassLoader != null) {
            FrameworkHooker.systemServerClassLoader = systemServerClassLoader;
        }
        hookHandles.clear();
        hookSummary.clear();
        hookOkCount = 0;
        hookSkipCount = 0;
        hookFailCount = 0;
        try {
            onHook();
        } catch (Throwable t) {
            logError("hook registration aborted, some hooks may be missing\n  " + t, t);
            printHookSummary();
        }
    }

    private static void hookExecutable(Executable e, String desc, Hooker hooker) {
        if (e == null) {
            hookSummary.add("  [SKIP] " + desc + "  -- not found on this Android version, ignorable");
            hookSkipCount++;
            return;
        }
        try {
            HookHandle h = module.hook(e).intercept(hooker);
            hookHandles.add(h);
            hookOkCount++;
        } catch (Throwable t) {
            hookSummary.add("  [FAIL] " + desc + "  ->  " + t);
            hookFailCount++;
        }
    }

    private static void printHookSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("hook installed: ").append(hookOkCount)
          .append(" ok / ").append(hookSkipCount)
          .append(" skipped / ").append(hookFailCount).append(" failed");
        // 明细只留跳过/失败：成功项已由计数表达，逐条打印纯属噪音
        if (hookFailCount > 0 || hookSkipCount > 0) {
            sb.append(":\n");
            for (String line : hookSummary) sb.append(line).append("\n");
        }
        logInfo(sb.toString().trim());
        hookSummary.clear();
    }

    private static void onHook() {
        Class<?> controllerClazz = ErrorDialogControllerClass();
        if (controllerClazz != null) {
            Method hasCrashDialogs = methodOfParamCount(controllerClazz, "hasCrashDialogs", 0);
            hookExecutable(hasCrashDialogs, "ErrorDialogController#hasCrashDialogs() -> true", chain -> true);
            Constructor<?> ctor = constructorOfParamCount(controllerClazz, 1);
            hookExecutable(ctor, "ErrorDialogController.<init>(1) -> clear mCrashDialogs", chain -> {
                Object result = chain.proceed();
                Object obj = chain.getThisObject();
                if (obj != null) setField(obj, "mCrashDialogs", Collections.emptyList());
                return result;
            });
            Method showCrashDialogs = methodOfParamCount(controllerClazz, "showCrashDialogs", 1);
            hookExecutable(showCrashDialogs, "ErrorDialogController#showCrashDialogs(1) -> null", chain -> null);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Class<?> atmsLocal = ActivityTaskManagerService_LocalServiceClass();
            if (atmsLocal != null) {
                Method m = methodOf(atmsLocal, "canShowErrorDialogs");
                hookExecutable(m, "ATMS LocalService#canShowErrorDialogs() -> false", chain -> false);
            }
            Class<?> ams = ActivityManagerServiceClass();
            if (ams != null) {
                Method m = methodOf(ams, "canShowErrorDialogs");
                hookExecutable(m, "AMS#canShowErrorDialogs() -> false", chain -> false);
            }
        }
        Method onCreate = methodOf(AppErrorDialogClass(), "onCreate", Bundle.class);
        hookExecutable(onCreate, "AppErrorDialog#onCreate(Bundle) -> cancel", chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Dialog) ((Dialog) chain.getThisObject()).cancel();
            return result;
        });
        Method onStart = methodOf(AppErrorDialogClass(), "onStart");
        hookExecutable(onStart, "AppErrorDialog#onStart() -> cancel", chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Dialog) ((Dialog) chain.getThisObject()).cancel();
            return result;
        });
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            Method m = methodOfParamCount(AppErrorsClass(), "handleAppCrashLSPB", 6);
            hookExecutable(m, "AppErrors#handleAppCrashLSPB(6) -> custom crash UI", chain -> {
                    Object result = chain.proceed();
                    Object arg1 = chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null;
                    if (arg1 instanceof String && "user-terminated".equals(arg1)) return result;
                    Object thisObj = chain.getThisObject();
                    Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                    if (context == null) return result;
                    ensureHostContext(context);
                    Object proc = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                    if (proc == null) {
                        logError("Received but got null ProcessRecord (Show UI failed)");
                        return result;
                    }
                    Object resultData = chain.getArgs().isEmpty() ? null : chain.getArgs().get(chain.getArgs().size() - 1);
                    // 防死锁：投到专用后台线程；AMS 持锁线程上同步 force-stop/notify 会 AB-BA 死锁
                    AppErrorsProcessData errData = new AppErrorsProcessData(thisObj, proc, resultData);
                    runOnBg(() -> handleShowAppErrorUi(errData, context));
                    return result;
                });
        } else {
            Method m = methodOf(AppErrorsClass(), "handleShowAppErrorUi", Message.class);
            hookExecutable(m, "AppErrors#handleShowAppErrorUi(Message) -> custom crash UI (API<=R)", chain -> {
                    Object result = chain.proceed();
                    Object thisObj = chain.getThisObject();
                    Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                    if (context == null) return result;
                    ensureHostContext(context);
                    Object resultData = null;
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Message)
                        resultData = ((Message) chain.getArgs().get(0)).obj;
                    Object proc = resultData != null ? getField(resultData, "proc") : null;
                    AppErrorsProcessData errData = new AppErrorsProcessData(thisObj, proc, resultData);
                    runOnBg(() -> handleShowAppErrorUi(errData, context));
                    return result;
                });
        }
        Method handleAppCrashInActivityController = methodOfParamCount(AppErrorsClass(), "handleAppCrashInActivityController", 8);
        hookExecutable(handleAppCrashInActivityController, "AppErrors#handleAppCrashInActivityController(8) -> record crash data", chain -> {
                Object result = chain.proceed();
                Object thisObj = chain.getThisObject();
                Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                if (context == null) return result;
                ensureHostContext(context);
                Object proc = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (proc == null) {
                    logError("Received but got null ProcessRecord");
                    return result;
                }
                ApplicationErrorReport.CrashInfo crashInfo = chain.getArgs().size() > 1 && chain.getArgs().get(1) instanceof ApplicationErrorReport.CrashInfo
                        ? (ApplicationErrorReport.CrashInfo) chain.getArgs().get(1) : null;
                AppErrorsProcessData recData = new AppErrorsProcessData(thisObj, proc, null);
                runOnBg(() -> handleAppErrorsInfo(recData, context, crashInfo));
                return result;
            });
        printHookSummary();
    }

    private FrameworkHooker() {}
}
