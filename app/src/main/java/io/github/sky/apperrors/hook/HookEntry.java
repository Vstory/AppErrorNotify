
package io.github.sky.apperrors.hook;

import android.util.Log;

import io.github.sky.apperrors.data.AppErrorsRecordData;
import io.github.sky.apperrors.data.ConfigData;
import io.github.sky.apperrors.data.MutedErrorsData;
import io.github.sky.apperrors.hook.entity.FrameworkHooker;

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
            
            FrameworkHooker.install(this, param.getClassLoader());
            
            
            try {
                android.content.Context sysCtx = FrameworkHooker.getSystemServerContext();
                if (sysCtx != null) {
                    AppErrorsRecordData.init(sysCtx);
                    FrameworkHooker.registerErrorChannel(sysCtx);
                    log(Log.INFO, TAG, "AppErrorsRecordData initialized at system server startup, folder=" + AppErrorsRecordData.getFolderPathForLog());
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "AppErrorsRecordData early init skipped: " + t, t);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onSystemServerStarting 异常: " + t, t);
        }
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        log(Log.INFO, TAG, "hot reloaded, hooks reinstalled");
        
        
        try {
            ConfigData.init(getRemotePreferences(ConfigData.PREFS_GROUP));
            MutedErrorsData.init(getRemotePreferences(MutedErrorsData.PREFS_GROUP));
        } catch (Throwable t) {
            log(Log.WARN, TAG, "onHotReloaded rebind prefs failed: " + t, t);
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
            log(Log.ERROR, TAG, "onHotReloaded 重装 hook 异常: " + t, t);
        }
        
        
        
        try {
            FrameworkHooker.restoreBroadcastChannelRegistered();
            android.content.Context sysCtx = FrameworkHooker.getSystemServerContext();
            if (sysCtx != null) {
                AppErrorsRecordData.init(sysCtx);
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "HotReloaded state restore skipped: " + t, t);
        }
    }
}
