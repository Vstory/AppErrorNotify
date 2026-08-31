/*
 * AppErrorsTracking - 应用配置模版类型定义类 (Java 化)
 */
package com.fankes.apperrors.data.enums;

/** 应用配置模版类型 */
public enum AppErrorsConfigType {
    /** 跟随全局配置 */
    GLOBAL,
    /** 对话框 */
    DIALOG,
    /** 通知 */
    NOTIFY,
    /** Toast */
    TOAST,
    /** 什么也不显示 */
    NOTHING
}
