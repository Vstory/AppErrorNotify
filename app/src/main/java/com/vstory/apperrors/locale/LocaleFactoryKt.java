
package com.vstory.apperrors.locale;

import android.content.Context;
import android.content.res.Resources;

import com.vstory.apperrors.generated.locale.ModuleAppLocale;

import kotlin.jvm.functions.Function0;


public class LocaleFactoryKt {

    private static ModuleAppLocale locale;

    
    public static ModuleAppLocale getLocale() {
        return locale;
    }

    
    public static void attachLocale(Context context) {
        locale = ModuleAppLocale.attach(context);
    }

    
    public static void attachLocale(Function0<Resources> provider) {
        locale = ModuleAppLocale.attach(provider);
    }

    
    public static boolean isLocaleInitialized() {
        return locale != null;
    }

    private LocaleFactoryKt() {}
}
