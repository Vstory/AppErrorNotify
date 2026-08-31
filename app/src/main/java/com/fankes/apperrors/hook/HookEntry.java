/*
 * AppErrorsTracking (api102 重构版) - 模块入口 (Java 化)
 * 基于 libxposed Modern API 102；作用域 = system_server 系统框架进程
 */
package com.fankes.apperrors.hook;

import android.util.Log;

import com.fankes.apperrors.data.AppErrorsRecordData;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.hook.entity.FrameworkHooker;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

/** api102 模块入口（java_init.list 声明） */
public class HookEntry extends XposedModule {

    public static final String TAG = "AppErrorsTracking";

    /** 当前进程的模块实例（system_server 内全局共享） */
    public static HookEntry instance;

    /** 模块实例是否已就绪 */
    public static boolean isReady() {
        return instance != null;
    }

    /** 获取模块实例（调用方需先确认 isReady） */
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
        // 数据层初始化（RemotePreferences：框架托管，UI 进程经 XposedService 同源读写）
        ConfigData.init(getRemotePreferences(ConfigData.PREFS_GROUP));
        AppErrorsRecordData.init(getRemotePreferences(AppErrorsRecordData.PREFS_GROUP));
        MutedErrorsData.init(getRemotePreferences(MutedErrorsData.PREFS_GROUP));
        // 注册 hook
        FrameworkHooker.install(this);
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        return true;
    }
}
