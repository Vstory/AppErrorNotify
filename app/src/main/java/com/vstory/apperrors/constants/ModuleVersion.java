
package com.vstory.apperrors.constants;

import com.vstory.apperrors.generated.ModuleAppProperties;
import com.vstory.apperrors.wrapper.BuildConfigWrapper;


public class ModuleVersion {

    
    public static final String GITHUB_COMMIT_ID = ModuleAppProperties.GITHUB_CI_COMMIT_ID;

    
    public static final String NAME = BuildConfigWrapper.VERSION_NAME;

    
    public static final int CODE = BuildConfigWrapper.VERSION_CODE;

    
    public static boolean isCiMode() {
        return !isBlank(GITHUB_COMMIT_ID);
    }

    
    public static String suffix() {
        return isBlank(GITHUB_COMMIT_ID) ? "" : "-" + GITHUB_COMMIT_ID;
    }

    
    public static final ModuleVersion INSTANCE = new ModuleVersion();
    private ModuleVersion() {}

    @Override
    public String toString() {
        return NAME + suffix() + "(" + CODE + ")";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
