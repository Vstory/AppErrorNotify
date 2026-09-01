
package com.vstory.apperrors.ui.activity.main;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;

import com.vstory.apperrors.R;
import com.vstory.apperrors.constants.ModuleVersion;
import com.vstory.apperrors.data.ConfigData;
import com.vstory.apperrors.data.AppErrorsConfigData;
import com.vstory.apperrors.data.factory.CompoundButtonFactoryKt;
import com.vstory.apperrors.databinding.ActivityMainBinding;
import com.vstory.apperrors.locale.LocaleFactoryKt;
import com.vstory.apperrors.ui.activity.base.BaseActivity;
import com.vstory.apperrors.ui.activity.debug.LoggerActivity;
import com.vstory.apperrors.ui.activity.debug.DebugActivity;
import com.vstory.apperrors.ui.activity.errors.AppErrorsMutedActivity;
import com.vstory.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import com.vstory.apperrors.utils.factory.DialogBuilder;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;
import com.vstory.apperrors.utils.tool.FrameworkTool;
import com.vstory.apperrors.utils.tool.LanguageData;
import com.vstory.apperrors.utils.tool.ModuleServiceHolder;

import io.github.libxposed.service.XposedService;


public class MainActivity extends BaseActivity<ActivityMainBinding> {

    
    private static final String systemVersion =
            Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") " + Build.DISPLAY;

    
    public static boolean isModuleValied = false;

    private static final String EASTER_EGG_PREF = "easter_egg_style";
    private static final int EASTER_EGG_CLICK_TARGET = 7;
    private static final int STYLE_MINE = 0;
    private static final int STYLE_MANAGER = 1;
    
    private static final int MAIN_TITLE_CLICK_TARGET = 5;
    private int easterEggClickCount = 0;
    private android.content.SharedPreferences easterEggPrefs;
    
    private int mainTitleClickCount = 0;

    @Override
    protected void onCreate() {
        checkingTopComponentName();
        easterEggPrefs = getSharedPreferences("easter_egg", MODE_PRIVATE);
        
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
        
        setupTitleLanguageToggle();
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
        
        binding.mgrAppsConfigsTemplateButton.setOnClickListener(v -> whenActivated(() -> navigateTo(ConfigureActivity.class)));
        
        binding.viewErrorsRecordButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsRecordActivity.class)));
        binding.viewMutedErrorsAppsButton.setOnClickListener(v -> whenActivated(() -> navigateTo(AppErrorsMutedActivity.class)));
        
        binding.debugSettingButton.setOnClickListener(v -> whenActivated(() -> navigateTo(DebugActivity.class)));
        
        refreshMuteIgnoreBehaviorText();
        binding.muteIgnoreBehaviorRow.setOnClickListener(v -> whenActivated(() -> showMuteIgnoreBehaviorDialog()));
        
        binding.titleLoggerIcon.setOnClickListener(v -> navigateTo(LoggerActivity.class));
        
        binding.titleGithubIcon.setOnClickListener(v -> FunctionFactoryKt.openBrowser(this, "https://github.com/Vstory/com.vstory.apperrors", ""));
        
        android.view.View.OnClickListener openGithub = v ->
                FunctionFactoryKt.openBrowser(this, "https://github.com/KitsunePie/AppErrorsTracking", "");
        binding.linkGithubUrl.setOnClickListener(openGithub);
        binding.linkWithFollowMe.setOnClickListener(v -> handleEasterEggClick());
        
        binding.hideIconInLauncherSwitch.setChecked(!FunctionFactoryKt.isLauncherIconShowing(this));
        binding.hideIconInLauncherSwitch.setOnCheckedChangeListener((btn, b) -> {
            if (!btn.isPressed()) return;
            FunctionFactoryKt.hideOrShowLauncherIcon(this, b);
        });
        
        initInfoCard();
        refreshInfoCard();
    }

    
    private void setupTitleLanguageToggle() {
        
        if (!LanguageData.isSystemChinese(this)) return;
        
        if (ModuleVersion.isCiMode()) return;
        binding.mainTitle.setOnClickListener(v -> handleTitleClick());
    }

    
    private void handleTitleClick() {
        mainTitleClickCount++;
        if (mainTitleClickCount < MAIN_TITLE_CLICK_TARGET) {
            int left = MAIN_TITLE_CLICK_TARGET - mainTitleClickCount;
            FunctionFactoryKt.toast(this, getString(R.string.lang_switch_progress, left));
            return;
        }
        mainTitleClickCount = 0;
        switchLanguage();
    }

    
    private void switchLanguage() {
        int curMode = LanguageData.getMode();
        boolean isEnglish = curMode == LanguageData.MODE_ENGLISH
                || (curMode == LanguageData.MODE_SYSTEM && !LanguageData.isSystemChinese(this));
        int next = isEnglish ? LanguageData.MODE_CHINESE : LanguageData.MODE_ENGLISH;
        LanguageData.setMode(next);
        
        LocaleFactoryKt.attachLocale(this);
        
        try { AppErrorsConfigData.notifyConfigChanged(this); } catch (Throwable ignored) { }
        
        FunctionFactoryKt.toast(this, next == LanguageData.MODE_ENGLISH
                ? getString(R.string.lang_switch_to_english)
                : getString(R.string.lang_switch_to_chinese));
        
        recreate();
    }

    
    private void refreshMuteIgnoreBehaviorText() {
        if (binding.muteIgnoreBehaviorValue == null) return;
        binding.muteIgnoreBehaviorValue.setText(ConfigData.isMuteIgnoreUntilReboot()
                ? LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorRestart()
                : LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorUnlock());
    }

    
    private void showMuteIgnoreBehaviorDialog() {
        DialogBuilder<?> dlg = new DialogBuilder<>(this);
        dlg.setTitle(LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorTitle());
        dlg.setMsg(buildMuteIgnoreBehaviorTip(LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorTip()));
        
        dlg.cancelButton(LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorUnlock(), () -> {
            ConfigData.setMuteIgnoreUntilReboot(false);
            AppErrorsConfigData.notifyConfigChanged(this);   
            refreshMuteIgnoreBehaviorText();
        });
        dlg.confirmButton(LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorRestart(), () -> {
            ConfigData.setMuteIgnoreUntilReboot(true);
            AppErrorsConfigData.notifyConfigChanged(this);   
            refreshMuteIgnoreBehaviorText();
        });
        dlg.show();
    }

    
    private CharSequence buildMuteIgnoreBehaviorTip(String tip) {
        SpannableString ss = new SpannableString(tip);
        
        String unlock = LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorUnlock();   
        String restart = LocaleFactoryKt.getLocale().getMuteIgnoreBehaviorRestart(); 
        int idx = tip.indexOf(unlock);
        if (idx >= 0) ss.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + unlock.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        idx = tip.indexOf(restart);
        if (idx >= 0) ss.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + restart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    
    private void initInfoCard() {
        binding.infoFrameworkVersionValue.setText(getString(R.string.not_installed));
        binding.infoSystemVersionValue.setText(systemVersion);
        binding.infoDeviceValue.setText(getDevice());
        binding.infoAbiValue.setText(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : getString(R.string.no_cpu_abi));
        binding.infoPackageValue.setText(getPackageName());
    }

    
    private void refreshInfoCard() {
        XposedService service = ModuleServiceHolder.getService();
        binding.infoFrameworkVersionValue.setText(service != null
                ? service.getFrameworkName() + " (" + service.getApiVersion() + ")"
                : getString(R.string.not_installed));
    }

    
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
        
        int icon = active ? currentCheckIcon() : R.drawable.ic_warn;
        binding.mainImgStatus.setImageResource(icon);
        
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

    
    private int currentCheckIcon() {
        int style = easterEggPrefs.getInt(EASTER_EGG_PREF, STYLE_MINE);
        return style == STYLE_MANAGER ? R.drawable.ic_check_ring_lsp : R.drawable.ic_check_ring;
    }

    
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

    
    private final ModuleServiceHolder.ServiceStateListener serviceStateListener = service -> {
        runOnUiThread(() -> {
            isModuleValied = service != null;
            refreshModuleStatus();
            refreshInfoCard();
        });
    };
}
