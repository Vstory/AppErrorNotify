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

public class AppErrorsApplication extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "AppErrorNotify";

    @Override
    public void onCreate() {
        super.onCreate();
        // 顺序敏感：init 必须在 registerListener 之前，否则 remotePrefs 会被清空
        ConfigData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
        XposedServiceHelper.registerListener(this);
        if (!ConfigData.isMirrorSeeded() && ConfigData.shouldAwaitFirstBind()) {
            boolean bound = ConfigData.awaitFirstBind(ConfigData.FIRST_BIND_WAIT_MS);
            if (!bound) ConfigData.noteBindWaitMiss();
            Log.i(TAG, "mirror not seeded → waited for bind: bound=" + bound
                    + ", seeded=" + ConfigData.isMirrorSeeded());
        }
        LocaleFactoryKt.attachLocale(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Override
    public void onServiceBind(XposedService service) {
        ConfigData.initService(service);
        MutedErrorsData.initService(service);
        ModuleServiceHolder.onServiceBind(service);
        AppErrorsConfigData.migrateDialogConfigToGlobalIfNeeded();
        AppErrorsConfigData.notifyConfigChanged(getApplicationContext());
    }

    @Override
    public void onServiceDied(XposedService service) {
        ConfigData.init(this);
        MutedErrorsData.init(this);
        ModuleLogger.init(this);
        ModuleServiceHolder.onServiceDied(service);
    }
}
