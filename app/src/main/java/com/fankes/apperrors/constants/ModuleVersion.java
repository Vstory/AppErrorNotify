/*
 * AppErrorsTracking - 模块版本常量定义类 (Java 化)
 */
package com.fankes.apperrors.constants;

import com.fankes.apperrors.generated.ModuleAppProperties;
import com.fankes.apperrors.wrapper.BuildConfigWrapper;

/** 模块版本常量定义类 */
public class ModuleVersion {

    /** 当前 GitHub 提交的 ID (CI 自动构建) */
    public static final String GITHUB_COMMIT_ID = ModuleAppProperties.GITHUB_CI_COMMIT_ID;

    /** 版本名称 */
    public static final String NAME = BuildConfigWrapper.VERSION_NAME;

    /** 版本号 */
    public static final int CODE = BuildConfigWrapper.VERSION_CODE;

    /** 是否为 CI 自动构建版本 */
    public static boolean isCiMode() {
        return !isBlank(GITHUB_COMMIT_ID);
    }

    /** 当前版本名称后缀 */
    public static String suffix() {
        return isBlank(GITHUB_COMMIT_ID) ? "" : "-" + GITHUB_COMMIT_ID;
    }

    /** 单例（与 Kotlin object 兼容：ModuleVersion.INSTANCE.toString()） */
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
