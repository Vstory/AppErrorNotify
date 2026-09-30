/*
 * AppErrorsTracking (api102 重构版) - 模块 Application (Java 化)
 */
package io.github.vstory.apperrors.application;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.data.MutedErrorsData;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.utils.tool.ModuleLogger;
import io.github.vstory.apperrors.utils.tool.ModuleServiceHolder;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 模块 Application */
public class AppErrorsApplication extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "AppErrorNotify";

    @Override
    public void onCreate() {
        super.onCreate();
        /**
         * ⚠️ 顺序（2026-09-30 issue#1 第二轮修复，三条都有理由，别调换）：
         *
         * ① 先装存储（service 未连接时 fallback 本地镜像）——attachLocale 懒读语言偏好依赖 ConfigData。
         * ② 再注册 service 监听：binder 在 provider 发布后即到达，**可能早于 Application.onCreate**，
         *    此时 `XposedServiceHelper` 会把它缓存；`registerListener` 会把缓存的 service **同步重放**
         *    → onServiceBind → ConfigData.initService（读 remote + 播种镜像）。
         *    ⚠️ 顺序反了（旧代码是 registerListener 在前、init 在后）会让刚绑好的 remotePrefs 被
         *    init(Context) 清成 null → 整个进程只能用镜像、写入永远进不了 remote。
         */
        ConfigData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
        XposedServiceHelper.registerListener(this);
        /**
         * ③ 首帧「存储就绪」有界等待：只在本地镜像**尚未播种**、且还有等待额度时等
         *    （最多 FIRST_BIND_WAIT_MS）。升级/首次安装后的第一次冷启动：存量值只在 remote、
         *    镜像还空着，等一次即可让首帧正确（开关 / 界面语言 / 忽略行为行）；
         *    镜像已播种（绝大多数冷启动）→ 立即返回，零开销。
         *    没装/未激活 LSPosed 的设备永远等不到 binder → 用「等待额度」（BIND_WAIT_MAX_MISSES）
         *    止损：最多白付 2 次，之后不再等（不能在无框架环境上每次冷启动都延迟 500ms）。
         *    ⚠️ 只能在这里等：provider 已发布、框架能把 binder 推进来（`ContentProvider.Transport.call`
         *       跑在 binder 线程，不被主线程阻塞）。放到 Activity.attachBaseContext 会与
         *       「框架等模块 provider 发布」互锁（见 ConfigData.FIRST_BIND_WAIT_MS 注释 / KB §24.6）。
         */
        if (!ConfigData.isMirrorSeeded() && ConfigData.shouldAwaitFirstBind()) {
            boolean bound = ConfigData.awaitFirstBind(ConfigData.FIRST_BIND_WAIT_MS);
            if (!bound) ConfigData.noteBindWaitMiss();
            Log.i(TAG, "mirror not seeded → waited for bind: bound=" + bound
                    + ", seeded=" + ConfigData.isMirrorSeeded());
        }
        /** 绑定 I18n */
        LocaleFactoryKt.attachLocale(this);
        /** 跟随系统夜间模式 */
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Override
    public void onServiceBind(XposedService service) {
        /**
         * ⚠️ 顺序：**先绑存储（RemotePreferences = 权威）→ 再通知监听器**。
         * 监听器（Activity 的 serviceStateListener）会按当前存储重放一次 UI 状态；
         * 若先通知，重放时 ConfigData 还指着本地镜像，界面会被"回放"成旧值。
         */
        ConfigData.initService(service);
        MutedErrorsData.initService(service);
        ModuleLogger.init(service.getRemotePreferences(ModuleLogger.PREFS_GROUP));
        /** 通知监听器（此时存储已就绪） */
        ModuleServiceHolder.onServiceBind(service);
        /** 一次性迁移：旧"对话框"配置 → 跟随全局（纯通知版废弃 DIALOG；迁移后广播通知 system_server 刷新） */
        AppErrorsConfigData.migrateDialogConfigToGlobalIfNeeded();
        AppErrorsConfigData.notifyConfigChanged(getApplicationContext());
    }

    @Override
    public void onServiceDied(XposedService service) {
        /** ⚠️ 顺序：先回退存储（本地镜像）→ 再通知监听器 */
        ConfigData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
        ModuleServiceHolder.onServiceDied(service);
    }
}
