package io.github.vstory.apperrors.ui.activity.errors;

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

import io.github.vstory.apperrors.R;
import io.github.vstory.apperrors.bean.AppErrorsInfoBean;
import io.github.vstory.apperrors.data.AppErrorsRecordData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.data.factory.CompoundButtonFactoryKt;
import io.github.vstory.apperrors.databinding.ActivityAppErrorsDetailBinding;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.factory.DialogBuilder;
import io.github.vstory.apperrors.utils.factory.DialogBuilderFactoryKt;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;
import io.github.vstory.apperrors.utils.tool.ModuleLogger;
import io.github.vstory.apperrors.utils.tool.StackTraceShareHelper;
import io.github.vstory.apperrors.wrapper.BuildConfigWrapper;

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

    /** 当前展示的记录；列表页传入的是摘要（stackTrace 已剥离），补拉完整记录后重渲染 */
    private AppErrorsInfoBean info;

    @Override
    protected void onCreate() {
        info = parseInfo(getIntent());
        if (info == null) {
            toastAndFinish("AppErrorsInfo");
            return;
        }
        if (info.isEmpty()) {
            showEmptyInfoDialog();
            return;
        }
        if (info.stackTrace == null) info.stackTrace = "";
        if (info.stackTrace.isEmpty() && info.recordKey != null && !info.recordKey.isEmpty()) {
            loadFullInfo();
            return;
        }
        render();
    }

    private AppErrorsInfoBean parseInfo(Intent intent) {
        try {
            Object extra = intent != null ? FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_APP_ERRORS_INFO) : null;
            if (extra instanceof AppErrorsInfoBean) return (AppErrorsInfoBean) extra;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showEmptyInfoDialog() {
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
    }

    /**
     * 列表页传给详情页的是摘要（stackTrace 为空），完整记录要按 recordKey 单取一条；
     * 拉不到时仍按摘要渲染，头部信息可用，只在堆栈区提示。
     */
    private void loadFullInfo() {
        AppErrorsRecordData.requestErrorDetail(this, info.recordKey, (ctx, intent) -> {
            AppErrorsInfoBean full = AppErrorsRecordData.beanFromResult(intent);
            if (full != null) {
                info = full;
                render();
                return;
            }
            FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getUnableGetAppErrorsRecordTip());
            render();
            binding.errorStackTraceMovableText.setText(getString(R.string.system_service_not_ready_tip));
            binding.errorStackTraceFixedText.setText(getString(R.string.system_service_not_ready_tip));
        });
    }

    private void render() {
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
        binding.appInfoItem.setOnClickListener(v -> FunctionFactoryKt.openSelfSetting(this, info.packageName));
        binding.printIcon.setOnClickListener(v -> {
            Log.e("AppErrorNotify", info.stackTrace);
            ModuleLogger.log("E", "AppErrorNotify", info.stackTrace, null);
            FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getPrintToLogcatSuccess());
        });
        binding.copyIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getCopyErrorStack(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) ->
                            FunctionFactoryKt.copyToClipboardMarkdown(this,
                                    info.stackOutputShareContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName)));
        });
        binding.exportIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getExportToFile(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) -> {
                        stackTrace = info.stackOutputFileContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
                        try {
                            Intent intent2 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent2.addCategory(Intent.CATEGORY_OPENABLE);
                            intent2.setType("*/*");
                            String packageName = sPackageName ? info.packageName : "anonymous";
                            intent2.putExtra(Intent.EXTRA_TITLE, packageName + "_" + info.getFileNameTime() + ".log");
                            startActivityForResult(intent2, WRITE_REQUEST_CODE);
                        } catch (Exception e) {
                            FunctionFactoryKt.toast(this, "Start Android SAF failed");
                        }
                    });
        });
        binding.shareIcon.setOnClickListener(v -> {
            StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getShareErrorStack(),
                    (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) -> {
                        String content = info.stackOutputShareContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
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
        binding.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, info.packageName));
        String appName = FunctionFactoryKt.appNameOf(this, info.packageName);
        binding.appNameText.setText(appName.trim().isEmpty() ? info.packageName : appName);
        binding.appVersionText.setText(info.getVersionBrand());
        ViewKt.setVisible(binding.appUserIdText, info.userId > 0);
        binding.appUserIdText.setText(LocaleFactoryKt.getLocale().userId(info.userId));
        binding.appCpuAbiText.setText(info.cpuAbi.trim().isEmpty() ? LocaleFactoryKt.getLocale().getNoCpuAbi() : info.cpuAbi);
        binding.appTargetSdkText.setText(LocaleFactoryKt.getLocale().appTargetSdk(info.targetSdk));
        binding.appMinSdkText.setText(LocaleFactoryKt.getLocale().appMinSdk(info.minSdk));
        ViewKt.setGone(binding.jvmErrorPanel, info.isNativeCrash);
        binding.errorTypeIcon.setImageResource(info.isNativeCrash ? R.drawable.ic_cpp : R.drawable.ic_java);
        binding.errorInfoText.setText(info.exceptionMessage);
        binding.errorTypeText.setText(info.exceptionClassName);
        binding.errorFileNameText.setText(info.throwFileName);
        binding.errorThrowClassText.setText(info.throwClassName);
        binding.errorThrowMethodText.setText(info.throwMethodName);
        binding.errorLineNumberText.setText(String.valueOf(info.throwLineNumber));
        binding.errorRecordTimeText.setText(info.getDateTime());
        binding.errorVersionText.setText(info.getVersionBrand());
        binding.errorPackageText.setText(info.packageName);
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
        String runningPage = info.getRunningActivity();
        boolean hasPageRow = (runningPage != null && !runningPage.trim().isEmpty()) || info.isBackgroundCrash();
        ViewKt.setGone(binding.errorPageRow, !hasPageRow);
        if (hasPageRow) {
            binding.errorPageText.setText(runningPage != null && !runningPage.trim().isEmpty()
                    ? runningPage.trim()
                    : LocaleFactoryKt.getLocale().getErrorPageBackground());
            bindCopyValue(binding.errorPageText);
            bindCopyLabel(binding.errorPageLabel, binding.errorPageText);
        }
        binding.stackCopyButton.setOnClickListener(v -> {
            FunctionFactoryKt.copyToClipboardMarkdown(this, info.stackTrace);
        });
        binding.errorStackTraceMovableText.setText(buildStyledStackTrace(info.stackTrace, info.isNativeCrash));
        binding.errorStackTraceFixedText.setText(buildStyledStackTrace(info.stackTrace, info.isNativeCrash));
        binding.appPanelScrollView.setOnScrollChangeListener(new android.view.View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(android.view.View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                String n = FunctionFactoryKt.appNameOf(AppErrorsDetailActivity.this, info.packageName);
                binding.detailTitleText.setText(scrollY >= FunctionFactoryKt.dp(30, AppErrorsDetailActivity.this)
                        ? (n.trim().isEmpty() ? info.packageName : n) : LocaleFactoryKt.getLocale().getErrorDetailView());
            }
        });
    }

    private CharSequence buildStyledStackTrace(String stackTrace, boolean isNativeCrash) {
        if (stackTrace == null || stackTrace.isEmpty()) return "";
        SpannableString spannable = new SpannableString(stackTrace);
        int colorError = ContextCompat.getColor(this, R.color.colorStackError);
        int colorGray = ContextCompat.getColor(this, R.color.colorTextGray);
        if (!isNativeCrash) {
            int firstLineEnd = stackTrace.indexOf('\n');
            int headEnd = firstLineEnd > 0 ? firstLineEnd : stackTrace.length();
            spannable.setSpan(new ForegroundColorSpan(colorError), 0, headEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, headEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (firstLineEnd > 0) {
                spannable.setSpan(new ForegroundColorSpan(colorGray), firstLineEnd, stackTrace.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannable;
        }
        int[] signalRange = locateNativeSignalLine(stackTrace);
        if (signalRange == null) {
            spannable.setSpan(new ForegroundColorSpan(colorGray), 0, stackTrace.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
        }
        if (signalRange[0] > 0) {
            spannable.setSpan(new ForegroundColorSpan(colorGray), 0, signalRange[0], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        spannable.setSpan(new ForegroundColorSpan(colorError), signalRange[0], signalRange[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), signalRange[0], signalRange[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (signalRange[1] < stackTrace.length()) {
            spannable.setSpan(new ForegroundColorSpan(colorGray), signalRange[1], stackTrace.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private int[] locateNativeSignalLine(String stackTrace) {
        int lineStart = 0;
        while (lineStart <= stackTrace.length()) {
            int lineEnd = stackTrace.indexOf('\n', lineStart);
            int end = lineEnd == -1 ? stackTrace.length() : lineEnd;
            String line = stackTrace.substring(lineStart, end);
            if (line.matches("signal\\s+\\d+\\s*\\([^)]*\\)\\s*,?.*")) {
                return new int[]{lineStart, end};
            }
            if (lineEnd == -1) break;
            lineStart = lineEnd + 1;
        }
        return null;
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

    // 小屏 setTextIsSelectable 会导致布局自滑，故关掉
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
        AppErrorsInfoBean next = parseInfo(intent);
        if (next == null || next.isEmpty()) return;
        info = next;
        binding.appPanelScrollView.scrollTo(0, 0);
        if (info.stackTrace == null) info.stackTrace = "";
        if (info.stackTrace.isEmpty() && info.recordKey != null && !info.recordKey.isEmpty()) {
            loadFullInfo();
            return;
        }
        render();
    }
}
