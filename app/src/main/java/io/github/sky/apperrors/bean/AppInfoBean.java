
package io.github.sky.apperrors.bean;

import android.graphics.drawable.Drawable;

import java.io.Serializable;


public class AppInfoBean implements Serializable {

    
    public Drawable icon;
    
    public String name;
    
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
