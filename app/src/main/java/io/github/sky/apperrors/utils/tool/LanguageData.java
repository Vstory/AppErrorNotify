/*
 * AppErrorsTracking - 界面语言切换存储控制类
 * 需求: 系统语言为中文时, 点击主界面标题5次 → 切换界面语言(支持中英切换)
 * 存储: 走 ConfigData(RemotePreferences, UI与system_server跨进程共享), key=_app_locale
 */
package io.github.sky.apperrors.utils.tool;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import io.github.sky.apperrors.data.ConfigData;

import java.util.Locale;

/** 界面语言偏好存储 + 强制语言 Resources 创建（跨进程共享：UI 写、system_server 读） */
public class LanguageData {

    /** 存储键（存到 ConfigData 的 remote prefs，UI/system_server 共享同一份） */
    public static final String KEY_LOCALE = "_app_locale";

    /** 语言模式 */
    public static final int MODE_SYSTEM = 0;     // 跟随系统
    public static final int MODE_ENGLISH = 1;    // 强制英文
    public static final int MODE_CHINESE = 2;    // 强制中文

    /** 读取当前语言偏好（UI/system_server 均可） */
    public static int getMode() {
        return ConfigData.getInt(KEY_LOCALE, MODE_SYSTEM);
    }

    /** 保存语言偏好（UI 写入后广播，system_server 重新读取生效） */
    public static void setMode(int mode) {
        ConfigData.putInt(KEY_LOCALE, mode);
    }

    /** 系统是否为中文 */
    public static boolean isSystemChinese(Context context) {
        Locale locale = Locale.getDefault();
        return "zh".equals(locale.getLanguage());
    }

    /** 解析当前应使用的 Locale: 语言偏好优先, 否则跟随系统 */
    public static Locale resolveLocale(Context context) {
        switch (getMode()) {
            case MODE_ENGLISH: return Locale.ENGLISH;
            case MODE_CHINESE: return Locale.SIMPLIFIED_CHINESE;
            default: return Locale.getDefault();
        }
    }

    /** 用目标语言创建 Context(基于 base) — 用于 Activity.attachBaseContext 与 Rescores */
    public static Context wrap(Context base) {
        if (base == null) return null;
        int mode = getMode();
        // 跟随系统: 直接用原 context
        if (mode == MODE_SYSTEM) return base;
        Locale target = resolveLocale(base);
        Resources res = base.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(target);
        return base.createConfigurationContext(config);
    }

    /** 根据目标语言重建 Resources(用于 LocaleFactory attach), 返回目标语言 Resources */
    public static Resources resolveResources(Context context) {
        Context wrapped = wrap(context);
        if (wrapped == null) return context.getResources();
        return wrapped.getResources();
    }

    /** 当前是否强制英文（用于其他判断） */
    public static boolean isForcedEnglish() {
        return getMode() == MODE_ENGLISH;
    }
}
