/*
 * AppErrorsTracking - 已忽略异常的应用 bean (Java 化)
 */
package com.fankes.apperrors.bean;

import java.io.Serializable;

/**
 * 已忽略异常的应用 bean
 */
public class MutedErrorsAppBean implements Serializable {

    /** 已忽略的异常类型 */
    public enum MuteType { UNTIL_UNLOCKS, UNTIL_REBOOTS }

    /** 类型 */
    public MuteType type;
    /** 包名 */
    public String packageName;

    public MutedErrorsAppBean() {
    }

    public MutedErrorsAppBean(MuteType type, String packageName) {
        this.type = type;
        this.packageName = packageName;
    }

    public MuteType getType() { return type; }
    public void setType(MuteType type) { this.type = type; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
}
