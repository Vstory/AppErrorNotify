/*
 * AppErrorsTracking - I18n 工厂 (Java 化, 保持 LocaleFactoryKt 类名)
 */
package io.github.sky.apperrors.locale;

import android.content.Context;
import android.content.res.Resources;

import io.github.sky.apperrors.generated.locale.ModuleAppLocale;

import kotlin.jvm.functions.Function0;

/** I18n 工厂（原 LocaleFactory.kt 顶层函数/属性） */
public class LocaleFactoryKt {

    private static ModuleAppLocale locale;

    /** I18n 实例 */
    public static ModuleAppLocale getLocale() {
        return locale;
    }

    /** 绑定 I18n（UI 进程） */
    public static void attachLocale(Context context) {
        locale = ModuleAppLocale.attach(context);
    }

    /** 绑定 I18n（system_server 进程，懒加载模块资源） */
    public static void attachLocale(Function0<Resources> provider) {
        locale = ModuleAppLocale.attach(provider);
    }

    /** I18n 是否已初始化 */
    public static boolean isLocaleInitialized() {
        return locale != null;
    }

    private LocaleFactoryKt() {}
}
