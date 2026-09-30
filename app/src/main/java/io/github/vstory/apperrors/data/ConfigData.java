/*
 * AppErrorsTracking (api102 重构版) - 全局配置存储控制类 (Java 化)
 */
package io.github.vstory.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import io.github.vstory.apperrors.data.enums.AppErrorsConfigType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局配置存储控制类（api102 RemotePreferences，system_server 与模块 UI 同源）
 */
public class ConfigData {

    /** RemotePreferences 组名 */
    public static final String PREFS_GROUP = "app_errors_config";

    /** UI 本地 fallback 文件名 */
    private static final String LOCAL_PREFS_NAME = "io.github.vstory.apperrors_preferences";

    // ===== 键值名称（与原版一致） =====
    private static final String KEY_SHOW_DEVELOPER_NOTICE = "_show_developer_notice";
    private static final String KEY_ENABLE_MATERIAL3_STYLE_DIALOG = "_enable_material3_style_dialog";
    private static final String KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT = "_enable_only_show_errors_in_front";
    private static final String KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN = "_enable_only_show_errors_in_main";
    private static final String KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS = "_enable_always_shows_reopen_app_options";
    private static final String KEY_ENABLE_APP_CONFIG_TEMPLATE = "_enable_app_config_template";
    private static final String KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG = "_enable_prevent_misoperation_for_dialog";
    private static final String KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE = "_disable_auto_wrap_error_stack_trace";
    private static final String KEY_SHARE_WITH_FILE = "_share_with_file";
    private static final String KEY_GLOBAL_SHOW_ERRORS_TYPE = "_global_show_errors_type";
    private static final String KEY_MUTE_IGNORE_UNTIL_REBOOT = "_mute_ignore_until_reboot";
    private static final String KEY_ENABLE_DEBUG = "_enable_debug";

    /**
     * 首帧「存储就绪」等待上限（ms）——**只在本地镜像尚未播种、且还有等待额度时**才用
     * （见 {@link #isMirrorSeeded()} 与 {@link #shouldAwaitFirstBind()}）。
     *
     * 用途（2026-09-30 issue#1 第二轮）：把「读早于绑定」从事后自愈改成事前保证——冷启动时先在
     * {@code Application.onCreate} 里等一次，等到了首帧读的就是 remote 权威值（开关 / 界面语言 /
     * 忽略行为行同源）；没等到就按镜像继续，UI 层监听器仍会在真正绑定时重放。
     * 镜像播种过一次后永远不再等待（绝大多数冷启动零开销）。
     *
     * ⚠️ 等待点必须能在 {@code Application.onCreate}，**绝不能在 {@code Activity.attachBaseContext}**：
     *    provider 的发布发生在 Application.onCreate 之前，此时框架的 binder 已能推进来；
     *    而在 attachBaseContext 阻塞会让「框架等模块 provider 发布」与「模块等框架 binder」
     *    互相等待（互锁），直到框架侧超时。详见知识库 dev-guide/实战/api102开发实战.md §24.6。
     */
    public static final long FIRST_BIND_WAIT_MS = 500;

    /** 本地镜像「已播种」标记（只写镜像文件，不写 remote；镜像文件在应用私有目录，升级/重启都不丢） */
    private static final String KEY_MIRROR_SEEDED = "__mirror_seeded";

    /** 「等过但没等到绑定」的累计次数（只写镜像文件，不进 remote） */
    private static final String KEY_BIND_WAIT_MISSES = "__bind_wait_misses";

    /**
     * 等待失手上限：连续没等到绑定的次数达到它就不再等。
     *
     * 为什么需要：没装/没激活 LSPosed 的设备上**永远不会**有 binder —— 若只看「镜像未播种」就等，
     * 这些用户每次冷启动都要白付 {@link #FIRST_BIND_WAIT_MS}（对公开仓库的普通用户是纯损失）。
     * 有额度上限后：最多白付 2 次（每次 ≤500ms），成功绑定过一次就清零并因「已播种」不再进入等待。
     */
    private static final int BIND_WAIT_MAX_MISSES = 2;

    /** 绑定/断开唤醒锁（{@link #awaitFirstBind} 的 wait/notify 监视器） */
    private static final Object BIND_LOCK = new Object();

    /** 远程偏好（UI 连接 service 后 / system_server 侧；**权威**） */
    private static volatile SharedPreferences remotePrefs;

    /** 本地镜像（模块 UI 进程私有文件；service 未绑定阶段读它，写入也同步写它） */
    private static volatile SharedPreferences localPrefs;

    /** UI 本地上下文 */
    private static Context uiContext;

    /** service 未绑定期间的写入（key → 值）：绑定后回灌 remote，避免"绑前/断连时点的开关"只留在镜像里 */
    private static final Map<String, Object> pendingWrites = new ConcurrentHashMap<>();

    /** 当前生效的读取源：已绑定 service 时以 remote 为权威，否则读本地镜像 */
    private static SharedPreferences reader() {
        SharedPreferences remote = remotePrefs;
        if (remote != null) return remote;
        return localPrefs;
    }

    /** system_server 初始化（RemotePreferences） */
    public static void init(SharedPreferences prefs) {
        remotePrefs = prefs;
    }

    /** 模块 UI 初始化（service 连接前 fallback 本地镜像） */
    public static void init(Context context) {
        uiContext = context.getApplicationContext();
        localPrefs = uiContext.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        // service 断开/未绑定：真正回退到本地镜像
        //（原实现只记 uiContext、不清 remotePrefs，"回退本地存储"其实没发生）
        remotePrefs = null;
        // 对齐原版：init 时加载应用配置模板集合，避免后续 putAppShowingType 操作空集合导致配置丢失
        AppErrorsConfigData.refresh();
    }

    /** 模块 UI 连接 XposedService 后切换到远程存储 */
    public static void initService(io.github.libxposed.service.XposedService service) {
        remotePrefs = service.getRemotePreferences(PREFS_GROUP);
        // ① 先回灌"未绑定期间的写入"（用户刚点的开关不能被吞掉）
        flushPendingToRemote();
        // ② 再把 remote（权威）反向补齐本地镜像 → 下次冷启动"service 还没绑上"时读到的是上次真实值
        syncRemoteIntoMirror();
        // 对齐原版：切换远程存储后重新加载应用配置模板集合，保证 UI 显示/操作基于最新数据
        AppErrorsConfigData.refresh();
        // ③ 绑定成功 → 清零「等待失手」计数（止损额度恢复）
        resetBindWaitMisses();
        // ④ 唤醒等待首帧就绪的线程（Application.onCreate 的有界等待；镜像播种已完成）
        synchronized (BIND_LOCK) {
            BIND_LOCK.notifyAll();
        }
    }

    /** 刷新存储控制类（直读模式，占位兼容） */
    public static void refresh() {
    }

    // ===== 首帧就绪（等待首次绑定 = remote 可用 + 镜像已播种） =====

    /** 本地镜像是否已从 remote（权威）播种过 */
    public static boolean isMirrorSeeded() {
        SharedPreferences local = localPrefs;
        return local != null && local.getBoolean(KEY_MIRROR_SEEDED, false);
    }

    /** 是否还有「等待首次绑定」的额度（未连续失手到上限）。与 {@link #isMirrorSeeded()} 一起决定要不要等 */
    public static boolean shouldAwaitFirstBind() {
        SharedPreferences local = localPrefs;
        if (local == null) return false;
        return local.getInt(KEY_BIND_WAIT_MISSES, 0) < BIND_WAIT_MAX_MISSES;
    }

    /** 记一次「等过但没等到绑定」（未激活 LSPosed 的设备上止损用；绑定成功后清零） */
    public static void noteBindWaitMiss() {
        SharedPreferences local = localPrefs;
        if (local == null) return;
        try {
            local.edit()
                    .putInt(KEY_BIND_WAIT_MISSES, local.getInt(KEY_BIND_WAIT_MISSES, 0) + 1)
                    .commit();
        } catch (Throwable ignored) { /* 计数失败仅影响止损，忽略 */ }
    }

    /** 绑定成功 → 清零「等待失手」计数（下次镜像万一又被清空，仍留有等待额度） */
    private static void resetBindWaitMisses() {
        SharedPreferences local = localPrefs;
        if (local == null) return;
        try {
            local.edit().putInt(KEY_BIND_WAIT_MISSES, 0).commit();
        } catch (Throwable ignored) { /* 忽略 */ }
    }

    /**
     * 有界等待「首次 service 绑定」（= 第一次拿到 remote 权威存储 + 播种镜像）。
     *
     * 冷启动时在 {@code Application.onCreate}（registerListener 之后、任何配置读取之前）调用一次：
     * 绑定早于本次调用（binder 在 provider 发布后即到、被 XposedServiceHelper 缓存并同步重放）时立即返回；
     * 否则最多等 {@code timeoutMs}（典型几十 ms 内到）。
     *
     * @return true = 已绑定（remote 可用）；false = 超时（按本地镜像继续，UI 层会在真正绑定时重放）
     */
    public static boolean awaitFirstBind(long timeoutMs) {
        if (remotePrefs != null) return true;
        // 用 System.nanoTime()（单调递增、纯 JDK）而不是 android.os.SystemClock：
        // 等待窗口只有几百 ms，"休眠不计时"的语义无关；好处是这段逻辑可脱离 Android 运行时
        // 做离机自检（见知识库项目记录里的自检脚本）。
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1_000_000L;
        synchronized (BIND_LOCK) {
            while (remotePrefs == null) {
                long remainNanos = deadline - System.nanoTime();
                if (remainNanos <= 0) return false;
                try {
                    BIND_LOCK.wait(Math.max(1L, remainNanos / 1_000_000L), (int) (remainNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    // ===== 镜像 ↔ remote 同步 =====

    /** 把未绑定期间暂存（pendingWrites）的写入回灌到 remote；失败则保留，下次绑定再试 */
    private static void flushPendingToRemote() {
        SharedPreferences remote = remotePrefs;
        if (remote == null || pendingWrites.isEmpty()) return;
        try {
            SharedPreferences.Editor editor = remote.edit();
            for (Map.Entry<String, Object> entry : pendingWrites.entrySet())
                putObject(editor, entry.getKey(), entry.getValue());
            editor.commit();
            pendingWrites.clear();
        } catch (Throwable ignored) { /* 写失败：保留 pending */ }
    }

    /** 把 remote（权威）的值反向补齐本地镜像，并标记「已播种」 */
    private static void syncRemoteIntoMirror() {
        SharedPreferences local = localPrefs;
        SharedPreferences remote = remotePrefs;
        if (local == null || remote == null) return;
        try {
            Map<String, ?> all = remote.getAll();
            SharedPreferences.Editor editor = local.edit();
            for (Map.Entry<String, ?> entry : all.entrySet())
                putObject(editor, entry.getKey(), entry.getValue());
            // ⚠️ 即使 remote 为空也要打这个标记：remote「确实没有值」本身就是权威事实。
            //    若空着不打标记 → 镜像永远 unseeded → 每次冷启动都白等一次 FIRST_BIND_WAIT_MS。
            editor.putBoolean(KEY_MIRROR_SEEDED, true);
            editor.commit();
        } catch (Throwable ignored) { /* 镜像同步失败不影响主流程（下次绑定再试） */ }
    }

    /** 按值类型写入编辑器（回灌 / 镜像同步复用） */
    @SuppressWarnings("unchecked")
    private static void putObject(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }

    // ===== 底层键值操作（internal） =====

    /** 编辑器写入动作（本地镜像与 remote 复用同一段 put 逻辑） */
    private interface EditorOp {
        void put(SharedPreferences.Editor editor);
    }

    /**
     * 统一写入：**本地镜像 + remote**（已绑定则同写；未绑定则记为 pending，绑定时回灌）。
     *
     * ⚠️ 必须用 commit() 而不是 apply()：libxposed service 的 RemotePreferences.apply()
     *    是把提交动作丢到后台线程异步执行（见 RemotePreferences$Editor.apply → static EXECUTOR），
     *    用户"点完开关立刻清后台"会丢写；commit() 是同步 binder 提交，返回即已落到框架侧。
     */
    private static void write(String key, Object pendingValue, EditorOp op) {
        SharedPreferences local = localPrefs;
        if (local != null) {
            try {
                SharedPreferences.Editor editor = local.edit();
                op.put(editor);
                editor.commit();
            } catch (Throwable ignored) { /* 镜像写失败不影响主流程 */ }
        }
        SharedPreferences remote = remotePrefs;
        if (remote != null) {
            try {
                SharedPreferences.Editor editor = remote.edit();
                op.put(editor);
                editor.commit();
            } catch (Throwable ignored) { /* system_server 侧 RemotePreferences 只读：忽略 */ }
        } else if (local != null) {
            // UI 进程、service 未绑定：暂存，绑定后回灌 remote（否则写入只留在镜像里，system_server 看不到）
            pendingWrites.put(key, pendingValue);
        }
    }

    public static Set<String> getStringSet(String key) {
        SharedPreferences p = reader();
        return p != null ? p.getStringSet(key, new java.util.HashSet<String>()) : new java.util.HashSet<String>();
    }

    public static void putStringSet(String key, Set<String> value) {
        if (value == null) return;
        // 防御性拷贝：调用方（如 AppErrorsConfigData）传的是可变的静态 Set，后续还会改
        final Set<String> copy = new java.util.HashSet<>(value);
        write(key, copy, editor -> editor.putStringSet(key, copy));
    }

    public static int getInt(String key, int def) {
        SharedPreferences p = reader();
        return p != null ? p.getInt(key, def) : def;
    }

    public static void putInt(String key, int value) {
        write(key, value, editor -> editor.putInt(key, value));
    }

    public static boolean getBoolean(String key, boolean def) {
        SharedPreferences p = reader();
        return p != null ? p.getBoolean(key, def) : def;
    }

    public static void putBoolean(String key, boolean value) {
        write(key, value, editor -> editor.putBoolean(key, value));
    }

    // ===== 属性（UI 与 Host 共用；Kotlin 属性语法可映射） =====
    public static boolean isShowDeveloperNotice() {
        return getBoolean(KEY_SHOW_DEVELOPER_NOTICE, true);
    }
    public static void setShowDeveloperNotice(boolean value) {
        putBoolean(KEY_SHOW_DEVELOPER_NOTICE, value);
    }

    public static boolean isEnableMaterial3StyleAppErrorsDialog() {
        return getBoolean(KEY_ENABLE_MATERIAL3_STYLE_DIALOG, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
    }
    public static void setEnableMaterial3StyleAppErrorsDialog(boolean value) {
        putBoolean(KEY_ENABLE_MATERIAL3_STYLE_DIALOG, value);
    }

    public static boolean isEnableOnlyShowErrorsInFront() {
        return getBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT, false);
    }
    public static void setEnableOnlyShowErrorsInFront(boolean value) {
        putBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_FRONT, value);
    }

    public static boolean isEnableOnlyShowErrorsInMain() {
        return getBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN, false);
    }
    public static void setEnableOnlyShowErrorsInMain(boolean value) {
        putBoolean(KEY_ENABLE_ONLY_SHOW_ERRORS_IN_MAIN, value);
    }

    public static boolean isEnableAlwaysShowsReopenAppOptions() {
        return getBoolean(KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS, false);
    }
    public static void setEnableAlwaysShowsReopenAppOptions(boolean value) {
        putBoolean(KEY_ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS, value);
    }

    public static boolean isEnableAppConfigTemplate() {
        return getBoolean(KEY_ENABLE_APP_CONFIG_TEMPLATE, false);
    }
    public static void setEnableAppConfigTemplate(boolean value) {
        putBoolean(KEY_ENABLE_APP_CONFIG_TEMPLATE, value);
    }

    public static boolean isEnablePreventMisoperation() {
        return getBoolean(KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG, false);
    }
    public static void setEnablePreventMisoperation(boolean value) {
        putBoolean(KEY_ENABLE_PREVENT_MISOPERATION_FOR_DIALOG, value);
    }

    public static boolean isDisableAutoWrapErrorStackTrace() {
        return getBoolean(KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE, false);
    }
    public static void setDisableAutoWrapErrorStackTrace(boolean value) {
        putBoolean(KEY_DISABLE_AUTO_WRAP_ERROR_STACK_TRACE, value);
    }

    public static boolean isShareWithFile() {
        return getBoolean(KEY_SHARE_WITH_FILE, false);
    }
    public static void setShareWithFile(boolean value) {
        putBoolean(KEY_SHARE_WITH_FILE, value);
    }

    /** 全局错误显示类型（AppErrorsConfigType.ordinal；默认通知——本模块为通知版定位） */
    public static int getGlobalShowErrorsType() {
        return getInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, AppErrorsConfigType.NOTIFY.ordinal());
    }
    public static void setGlobalShowErrorsType(int value) {
        putInt(KEY_GLOBAL_SHOW_ERRORS_TYPE, value);
    }

    /** 通知「忽略该应用」按钮：true=忽略直到重启（默认），false=忽略直到解锁 */
    public static boolean isMuteIgnoreUntilReboot() {
        return getBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, true);
    }
    public static void setMuteIgnoreUntilReboot(boolean value) {
        putBoolean(KEY_MUTE_IGNORE_UNTIL_REBOOT, value);
    }

    /** 调试日志开关（默认关闭）：关闭时 system_server 只输出「崩溃记录」1条日志，其余通道回传日志不打 */
    public static boolean isEnableDebug() {
        return getBoolean(KEY_ENABLE_DEBUG, false);
    }
    public static void setEnableDebug(boolean value) {
        putBoolean(KEY_ENABLE_DEBUG, value);
    }

    private ConfigData() {}
}
