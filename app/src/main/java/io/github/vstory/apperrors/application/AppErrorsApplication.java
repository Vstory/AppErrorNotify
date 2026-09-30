/*
 * AppErrorsTracking (api102 重构版) - 模块 Application (Java 化)
 */
package io.github.vstory.apperrors.application;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.AppErrorsRecordData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.data.MutedErrorsData;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.utils.tool.ModuleLogger;
import io.github.vstory.apperrors.utils.tool.ModuleServiceHolder;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 模块 Application */
public class AppErrorsApplication extends Application implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onCreate() {
        super.onCreate();
        /** 连接 XposedService（模块激活检测/远程存储） */
        XposedServiceHelper.registerListener(this);
        /** 装载存储控制类（service 未连接时 fallback 本地）——需在 bind I18n 前，attachLocale 懒读语言偏好依赖 ConfigData */
        ConfigData.init(this);
        MutedErrorsData.init(this);
        /** 绑定 I18n */
        LocaleFactoryKt.attachLocale(this);
        /** 跟随系统夜间模式 */
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ModuleLogger.init(this);
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
