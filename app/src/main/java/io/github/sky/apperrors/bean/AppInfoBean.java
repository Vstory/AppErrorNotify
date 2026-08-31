/*
 * AppErrorsTracking - 应用信息 bean (Java 化)
 */
package io.github.sky.apperrors.bean;

import android.graphics.drawable.Drawable;

import java.io.Serializable;

/**
 * 应用信息 bean
 */
public class AppInfoBean implements Serializable {

    /** 图标 */
    public Drawable icon;
    /** APP 名称 */
    public String name;
    /** APP 包名 */
    public String packageName;

    public AppInfoBean() {
    }

    public AppInfoBean(Drawable icon, String name, String packageName) {
        this.icon = icon;
        this.name = name;
        this.packageName = packageName;
    }

    public Drawable getIcon() { return icon; }
    public void setIcon(Drawable icon) { this.icon = icon; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
}
