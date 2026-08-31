/*
 * AppErrorsTracking (api102 重构版) - 系统框架 hook 实体 (Java 化)
 * 原 YukiHookAPI 版 → 纯 libxposed API 102：module.hook(Executable).intercept(chain)
 */
package com.fankes.apperrors.hook.entity;

import android.app.ApplicationErrorReport;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;

import com.fankes.apperrors.R;
import com.fankes.apperrors.bean.AppErrorsDisplayBean;
import com.fankes.apperrors.bean.AppErrorsInfoBean;
import com.fankes.apperrors.data.AppErrorsConfigData;
import com.fankes.apperrors.data.AppErrorsRecordData;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.data.enums.AppErrorsConfigType;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.errors.AppErrorsDisplayActivity;
import com.fankes.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;
import com.fankes.apperrors.utils.tool.ModuleLogger;
import com.fankes.apperrors.wrapper.BuildConfigWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedModule;

/** 系统框架 hook 实体 */
public class FrameworkHooker {

    private static final String TAG = "AppErrorsTracking";

    /** 模块实例（HookEntry.onSystemServerStarting 注入） */
    private static XposedModule module;

    /** system_server Context（首次 hook 时从 AppErrors.mContext 取得） */
    private static Context hostContext;

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

    /** 多候选类 */
    private static Class<?> classOf(String... names) {
        for (String name : names) {
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

    /** 模块 APK 资源（system_server 内加载） */
    private static android.content.res.Resources moduleResources() {
        Context context = hostContext;
        if (context == null) return null;
        ApplicationInfo ai = module != null ? module.getModuleApplicationInfo() : null;
        if (ai == null) return null;
        try {
            return context.getPackageManager().getResourcesForApplication(ai);
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
        registerLifecycle(context);
    }

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
        } else if (ConfigData.isEnableAppConfigTemplate()) {
            if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.GLOBAL, d.packageName())) {
                if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.DIALOG, ""))
                    showAppErrorsWithDialog(context, d, appName, errorTitle);
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, ""))
                    showAppErrorsWithNotify(context, errorTitle);
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, ""))
                    FunctionFactoryKt.toast(context, errorTitle);
            } else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.DIALOG, d.packageName()))
                showAppErrorsWithDialog(context, d, appName, errorTitle);
            else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, d.packageName()))
                showAppErrorsWithNotify(context, errorTitle);
            else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, d.packageName()))
                FunctionFactoryKt.toast(context, errorTitle);
        } else {
            showAppErrorsWithDialog(context, d, appName, errorTitle);
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

    private static void showAppErrorsWithNotify(Context context, String errorTitle) {
        IconCompat icon;
        android.content.res.Resources res = moduleResources();
        Drawable dIcon = res != null ? FunctionFactoryKt.drawableOf(res, R.drawable.ic_notify) : null;
        if (dIcon != null) {
            icon = IconCompat.createWithBitmap(toBitmap(dIcon));
        } else {
            icon = IconCompat.createWithResource(context, android.R.drawable.stat_notify_error);
        }
        FunctionFactoryKt.pushNotify(context, "APPS_ERRORS", LocaleFactoryKt.getLocale().getAppName(),
                errorTitle, LocaleFactoryKt.getLocale().getAppErrorsTip(), icon, 0xFFFF6200,
                AppErrorsRecordActivity.Companion.intent());
    }

    private static void showAppErrorsWithDialog(Context context, AppErrorsProcessData d, String appName, String errorTitle) {
        boolean isActualApp = d.isActualApp();
        AppErrorsDisplayActivity.Companion.start(context, new AppErrorsDisplayBean(
                d.pid(), d.userId(), d.packageName(), d.processName(), appName, errorTitle,
                isActualApp, isActualApp,
                isActualApp && (!d.isRepeatingCrash() || ConfigData.isEnableAlwaysShowsReopenAppOptions())
                        && FunctionFactoryKt.isAppCanOpened(context, d.packageName()) && d.isMainProcess()));
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
        AppErrorsRecordData.add(AppErrorsInfoBean.clone(context, d.pid(), d.userId(),
                appInfo != null ? appInfo.packageName : null, info));
        logInfo("Received crash application data" + (d.userId() != 0 ? " --user " + d.userId() : "") + " --pid " + d.pid());
    }

    /** 由 HookEntry 注入模块实例并注册 hook */
    public static void install(XposedModule module) {
        FrameworkHooker.module = module;
        onHook();
    }

    private static void onHook() {
        /** 干掉原生错误对话框 - 如果有 */
        Class<?> controllerClazz = ErrorDialogControllerClass();
        if (controllerClazz != null) {
            Method hasCrashDialogs = methodOfParamCount(controllerClazz, "hasCrashDialogs", 0);
            if (hasCrashDialogs != null)
                module.hook(hasCrashDialogs).intercept(chain -> true);
            Constructor<?> ctor = constructorOfParamCount(controllerClazz, 1);
            if (ctor != null) {
                module.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    Object obj = chain.getThisObject();
                    if (obj != null) setField(obj, "mCrashDialogs", Collections.emptyList());
                    return result;
                });
            }
            Method showCrashDialogs = methodOfParamCount(controllerClazz, "showCrashDialogs", 1);
            if (showCrashDialogs != null)
                module.hook(showCrashDialogs).intercept(chain -> null);
        }
        /** 干掉原生错误对话框 - API 30 以下 */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Class<?> atmsLocal = ActivityTaskManagerService_LocalServiceClass();
            if (atmsLocal != null) {
                Method m = methodOf(atmsLocal, "canShowErrorDialogs");
                if (m != null) module.hook(m).intercept(chain -> false);
            }
            Class<?> ams = ActivityManagerServiceClass();
            if (ams != null) {
                Method m = methodOf(ams, "canShowErrorDialogs");
                if (m != null) module.hook(m).intercept(chain -> false);
            }
        }
        /** 干掉原生错误对话框 - 如果上述方法全部失效则直接结束对话框 */
        Method onCreate = methodOf(AppErrorDialogClass(), "onCreate", Bundle.class);
        if (onCreate != null) {
            module.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof Dialog) ((Dialog) chain.getThisObject()).cancel();
                return result;
            });
        }
        Method onStart = methodOf(AppErrorDialogClass(), "onStart");
        if (onStart != null) {
            module.hook(onStart).intercept(chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof Dialog) ((Dialog) chain.getThisObject()).cancel();
                return result;
            });
        }
        /** 注入自定义错误对话框 */
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            // AOSP 签名（Android 12+ 各版本一致）：handleAppCrashLSPB(ProcessRecord app, String reason,
            //     String shortMsg, String longMsg, String stackTrace, AppErrorDialog.Data data)
            Method m = methodOfParamCount(AppErrorsClass(), "handleAppCrashLSPB", 6);
            if (m != null) {
                module.hook(m).intercept(chain -> {
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
            }
        } else {
            Method m = methodOf(AppErrorsClass(), "handleShowAppErrorUi", Message.class);
            if (m != null) {
                module.hook(m).intercept(chain -> {
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
        }
        /** 记录异常数据（ActivityController 路径） */
        // AOSP 签名（Android 12+ 各版本一致）：handleAppCrashInActivityController(ProcessRecord r,
        //     ApplicationErrorReport.CrashInfo crashInfo, String shortMsg, String longMsg,
        //     String stackTrace, long timeMillis, int callingPid, int callingUid)
        Method handleAppCrashInActivityController = methodOfParamCount(AppErrorsClass(), "handleAppCrashInActivityController", 8);
        if (handleAppCrashInActivityController != null) {
            module.hook(handleAppCrashInActivityController).intercept(chain -> {
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
        }
    }

    private FrameworkHooker() {}
}
