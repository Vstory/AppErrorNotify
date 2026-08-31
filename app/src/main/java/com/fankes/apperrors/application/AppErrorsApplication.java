/*
 * AppErrorsTracking (api102 重构版) - 模块 Application (Java 化)
 */
package com.fankes.apperrors.application;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.fankes.apperrors.data.AppErrorsRecordData;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.utils.tool.ModuleLogger;
import com.fankes.apperrors.utils.tool.ModuleServiceHolder;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 模块 Application */
public class AppErrorsApplication extends Application implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onCreate() {
        super.onCreate();
        /** 连接 XposedService（模块激活检测/远程存储） */
        XposedServiceHelper.registerListener(this);
        /** 绑定 I18n */
        LocaleFactoryKt.attachLocale(this);
        /** 跟随系统夜间模式 */
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        /** 装载存储控制类（service 未连接时 fallback 本地） */
        ConfigData.init(this);
        AppErrorsRecordData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        ModuleServiceHolder.onServiceBind(service);
        /** 切换到 RemotePreferences（与 system_server 同源） */
        ConfigData.initService(service);
        AppErrorsRecordData.initService(service);
        MutedErrorsData.initService(service);
        ModuleLogger.init(service.getRemotePreferences(ModuleLogger.PREFS_GROUP));
    }

    @Override
    public void onServiceDied(XposedService service) {
        ModuleServiceHolder.onServiceDied(service);
        /** 回退本地存储 */
        ConfigData.init(this);
        AppErrorsRecordData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
    }
}
