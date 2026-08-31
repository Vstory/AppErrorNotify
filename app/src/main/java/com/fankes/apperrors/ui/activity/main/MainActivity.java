/*
 * AppErrorsTracking - 主界面 Activity (Java 化)
 */
package com.fankes.apperrors.ui.activity.main;

import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;
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

    private static final String EASTER_EGG_PREF = "easter_egg_style";
    private static final int EASTER_EGG_CLICK_TARGET = 7;
    private static final int STYLE_MINE = 0;
    private static final int STYLE_MANAGER = 1;
    private int easterEggClickCount = 0;
    private android.content.SharedPreferences easterEggPrefs;

    @Override
    protected void onCreate() {
        checkingTopComponentName();
        easterEggPrefs = getSharedPreferences("easter_egg", MODE_PRIVATE);
        /** 设置 CI 自动构建标识 */
        if (ModuleVersion.isCiMode()) {
            binding.mainTitle.setText("CI " + ModuleVersion.GITHUB_COMMIT_ID);
            binding.mainTitle.setOnClickListener(v -> {
                DialogBuilder<?> dlg = new DialogBuilder<>(this);
                dlg.setTitle(LocaleFactoryKt.getLocale().getCiNoticeDialogTitle());
                dlg.setMsg(LocaleFactoryKt.getLocale().ciNoticeDialogContent(ModuleVersion.GITHUB_COMMIT_ID));
                dlg.confirmButton(LocaleFactoryKt.getLocale().getGotIt());
                dlg.noCancelable();
                dlg.show();
            });
        }
        binding.mainTextModuleVersion.setText(getString(R.string.module_version_plain, ModuleVersion.INSTANCE.toString()));
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
        /** 管理应用配置模板按钮点击事件 */
        binding.mgrAppsConfigsTemplateButton.setOnClickListener(v -> whenActivated(() -> navigateTo(ConfigureActivity.class)));
        /** 功能管理按钮点击事件 */
        binding.viewErrorsRecordButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsRecordActivity.class)));
        binding.viewMutedErrorsAppsButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsMutedActivity.class)));
        /** 调试日志按钮点击事件 */
        binding.titleLoggerIcon.setOnClickListener(v -> navigateTo(LoggerActivity.class));
        /** 项目地址按钮点击事件 */
        binding.titleGithubIcon.setOnClickListener(v -> FunctionFactoryKt.openBrowser(this, "https://github.com/Vstory/com.fankes.apperrors", ""));
        /** 关于本项目 → 链接行打开原项目 GitHub 水波纹 */
        android.view.View.OnClickListener openGithub = v ->
                FunctionFactoryKt.openBrowser(this, "https://github.com/KitsunePie/AppErrorsTracking", "");
        binding.linkGithubUrl.setOnClickListener(openGithub);
        binding.linkWithFollowMe.setOnClickListener(v -> handleEasterEggClick());
        /** 设置桌面图标显示隐藏 */
        binding.hideIconInLauncherSwitch.setChecked(!FunctionFactoryKt.isLauncherIconShowing(this));
        binding.hideIconInLauncherSwitch.setOnCheckedChangeListener((btn, b) -> {
            if (!btn.isPressed()) return;
            FunctionFactoryKt.hideOrShowLauncherIcon(this, b);
        });
        /** 信息卡：填充设备/框架信息（参考 LSPosed 概览页 info_card） */
        initInfoCard();
        refreshInfoCard();
    }

    /** 填充信息卡（框架/系统/设备/ABI/包名，对齐 LSPosed 概览页 info_card，模块版本在激活卡显示不重复） */
    private void initInfoCard() {
        binding.infoFrameworkVersionValue.setText(getString(R.string.not_installed));
        binding.infoSystemVersionValue.setText(systemVersion);
        binding.infoDeviceValue.setText(getDevice());
        binding.infoAbiValue.setText(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : getString(R.string.no_cpu_abi));
        binding.infoPackageValue.setText(getPackageName());
    }

    /** 运行时刷新信息卡框架字段（与状态卡联动，避免"已激活但框架版本未安装"不一致） */
    private void refreshInfoCard() {
        XposedService service = ModuleServiceHolder.getService();
        binding.infoFrameworkVersionValue.setText(service != null
                ? service.getFrameworkName() + " (" + service.getApiVersion() + ")"
                : getString(R.string.not_installed));
    }

    /** 设备名（制造商标记+品牌+型号，首字母大写，对齐 LSPosed getDevice()） */
    private String getDevice() {
        String manufacturer = Character.toUpperCase(Build.MANUFACTURER.charAt(0)) + Build.MANUFACTURER.substring(1);
        if (!Build.BRAND.equals(Build.MANUFACTURER)) {
            manufacturer += " " + Character.toUpperCase(Build.BRAND.charAt(0)) + Build.BRAND.substring(1);
        }
        manufacturer += " " + Build.MODEL + " ";
        return manufacturer;
    }

    private void navigateTo(Class<?> clazz) {
        Intent intent = new Intent(this, clazz);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 刷新模块状态（激活卡：浅绿底深字 + 右侧大对勾，对齐 LSPosed 概览页） */
    private void refreshModuleStatus() {
        boolean active = ModuleServiceHolder.isActive();
        boolean partial = active && !isModuleValied;
        int bgColor;
        int statusTextColor;
        if (partial) {
            bgColor = ContextCompat.getColor(this, R.color.statusPartial);
            statusTextColor = ContextCompat.getColor(this, R.color.statusPartialText);
        } else if (active) {
            bgColor = ContextCompat.getColor(this, R.color.statusActive);
            statusTextColor = ContextCompat.getColor(this, R.color.statusActiveText);
        } else {
            bgColor = ContextCompat.getColor(this, R.color.statusInactive);
            statusTextColor = ContextCompat.getColor(this, R.color.statusInactiveText);
        }
        binding.mainLinStatus.setCardBackgroundColor(bgColor);
        binding.mainTextStatus.setTextColor(statusTextColor);
        binding.mainTextModuleVersion.setTextColor(statusTextColor);
        binding.mainTextApiWay.setTextColor(statusTextColor);
        // 右侧勾：激活=圆环勾；未激活=白色警告
        int icon = active ? currentCheckIcon() : R.drawable.ic_warn;
        binding.mainImgStatus.setImageResource(icon);
        // 圆环正常尺寸，不探出
        binding.mainImgStatus.getLayoutParams().width = dp(56);
        binding.mainImgStatus.getLayoutParams().height = dp(56);
        binding.mainImgStatus.setTranslationX(0);
        binding.mainImgStatus.setAlpha(1f);
        binding.mainImgStatus.requestLayout();
        binding.mainTextStatus.setText(partial
                ? LocaleFactoryKt.getLocale().getModuleNotFullyActivated()
                : active ? LocaleFactoryKt.getLocale().getModuleIsActivated()
                : LocaleFactoryKt.getLocale().getModuleNotActivated());
        ViewKt.setVisible(binding.mainTextApiWay, active);
        XposedService service = ModuleServiceHolder.getService();
        binding.mainTextApiWay.setText(service != null
                ? "Activated by " + service.getFrameworkName() + " API " + service.getApiVersion() : "");
    }

    /** dp 转像素 */
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void handleEasterEggClick() {
        easterEggClickCount++;
        if (easterEggClickCount < EASTER_EGG_CLICK_TARGET) {
            int left = EASTER_EGG_CLICK_TARGET - easterEggClickCount;
            FunctionFactoryKt.toast(this, getString(R.string.easter_egg_progress, left));
            return;
        }
        easterEggClickCount = 0;
        int current = easterEggPrefs.getInt(EASTER_EGG_PREF, STYLE_MINE);
        int next = (current + 1) % 2;
        easterEggPrefs.edit().putInt(EASTER_EGG_PREF, next).apply();
        FunctionFactoryKt.toast(this, next == STYLE_MANAGER
                ? getString(R.string.easter_egg_switch_manager)
                : getString(R.string.easter_egg_switch_mine));
        refreshModuleStatus();
    }

    /** 当前样式下的勾图标 */
    private int currentCheckIcon() {
        int style = easterEggPrefs.getInt(EASTER_EGG_PREF, STYLE_MINE);
        return style == STYLE_MANAGER ? R.drawable.ic_check_ring_lsp : R.drawable.ic_check_ring;
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
        refreshInfoCard();
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
            refreshInfoCard();
        });
    };
}
