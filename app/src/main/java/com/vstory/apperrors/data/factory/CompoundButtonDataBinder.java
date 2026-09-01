
package com.vstory.apperrors.data.factory;

import android.widget.CompoundButton;

import java.util.function.Consumer;


public class CompoundButtonDataBinder {

    private final CompoundButton button;

    
    Consumer<Boolean> initializeCallback;
    
    Consumer<Boolean> changedCallback;
    
    Consumer<Boolean> applyChangesCallback;

    
    public boolean isAutoApplyChanges = true;

    public CompoundButtonDataBinder(CompoundButton button) {
        this.button = button;
    }

    
    public void onInitialize(Consumer<Boolean> result) {
        initializeCallback = result;
    }

    
    public void onChanged(Consumer<Boolean> result) {
        changedCallback = result;
    }

    
    public void reinitialize() {
        if (initializeCallback != null) initializeCallback.accept(button.isChecked());
    }

    
    public void applyChangesAndReinitialize() {
        applyChanges();
        reinitialize();
    }

    
    public void applyChanges() {
        if (applyChangesCallback != null) applyChangesCallback.accept(button.isChecked());
    }

    
    public void cancelChanges() {
        button.setChecked(!button.isChecked());
    }
}
