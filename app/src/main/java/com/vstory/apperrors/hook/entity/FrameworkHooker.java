
package com.vstory.apperrors.hook.entity;

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

import com.vstory.apperrors.R;
import com.vstory.apperrors.bean.AppErrorsInfoBean;
import com.vstory.apperrors.data.AppErrorsConfigData;
import com.vstory.apperrors.data.AppErrorsRecordData;
import com.vstory.apperrors.data.ConfigData;
import com.vstory.apperrors.data.MutedErrorsData;
import com.vstory.apperrors.data.enums.AppErrorsConfigType;
import com.vstory.apperrors.locale.LocaleFactoryKt;
import com.vstory.apperrors.ui.activity.errors.AppErrorsDetailActivity;
import com.vstory.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;
import com.vstory.apperrors.utils.tool.Debug;
import com.vstory.apperrors.utils.tool.ModuleLogger;
import com.vstory.apperrors.wrapper.BuildConfigWrapper;

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


public class FrameworkHooker {

    private static final String TAG = "AppErrorNotify";

    
    private static XposedModule module;

    
    private static ClassLoader systemServerClassLoader;

    
    private static Context hostContext;

    
    private static final List<HookHandle> hookHandles = new ArrayList<>();

    
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
                    int langMode = com.vstory.apperrors.utils.tool.LanguageData.getMode();
                    if (langMode == com.vstory.apperrors.utils.tool.LanguageData.MODE_ENGLISH) {
                        cfg.setLocale(java.util.Locale.ENGLISH);
                    } else if (langMode == com.vstory.apperrors.utils.tool.LanguageData.MODE_CHINESE) {
                        cfg.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);
                    }
                } catch (Throwable ignored) {  }
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
            logError("异常记录数据初始化失败\n  " + t, t);
        }
        registerLifecycle(context);
        registerErrorChannel(context);
    }

    
    static final String ACTION_GET_ERRORS = "com.vstory.apperrors.action.GET_ERRORS";
    static final String ACTION_ERRORS_RESULT = "com.vstory.apperrors.action.ERRORS_RESULT";
    static final String ACTION_CLEAR_ERRORS = "com.vstory.apperrors.action.CLEAR_ERRORS";
    static final String ACTION_REMOVE_ERROR = "com.vstory.apperrors.action.REMOVE_ERROR";
    static final String EXTRA_ERRORS = "errors";
    static final String EXTRA_BEAN = "bean";
    
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

    
    public static void restoreBroadcastChannelRegistered() {
        errorChannelRegistered = true;
    }

    
    public static boolean isBroadcastChannelRegistered() {
        return errorChannelRegistered;
    }

    
    private static final int ERROR_CHANNEL_RETRY_MAX = 20;

    
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
                            
                            
                            
                            java.util.List<com.vstory.apperrors.bean.AppErrorsInfoBean> latest =
                                    AppErrorsRecordData.latestFromFiles();
                            Intent result = new Intent(ACTION_ERRORS_RESULT);
                            result.setPackage(BuildConfigWrapper.APPLICATION_ID); 
                            result.putExtra(EXTRA_ERRORS, new java.util.ArrayList<>(latest));
                            ctx.sendBroadcast(result);
                            logDebug("Error channel: sent " + latest.size() + " records to UI");
                        } else if (ACTION_CLEAR_ERRORS.equals(action)) {
                            AppErrorsRecordData.clearAll();
                            logDebug("Error channel: cleared all records from UI");
                        } else if (ACTION_REMOVE_ERROR.equals(action)) {
                            Object bean = FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_BEAN);
                            if (bean instanceof com.vstory.apperrors.bean.AppErrorsInfoBean)
                                AppErrorsRecordData.remove((com.vstory.apperrors.bean.AppErrorsInfoBean) bean);
                            logDebug("Error channel: removed one record from UI");
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
                            if (bean instanceof com.vstory.apperrors.bean.MutedErrorsAppBean)
                                MutedErrorsData.unmuteErrorsApp((com.vstory.apperrors.bean.MutedErrorsAppBean) bean);
                            logDebug("Mute channel: unmuted one app");
                        } else if (ACTION_UNMUTE_ALL.equals(action)) {
                            MutedErrorsData.unmuteAllErrorsApps();
                            logDebug("Mute channel: unmuted all apps");
                        } else if (AppErrorsConfigData.ACTION_CONFIG_CHANGED.equals(action)) {
                            
                            
                            AppErrorsConfigData.refresh();
                            
                            if (LocaleFactoryKt.isLocaleInitialized()) {
                                LocaleFactoryKt.attachLocale(() -> {
                                    android.content.res.Resources r = moduleResources();
                                    return r != null ? r : null;
                                });
                            }
                            logDebug("Config channel: refreshed app config template from UI");
                        }
                    } catch (Throwable t) {
                        logError("错误通道处理失败\n  " + t);
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
            logDebug("Error channel registered");
            errorChannelRetry = 0;
        } catch (Throwable t) {
            errorChannelRegistered = false;
            logError("错误通道注册失败\n  " + t);
            
            if (context != null && errorChannelRetry < ERROR_CHANNEL_RETRY_MAX) {
                errorChannelRetry++;
                final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.postDelayed(() -> registerErrorChannel(context), 3000L);
            }
        }
    }

    
    private static int errorChannelRetry = 0;

    
    private static void registerLifecycle(Context context) {
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
    }

    
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

        
        if (MutedErrorsData.getMutedErrorsIfUnlockApps().contains(d.packageName())
                || MutedErrorsData.getMutedErrorsIfRestartApps().contains(d.packageName())) return;
        
        if ((d.isBackgroundProcess() || !FunctionFactoryKt.isAppCanOpened(context, d.packageName()))
                && ConfigData.isEnableOnlyShowErrorsInFront()) return;
        
        if (!d.isMainProcess() && ConfigData.isEnableOnlyShowErrorsInMain()) return;

        if (BuildConfigWrapper.APPLICATION_ID.equals(d.packageName())) {
            FunctionFactoryKt.toast(context, "AppErrorNotify has crashed, please see the log in console");
            logError("AppErrorNotify 自身崩溃\n  详见控制台 Android Runtime Exception");
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

            
            com.vstory.apperrors.bean.AppErrorsInfoBean bean = null;
            for (com.vstory.apperrors.bean.AppErrorsInfoBean b : AppErrorsRecordData.allData) {
                if (b.pid == d.pid()) { bean = b; break; }
            }
            if (bean == null) {
                ApplicationInfo ai = d.appInfo();
                bean = com.vstory.apperrors.bean.AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
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
            
            manager.notify(d.pid(), builder.build());
        } catch (Throwable t) {
            logError("发送崩溃通知失败\n  " + t);
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
        AppErrorsRecordData.add(AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                appInfo != null ? appInfo.packageName : null, info));
        
        
        String pkg = d.packageName();
        String crashKind = d.isRepeatingCrash() ? "keeps stopping" : "has stopped";
        StringBuilder sb = new StringBuilder();
        sb.append("崩溃报告: \"").append(pkg).append("\" ").append(crashKind);
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
            
            logError("hook 注册整体异常，部分 hook 可能未注册\n  " + t, t);
            printHookSummary();
        }
    }

    
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

    
    private static void printHookSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hook 注册完成：成功 ").append(hookOkCount)
          .append(" / 跳过 ").append(hookSkipCount)
          .append(" / 失败 ").append(hookFailCount).append(" 条");
        
        if (hookFailCount > 0) {
            sb.append("：\n");
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
            hookExecutable(ctor, "ErrorDialogController.<init>(1) -> 清空 mCrashDialogs", chain -> {
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
            hookExecutable(m, "AppErrors#handleAppCrashLSPB(6) -> 自定义崩溃 UI", chain -> {
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
                    
                    handleShowAppErrorUi(new AppErrorsProcessData(thisObj, proc, resultData), context);
                    return result;
                });
        } else {
            Method m = methodOf(AppErrorsClass(), "handleShowAppErrorUi", Message.class);
            hookExecutable(m, "AppErrors#handleShowAppErrorUi(Message) -> 自定义崩溃 UI (API<=R)", chain -> {
                    Object result = chain.proceed();
                    
                    Object thisObj = chain.getThisObject();
                    Context context = thisObj != null ? (Context) getField(thisObj, "mContext") : null;
                    if (context == null) return result;
                    ensureHostContext(context);
                    
                    Object resultData = null;
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Message)
                        resultData = ((Message) chain.getArgs().get(0)).obj;
                    
                    Object proc = resultData != null ? getField(resultData, "proc") : null;
                    
                    handleShowAppErrorUi(new AppErrorsProcessData(thisObj, proc, resultData), context);
                    return result;
                });
        }
        
        
        
        
        Method handleAppCrashInActivityController = methodOfParamCount(AppErrorsClass(), "handleAppCrashInActivityController", 8);
        hookExecutable(handleAppCrashInActivityController, "AppErrors#handleAppCrashInActivityController(8) -> 记录崩溃数据", chain -> {
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
                handleAppErrorsInfo(new AppErrorsProcessData(thisObj, proc, null), context, crashInfo);
                return result;
            });
        
        printHookSummary();
    }

    private FrameworkHooker() {}
}
