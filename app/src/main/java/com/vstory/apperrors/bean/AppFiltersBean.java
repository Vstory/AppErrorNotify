
package com.vstory.apperrors.bean;

import com.vstory.apperrors.bean.enums.AppFiltersType;

import java.io.Serializable;


public class AppFiltersBean implements Serializable {

    
    public String name = "";
    
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
