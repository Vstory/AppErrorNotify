/*
 * AppErrorsTracking - 主界面 Activity (Java 化)
 */
package com.fankes.apperrors.ui.activity.main;

import android.content.Intent;
import android.os.Build;

import androidx.core.view.ViewKt;

import com.fankes.apperrors.R;
import com.fankes.apperrors.constants.ModuleVersion;
import com.fankes.apperrors.data.ConfigData;
import com.fankes.apperrors.data.factory.CompoundButtonFactoryKt;
import com.fankes.apperrors.databinding.ActivityMainBinding;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.base.BaseActivity;
import com.fankes.apperrors.ui.activity.debug.LoggerActivity;
import com.fankes.apperrors.ui.activity.errors.AppErrorsMutedActivity;
import com.fankes.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import com.fankes.apperrors.utils.factory.DialogBuilder;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;
import com.fankes.apperrors.utils.tool.FrameworkTool;
import com.fankes.apperrors.utils.tool.ModuleServiceHolder;

import io.github.libxposed.service.XposedService;

/** 主界面 Activity */
public class MainActivity extends BaseActivity<ActivityMainBinding> {

    /** 系统版本 */
    private static final String systemVersion =
            Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") " + Build.DISPLAY;

    /** 模块是否有效 */
    public static boolean isModuleValied = false;

    @Override
    protected void onCreate() {
        checkingTopComponentName();
        /** 设置 CI 自动构建标识 */
        if (ModuleVersion.isCiMode()) {
            binding.mainTextReleaseVersion.setText("CI " + ModuleVersion.GITHUB_COMMIT_ID);
            ViewKt.setVisible(binding.mainTextReleaseVersion, true);
            binding.mainTextReleaseVersion.setOnClickListener(v -> {
                DialogBuilder<?> dlg = new DialogBuilder<>(this);
                dlg.setTitle(LocaleFactoryKt.getLocale().getCiNoticeDialogTitle());
                dlg.setMsg(LocaleFactoryKt.getLocale().ciNoticeDialogContent(ModuleVersion.GITHUB_COMMIT_ID));
                dlg.confirmButton(LocaleFactoryKt.getLocale().getGotIt());
                dlg.noCancelable();
                dlg.show();
            });
        }
        binding.mainTextVersion.setText(LocaleFactoryKt.getLocale().moduleVersion(ModuleVersion.INSTANCE.toString()));
        binding.mainTextSystemVersion.setText(LocaleFactoryKt.getLocale().systemVersion(systemVersion));
        CompoundButtonFactoryKt.bind(binding.onlyShowErrorsInFrontSwitch,
                () -> ConfigData.isEnableOnlyShowErrorsInFront(),
                value -> ConfigData.setEnableOnlyShowErrorsInFront(value), null);
        CompoundButtonFactoryKt.bind(binding.onlyShowErrorsInMainProcessSwitch,
                () -> ConfigData.isEnableOnlyShowErrorsInMain(),
                value -> ConfigData.setEnableOnlyShowErrorsInMain(value), null);
        CompoundButtonFactoryKt.bind(binding.shareWithFile,
                () -> ConfigData.isShareWithFile(),
                value -> ConfigData.setShareWithFile(value), null);
        CompoundButtonFactoryKt.bind(binding.enableAppsConfigsTemplateSwitch,
                () -> ConfigData.isEnableAppConfigTemplate(),
                value -> ConfigData.setEnableAppConfigTemplate(value), binder -> {
                    binder.onInitialize(checked -> ViewKt.setVisible(binding.mgrAppsConfigsTemplateButton, checked));
                    binder.onChanged(checked -> binder.reinitialize());
                });
        /** 系统版本点击事件 */
        binding.mainTextSystemVersion.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(systemVersion);
            dlg.confirmButton(LocaleFactoryKt.getLocale().getGotIt());
            dlg.show();
        });
        /** 管理应用配置模板按钮点击事件 */
        binding.mgrAppsConfigsTemplateButton.setOnClickListener(v -> whenActivated(() -> navigateTo(ConfigureActivity.class)));
        /** 功能管理按钮点击事件 */
        binding.viewErrorsRecordButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsRecordActivity.class)));
        binding.viewMutedErrorsAppsButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsMutedActivity.class)));
        /** 调试日志按钮点击事件 */
        binding.titleLoggerIcon.setOnClickListener(v -> navigateTo(LoggerActivity.class));
        /** 项目地址按钮点击事件 */
        binding.titleGithubIcon.setOnClickListener(v -> FunctionFactoryKt.openBrowser(this, "https://github.com/Vstory/com.fankes.apperrors", ""));
        /** 关于本项目 → 跳转原项目 GitHub（标题/链接行/整卡均可点，水波纹反馈） */
        android.view.View.OnClickListener openGithub = v ->
                FunctionFactoryKt.openBrowser(this, "https://github.com/KitsunePie/AppErrorsTracking", "");
        binding.linkWithFollowMe.setOnClickListener(openGithub);
        binding.linkGithubUrl.setOnClickListener(openGithub);
        binding.paymentFollowingZhCnItem.setOnClickListener(openGithub);
        /** 设置桌面图标显示隐藏 */
        binding.hideIconInLauncherSwitch.setChecked(!FunctionFactoryKt.isLauncherIconShowing(this));
        binding.hideIconInLauncherSwitch.setOnCheckedChangeListener((btn, b) -> {
            if (!btn.isPressed()) return;
            FunctionFactoryKt.hideOrShowLauncherIcon(this, b);
        });
    }

    private void navigateTo(Class<?> clazz) {
        Intent intent = new Intent(this, clazz);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 刷新模块状态 */
    private void refreshModuleStatus() {
        binding.mainLinStatus.setBackgroundResource(
                ModuleServiceHolder.isActive() && !isModuleValied ? R.drawable.bg_yellow_round
                        : ModuleServiceHolder.isActive() ? R.drawable.bg_green_round
                        : R.drawable.bg_dark_round);
        binding.mainImgStatus.setImageResource(ModuleServiceHolder.isActive() ? R.drawable.ic_success : R.drawable.ic_warn);
        binding.mainTextStatus.setText(ModuleServiceHolder.isActive() && !isModuleValied
                ? LocaleFactoryKt.getLocale().getModuleNotFullyActivated()
                : ModuleServiceHolder.isActive() ? LocaleFactoryKt.getLocale().getModuleIsActivated()
                : LocaleFactoryKt.getLocale().getModuleNotActivated());
        ViewKt.setVisible(binding.mainTextApiWay, ModuleServiceHolder.isActive());
        XposedService service = ModuleServiceHolder.getService();
        binding.mainTextApiWay.setText(service != null
                ? "Activated by " + service.getFrameworkName() + " API " + service.getApiVersion() : "");
    }

    /** 当模块激活后才能执行相应功能 */
    private void whenActivated(Runnable callback) {
        if (ModuleServiceHolder.isActive()) callback.run();
        else FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getModuleNotActivated());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshModuleStatus();
        ModuleServiceHolder.addServiceStateListener(serviceStateListener, true);
    }

    @Override
    protected void onPause() {
        ModuleServiceHolder.removeServiceStateListener(serviceStateListener);
        super.onPause();
    }

    /** XposedService 状态监听 */
    private final ModuleServiceHolder.ServiceStateListener serviceStateListener = service -> {
        runOnUiThread(() -> {
            isModuleValied = service != null;
            refreshModuleStatus();
        });
    };
}
