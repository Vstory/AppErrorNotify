
package io.github.vstory.apperrors.utils.factory;

import android.content.Context;

import androidx.viewbinding.ViewBinding;

import java.util.function.Consumer;


public class DialogBuilderFactoryKt {

    
    public static <VB extends ViewBinding> void showDialog_Generics(Context context, Class<VB> bindingClass,
                                                                    boolean isDisableMaterial3,
                                                                    Consumer<DialogBuilder<VB>> initiate) {
        DialogBuilder<VB> builder = new DialogBuilder<>(context, isDisableMaterial3, bindingClass);
        if (initiate != null) initiate.accept(builder);
        builder.show();
    }

    
    public static void showDialog(Context context, boolean isDisableMaterial3,
                                  Consumer<DialogBuilder<ViewBinding>> initiate) {
        DialogBuilder<ViewBinding> builder = new DialogBuilder<>(context, isDisableMaterial3, null);
        if (initiate != null) initiate.accept(builder);
        builder.show();
    }

    private DialogBuilderFactoryKt() {}
}
