/*
 * AppErrorsTracking - 对话框工厂 (Java 化, 保持 DialogBuilderFactoryKt 类名)
 */
package io.github.sky.apperrors.utils.factory;

import android.content.Context;

import androidx.viewbinding.ViewBinding;

import java.util.function.Consumer;

/** 对话框工厂（原 DialogBuilderFactory.kt 顶层 showDialog 函数） */
public class DialogBuilderFactoryKt {

    /** 构造 VB 自定义 View 对话框 */
    public static <VB extends ViewBinding> void showDialog_Generics(Context context, Class<VB> bindingClass,
                                                                    boolean isDisableMaterial3,
                                                                    Consumer<DialogBuilder<VB>> initiate) {
        DialogBuilder<VB> builder = new DialogBuilder<>(context, isDisableMaterial3, bindingClass);
        if (initiate != null) initiate.accept(builder);
        builder.show();
    }

    /** 构造普通对话框 */
    public static void showDialog(Context context, boolean isDisableMaterial3,
                                  Consumer<DialogBuilder<ViewBinding>> initiate) {
        DialogBuilder<ViewBinding> builder = new DialogBuilder<>(context, isDisableMaterial3, null);
        if (initiate != null) initiate.accept(builder);
        builder.show();
    }

    private DialogBuilderFactoryKt() {}
}
