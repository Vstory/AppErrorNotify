/*
 * AppErrorsTracking (api102 重构版) - CompoundButton 数据绑定器 (Java 化)
 */
package io.github.sky.apperrors.data.factory;

import android.widget.CompoundButton;

import java.util.function.Consumer;

/** CompoundButton 数据绑定器 */
public class CompoundButtonDataBinder {

    private final CompoundButton button;

    /** 状态初始化回调事件 */
    Consumer<Boolean> initializeCallback;
    /** 状态改变回调事件 */
    Consumer<Boolean> changedCallback;
    /** 应用更改回调事件 */
    Consumer<Boolean> applyChangesCallback;

    /** 是否启用自动应用更改 */
    public boolean isAutoApplyChanges = true;

    public CompoundButtonDataBinder(CompoundButton button) {
        this.button = button;
    }

    /** 监听状态初始化 */
    public void onInitialize(Consumer<Boolean> result) {
        initializeCallback = result;
    }

    /** 监听状态改变 */
    public void onChanged(Consumer<Boolean> result) {
        changedCallback = result;
    }

    /** 重新初始化 */
    public void reinitialize() {
        if (initializeCallback != null) initializeCallback.accept(button.isChecked());
    }

    /** 应用更改并重新初始化 */
    public void applyChangesAndReinitialize() {
        applyChanges();
        reinitialize();
    }

    /** 应用更改 */
    public void applyChanges() {
        if (applyChangesCallback != null) applyChangesCallback.accept(button.isChecked());
    }

    /** 取消更改 */
    public void cancelChanges() {
        button.setChecked(!button.isChecked());
    }
}
