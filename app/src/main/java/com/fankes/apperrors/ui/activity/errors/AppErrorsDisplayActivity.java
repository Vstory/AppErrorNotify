/*
 * AppErrorsTracking - 异常显示 Activity (Java 化)
 */
package com.fankes.apperrors.ui.activity.errors;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.ImageView;

import androidx.core.view.ViewKt;

import com.fankes.apperrors.R;
import com.fankes.apperrors.bean.AppErrorsDisplayBean;
import com.fankes.apperrors.bean.AppErrorsInfoBean;
import com.fankes.apperrors.data.AppErrorsRecordData;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.databinding.ActivityAppErrorsDisplayBinding;
import com.fankes.apperrors.databinding.DiaAppErrorsDisplayBinding;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.base.BaseActivity;
import com.fankes.apperrors.utils.factory.DialogBuilderFactoryKt;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;
import com.fankes.apperrors.wrapper.BuildConfigWrapper;

/** 异常显示 Activity */
public class AppErrorsDisplayActivity extends BaseActivity<ActivityAppErrorsDisplayBinding> {

    /** 当前实例 - 单例运行 */
    private static AppErrorsDisplayActivity instance;

    /** AppErrorsDisplayBean 传值 */
    private static final String EXTRA_APP_ERRORS_DISPLAY = "app_errors_display_extra";

    /** 启动 AppErrorsDisplayActivity（FrameworkHooker 等调用） */
    public static final Companion Companion = new Companion();

    public static class Companion {
        public void start(Context context, AppErrorsDisplayBean appErrorsDisplay) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(BuildConfigWrapper.APPLICATION_ID, AppErrorsDisplayActivity.class.getName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(EXTRA_APP_ERRORS_DISPLAY, appErrorsDisplay);
            context.startActivity(intent);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate() {
        if (instance != null) instance.finish();
        instance = this;
        AppErrorsDisplayBean parsed = null;
        try {
            Object extra = getIntent() != null ? getIntent().getSerializableExtra(EXTRA_APP_ERRORS_DISPLAY) : null;
            if (extra instanceof AppErrorsDisplayBean) parsed = (AppErrorsDisplayBean) extra;
        } catch (Exception ignored) {
        }
        final AppErrorsDisplayBean appErrorsDisplay = parsed;
        if (appErrorsDisplay == null) {
            toastAndFinish("AppErrorsDisplay");
            return;
        }
        /** 设置 Material 3 动态颜色主题 */
        if (ConfigData.isEnableMaterial3StyleAppErrorsDialog())
            setTheme(R.style.Theme_AppErrorsTracking_Translucent_Material3);
        /** 显示对话框 */
        DialogBuilderFactoryKt.showDialog_Generics(this, DiaAppErrorsDisplayBinding.class,
                !ConfigData.isEnableMaterial3StyleAppErrorsDialog(), builder -> {
                    builder.setTitle(appErrorsDisplay.title);
                    if (ConfigData.isEnableMaterial3StyleAppErrorsDialog() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        int color = FunctionFactoryKt.colorOf(getResources(), android.R.color.system_accent1_600);
                        ImageView[] icons = {
                                builder.getBinding().appInfoIcon, builder.getBinding().closeAppIcon,
                                builder.getBinding().reopenAppIcon, builder.getBinding().errorDetailIcon,
                                builder.getBinding().mutedIfUnlockIcon, builder.getBinding().mutedIfRestartIcon
                        };
                        for (ImageView icon : icons) icon.setColorFilter(color);
                    }
                    DiaAppErrorsDisplayBinding db = builder.getBinding();
                    ViewKt.setVisible(db.processNameText, !appErrorsDisplay.packageName.equals(appErrorsDisplay.processName));
                    ViewKt.setVisible(db.appInfoItem, appErrorsDisplay.isShowAppInfoButton);
                    ViewKt.setVisible(db.closeAppItem, !appErrorsDisplay.isShowReopenButton && appErrorsDisplay.isShowCloseAppButton);
                    ViewKt.setVisible(db.reopenAppItem, appErrorsDisplay.isShowReopenButton);
                    db.processNameText.setText(LocaleFactoryKt.getLocale().crashProcess(appErrorsDisplay.processName));
                    db.appInfoItem.setOnClickListener(v -> {
                        builder.cancel();
                        FunctionFactoryKt.openSelfSetting(AppErrorsDisplayActivity.this, appErrorsDisplay.packageName);
                    });
                    db.closeAppItem.setOnClickListener(v -> builder.cancel());
                    db.reopenAppItem.setOnClickListener(v -> {
                        FunctionFactoryKt.openApp(AppErrorsDisplayActivity.this, appErrorsDisplay.packageName, appErrorsDisplay.userId);
                        builder.cancel();
                    });
                    db.errorDetailItem.setOnClickListener(v -> {
                        AppErrorsInfoBean info = null;
                        for (AppErrorsInfoBean bean : AppErrorsRecordData.allData) {
                            if (bean.pid == appErrorsDisplay.pid) { info = bean; break; }
                        }
                        if (info == null) info = new AppErrorsInfoBean();
                        AppErrorsDetailActivity.Companion.start(AppErrorsDisplayActivity.this, info);
                        builder.cancel();
                    });
                    db.mutedIfUnlockItem.setOnClickListener(v -> {
                        MutedErrorsData.mutedErrorsIfUnlock(appErrorsDisplay.packageName);
                        FunctionFactoryKt.toast(AppErrorsDisplayActivity.this,
                                LocaleFactoryKt.getLocale().muteIfUnlockTip(appErrorsDisplay.appName));
                        builder.cancel();
                    });
                    db.mutedIfRestartItem.setOnClickListener(v -> {
                        MutedErrorsData.mutedErrorsIfRestart(appErrorsDisplay.packageName);
                        FunctionFactoryKt.toast(AppErrorsDisplayActivity.this,
                                LocaleFactoryKt.getLocale().muteIfRestartTip(appErrorsDisplay.appName));
                        builder.cancel();
                    });
                    builder.onCancel(AppErrorsDisplayActivity.this::finish);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
