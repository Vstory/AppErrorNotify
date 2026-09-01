
package com.vstory.apperrors.bean;

import java.io.Serializable;


public class MutedErrorsAppBean implements Serializable {

    
    public enum MuteType { UNTIL_UNLOCKS, UNTIL_REBOOTS }

    
    public MuteType type;
    
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
