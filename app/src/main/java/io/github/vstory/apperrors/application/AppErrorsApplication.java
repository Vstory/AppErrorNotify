
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


public class AppErrorsApplication extends Application implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onCreate() {
        super.onCreate();
        
        XposedServiceHelper.registerListener(this);
        
        ConfigData.init(this);
        MutedErrorsData.init(this);
        
        LocaleFactoryKt.attachLocale(this);
        
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ModuleLogger.init(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        ModuleServiceHolder.onServiceBind(service);
        
        ConfigData.initService(service);
        
        AppErrorsConfigData.migrateDialogConfigToGlobalIfNeeded();
        AppErrorsConfigData.notifyConfigChanged(getApplicationContext());
        
        MutedErrorsData.initService(service);
        ModuleLogger.init(service.getRemotePreferences(ModuleLogger.PREFS_GROUP));
    }

    @Override
    public void onServiceDied(XposedService service) {
        ModuleServiceHolder.onServiceDied(service);
        
        ConfigData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
    }
}
