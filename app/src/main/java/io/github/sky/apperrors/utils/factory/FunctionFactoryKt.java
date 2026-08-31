/*
 * AppErrorsTracking - 通用函数工厂 (Java 化, 保持 FunctionFactoryKt 类名兼容 Kotlin 调用)
 * 原 Kotlin 顶层扩展函数 → Java 静态方法, receiver 变第一参数
 */
package io.github.sky.apperrors.utils.factory;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.IconCompat;

import io.github.sky.apperrors.R;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.utils.tool.ModuleLogger;
import io.github.sky.apperrors.wrapper.BuildConfigWrapper;
import com.google.android.material.snackbar.Snackbar;
import com.topjohnwu.superuser.Shell;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** 通用函数工厂（原 FunctionFactory.kt 顶层函数/属性） */
public class FunctionFactoryKt {

    private FunctionFactoryKt() {}

    /** 当前系统环境是否为简体中文 */
    public static boolean isSystemLanguageSimplifiedChinese() {
        Locale locale = Locale.getDefault();
        return locale.getLanguage().equals("zh") && locale.getCountry().equals("CN");
    }

    /** 系统深色模式是否开启 */
    public static boolean isSystemInDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 系统深色模式是否没开启 */
    public static boolean isNotSystemInDarkMode(Context context) {
        return !isSystemInDarkMode(context);
    }

    /** dp 转换为 pxInt */
    public static int dp(Number n, Context context) {
        return dpFloat(n, context).intValue();
    }

    /** dp 转换为 pxFloat */
    public static Float dpFloat(Number n, Context context) {
        return n.floatValue() * context.getResources().getDisplayMetrics().density;
    }

    /** 获取 Drawable */
    public static Drawable drawableOf(Resources res, @DrawableRes int resId) {
        Drawable d = ResourcesCompat.getDrawable(res, resId, null);
        if (d == null) throw new IllegalStateException("Invalid resources");
        return d;
    }

    /** 获取颜色 */
    public static int colorOf(Resources res, @ColorRes int resId) {
        return ResourcesCompat.getColor(res, resId, null);
    }

    /** 得到 APP 安装包信息 (兼容) */
    private static PackageInfo getPackageInfoCompat(Context context, String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                return context.getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0L));
            else
                return context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 得到 APP 版本号 (兼容) */
    private static long versionCodeCompat(PackageInfo info) {
        return PackageInfoCompat.getLongVersionCode(info);
    }

    /** 获取系统中全部已安装应用列表 */
    public static List<PackageInfo> listOfPackages(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                return context.getPackageManager().getInstalledPackages(PackageInfoFlags.of(PackageManager.GET_CONFIGURATIONS));
            else
                return context.getPackageManager().getInstalledPackages(PackageManager.GET_CONFIGURATIONS);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 得到 APP 名称 */
    public static String appNameOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        if (info == null || info.applicationInfo == null) return "";
        CharSequence label = info.applicationInfo.loadLabel(context.getPackageManager());
        return label != null ? label.toString() : "";
    }

    /** 得到 APP 版本信息与版本号 */
    public static String appVersionBrandOf(Context context, String packageName) {
        String name = appVersionNameOf(context, packageName);
        return !isBlank(name) ? name + "(" + appVersionCodeOf(context, packageName) + ")" : "";
    }

    /** 得到 APP 版本名称 */
    public static String appVersionNameOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        return info != null && info.versionName != null ? info.versionName : "";
    }

    /** 得到 APP 版本号 */
    public static long appVersionCodeOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        return info != null ? versionCodeCompat(info) : -1L;
    }

    /** 得到 APP 目标 SDK 版本 */
    public static int appTargetSdkOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        return info != null && info.applicationInfo != null ? info.applicationInfo.targetSdkVersion : -1;
    }

    /** 得到 APP 最低 SDK 版本 */
    public static int appMinSdkOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        return info != null && info.applicationInfo != null ? info.applicationInfo.minSdkVersion : -1;
    }

    /** 获取 APP CPU ABI 名称 */
    public static String appCpuAbiOf(Context context, String packageName) {
        try {
            PackageInfo info = getPackageInfoCompat(context, packageName);
            if (info == null || info.applicationInfo == null) return "";
            java.lang.reflect.Field f = info.applicationInfo.getClass().getDeclaredField("primaryCpuAbi");
            f.setAccessible(true);
            Object v = f.get(info.applicationInfo);
            return v instanceof String ? (String) v : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 得到 APP 图标 */
    public static Drawable appIconOf(Context context, String packageName) {
        PackageInfo info = getPackageInfoCompat(context, packageName);
        if (info != null && info.applicationInfo != null) {
            Drawable icon = info.applicationInfo.loadIcon(context.getPackageManager());
            if (icon != null) return icon;
        }
        return drawableOf(context.getResources(), R.drawable.ic_android);
    }

    /** 获取 Serializable (兼容) */
    public static <T extends Serializable> T getSerializableExtraCompat(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                return (T) intent.getSerializableExtra(key, Serializable.class);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return (T) intent.getSerializableExtra(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** List<T> 转换为 ArrayList<T> */
    public static <T> ArrayList<T> toArrayList(List<T> list) {
        return new ArrayList<>(list);
    }

    /** 计算与当前时间戳相差的友好时间 */
    public static String difference(long timestamp, String now, String second, String minute, String hour, String day, String month, String year) {
        long diff = (System.currentTimeMillis() - timestamp) / 1000;
        if (diff >= 0 && diff <= 10) return now;
        if (diff >= 11 && diff <= 20) return "10 " + second;
        if (diff >= 21 && diff <= 30) return "20 " + second;
        if (diff >= 31 && diff <= 40) return "30 " + second;
        if (diff >= 41 && diff <= 50) return "40 " + second;
        if (diff >= 51 && diff <= 59) return "50 " + second;
        if (diff >= 60 && diff <= 3599) return Math.max(diff / 60, 1) + " " + minute;
        if (diff >= 3600 && diff <= 86399) return diff / 3600 + " " + hour;
        if (diff >= 86400 && diff <= 2591999) return diff / 86400 + " " + day;
        if (diff >= 2592000 && diff <= 31103999) return diff / 2592000 + " " + month;
        return diff / 31104000 + " " + year;
    }

    /** 保留小数 */
    public static String decimal(Number n, int count) {
        try {
            String pattern;
            switch (count) {
                case 0: pattern = "0"; break;
                case 1: pattern = "0.0"; break;
                case 2: pattern = "0.00"; break;
                case 3: pattern = "0.000"; break;
                case 4: pattern = "0.0000"; break;
                case 5: pattern = "0.00000"; break;
                case 6: pattern = "0.000000"; break;
                case 7: pattern = "0.0000000"; break;
                default: pattern = "0.0"; break;
            }
            DecimalFormat df = new DecimalFormat(pattern);
            df.setRoundingMode(RoundingMode.HALF_UP);
            String out = df.format(n);
            return out != null ? out : n.toString();
        } catch (Exception e) {
            return n.toString();
        }
    }

    /** Long 转换为 UTC 时间 */
    public static String toUtcTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
    }

    /**
     * Long 转换为文件名安全时间（本地时区，无冒号，兼容文件系统）
     * 格式：yyyy-MM-dd_HH-mm-ss-SSS（如 2026-09-01_05-56-36-123）
     * 用于导出/分享的文件名，避免 toUtcTime 的冒号导致命名冲突。
     */
    public static String toFileNameTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault()).format(new Date(timestamp));
    }

    /** 弹出 Toast */
    public static void toast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            ModuleLogger.log("W", "AppErrorsTracking", msg, e);
        }
    }

    /** 弹出 Snackbar */
    public static void snake(Context context, String msg, String actionText, Runnable callback) {
        try {
            Snackbar snackbar = Snackbar.make(((Activity) context).findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG);
            if (!isBlank(actionText)) {
                snackbar.setActionTextColor(isSystemInDarkMode(context) ? Color.BLACK : Color.WHITE);
                snackbar.setAction(actionText, v -> callback.run());
            }
            snackbar.show();
        } catch (Exception ignored) {
        }
    }

    /** 推送通知 */
    public static void pushNotify(Context context, String channelId, String channelName, String title, String content,
                                  IconCompat icon, int color, Intent intent) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                manager.createNotificationChannel(new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH));
            Random random = new Random();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setColor(color)
                    .setAutoCancel(true)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(icon)
                    .setContentIntent(PendingIntent.getActivity(context, random.nextInt(1000), intent, PendingIntent.FLAG_IMMUTABLE))
                    .setDefaults(NotificationCompat.DEFAULT_ALL);
            manager.notify(random.nextInt(1000), builder.build());
        } catch (Exception ignored) {
        }
    }

    /** 跳转到指定页面 */
    public static <T extends Activity> void navigate(Context context, Class<T> clazz, boolean isOutSide, Function1<Intent, Unit> initiate) {
        try {
            Intent intent = isOutSide ? new Intent() : new Intent(context instanceof Service ? context.getApplicationContext() : context, clazz);
            intent.setFlags(context instanceof Activity ? Intent.FLAG_ACTIVITY_NEW_TASK : Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (isOutSide) intent.setComponent(new ComponentName(BuildConfigWrapper.APPLICATION_ID, clazz.getName()));
            if (initiate != null) initiate.invoke(intent);
            context.startActivity(intent);
        } catch (Exception e) {
            toast(context, "Start " + clazz.getName() + " failed");
        }
    }

    /** 复制到剪贴板 */
    public static void copyToClipboard(Context context, String content) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(null, content));
            CharSequence text = cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null
                    ? cm.getPrimaryClip().getItemAt(0).getText() : "";
            if (text == null || !text.toString().equals(content))
                toast(context, LocaleFactoryKt.getLocale().getCopyFail());
            else
                toast(context, LocaleFactoryKt.getLocale().getCopied());
        } catch (Exception ignored) {
        }
    }

    /** 跳转 APP 自身设置界面 */
    public static void openSelfSetting(Context context, String packageName) {
        try {
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
            context.startActivity(intent);
        } catch (Exception e) {
            toast(context, "Cannot open \"" + packageName + "\"");
        }
    }

    /** 启动系统浏览器 */
    public static void openBrowser(Context context, String url, String packageName) {
        try {
            Intent intent = new Intent();
            if (!isBlank(packageName)) intent.setPackage(packageName);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            if (!isBlank(packageName)) snake(context, "Cannot start \"" + packageName + "\"", "", () -> {});
            else snake(context, "Start system browser failed", "", () -> {});
        }
    }

    /** 当前 APP 是否可被启动 */
    public static boolean isAppCanOpened(Context context, String packageName) {
        try {
            return context.getPackageManager().getLaunchIntentForPackage(packageName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 启动指定 APP */
    public static void openApp(Context context, String packageName, int userId) {
        try {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) return;
            if (userId == 0) {
                context.startActivity(launchIntent);
            } else {
                Method startAsUser = Context.class.getMethod("startActivityAsUser", Intent.class, UserHandle.class);
                Method userHandleOf = UserHandle.class.getMethod("of", int.class);
                Object userHandle = userHandleOf.invoke(null, userId);
                startAsUser.invoke(context, launchIntent, userHandle);
            }
        } catch (Exception e) {
            toast(context, "Cannot start \"" + packageName + "\"" + (userId > 0 ? " for user " + userId : ""));
        }
    }

    /** 是否有 Root 权限 */
    public static boolean isRootAccess() {
        try {
            Boolean granted = Shell.isAppGrantedRoot();
            return granted != null ? granted : true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 执行命令 */
    public static String execShell(String cmd, boolean isSu) {
        try {
            List<String> out = (isSu ? Shell.su(cmd) : Shell.sh(cmd)).exec().getOut();
            return out != null && !out.isEmpty() ? out.get(0).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 隐藏或显示启动器图标 */
    public static void hideOrShowLauncherIcon(Context context, boolean isShow) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context.getPackageName(), BuildConfigWrapper.APPLICATION_ID + ".Home"),
                isShow ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** 获取启动器图标状态 */
    public static boolean isLauncherIconShowing(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context.getPackageName(), BuildConfigWrapper.APPLICATION_ID + ".Home"))
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
