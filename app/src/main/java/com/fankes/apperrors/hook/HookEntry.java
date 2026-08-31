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

import java.util.List;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
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
        try {
            // 配置存储（RemotePreferences 只读，仅读配置；记录写文件见 AppErrorsRecordData）
            ConfigData.init(getRemotePreferences(ConfigData.PREFS_GROUP));
            MutedErrorsData.init(getRemotePreferences(MutedErrorsData.PREFS_GROUP));
            // 注册 hook（必须传 system_server 的 ClassLoader，否则加载不到 com.android.server.* 隐藏类）
            FrameworkHooker.install(this, param.getClassLoader());
            // 尽早初始化异常记录目录 + 广播通道（原版在 Application onCreate 创建；此处反射拿 system_server Context，
            // 使 /data/misc/apperrors_<random>/ 在 system_server 启动时就创建，且 UI 拉取广播通道立即可用）
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
        log(Log.INFO, TAG, "onHotReloaded: 热重载完成，unhook 旧 hooks 并重装");
        ClassLoader cl = null;
        // 先 unhook 框架传回的旧句柄（热重载不会重放 onSystemServerStarting，这里手动接管）
        List<HookHandle> oldHandles = param.getOldHookHandles();
        if (oldHandles != null) {
            // 从旧 hook 的 executable 反查 system_server classloader（最可靠；HotReloadedParam 本身没有 getClassLoader）
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
        // 重装 hooks（cl 为 null 时 install 保留已保存值 / classOf 兜底）
        try {
            FrameworkHooker.install(this, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onHotReloaded 重装 hook 异常: " + t, t);
        }
    }
}
