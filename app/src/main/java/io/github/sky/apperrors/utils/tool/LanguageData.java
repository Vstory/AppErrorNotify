
package io.github.sky.apperrors.utils.tool;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import io.github.sky.apperrors.data.ConfigData;

import java.util.Locale;


public class LanguageData {

    
    public static final String KEY_LOCALE = "_app_locale";

    
    public static final int MODE_SYSTEM = 0;     
    public static final int MODE_ENGLISH = 1;    
    public static final int MODE_CHINESE = 2;    

    
    public static int getMode() {
        return ConfigData.getInt(KEY_LOCALE, MODE_SYSTEM);
    }

    
    public static void setMode(int mode) {
        ConfigData.putInt(KEY_LOCALE, mode);
    }

    
    public static boolean isSystemChinese(Context context) {
        Locale locale = Locale.getDefault();
        return "zh".equals(locale.getLanguage());
    }

    
    public static Locale resolveLocale(Context context) {
        switch (getMode()) {
            case MODE_ENGLISH: return Locale.ENGLISH;
            case MODE_CHINESE: return Locale.SIMPLIFIED_CHINESE;
            default: return Locale.getDefault();
        }
    }

    
    public static Context wrap(Context base) {
        if (base == null) return null;
        int mode = getMode();
        
        if (mode == MODE_SYSTEM) return base;
        Locale target = resolveLocale(base);
        Resources res = base.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(target);
        return base.createConfigurationContext(config);
    }

    
    public static Resources resolveResources(Context context) {
        Context wrapped = wrap(context);
        if (wrapped == null) return context.getResources();
        return wrapped.getResources();
    }

    
    public static boolean isForcedEnglish() {
        return getMode() == MODE_ENGLISH;
    }
}
