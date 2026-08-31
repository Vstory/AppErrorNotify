/*
 * AppErrorsTracking (api102 重构版) - 系统框架 hook 实体 (Java 化)
 * 原 YukiHookAPI 版 → 纯 libxposed API 102：module.hook(Executable).intercept(chain)
 */
package com.fankes.apperrors.hook.entity;

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
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;

import com.fankes.apperrors.R;
import com.fankes.apperrors.bean.AppErrorsInfoBean;
import com.fankes.apperrors.data.AppErrorsConfigData;
import com.fankes.apperrors.data.AppErrorsRecordData;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.data.enums.AppErrorsConfigType;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.errors.AppErrorsDetailActivity;
import com.fankes.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;
import com.fankes.apperrors.utils.tool.ModuleLogger;
import com.fankes.apperrors.wrapper.BuildConfigWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedModule;

/** 系统框架 hook 实体 */
public class FrameworkHooker {

    private static final String TAG = "AppErrorsTracking";

    /** 模块实例（HookEntry.onSystemServerStarting 注入） */
    private static XposedModule module;

    /** system_server 的 ClassLoader（必须用它加载 com.android.server.* 隐藏类，不能用 Class.forName 默认加载器！） */
    private static ClassLoader systemServerClassLoader;

    /** system_server Context（首次 hook 时从 AppErrors.mContext 取得） */
    private static Context hostContext;

    /** 已注册的 hook 句柄（热重载重装时清空重建） */
    private static final List<HookHandle> hookHandles = new ArrayList<>();

    /** hook 注册汇总（一次性输出，避免刷屏） */
    private static final List<String> hookSummary = new ArrayList<>();
    private static int hookOkCount = 0;
    private static int hookSkipCount = 0;
    private static int hookFailCount = 0;

    private static void log(int level, Object msg, Throwable e) {
        if (module != null) {
            module.log(level, TAG, msg != null ? msg.toString() : "", e);
            String p;
            switch (level) {
                case Log.ERROR: p = "E"; break;
                case Log.WARN: p = "W"; break;
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
    private static void logWarn(Object msg) { log(Log.WARN, msg, null); }

    // ===== Java 标准反射工具 =====

    /** 用 system_server ClassLoader 加载类（Android 16+ 隐藏 API 限制，必须用它，不能 Class.forName 默认加载器） */
    private static Class<?> classOf(String... names) {
        for (String name : names) {
            // 1. system_server classloader（首次 onSystemServerStarting 保存；热重载从旧 hook executable 反查）
            if (systemServerClassLoader != null) {
                try {
                    return Class.forName(name, false, systemServerClassLoader);
                } catch (Throwable ignored) {
                }
            }
            // 2. 兜底：boot classpath 的 framework classloader（ActivityThread 在 boot classpath，其加载器可看到 framework 类）
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
            // 3. 最后兜底：模块默认 classloader
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 必须存在的类 */
    private static Class<?> classOfRequired(String... names) {
        Class<?> c = classOf(names);
        if (c == null) throw new IllegalStateException("Class not found: " + names[0]);
        return c;
    }

    /** 读字段（含父类） */
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

    /** 写字段（含父类） */
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

    /** 调用无参方法（含父类） */
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

    /** 获取方法（指定类，供 hook）- 精确参数类型匹配 */
    private static Method methodOf(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 获取方法（指定类，供 hook）- 按参数个数匹配（兼容各 Android 版本签名差异，原 YukiHookAPI parameterCount 语义） */
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

    /** 获取构造器（指定类，供 hook）- 精确参数类型匹配 */
    private static Constructor<?> constructorOf(Class<?> clazz, Class<?>... paramTypes) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(paramTypes);
            c.setAccessible(true);
            return c;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 获取构造器（指定类，供 hook）- 按参数个数匹配 */
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

    // ===== 懒加载框架类 =====

    private static Class<?> UserControllerClass() { return classOf("com.android.server.am.UserController"); }
    private static Class<?> AppErrorsClass() { return classOfRequired("com.android.server.am.AppErrors"); }
    private static Class<?> AppErrorDialogClass() { return classOfRequired("com.android.server.am.AppErrorDialog"); }
    private static Class<?> AppErrorDialog_DataClass() { return classOfRequired("com.android.server.am.AppErrorDialog$Data"); }
    private static Class<?> ProcessRecordClass() { return classOfRequired("com.android.server.am.ProcessRecord"); }
    private static Class<?> ActivityManagerServiceClass() { return classOf("com.android.server.am.ActivityManagerService"); }
    private static Class<?> ActivityTaskManagerService_LocalServiceClass() { return classOf("com.android.server.wm.ActivityTaskManagerService$LocalService"); }
    private static Class<?> PackageListClass() { return classOf("com.android.server.am.ProcessRecord$PackageList", "com.android.server.am.PackageList"); }
    private static Class<?> ErrorDialogControllerClass() { return classOf("com.android.server.am.ProcessRecord$ErrorDialogController", "com.android.server.am.ErrorDialogController"); }

    /** 模块 APK 资源（system_server 内加载）
     *  ⚠️ 不能用 getResourcesForApplication()：在 system_server 里返回的 Resources 资源表不完整，
     *      getString 抛 Resources$NotFoundException（日志实证）。原版 YukiHookAPI 用 XModuleResources
     *      （= AssetManager.addAssetPath(模块APK) + new Resources）从模块 APK 路径直接加载。
     *      api102 没有 XModuleResources，这里用 AssetManager 等价实现。 */
    private static android.content.res.Resources moduleResources() {
        Context context = hostContext;
        if (context == null) return null;
        ApplicationInfo ai = module != null ? module.getModuleApplicationInfo() : null;
        if (ai == null) return null;
        try {
            String apkPath = ai.sourceDir;
            if (apkPath == null) return null;
            android.content.res.AssetManager am = context.getResources().getAssets();
            // 创建一个独立的 AssetManager 并添加模块 APK 路径（等价 XModuleResources.createInstance）
            try {
                android.content.res.AssetManager am2 = (android.content.res.AssetManager) android.content.res.AssetManager.class.getConstructor().newInstance();
                // addAssetPath 是隐藏 API，Android 9+ 反射受限但 system_server 内可访问
                java.lang.reflect.Method addPath = android.content.res.AssetManager.class.getMethod("addAssetPath", String.class);
                int cookie = (Integer) addPath.invoke(am2, apkPath);
                if (cookie == 0) return null;
                return new android.content.res.Resources(am2, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());
            } catch (Throwable t) {
                // fallback: 老方案（至少尝试）
                return context.getPackageManager().getResourcesForApplication(ai);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 获取 system_server 的 Context（ActivityThread.mSystemContext，onSystemServerStarting 早期可用）
     *  用于在 system_server 启动时就初始化文件存储目录（原版在 Application onCreate 初始化） */
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

    /** system_server 初始化（懒：首次 hook 拿到 Context 后） */
    private static void ensureHostContext(Context context) {
        if (hostContext != null) return;
        hostContext = context;
        if (!LocaleFactoryKt.isLocaleInitialized()) {
            LocaleFactoryKt.attachLocale(() -> {
                android.content.res.Resources r = moduleResources();
                return r != null ? r : context.getResources();
            });
        }
        // 初始化异常记录存储（system_server 写文件 /data/misc/apperrors_<random>/，与 UI 进程同源）
        // ensureHostContext 可能由 onSystemServerStarting 后的首次崩溃触发；此处幂等兜底
        try {
            AppErrorsRecordData.init(context);
        } catch (Throwable t) {
            logError("AppErrorsRecordData.init failed", t);
        }
        registerLifecycle(context);
        registerErrorChannel(context);
    }

    /** 广播通道 action 常量（与 UI 进程 AppErrorsRecordData 约定） */
    static final String ACTION_GET_ERRORS = "com.fankes.apperrors.action.GET_ERRORS";
    static final String ACTION_ERRORS_RESULT = "com.fankes.apperrors.action.ERRORS_RESULT";
    static final String ACTION_CLEAR_ERRORS = "com.fankes.apperrors.action.CLEAR_ERRORS";
    static final String ACTION_REMOVE_ERROR = "com.fankes.apperrors.action.REMOVE_ERROR";
    static final String EXTRA_ERRORS = "errors";
    static final String EXTRA_BEAN = "bean";
    /** 日志通道（ModuleLogger 约定，见 ModuleLogger） */
    static final String ACTION_GET_LOGS = ModuleLogger.ACTION_GET_LOGS;
    static final String ACTION_LOGS_RESULT = ModuleLogger.ACTION_LOGS_RESULT;
    static final String EXTRA_LOGS = ModuleLogger.EXTRA_LOGS;
    /** 忽略通道（MutedErrorsData 约定，见 MutedErrorsData） */
    static final String ACTION_GET_MUTED = MutedErrorsData.ACTION_GET_MUTED;
    static final String ACTION_MUTED_RESULT = MutedErrorsData.ACTION_MUTED_RESULT;
    static final String ACTION_MUTE_ERROR = MutedErrorsData.ACTION_MUTE_ERROR;
    static final String ACTION_UNMUTE_ERROR = MutedErrorsData.ACTION_UNMUTE_ERROR;
    static final String ACTION_UNMUTE_ALL = MutedErrorsData.ACTION_UNMUTE_ALL;
    static final String EXTRA_MUTED = MutedErrorsData.EXTRA_MUTED;
    static final String EXTRA_PACKAGE = MutedErrorsData.EXTRA_PACKAGE;

    /** 广播通道是否已注册（幂等） */
    private static volatile boolean errorChannelRegistered = false;

    /** 通道注册失败重试次数上限 */
    private static final int ERROR_CHANNEL_RETRY_MAX = 20;

    /**
     * 注册异常记录广播通道（UI 进程经广播从 system_server 拉记录/清空/删除；
     *  ⚠️ UI 进程不能直接读 /data/misc/ 文件（SELinux+DAC 权限不足），原版用 dataChannel 广播中转，
     *     这里用标准系统广播等价实现）
     *  ⚠️ 必须 RECEIVER_EXPORTED：UI 进程（普通 UID）发的广播要被 system_server 的 receiver 收到，
     *     NOT_EXPORTED 只能收系统/同 UID 广播（真机实证：UI 转圈拉不到数据）
     */
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
                            Intent result = new Intent(ACTION_ERRORS_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID); // 只发给模块 UI
                            result.putExtra(EXTRA_ERRORS, AppErrorsRecordData.allData);
                            ctx.sendBroadcast(result);
                            logInfo("Error channel: sent " + AppErrorsRecordData.allData.size() + " records to UI");
                        } else if (ACTION_CLEAR_ERRORS.equals(action)) {
                            AppErrorsRecordData.clearAll();
                            logInfo("Error channel: cleared all records from UI");
                        } else if (ACTION_REMOVE_ERROR.equals(action)) {
                            Object bean = intent.getSerializableExtra(EXTRA_BEAN);
                            if (bean instanceof com.fankes.apperrors.bean.AppErrorsInfoBean)
                                AppErrorsRecordData.remove((com.fankes.apperrors.bean.AppErrorsInfoBean) bean);
                            logInfo("Error channel: removed one record from UI");
                        } else if (ACTION_GET_LOGS.equals(action)) {
                            // 调试日志：回传 system_server 内存日志给 UI
                            Intent result = new Intent(ACTION_LOGS_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_LOGS, new java.util.ArrayList<>(ModuleLogger.allData()));
                            ctx.sendBroadcast(result);
                            logInfo("Log channel: sent " + ModuleLogger.allData().size() + " logs to UI");
                        } else if (ACTION_GET_MUTED.equals(action)) {
                            // 忽略列表：回传 system_server 内存忽略列表给 UI（system_server 是权威）
                            Intent result = new Intent(ACTION_MUTED_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID);
                            result.putExtra(EXTRA_MUTED, MutedErrorsData.fetchMutedErrorsAppsData());
                            ctx.sendBroadcast(result);
                            logInfo("Mute channel: sent " + MutedErrorsData.fetchMutedErrorsAppsData().size() + " muted apps to UI");
                        } else if (ACTION_MUTE_ERROR.equals(action)) {
                            String pkg = intent.getStringExtra(EXTRA_PACKAGE);
                            if (pkg != null && !pkg.isEmpty()) {
                                // 通知「忽略该应用」按钮行为：根据用户配置 直到重启/直到解锁
                                if (ConfigData.isMuteIgnoreUntilReboot()) {
                                    MutedErrorsData.mutedErrorsIfRestart(pkg);
                                    logInfo("Mute channel: muted \"" + pkg + "\" until restart");
                                } else {
                                    MutedErrorsData.mutedErrorsIfUnlock(pkg);
                                    logInfo("Mute channel: muted \"" + pkg + "\" until unlock");
                                }
                            }
                        } else if (ACTION_UNMUTE_ERROR.equals(action)) {
                            Object bean = intent.getSerializableExtra(EXTRA_BEAN);
                            if (bean instanceof com.fankes.apperrors.bean.MutedErrorsAppBean)
                                MutedErrorsData.unmuteErrorsApp((com.fankes.apperrors.bean.MutedErrorsAppBean) bean);
                            logInfo("Mute channel: unmuted one app");
                        } else if (ACTION_UNMUTE_ALL.equals(action)) {
                            MutedErrorsData.unmuteAllErrorsApps();
                            logInfo("Mute channel: unmuted all apps");
                        } else if (AppErrorsConfigData.ACTION_CONFIG_CHANGED.equals(action)) {
                            // 配置模板变更：UI 保存后立即刷新内存 Set（原版靠 onRefreshFrameworkPrefsData 回调，
                            //  libxposed 无此回调 → 用广播等价；崩溃时读时刷新仍兜底）
                            AppErrorsConfigData.refresh();
                            logInfo("Config channel: refreshed app config template from UI");
                        }
                    } catch (Throwable t) {
                        logWarn("Error channel handle failed: " + t);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_GET_ERRORS);
            filter.addAction(ACTION_CLEAR_ERRORS);
            filter.addAction(ACTION_REMOVE_ERROR);
            filter.addAction(ACTION_GET_LOGS);
            filter.addAction(ACTION_GET_MUTED);
            filter.addAction(ACTION_MUTE_ERROR);
            filter.addAction(ACTION_UNMUTE_ERROR);
            filter.addAction(ACTION_UNMUTE_ALL);
            filter.addAction(AppErrorsConfigData.ACTION_CONFIG_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            logInfo("Error channel registered");
            errorChannelRetry = 0;
        } catch (Throwable t) {
            errorChannelRegistered = false;
            logWarn("Error channel register failed: " + t);
            // 系统服务未就绪（如 ActivityManager 为 null）→ 延迟重试，保证 UI 拉取通道可用
            if (context != null && errorChannelRetry < ERROR_CHANNEL_RETRY_MAX) {
                errorChannelRetry++;
                final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.postDelayed(() -> registerErrorChannel(context), 3000L);
            }
        }
    }

    /** 通道注册重试计数 */
    private static int errorChannelRetry = 0;

    /** 注册生命周期广播（替代原 YukiHookAPI onAppLifecycle） */
    private static void registerLifecycle(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    MutedErrorsData.clearIfUnlock();
                    logInfo("User present, cleared muted errors until unlocks");
                } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                    if (LocaleFactoryKt.isLocaleInitialized()) {
                        LocaleFactoryKt.attachLocale(() -> {
                            android.content.res.Resources r = moduleResources();
                            return r != null ? r : (ctx != null ? ctx.getResources() : null);
                        });
                        logInfo("Locale changed, refreshed module locale");
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
    }

    /** APP 进程异常数据定义类 */
    private static class AppErrorsProcessData {
        private final Object errors;
        private final Object proc;
        private final Object resultData;

        AppErrorsProcessData(Object errors, Object proc, Object resultData) {
            this.errors = errors;
            this.proc = proc;
            this.resultData = resultData;
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

    /** 处理 APP 进程异常信息展示 */
    private static void handleShowAppErrorUi(AppErrorsProcessData d, Context context) {
        /** 当前 APP 名称 */
        String appName;
        ApplicationInfo info = d.appInfo();
        if (info != null) {
            String n = FunctionFactoryKt.appNameOf(context, info.packageName);
            appName = n.trim().isEmpty() ? info.packageName : n;
        } else {
            appName = d.packageName();
        }

        /** 当前 APP 名称 (包含用户 ID) */
        String appNameWithUserId = d.userId() != 0 ? appName + " (" + LocaleFactoryKt.getLocale().userId(d.userId()) + ")" : appName;

        /** 崩溃标题 */
        String errorTitle = d.isRepeatingCrash()
                ? LocaleFactoryKt.getLocale().aerrRepeatedTitle(appNameWithUserId)
                : LocaleFactoryKt.getLocale().aerrTitle(appNameWithUserId);

        /** 判断是否为已忽略的 APP */
        if (MutedErrorsData.getMutedErrorsIfUnlockApps().contains(d.packageName())
                || MutedErrorsData.getMutedErrorsIfRestartApps().contains(d.packageName())) return;
        /** 判断是否为后台进程 */
        if ((d.isBackgroundProcess() || !FunctionFactoryKt.isAppCanOpened(context, d.packageName()))
                && ConfigData.isEnableOnlyShowErrorsInFront()) return;
        /** 判断是否为主进程 */
        if (!d.isMainProcess() && ConfigData.isEnableOnlyShowErrorsInMain()) return;

        if (BuildConfigWrapper.APPLICATION_ID.equals(d.packageName())) {
            FunctionFactoryKt.toast(context, "AppErrorsTracking has crashed, please see the log in console");
            logError("AppErrorsTracking has crashed itself, please see the Android Runtime Exception in console");
        } else if (!ConfigData.isEnableAppConfigTemplate()) {
            // 模板未启用（默认）：统一发送系统通知
            sendCrashNotification(context, d, appName, errorTitle);
        } else {
            // 模板已启用：按 per-app 配置决定显示方式。
            // ⚠️ system_server 每次崩溃时从 RemotePreferences 重读（UI 保存后无需重启立即生效），
            //    4 次 getStringSet IPC 开销在崩溃频率下可忽略
            AppErrorsConfigData.refresh();
            AppErrorsConfigType type = resolveAppShowType(d.packageName());
            logInfo("App config template: \"" + d.packageName() + "\" -> " + type.name());
            switch (type) {
                case TOAST:
                    FunctionFactoryKt.toast(context, errorTitle);
                    break;
                case NOTHING:
                    // 静默（仍记录历史 + 日志）
                    break;
                case NOTIFY:
                default:
                    sendCrashNotification(context, d, appName, errorTitle);
                    break;
                case GLOBAL:
                    // 未配置 → 跟随全局显示类型
                    AppErrorsConfigType global = AppErrorsConfigType.values()[ConfigData.getGlobalShowErrorsType()];
                    switch (global) {
                        case DIALOG:
                            // 旧全局配置兼容降级：DIALOG → 通知
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

        /** 打印错误日志 */
        if (d.isActualApp()) {
            String msg = "Application \"" + d.packageName() + "\" " + (d.isRepeatingCrash() ? "keeps stopping" : "has stopped")
                    + (!d.packageName().equals(d.processName()) ? " --process \"" + d.processName() + "\"" : "")
                    + (d.userId() != 0 ? " --user " + d.userId() : "") + " --pid " + d.pid();
            logError(msg);
        } else {
            logError("Process \"" + d.processName() + "\" " + (d.isRepeatingCrash() ? "keeps stopping" : "has stopped") + " --pid " + d.pid());
        }
    }

    /** 解析应用配置模板中该应用的显示类型（未配置 → GLOBAL 跟随全局；旧 DIALOG 配置 v1.9(42) 起废弃，同样按 GLOBAL 处理） */
    private static AppErrorsConfigType resolveAppShowType(String packageName) {
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, packageName)) return AppErrorsConfigType.NOTIFY;
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, packageName)) return AppErrorsConfigType.TOAST;
        if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTHING, packageName)) return AppErrorsConfigType.NOTHING;
        return AppErrorsConfigType.GLOBAL;
    }

    /**
     * 发送崩溃通知（需求：不弹窗口/气泡 → 系统通知）
     * - contentIntent：点击通知打开模块异常记录列表
     * - action 按钮「查看详情」：跳转模块 AppErrorsDetailActivity 查看具体崩溃信息
     * 由 system_server（uid=1000）发送，无需运行时权限；通知 channel 幂等创建
     */
    private static void sendCrashNotification(Context context, AppErrorsProcessData d, String appName, String errorTitle) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            String channelId = "APPS_ERRORS";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                manager.createNotificationChannel(new NotificationChannel(channelId,
                        LocaleFactoryKt.getLocale().getAppName(), NotificationManager.IMPORTANCE_HIGH));

            // 通知图标：优先模块资源 ic_notify（转 bitmap），fallback 系统 stat_notify_error
            android.graphics.drawable.Icon icon;
            android.content.res.Resources res = moduleResources();
            Drawable dIcon = res != null ? FunctionFactoryKt.drawableOf(res, R.drawable.ic_notify) : null;
            if (dIcon != null) {
                icon = Icon.createWithBitmap(toBitmap(dIcon));
            } else {
                icon = Icon.createWithResource(context, android.R.drawable.stat_notify_error);
            }

            // 找到对应崩溃记录（详情页用；pid 匹配 allData 最新记录）
            com.fankes.apperrors.bean.AppErrorsInfoBean bean = null;
            for (com.fankes.apperrors.bean.AppErrorsInfoBean b : AppErrorsRecordData.allData) {
                if (b.pid == d.pid()) { bean = b; break; }
            }
            if (bean == null) {
                ApplicationInfo ai = d.appInfo();
                bean = com.fankes.apperrors.bean.AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                        ai != null ? ai.packageName : d.packageName(), null);
            }

            // contentIntent：打开模块异常记录列表
            Intent listIntent = AppErrorsRecordActivity.Companion.intent();
            listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent contentPi = PendingIntent.getActivity(context, 0, listIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // action 按钮「查看信息」：跳转 AppErrorsDetailActivity 带具体崩溃信息
            Intent detailIntent = new Intent();
            detailIntent.setComponent(new ComponentName(BuildConfigWrapper.APPLICATION_ID,
                    AppErrorsDetailActivity.class.getName()));
            detailIntent.putExtra("app_errors_info_extra", bean);
            detailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent detailPi = PendingIntent.getActivity(context, 1, detailIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // action 按钮「忽略该应用」：发广播 → system_server 内存忽略（直到重启），与忽略列表闭环
            // ⚠️ 不能 setPackage()：广播要能被 system_server 进程的动态 receiver 收到（同 Error channel 约定）
            Intent muteIntent = new Intent(MutedErrorsData.ACTION_MUTE_ERROR);
            muteIntent.putExtra(MutedErrorsData.EXTRA_PACKAGE, d.packageName());
            PendingIntent mutePi = PendingIntent.getBroadcast(context, 2, muteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = new Notification.Builder(context, channelId)
                    .setSmallIcon(icon)
                    .setColor(0xFFFF6200)
                    .setAutoCancel(true)
                    .setContentTitle(errorTitle)
                    .setContentText(LocaleFactoryKt.getLocale().getAppErrorsTip())
                    .setContentIntent(contentPi)
                    .addAction(0, LocaleFactoryKt.getLocale().getNotificationIgnoreApp(), mutePi)
                    .addAction(0, LocaleFactoryKt.getLocale().getNotificationViewInfo(), detailPi)
                    .setDefaults(Notification.DEFAULT_ALL);
            // notificationId 用 pid：同一进程反复崩溃覆盖同一条通知，不堆积
            manager.notify(d.pid(), builder.build());
            logInfo("Crash notification sent: " + errorTitle + " (pid " + d.pid() + ")");
        } catch (Throwable t) {
            logWarn("Send crash notification failed: " + t);
        }
    }

    /** Drawable 转 Bitmap（替代 core-ktx toBitmap） */
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

    /** 处理 APP 进程异常数据 */
    private static void handleAppErrorsInfo(AppErrorsProcessData d, Context context, ApplicationErrorReport.CrashInfo info) {
        ApplicationInfo appInfo = d.appInfo();
        if (BuildConfigWrapper.APPLICATION_ID.equals(d.packageName())) {
            // 模块自身崩溃：输出完整堆栈（UI 进程崩溃时 system_server 只能看到事件，这里补堆栈）
            if (info != null) {
                logError("AppErrorsTracking crashed itself, stackTrace:\n" + info.stackTrace, null);
            }
        }
        AppErrorsRecordData.add(AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                appInfo != null ? appInfo.packageName : null, info));
        logInfo("Received crash application data" + (d.userId() != 0 ? " --user " + d.userId() : "") + " --pid " + d.pid());
    }

    /** 由 HookEntry 注入模块实例并注册 hook（热重载重装时也会调用，先清空旧句柄列表） */
    public static void install(XposedModule module, ClassLoader systemServerClassLoader) {
        FrameworkHooker.module = module;
        // 仅在首次（或显式传入）时更新 classloader；热重载传 null 保留已保存值
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
            // 防止单个 hook 点异常导致整个注册静默中断（症状：只有 onSystemServerStarting，没有 Hook 注册完成）
            logError("FrameworkHooker.onHook 整体异常，部分 hook 可能未注册: " + t, t);
            printHookSummary();
        }
    }

    /** 记录一个 hook 注册结果（不立即打印，由 printHookSummary 汇总） */
    private static void hookExecutable(Executable e, String desc, Hooker hooker) {
        if (e == null) {
            hookSummary.add("  [SKIP] " + desc + "（方法/构造器未找到，Android 版本差异，可忽略）");
            hookSkipCount++;
            return;
        }
        try {
            HookHandle h = module.hook(e).intercept(hooker);
            hookHandles.add(h);
            hookSummary.add("  [OK]   " + desc);
            hookOkCount++;
        } catch (Throwable t) {
            hookSummary.add("  [FAIL] " + desc + "  →  " + t);
            hookFailCount++;
        }
    }

    /** 一次性输出全部 hook 注册结果（避免刷屏） */
    private static void printHookSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hook 注册完成：成功 ").append(hookOkCount)
          .append(" / 跳过 ").append(hookSkipCount)
          .append(" / 失败 ").append(hookFailCount).append(" 条：");
        if (hookOkCount + hookSkipCount + hookFailCount > 0) {
            sb.append("\n");
            for (String line : hookSummary) sb.append(line).append("\n");
        }
        logInfo(sb.toString().trim());
        hookSummary.clear();
    }

    private static void onHook() {
        /** 干掉原生错误对话框 - 如果有 */
        Class<?> controllerClazz = ErrorDialogControllerClass();
        if (controllerClazz != null) {
            Method hasCrashDialogs = methodOfParamCount(controllerClazz, "hasCrashDialogs", 0);
            hookExecutable(hasCrashDialogs, "ErrorDialogController#hasCrashDialogs() -> true", chain -> true);
            Constructor<?> ctor = constructorOfParamCount(controllerClazz, 1);
            hookExecutable(ctor, "ErrorDialogController.<init>(1) -> 清空 mCrashDialogs", chain -> {
                Object result = chain.proceed();
                Object obj = chain.getThisObject();
                if (obj != null) setField(obj, "mCrashDialogs", Collections.emptyList());
                return result;
            });
            Method showCrashDialogs = methodOfParamCount(controllerClazz, "showCrashDialogs", 1);
            hookExecutable(showCrashDialogs, "ErrorDialogController#showCrashDialogs(1) -> null", chain -> null);
        }
        /** 干掉原生错误对话框 - API 30 以下 */
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
        /** 干掉原生错误对话框 - 如果上述方法全部失效则直接结束对话框 */
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
        /** 注入自定义错误对话框 */
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            // AOSP 签名（Android 12+ 各版本一致）：handleAppCrashLSPB(ProcessRecord app, String reason,
            //     String shortMsg, String longMsg, String stackTrace, AppErrorDialog.Data data)
            Method m = methodOfParamCount(AppErrorsClass(), "handleAppCrashLSPB", 6);
            hookExecutable(m, "AppErrors#handleAppCrashLSPB(6) -> 自定义崩溃 UI", chain -> {
                    Object result = chain.proceed();
                    /** 如果为用户终止则不展示异常 */
                    Object arg1 = chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null;
                    if (arg1 instanceof String && "user-terminated".equals(arg1)) return result;
                    /** 当前实例 */
                    Object thisObj = chain.getThisObject();
                    Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                    if (context == null) return result;
                    ensureHostContext(context);
                    /** 当前进程信息 */
                    Object proc = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                    if (proc == null) {
                        logWarn("Received but got null ProcessRecord (Show UI failed)");
                        return result;
                    }
                    /** 当前错误数据 */
                    Object resultData = chain.getArgs().isEmpty() ? null : chain.getArgs().get(chain.getArgs().size() - 1);
                    /** 创建 APP 进程异常数据类 */
                    handleShowAppErrorUi(new AppErrorsProcessData(thisObj, proc, resultData), context);
                    return result;
                });
        } else {
            Method m = methodOf(AppErrorsClass(), "handleShowAppErrorUi", Message.class);
            hookExecutable(m, "AppErrors#handleShowAppErrorUi(Message) -> 自定义崩溃 UI (API<=R)", chain -> {
                    Object result = chain.proceed();
                    /** 当前实例 */
                    Object thisObj = chain.getThisObject();
                    Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                    if (context == null) return result;
                    ensureHostContext(context);
                    /** 当前错误数据 */
                    Object resultData = null;
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Message)
                        resultData = ((Message) chain.getArgs().get(0)).obj;
                    /** 当前进程信息 */
                    Object proc = resultData != null ? getField(resultData, "proc") : null;
                    /** 创建 APP 进程异常数据类 */
                    handleShowAppErrorUi(new AppErrorsProcessData(thisObj, proc, resultData), context);
                    return result;
                });
        }
        /** 记录异常数据（ActivityController 路径） */
        // AOSP 签名（Android 12+ 各版本一致）：handleAppCrashInActivityController(ProcessRecord r,
        //     ApplicationErrorReport.CrashInfo crashInfo, String shortMsg, String longMsg,
        //     String stackTrace, long timeMillis, int callingPid, int callingUid)
        Method handleAppCrashInActivityController = methodOfParamCount(AppErrorsClass(), "handleAppCrashInActivityController", 8);
        hookExecutable(handleAppCrashInActivityController, "AppErrors#handleAppCrashInActivityController(8) -> 记录崩溃数据", chain -> {
                Object result = chain.proceed();
                /** 当前实例 */
                Object thisObj = chain.getThisObject();
                Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                if (context == null) return result;
                ensureHostContext(context);
                /** 当前进程信息 */
                Object proc = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (proc == null) {
                    logWarn("Received but got null ProcessRecord");
                    return result;
                }
                /** 创建 APP 进程异常数据类 */
                ApplicationErrorReport.CrashInfo crashInfo = chain.getArgs().size() > 1 && chain.getArgs().get(1) instanceof ApplicationErrorReport.CrashInfo
                        ? (ApplicationErrorReport.CrashInfo) chain.getArgs().get(1) : null;
                handleAppErrorsInfo(new AppErrorsProcessData(thisObj, proc, null), context, crashInfo);
                return result;
            });
        /** 一次性输出全部 hook 注册结果 */
        printHookSummary();
    }

    private FrameworkHooker() {}
}
