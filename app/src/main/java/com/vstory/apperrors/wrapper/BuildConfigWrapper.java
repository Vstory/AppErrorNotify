
package com.vstory.apperrors.wrapper;

import com.vstory.apperrors.BuildConfig;


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
