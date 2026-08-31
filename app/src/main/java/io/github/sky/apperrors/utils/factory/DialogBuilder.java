/*
 * AppErrorsTracking - 对话框构造器 (Java 化)
 */
package io.github.sky.apperrors.utils.factory;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import io.github.sky.apperrors.data.ConfigData;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.lang.reflect.Method;

/** 对话框构造器 */
public class DialogBuilder<VB extends ViewBinding> {

    private final Context context;
    private final boolean isDisableMaterial3;
    private final Class<?> bindingClass;

    /** 实例对象 */
    private AlertDialog.Builder instance;
    /** 对话框取消监听 */
    private Runnable onCancel;
    /** 对话框实例 */
    private Dialog dialogInstance;
    /** 自定义布局 */
    private View customLayoutView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 获取绑定布局对象（lazy） */
    private VB binding;

    @SuppressWarnings("unchecked")
    public VB getBinding() {
        if (binding == null) {
            try {
                Method m = bindingClass.getMethod("inflate", LayoutInflater.class);
                VB vb = (VB) m.invoke(null, LayoutInflater.from(context));
                if (vb == null) throw new IllegalStateException("inflate returned null");
                customLayoutView = vb.getRoot();
                binding = vb;
            } catch (Exception e) {
                throw new IllegalStateException("This dialog maybe not a custom view dialog", e);
            }
        }
        return binding;
    }

    public DialogBuilder(Context context, boolean isDisableMaterial3, Class<?> bindingClass) {
        this.context = context;
        this.isDisableMaterial3 = isDisableMaterial3;
        this.bindingClass = bindingClass;
        if ("system_server".equals(android.os.Process.myProcessName()))
            throw new IllegalStateException("This dialog is not allowed to created in Xposed environment");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        if (isDisableMaterial3 && builder.getBackground() instanceof MaterialShapeDrawable) {
            MaterialShapeDrawable bg = (MaterialShapeDrawable) builder.getBackground();
            bg.setCornerSize(FunctionFactoryKt.dpFloat(15, context));
        }
        instance = builder;
    }

    public DialogBuilder(Context context) {
        this(context, false, null);
    }

    /** 设置对话框不可关闭 */
    public void noCancelable() {
        if (instance != null) instance.setCancelable(false);
    }

    /** 设置对话框标题 */
    public void setTitle(String value) {
        if (instance != null) instance.setTitle(value);
    }

    /** 设置对话框消息内容 */
    public void setMsg(String value) {
        if (instance != null) instance.setMessage(value);
    }

    /** 设置对话框消息内容（支持富文本，如 Html.fromHtml 生成的 Spanned） */
    public void setMsg(CharSequence value) {
        if (instance != null) instance.setMessage(value);
    }

    /** 设置进度条对话框消息内容 */
    public void setProgressContent(String value) {
        if (customLayoutView == null) {
            LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            ll.setGravity(Gravity.CENTER | Gravity.START);
            CircularProgressIndicator indicator = new CircularProgressIndicator(context);
            indicator.setIndeterminate(true);
            indicator.setTrackCornerRadius(FunctionFactoryKt.dp(10, context));
            ll.addView(indicator);
            View spacer = new View(context);
            spacer.setLayoutParams(new ViewGroup.LayoutParams(FunctionFactoryKt.dp(20, context), 5));
            ll.addView(spacer);
            TextView tv = new TextView(context);
            tv.setTag("progressContent");
            tv.setText(value);
            ll.addView(tv);
            ll.setPadding(FunctionFactoryKt.dp(20, context), FunctionFactoryKt.dp(20, context),
                    FunctionFactoryKt.dp(20, context), FunctionFactoryKt.dp(20, context));
            customLayoutView = ll;
        } else {
            View v = customLayoutView.findViewWithTag("progressContent");
            if (v instanceof TextView) ((TextView) v).setText(value);
        }
    }

    /** 设置对话框确定按钮 */
    public void confirmButton(String text, Runnable callback) {
        if (instance != null) instance.setPositiveButton(text, (d, w) -> { if (callback != null) callback.run(); });
    }

    public void confirmButton(String text) {
        confirmButton(text, null);
    }

    public void confirmButton(Runnable callback) {
        confirmButton(LocaleFactoryKt.getLocale().getConfirm(), callback);
    }

    public void confirmButton() {
        confirmButton(LocaleFactoryKt.getLocale().getConfirm(), null);
    }

    /** 设置对话框取消按钮 */
    public void cancelButton(String text, Runnable callback) {
        if (instance != null) instance.setNegativeButton(text, (d, w) -> { if (callback != null) callback.run(); });
    }

    public void cancelButton(String text) {
        cancelButton(text, null);
    }

    public void cancelButton(Runnable callback) {
        cancelButton(LocaleFactoryKt.getLocale().getCancel(), callback);
    }

    public void cancelButton() {
        cancelButton(LocaleFactoryKt.getLocale().getCancel(), null);
    }

    /** 设置对话框第三个按钮 */
    public void neutralButton(String text, Runnable callback) {
        if (instance != null) instance.setNeutralButton(text, (d, w) -> { if (callback != null) callback.run(); });
    }

    public void neutralButton(String text) {
        neutralButton(text, null);
    }

    public void neutralButton(Runnable callback) {
        neutralButton(LocaleFactoryKt.getLocale().getMore(), callback);
    }

    public void neutralButton() {
        neutralButton(LocaleFactoryKt.getLocale().getMore(), null);
    }

    /** 当对话框关闭时 */
    public void onCancel(Runnable callback) {
        onCancel = callback;
    }

    /** 取消对话框 */
    public void cancel() {
        if (dialogInstance != null) dialogInstance.cancel();
    }

    /** 显示对话框 */
    public void show() {
        /** 若当前自定义 View 的对话框没有调用 binding 将会对其手动调用一次以确保显示布局 */
        if (bindingClass != null) getBinding();
        try {
            Dialog dialog = instance.create();
            if (customLayoutView != null) dialog.setContentView(customLayoutView);
            dialogInstance = dialog;
            dialog.setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); });
            if (ConfigData.isEnablePreventMisoperation()) {
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        mainHandler.postDelayed(() -> dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), 1000);
                    }
                });
            }
            dialog.show();
        } catch (Exception ignored) {
        }
    }
}
