/*
 * AppErrorsTracking - 对 BuildConfig 的包装 (Java 化)
 */
package com.fankes.apperrors.wrapper;

import com.fankes.apperrors.BuildConfig;

/** 对 {@link BuildConfig} 的包装 */
public class BuildConfigWrapper {

    public static final String APPLICATION_ID = BuildConfig.APPLICATION_ID;
    public static final String VERSION_NAME = BuildConfig.VERSION_NAME;
    public static final int VERSION_CODE = BuildConfig.VERSION_CODE;
    public static final boolean DEBUG = BuildConfig.DEBUG;

    public static boolean isDebug() {
        return DEBUG;
    }

    private BuildConfigWrapper() {}
}
