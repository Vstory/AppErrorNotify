/*
 * AppErrorsTracking - 应用异常信息显示 bean (Java 化)
 */
package com.fankes.apperrors.bean;

import java.io.Serializable;

/**
 * 应用异常信息显示 bean
 */
public class AppErrorsDisplayBean implements Serializable {

    /** APP 进程 ID */
    public int pid;
    /** APP 用户 ID */
    public int userId;
    /** APP 包名 */
    public String packageName;
    /** APP 进程名 */
    public String processName;
    /** APP 名称 */
    public String appName;
    /** 标题 */
    public String title;
    /** 是否显示应用信息按钮 */
    public boolean isShowAppInfoButton;
    /** 是否显示关闭应用按钮 */
    public boolean isShowCloseAppButton;
    /** 是否显示重新打开按钮 */
    public boolean isShowReopenButton;

    public AppErrorsDisplayBean() {
    }

    public AppErrorsDisplayBean(int pid, int userId, String packageName, String processName, String appName,
                                String title, boolean isShowAppInfoButton, boolean isShowCloseAppButton,
                                boolean isShowReopenButton) {
        this.pid = pid;
        this.userId = userId;
        this.packageName = packageName;
        this.processName = processName;
        this.appName = appName;
        this.title = title;
        this.isShowAppInfoButton = isShowAppInfoButton;
        this.isShowCloseAppButton = isShowCloseAppButton;
        this.isShowReopenButton = isShowReopenButton;
    }

    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isShowAppInfoButton() { return isShowAppInfoButton; }
    public void setShowAppInfoButton(boolean showAppInfoButton) { isShowAppInfoButton = showAppInfoButton; }
    public boolean isShowCloseAppButton() { return isShowCloseAppButton; }
    public void setShowCloseAppButton(boolean showCloseAppButton) { isShowCloseAppButton = showCloseAppButton; }
    public boolean isShowReopenButton() { return isShowReopenButton; }
    public void setShowReopenButton(boolean showReopenButton) { isShowReopenButton = showReopenButton; }
}
