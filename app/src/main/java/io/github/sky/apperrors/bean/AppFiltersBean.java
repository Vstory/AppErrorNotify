/*
 * AppErrorsTracking - 应用过滤条件 bean (Java 化)
 */
package io.github.sky.apperrors.bean;

import io.github.sky.apperrors.bean.enums.AppFiltersType;

import java.io.Serializable;

/**
 * 应用过滤条件 bean
 */
public class AppFiltersBean implements Serializable {

    /** 名称或包名 */
    public String name = "";
    /** 过滤条件类型 */
    public AppFiltersType type = AppFiltersType.USER;

    public AppFiltersBean() {
    }

    public AppFiltersBean(String name, AppFiltersType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AppFiltersType getType() { return type; }
    public void setType(AppFiltersType type) { this.type = type; }
}
