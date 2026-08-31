/*
 * AppErrorsTracking (api102 重构版) - 模块属性常量 (Java 化)
 * 原 gropify 插件从 gradle.properties 生成 → 手动定义（值固定为空）
 */
package com.fankes.apperrors.generated;

/** 模块属性常量 */
public class ModuleAppProperties {

    /** GitHub CI 提交 ID（CI 自动构建用，本地构建为空） */
    public static final String GITHUB_CI_COMMIT_ID = "";

    /** App Center Secret（匿名统计用，空则关闭） */
    public static final String APP_CENTER_SECRET = "";

    private ModuleAppProperties() {}
}
