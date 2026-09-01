
package io.github.sky.apperrors.data.factory;

import android.widget.CompoundButton;

import java.util.function.Consumer;


public class CompoundButtonFactoryKt {

    
    public static void bind(CompoundButton button, java.util.function.Supplier<Boolean> get, Consumer<Boolean> set,
                            Consumer<CompoundButtonDataBinder> initiate) {
        CompoundButtonDataBinder binder = new CompoundButtonDataBinder(button);
        if (initiate != null) initiate.accept(binder);
        boolean checked = get.get();
        button.setChecked(checked);
        if (binder.initializeCallback != null) binder.initializeCallback.accept(checked);
        binder.applyChangesCallback = set;
        button.setOnCheckedChangeListener((btn, isChecked) -> {
            if (btn.isPressed()) {
                if (binder.isAutoApplyChanges && binder.applyChangesCallback != null)
                    binder.applyChangesCallback.accept(isChecked);
                if (binder.changedCallback != null) binder.changedCallback.accept(isChecked);
            }
        });
    }

    private CompoundButtonFactoryKt() {}
}
