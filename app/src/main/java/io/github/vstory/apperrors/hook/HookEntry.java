package io.github.vstory.apperrors.hook;

import android.util.Log;

import io.github.vstory.apperrors.data.AppErrorsRecordData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.data.MutedErrorsData;
import io.github.vstory.apperrors.hook.entity.FrameworkHooker;

import java.util.List;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public class HookEntry extends XposedModule {

    public static final String TAG = "AppErrorNotify";

    public static HookEntry instance;

    public static boolean isReady() {
        return instance != null;
    }

    public static HookEntry getInstance() {
        return instance;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        instance = this;
        log(Log.INFO, TAG, "onModuleLoaded: " + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        log(Log.INFO, TAG, "onSystemServerStarting: system_server 初始化 + 注册 hook");
        try {
            ConfigData.init(getRemotePreferences(ConfigData.PREFS_GROUP));
            MutedErrorsData.init(getRemotePreferences(MutedErrorsData.PREFS_GROUP));
            // 必须传 system_server 的 ClassLoader，否则加载不到 com.android.server.*
            FrameworkHooker.install(this, param.getClassLoader());
            try {
                android.content.Context sysCtx = FrameworkHooker.getSystemServerContext();
                if (sysCtx != null) {
                    AppErrorsRecordData.init(sysCtx);
                    FrameworkHooker.registerErrorChannel(sysCtx);
                    log(Log.INFO, TAG, "AppErrorsRecordData initialized at system server startup, folder=" + AppErrorsRecordData.getFolderPathForLog());
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "异常记录数据早期初始化跳过\n  " + t, t);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onSystemServerStarting 异常\n  " + t, t);
        }
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        // 契约要求返回 true 前注销 external callbacks；注销失败也放行，
        // 否则用户会卡在「热重载永远失败」，比残留一个 receiver 更难恢复
        try {
            FrameworkHooker.unregisterExternalCallbacks();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onHotReloading 注销 external callbacks 失败\n  " + t, t);
        }
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        // 热重载会重置 static，必须恢复 instance，否则 Debug.d 拿不到模块实例
        instance = this;
        log(Log.INFO, TAG, "hot reloaded, hooks reinstalled");
        try {
            ConfigData.init(getRemotePreferences(ConfigData.PREFS_GROUP));
            MutedErrorsData.init(getRemotePreferences(MutedErrorsData.PREFS_GROUP));
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onHotReloaded 配置绑定失败\n  " + t, t);
        }
        ClassLoader cl = null;
        List<HookHandle> oldHandles = param.getOldHookHandles();
        if (oldHandles != null) {
            for (HookHandle h : oldHandles) {
                try {
                    cl = h.getExecutable().getDeclaringClass().getClassLoader();
                    if (cl != null) break;
                } catch (Throwable ignored) {}
            }
            for (HookHandle h : oldHandles) {
                try { h.unhook(); } catch (Throwable ignored) {}
            }
        }
        try {
            FrameworkHooker.install(this, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onHotReloaded 重装 hook 异常\n  " + t, t);
        }
        // 热重载重置了 static 且不重放 onSystemServerStarting：记录存储与通道都要在这里重建；
        // 旧 receiver 已在 onHotReloading 注销，此刻注册才能把新 filter 装上
        try {
            android.content.Context sysCtx = FrameworkHooker.getSystemServerContext();
            if (sysCtx != null) {
                AppErrorsRecordData.init(sysCtx);
                FrameworkHooker.registerExternalCallbacks(sysCtx);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "HotReloaded 状态恢复跳过\n  " + t, t);
        }
    }
}
