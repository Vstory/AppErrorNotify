
package com.vstory.apperrors.ui.activity.errors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewKt;

import com.vstory.apperrors.R;
import com.vstory.apperrors.bean.AppErrorsInfoBean;
import com.vstory.apperrors.data.ConfigData;
import com.vstory.apperrors.data.factory.CompoundButtonFactoryKt;
import com.vstory.apperrors.databinding.ActivityAppErrorsDetailBinding;
import com.vstory.apperrors.locale.LocaleFactoryKt;
import com.vstory.apperrors.ui.activity.base.BaseActivity;
import com.vstory.apperrors.utils.factory.DialogBuilder;
import com.vstory.apperrors.utils.factory.DialogBuilderFactoryKt;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;
import com.vstory.apperrors.utils.tool.ModuleLogger;
import com.vstory.apperrors.utils.tool.StackTraceShareHelper;
import com.vstory.apperrors.wrapper.BuildConfigWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;


public class AppErrorsDetailActivity extends BaseActivity<ActivityAppErrorsDetailBinding> {

    
    private static final int WRITE_REQUEST_CODE = 0;

    
    private static final String EXTRA_APP_ERRORS_INFO = "app_errors_info_extra";

    
    public static final Companion Companion = new Companion();

    public static class Companion {
        public void start(Context context, AppErrorsInfoBean appErrorsInfo) {
            Intent intent = new Intent(context, AppErrorsDetailActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(EXTRA_APP_ERRORS_INFO, appErrorsInfo);
            context.startActivity(intent);
        }
    }

    
    private String stackTrace = "";

    @Override
    protected void onCreate() {
        if (!initUi(getIntent())) return;
        binding.titleBackIcon.setOnClickListener(v -> onBackPressed());
        CompoundButtonFactoryKt.bind(binding.disableAutoWrapErrorStackTraceSwitch,
                () -> ConfigData.isDisableAutoWrapErrorStackTrace(),
                value -> ConfigData.setDisableAutoWrapErrorStackTrace(value),
                binder -> {
                    binder.onInitialize(checked -> {
                        ViewKt.setVisible(binding.errorStackTraceScrollView, checked);
                        ViewKt.setGone(binding.errorStackTraceFixedText, checked);
                    });
                    binder.onChanged(checked -> {
                        binder.reinitialize();
                        resetScrollView();
                    });
                });
        binding.detailTitleText.setOnClickListener(v -> binding.appPanelScrollView.smoothScrollTo(0, 0));
        resetScrollView();
    }

    
    private boolean initUi(Intent intent) {
        AppErrorsInfoBean parsedInfo = null;
        try {
            Object extra = intent != null ? FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_APP_ERRORS_INFO) : null;
            if (extra instanceof AppErrorsInfoBean) parsedInfo = (AppErrorsInfoBean) extra;
        } catch (Exception ignored) {
        }
        final AppErrorsInfoBean appErrorsInfo = parsedInfo;
        if (appErrorsInfo == null) {
            toastAndFinish("AppErrorsInfo");
            return false;
        }
        if (appErrorsInfo.isEmpty()) {
            ViewKt.setVisible(binding.appPanelScrollView, false);
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getUnableGetAppErrorsRecordTip());
            dlg.confirmButton(LocaleFactoryKt.getLocale().getGotIt(), () -> {
                dlg.cancel();
                finish();
            });
            dlg.cancelButton(LocaleFactoryKt.getLocale().getGoItNow(), () -> {
                dlg.cancel();
                finish();
                Intent nav = new Intent(this, AppErrorsRecordActivity.class);
                nav.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(nav);
            });
            dlg.noCancelable();
            dlg.show();
            return false;
        }
        binding.appInfoItem.setOnClickListener(v -> FunctionFactoryKt.openSelfSetting(this, appErrorsInfo.packageName));
        binding.printIcon.setOnClickListener(v -> {
            Log.e("AppErrorNotify", appErrorsInfo.stackTrace);
            ModuleLogger.log("E", "AppErrorNotify", appErrorsInfo.stackTrace, null);
            FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getPrintToLogcatSuccess());
        });
        binding.copyIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getCopyErrorStack(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) ->
                            FunctionFactoryKt.copyToClipboard(this,
                                    appErrorsInfo.stackOutputShareContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName)));
        });
        binding.exportIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getExportToFile(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) -> {
                        stackTrace = appErrorsInfo.stackOutputFileContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
                        try {
                            Intent intent2 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent2.addCategory(Intent.CATEGORY_OPENABLE);
                            intent2.setType("*/*");
                            String packageName = sPackageName ? appErrorsInfo.packageName : "anonymous";
                            intent2.putExtra(Intent.EXTRA_TITLE, packageName + "_" + appErrorsInfo.getFileNameTime() + ".log");
                            startActivityForResult(intent2, WRITE_REQUEST_CODE);
                        } catch (Exception e) {
                            FunctionFactoryKt.toast(this, "Start Android SAF failed");
                        }
                    });
        });
        binding.shareIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getShareErrorStack(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) -> {
                        String content = appErrorsInfo.stackOutputShareContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
                        Intent share = new Intent(Intent.ACTION_SEND);
                        if (ConfigData.isShareWithFile()) {
                            share.setType("application/octet-stream");
                            try {
                                File file = File.createTempFile("app_errors_stacktrace_", ".log", getCacheDir());
                                file.deleteOnExit();
                                FileWriter writer = new FileWriter(file);
                                writer.write(content);
                                writer.close();
                                share.putExtra(Intent.EXTRA_STREAM,
                                        FileProvider.getUriForFile(this, getPackageName() + ".provider", file));
                            } catch (Exception e) {
                                FunctionFactoryKt.toast(this, "Create temp file failed");
                            }
                        } else {
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, content);
                        }
                        startActivity(Intent.createChooser(share, LocaleFactoryKt.getLocale().getShareErrorStack()));
                    });
        });
        binding.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, appErrorsInfo.packageName));
        String appName = FunctionFactoryKt.appNameOf(this, appErrorsInfo.packageName);
        binding.appNameText.setText(appName.trim().isEmpty() ? appErrorsInfo.packageName : appName);
        binding.appVersionText.setText(appErrorsInfo.getVersionBrand());
        ViewKt.setVisible(binding.appUserIdText, appErrorsInfo.userId > 0);
        binding.appUserIdText.setText(LocaleFactoryKt.getLocale().userId(appErrorsInfo.userId));
        binding.appCpuAbiText.setText(appErrorsInfo.cpuAbi.trim().isEmpty() ? LocaleFactoryKt.getLocale().getNoCpuAbi() : appErrorsInfo.cpuAbi);
        binding.appTargetSdkText.setText(LocaleFactoryKt.getLocale().appTargetSdk(appErrorsInfo.targetSdk));
        binding.appMinSdkText.setText(LocaleFactoryKt.getLocale().appMinSdk(appErrorsInfo.minSdk));
        ViewKt.setGone(binding.jvmErrorPanel, appErrorsInfo.isNativeCrash);
        binding.errorTypeIcon.setImageResource(appErrorsInfo.isNativeCrash ? R.drawable.ic_cpp : R.drawable.ic_java);
        binding.errorInfoText.setText(appErrorsInfo.exceptionMessage);
        binding.errorTypeText.setText(appErrorsInfo.exceptionClassName);
        binding.errorFileNameText.setText(appErrorsInfo.throwFileName);
        binding.errorThrowClassText.setText(appErrorsInfo.throwClassName);
        binding.errorThrowMethodText.setText(appErrorsInfo.throwMethodName);
        binding.errorLineNumberText.setText(String.valueOf(appErrorsInfo.throwLineNumber));
        binding.errorRecordTimeText.setText(appErrorsInfo.getDateTime());
        
        binding.errorVersionText.setText(appErrorsInfo.getVersionBrand());
        binding.errorPackageText.setText(appErrorsInfo.packageName);
        
        bindCopyValue(binding.errorInfoText);
        bindCopyValue(binding.errorTypeText);
        bindCopyValue(binding.errorFileNameText);
        bindCopyValue(binding.errorThrowClassText);
        bindCopyValue(binding.errorThrowMethodText);
        bindCopyValue(binding.errorLineNumberText);
        bindCopyValue(binding.errorRecordTimeText);
        bindCopyValue(binding.errorVersionText);
        bindCopyValue(binding.errorPackageText);
        
        bindCopyLabel(binding.errorVersionLabel, binding.errorVersionText);
        bindCopyLabel(binding.errorPackageLabel, binding.errorPackageText);
        bindCopyLabel(binding.errorInfoLabel, binding.errorInfoText);
        bindCopyLabel(binding.errorTypeLabel, binding.errorTypeText);
        bindCopyLabel(binding.errorFileNameLabel, binding.errorFileNameText);
        bindCopyLabel(binding.errorThrowClassLabel, binding.errorThrowClassText);
        bindCopyLabel(binding.errorThrowMethodLabel, binding.errorThrowMethodText);
        bindCopyLabel(binding.errorLineNumberLabel, binding.errorLineNumberText);
        bindCopyLabel(binding.errorRecordTimeLabel, binding.errorRecordTimeText);
        
        binding.stackCopyButton.setOnClickListener(v -> {
            FunctionFactoryKt.copyToClipboard(this, appErrorsInfo.stackTrace);
        });
        binding.errorStackTraceMovableText.setText(buildStyledStackTrace(appErrorsInfo.stackTrace));
        binding.errorStackTraceFixedText.setText(buildStyledStackTrace(appErrorsInfo.stackTrace));
        binding.appPanelScrollView.setOnScrollChangeListener(new android.view.View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(android.view.View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                String n = FunctionFactoryKt.appNameOf(AppErrorsDetailActivity.this, appErrorsInfo.packageName);
                binding.detailTitleText.setText(scrollY >= FunctionFactoryKt.dp(30, AppErrorsDetailActivity.this)
                        ? (n.trim().isEmpty() ? appErrorsInfo.packageName : n) : LocaleFactoryKt.getLocale().getAppName());
            }
        });
        return true;
    }

    
    private CharSequence buildStyledStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) return "";
        SpannableString spannable = new SpannableString(stackTrace);
        int firstLineEnd = stackTrace.indexOf('\n');
        int headEnd = firstLineEnd > 0 ? firstLineEnd : stackTrace.length();
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorStackError)), 0, headEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, headEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (firstLineEnd > 0) {
            spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorTextGray)), firstLineEnd, stackTrace.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    
    private void bindCopyValue(android.widget.TextView view) {
        view.setOnClickListener(v -> {
            String value = view.getText() == null ? "" : view.getText().toString();
            if (!value.trim().isEmpty()) FunctionFactoryKt.copyToClipboard(this, value);
        });
    }

    
    private void bindCopyLabel(android.widget.TextView label, android.widget.TextView valueView) {
        label.setOnClickListener(v -> {
            String value = valueView.getText() == null ? "" : valueView.getText().toString();
            if (value.trim().isEmpty()) return;
            String labelText = label.getText() == null ? "" : label.getText().toString();
            FunctionFactoryKt.copyToClipboard(this, labelText + "：" + value);
        });
    }

    
    private void resetScrollView() {
        binding.rootView.post(() -> {
            binding.appPanelScrollView.scrollTo(0, 0);
            binding.errorStackTraceScrollView.scrollTo(0, 0);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                if (data != null && data.getData() != null) {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) {
                        os.write(stackTrace.getBytes());
                        os.close();
                    }
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getOutputStackSuccess());
                } else {
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getOutputStackFail());
                }
            } catch (Exception e) {
                FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getOutputStackFail());
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (getIntent() != null) getIntent().removeExtra(EXTRA_APP_ERRORS_INFO);
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (initUi(intent)) binding.appPanelScrollView.scrollTo(0, 0);
    }
}
