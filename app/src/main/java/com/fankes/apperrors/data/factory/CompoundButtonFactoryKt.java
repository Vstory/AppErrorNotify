/*
 * AppErrorsTracking (api102 重构版) - CompoundButton 配置绑定工厂 (Java 化)
 */
package com.fankes.apperrors.data.factory;

import android.widget.CompoundButton;

import java.util.function.Consumer;

/** CompoundButton 配置绑定工厂（原 CompoundButtonFactory.kt 顶层函数 + 类） */
public class CompoundButtonFactoryKt {

    /** 绑定（原 CompoundButton.bind 扩展函数） */
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
