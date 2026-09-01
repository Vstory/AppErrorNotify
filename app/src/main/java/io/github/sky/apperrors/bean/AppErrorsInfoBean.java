
package io.github.sky.apperrors.bean;

import android.app.ApplicationErrorReport;
import android.content.Context;
import android.os.Build;

import io.github.sky.apperrors.constants.ModuleVersion;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AppErrorsInfoBean implements Serializable {

    
    private static final long serialVersionUID = -6072706396475104321L;

    @SerializedName("pid") public int pid = -1;
    @SerializedName("userId") public int userId = -1;
    @SerializedName("cpuAbi") public String cpuAbi = "";
    @SerializedName("packageName") public String packageName = "";
    @SerializedName("versionName") public String versionName = "";
    @SerializedName("versionCode") public long versionCode = -1L;
    @SerializedName("targetSdk") public int targetSdk = -1;
    @SerializedName("minSdk") public int minSdk = -1;
    @SerializedName("isNativeCrash") public boolean isNativeCrash = false;
    @SerializedName("exceptionClassName") public String exceptionClassName = "";
    @SerializedName("exceptionMessage") public String exceptionMessage = "";
    @SerializedName("throwFileName") public String throwFileName = "";
    @SerializedName("throwClassName") public String throwClassName = "";
    @SerializedName("throwMethodName") public String throwMethodName = "";
    @SerializedName("throwLineNumber") public int throwLineNumber = -1;
    @SerializedName("stackTrace") public String stackTrace = "";
    @SerializedName("timestamp") public long timestamp = -1L;

    public AppErrorsInfoBean() {
    }

    
    public static AppErrorsInfoBean clone(Context context, int pid, int userId, String packageName,
                                          ApplicationErrorReport.CrashInfo crashInfo) {
        boolean isNativeCrash = crashInfo != null && crashInfo.exceptionClassName != null
                && crashInfo.exceptionClassName.toLowerCase(Locale.ROOT).equals("native crash");
        AppErrorsInfoBean bean = new AppErrorsInfoBean();
        bean.pid = pid;
        bean.userId = userId;
        bean.cpuAbi = packageName != null ? FunctionFactoryKt.appCpuAbiOf(context, packageName) : "";
        bean.packageName = packageName != null ? packageName : "unknown";
        bean.versionName = packageName != null
                ? (isBlank(FunctionFactoryKt.appVersionNameOf(context, packageName)) ? "unknown" : FunctionFactoryKt.appVersionNameOf(context, packageName))
                : "";
        bean.versionCode = packageName != null ? FunctionFactoryKt.appVersionCodeOf(context, packageName) : -1L;
        bean.targetSdk = packageName != null ? FunctionFactoryKt.appTargetSdkOf(context, packageName) : -1;
        bean.minSdk = packageName != null ? FunctionFactoryKt.appMinSdkOf(context, packageName) : -1;
        bean.isNativeCrash = isNativeCrash;
        bean.exceptionClassName = crashInfo != null && crashInfo.exceptionClassName != null ? crashInfo.exceptionClassName : "unknown";
        bean.exceptionMessage = resolveExceptionMessage(isNativeCrash, crashInfo);
        bean.throwFileName = crashInfo != null && crashInfo.throwFileName != null ? crashInfo.throwFileName : "unknown";
        bean.throwClassName = crashInfo != null && crashInfo.throwClassName != null ? crashInfo.throwClassName : "unknown";
        bean.throwMethodName = crashInfo != null && crashInfo.throwMethodName != null ? crashInfo.throwMethodName : "unknown";
        bean.throwLineNumber = crashInfo != null ? crashInfo.throwLineNumber : -1;
        bean.stackTrace = crashInfo != null && crashInfo.stackTrace != null ? crashInfo.stackTrace.trim() : "unknown";
        bean.timestamp = System.currentTimeMillis();
        return bean;
    }

    private static String resolveExceptionMessage(boolean isNativeCrash, ApplicationErrorReport.CrashInfo crashInfo) {
        String fallback = crashInfo != null && crashInfo.exceptionMessage != null ? crashInfo.exceptionMessage : "unknown";
        if (!isNativeCrash) return fallback;
        String stack = crashInfo != null ? crashInfo.stackTrace : null;
        if (stack != null && stack.contains("Abort message: '")) {
            try {
                return stack.split("Abort message: '")[1].split("'")[0];
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    
    public boolean isEmpty() {
        return pid == -1 && userId == -1 && timestamp == -1L;
    }

    
    public String getJsonFileName() {
        return getFileNameTime() + "_" + packageName + ".json";
    }

    
    public String getFileNameTime() {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    
    public String getVersionBrand() {
        return isBlank(versionName) ? "unknown" : versionName + "(" + versionCode + ")";
    }

    
    public String getUtcTime() {
        return FunctionFactoryKt.toUtcTime(timestamp);
    }

    
    public String getCrossTime() {
        return FunctionFactoryKt.difference(timestamp,
                LocaleFactoryKt.getLocale().getMomentAgo(),
                LocaleFactoryKt.getLocale().getSecondAgo(),
                LocaleFactoryKt.getLocale().getMinuteAgo(),
                LocaleFactoryKt.getLocale().getHourAgo(),
                LocaleFactoryKt.getLocale().getDayAgo(),
                LocaleFactoryKt.getLocale().getMonthAgo(),
                LocaleFactoryKt.getLocale().getYearAgo());
    }

    
    public String getDateTime() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(timestamp));
        } catch (Exception e) {
            return getUtcTime();
        }
    }

    
    public String stackOutputShareContent(boolean sDeviceBrand, boolean sDeviceModel, boolean sDisplay, boolean sPackageName) {
        return "Generated by AppErrorNotify " + ModuleVersion.INSTANCE
                + "\n" + "=================================================="
                + "\n" + environmentInfo(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
    }

    
    public String stackOutputFileContent(boolean sDeviceBrand, boolean sDeviceModel, boolean sDisplay, boolean sPackageName) {
        return "Generated by AppErrorNotify " + ModuleVersion.INSTANCE
                + "\n" + "================================================================"
                + "\n" + environmentInfo(sDeviceBrand, sDeviceModel, sDisplay, sPackageName);
    }

    
    private String environmentInfo(boolean sDeviceBrand, boolean sDeviceModel, boolean sDisplay, boolean sPackageName) {
        String display = by(Build.DISPLAY, sDisplay);
        return "[Device]: " + by(Build.BRAND, sDeviceBrand) + " " + by(Build.MODEL, sDeviceModel)
                + (isBlank(display) ? "" : " (" + display + ")")
                + "\n[Android]: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\n[System Locale]: " + Locale.getDefault()
                + "\n[Process]: pid " + pid + " / uid " + userId
                + "\n[CPU ABI]: " + (isBlank(cpuAbi) ? "none" : cpuAbi)
                + "\n[Package Name]: " + by(packageName, sPackageName)
                + "\n[Version]: " + versionText()
                + "\n[SDK]: target " + (targetSdk != -1 ? targetSdk : "unknown") + " / min " + (minSdk != -1 ? minSdk : "unknown")
                + "\n[Error Type]: " + (isNativeCrash ? "Native" : "JVM")
                + "\n[Crash Time]: " + getUtcTime()
                + "\n[Stack Trace]:\n" + stackTrace;
    }

    
    private String versionText() {
        String name = isBlank(versionName) ? "unknown" : versionName;
        String code = versionCode != -1L ? String.valueOf(versionCode) : "unknown";
        if ("unknown".equals(name) && "unknown".equals(code)) return "unknown";
        return name + " (" + code + ")";
    }

    
    private static String by(String s, boolean isDisplay) {
        return isDisplay ? s : "***";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getCpuAbi() { return cpuAbi; }
    public void setCpuAbi(String cpuAbi) { this.cpuAbi = cpuAbi; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public long getVersionCode() { return versionCode; }
    public void setVersionCode(long versionCode) { this.versionCode = versionCode; }
    public int getTargetSdk() { return targetSdk; }
    public void setTargetSdk(int targetSdk) { this.targetSdk = targetSdk; }
    public int getMinSdk() { return minSdk; }
    public void setMinSdk(int minSdk) { this.minSdk = minSdk; }
    public boolean isNativeCrash() { return isNativeCrash; }
    public void setNativeCrash(boolean nativeCrash) { isNativeCrash = nativeCrash; }
    public String getExceptionClassName() { return exceptionClassName; }
    public void setExceptionClassName(String exceptionClassName) { this.exceptionClassName = exceptionClassName; }
    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
    public String getThrowFileName() { return throwFileName; }
    public void setThrowFileName(String throwFileName) { this.throwFileName = throwFileName; }
    public String getThrowClassName() { return throwClassName; }
    public void setThrowClassName(String throwClassName) { this.throwClassName = throwClassName; }
    public String getThrowMethodName() { return throwMethodName; }
    public void setThrowMethodName(String throwMethodName) { this.throwMethodName = throwMethodName; }
    public int getThrowLineNumber() { return throwLineNumber; }
    public void setThrowLineNumber(int throwLineNumber) { this.throwLineNumber = throwLineNumber; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
