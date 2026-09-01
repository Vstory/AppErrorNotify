
package io.github.sky.apperrors.utils.tool;

import android.content.Context;

import io.github.sky.apperrors.databinding.DiaStackTraceShareBinding;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.utils.factory.DialogBuilderFactoryKt;


public class StackTraceShareHelper {

    public interface OnChooseCallback {
        void onChoose(boolean sDeviceBrand, boolean sDeviceModel, boolean sDisplay, boolean sPackageName);
    }

    
    public static void showChoose(Context context, String title, OnChooseCallback onChoose) {
        DialogBuilderFactoryKt.showDialog_Generics(context, DiaStackTraceShareBinding.class, false, builder -> {
            builder.setTitle(title);
            builder.confirmButton(LocaleFactoryKt.getLocale().getConfirm(), () -> {
                DiaStackTraceShareBinding binding = builder.getBinding();
                onChoose.onChoose(
                        binding.configCheck0.isChecked(),
                        binding.configCheck1.isChecked(),
                        binding.configCheck2.isChecked(),
                        binding.configCheck3.isChecked());
                builder.cancel();
            });
            builder.cancelButton();
        });
    }

    private StackTraceShareHelper() {}
}
