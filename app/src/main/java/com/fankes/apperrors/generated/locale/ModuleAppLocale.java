/*
 * AppErrorsTracking (api102 重构版) - 自动生成的国际化访问类 (Java 化)
 */
package com.fankes.apperrors.generated.locale;

import android.content.Context;
import android.content.res.Resources;

import com.fankes.apperrors.R;

import java.util.Locale;

import kotlin.jvm.functions.Function0;

/** 国际化访问类（原 ModuleAppLocale.kt，自动生成） */
public class ModuleAppLocale {

    private final Function0<Resources> provider;

    private static volatile ModuleAppLocale instance;

    public static ModuleAppLocale attach(Context context) {
        return new ModuleAppLocale(() -> context.getResources());
    }

    public static ModuleAppLocale attach(Function0<Resources> provider) {
        return new ModuleAppLocale(provider);
    }

    private ModuleAppLocale(Function0<Resources> provider) {
        this.provider = provider;
    }

    private Resources res() {
        return provider.invoke();
    }

    private String str(int resId) {
        Resources r = res();
        return r != null ? r.getString(resId) : "";
    }

    private String str(int resId, Object... args) {
        Resources r = res();
        return r != null ? r.getString(resId, args) : "";
    }

    /** 系统语言（如 zh / en） */
    public String getLanguage() { return Locale.getDefault().getLanguage(); }

    /** 系统国家/地区（如 CN / US） */
    public String getCountry() { return Locale.getDefault().getCountry(); }

    public String getAccessRootFail() { return str(R.string.access_root_fail); }
    public String getAccessRootFailTip() { return str(R.string.access_root_fail_tip); }
    public String getAllErrorsClearSuccess() { return str(R.string.all_errors_clear_success); }
    public String getAppErrorsStatistics() { return str(R.string.app_errors_statistics); }
    public String getAppErrorsTip() { return str(R.string.app_errors_tip); }
    public String getAppName() { return str(R.string.app_name); }
    public String getAreYouSureClearErrors() { return str(R.string.are_you_sure_clear_errors); }
    public String getAreYouSureExportAllErrors() { return str(R.string.are_you_sure_export_all_errors); }
    public String getAreYouSureRemoveRecord() { return str(R.string.are_you_sure_remove_record); }
    public String getAreYouSureUnmuteAll() { return str(R.string.are_you_sure_unmute_all); }
    public String getAreYourSureRestartSystem() { return str(R.string.are_your_sure_restart_system); }
    public String getCancel() { return str(R.string.cancel); }
    public String getCiNoticeDialogTitle() { return str(R.string.ci_notice_dialog_title); }
    public String getClearFilters() { return str(R.string.clear_filters); }
    public String getConfirm() { return str(R.string.confirm); }
    public String getCopied() { return str(R.string.copied); }
    public String getCopyErrorStack() { return str(R.string.copy_error_stack); }
    public String getCopyFail() { return str(R.string.copy_fail); }
    public String getDayAgo() { return str(R.string.day_ago); }
    public String getDeveloperNotice() { return str(R.string.developer_notice); }
    public String getDeveloperNoticeTip() { return str(R.string.developer_notice_tip); }
    public String getExportAll() { return str(R.string.export_all); }
    public String getExportAllErrorsFail() { return str(R.string.export_all_errors_fail); }
    public String getExportAllErrorsSuccess() { return str(R.string.export_all_errors_success); }
    public String getExportAllLogsFail() { return str(R.string.export_all_logs_fail); }
    public String getExportAllLogsSuccess() { return str(R.string.export_all_logs_success); }
    public String getExportToFile() { return str(R.string.export_to_file); }
    public String getFastRestart() { return str(R.string.fast_restart); }
    public String getFastRestartProblem() { return str(R.string.fast_restart_problem); }
    public String getFilterByCondition() { return str(R.string.filter_by_condition); }
    public String getFollowGlobalConfig() { return str(R.string.follow_global_config); }
    public String getGeneratingStatistics() { return str(R.string.generating_statistics); }
    public String getGlobalConfig() { return str(R.string.global_config); }
    public String getGoItNow() { return str(R.string.go_it_now); }
    public String getGotIt() { return str(R.string.got_it); }
    public String getHourAgo() { return str(R.string.hour_ago); }
    public String getLoading() { return str(R.string.loading); }
    public String getMinuteAgo() { return str(R.string.minute_ago); }
    public String getModuleIsActivated() { return str(R.string.module_is_activated); }
    public String getModuleNotActivated() { return str(R.string.module_not_activated); }
    public String getModuleNotFullyActivated() { return str(R.string.module_not_fully_activated); }
    public String getModuleNotFullyActivatedTip() { return str(R.string.module_not_fully_activated_tip); }
    public String getMomentAgo() { return str(R.string.moment_ago); }
    public String getMonthAgo() { return str(R.string.month_ago); }
    public String getMore() { return str(R.string.more); }
    public String getNotificationIgnoreApp() { return str(R.string.notification_ignore_app); }
    public String getNotificationViewInfo() { return str(R.string.notification_view_info); }
    public String getMuteIfRestart() { return str(R.string.mute_if_restart); }
    public String getMuteIfUnlock() { return str(R.string.mute_if_unlock); }
    public String getMuteIgnoreBehaviorTitle() { return str(R.string.mute_ignore_behavior_title); }
    public String getMuteIgnoreBehaviorUnlock() { return str(R.string.mute_ignore_behavior_unlock); }
    public String getMuteIgnoreBehaviorRestart() { return str(R.string.mute_ignore_behavior_restart); }
    public String getMuteIgnoreBehaviorTip() { return str(R.string.mute_ignore_behavior_tip); }
    public String getNoCpuAbi() { return str(R.string.no_cpu_abi); }
    public String getNoListData() { return str(R.string.no_list_data); }
    public String getNoListResult() { return str(R.string.no_list_result); }
    public String getNotice() { return str(R.string.notice); }
    public String getOutputStackFail() { return str(R.string.output_stack_fail); }
    public String getOutputStackSuccess() { return str(R.string.output_stack_success); }
    public String getPrintToLogcatSuccess() { return str(R.string.print_to_logcat_success); }
    public String getSecondAgo() { return str(R.string.second_ago); }
    public String getShareErrorStack() { return str(R.string.share_error_stack); }
    public String getShowErrorsNotify() { return str(R.string.show_errors_notify); }
    public String getShowErrorsToast() { return str(R.string.show_errors_toast); }
    public String getShowNothing() { return str(R.string.show_nothing); }
    public String getUnableGetAppErrorsRecordTip() { return str(R.string.unable_get_app_errors_record_tip); }
    public String getUpdateNow() { return str(R.string.update_now); }
    public String getWarning() { return str(R.string.warning); }
    public String getYearAgo() { return str(R.string.year_ago); }

    public String aerrRepeatedTitle(String p0) { return str(R.string.aerr_repeated_title, p0); }
    public String aerrTitle(String p0) { return str(R.string.aerr_title, p0); }
    public String appMinSdk(int p0) { return str(R.string.app_min_sdk, p0); }
    public String appTargetSdk(int p0) { return str(R.string.app_target_sdk, p0); }
    public String areYouSureApplySiteApps(int p0) { return str(R.string.are_you_sure_apply_site_apps, p0); }
    public String batchOperationsNumber(int p0) { return str(R.string.batch_operations_number, p0); }
    public String ciNoticeDialogContent(String p0) { return str(R.string.ci_notice_dialog_content, p0); }
    public String clickToUpdate(String p0) { return str(R.string.click_to_update, p0); }
    public String crashProcess(String p0) { return str(R.string.crash_process, p0); }
    public String latestVersion(String p0) { return str(R.string.latest_version, p0); }
    public String latestVersionTip(String p0, String p1) { return str(R.string.latest_version_tip, p0, p1); }
    public String moduleVersion(String p0) { return str(R.string.module_version, p0); }
    public String muteIfRestartTip(String p0) { return str(R.string.mute_if_restart_tip, p0); }
    public String muteIfUnlockTip(String p0) { return str(R.string.mute_if_unlock_tip, p0); }
    public String recordCount(int p0) { return str(R.string.record_count, p0); }
    public String resultCount(int p0) { return str(R.string.result_count, p0); }
    public String systemVersion(String p0) { return str(R.string.system_version, p0); }
    public String totalAppsUnit(int p0) { return str(R.string.total_apps_unit, p0); }
    public String totalErrorsUnit(int p0) { return str(R.string.total_errors_unit, p0); }
    public String userId(int p0) { return str(R.string.user_id, p0); }
}
