/*
 * AppErrorsTracking (api102 重构版) - 异常记录存储控制类 (Java 化)
 * 原版 YukiHookAPI 存储架构：system_server 直接写文件 /data/misc/
 * ⚠️ 不能用 RemotePreferences 写：libxposed 被注入进程（system_server）拿到的
 *    RemotePreferences 是只读实现（LSPosedRemotePreferences.edit() 抛 UnsupportedOperationException）！
 *    原版就是这么做的（system_server uid=1000 有 /data/misc 写权限）
 *
 * 目录：/data/misc/apperrornotify/（固定路径，system_server 写、模块 UI 只读）
 *   v1.18 及更早用 /data/misc/apperrors_<random16>/ + id 文件 apperrors_dir_id 存随机串，
 *   但 system_server 写该 id 文件会静默失败（SELinux）→ 随机目录不可复现，只剩兜底扫描在撑。
 *   现改为固定目录，旧随机目录内的 JSON 在首次启动时一次性迁移（见 migrateLegacyFolder）。
 */
package io.github.vstory.apperrors.data;

import android.content.Context;
import android.util.Log;

import io.github.vstory.apperrors.bean.AppErrorsInfoBean;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异常记录存储控制类（system_server 写文件 /data/misc/apperrornotify/，模块 UI 读同一目录）
 */
public class AppErrorsRecordData {

    /** 记录目录（固定路径；命名取应用名小写） */
    private static final String FOLDER_PATH = "/data/misc/apperrornotify/";

    /** 旧版随机目录前缀（v1.18 及更早），仅用于迁移与读取兜底；下个版本删 */
    private static final String LEGACY_FOLDER_PREFIX = "apperrors_";

    /** 旧版随机串 id 文件，迁移后删除；下个版本删 */
    private static final String LEGACY_ID_FILE_PATH = "/data/misc/apperrors_dir_id";

    /** 历史记录保留上限（条数）：超出后丢弃最旧记录，防止风暴/长期使用让 /data/misc 下 JSON 无限累积 */
    private static final int MAX_RECORDS = 200;

    /**
     * recordKey 只用于进程间定位记录（Intent extras / 广播），不属于磁盘格式。
     * 用「只作用于序列化」的排除策略而不是 transient 表达这个意图：transient 会被 Java
     * 原生序列化一并跳过，bean 传到 UI 侧就丢了 key。
     */
    private static final Gson gson = new GsonBuilder()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return "recordKey".equals(f.getName());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    /** 只给单测看磁盘 JSON 形状（见 RecordKeySerializationTest） */
    static Gson gsonForTest() {
        return gson;
    }

    /** 当前实例 */
    private static Context context;

    /** 当前模式：true=system_server（可生成/写）；false=UI 进程（只读） */
    private static volatile boolean isSystemServerMode = true;

    /** 目录路径（system_server 进程内缓存；UI 进程每次重新解析） */
    private static volatile String folderPath;

    /** 已记录的全部 APP 异常信息数组 */
    public static CopyOnWriteArrayList<AppErrorsInfoBean> allData = new CopyOnWriteArrayList<>();

    private static void log(String msg, Throwable e) {
        // ⚠️ 不能用 HookEntry.log()：HookEntry 只在 system_server 注入时存在，
        //    UI 进程加载 HookEntry 类会 NoClassDefFoundError → 模块自身崩溃（真机实证）
        android.util.Log.i("AppErrorNotify", msg != null ? msg : "", e);
    }

    // ===== 目录解析 / 旧目录迁移（旧版兼容代码下个版本删） =====

    /** 旧版随机目录中含记录的最新一个；无则 null */
    private static File legacyDataFolder() {
        try {
            File[] dirs = new File("/data/misc/").listFiles();
            if (dirs == null) return null;
            File best = null;
            for (File d : dirs) {
                if (!d.isDirectory() || !d.getName().startsWith(LEGACY_FOLDER_PREFIX)) continue;
                File[] children = d.listFiles();
                if (children == null || children.length == 0) continue;
                if (best == null || d.lastModified() > best.lastModified()) best = d;
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 旧随机目录 → 固定目录（一次性）：只搬 .json，搬空后才删旧目录与 id 文件，
     * 中途失败则保留旧目录（下次启动重试），不丢记录
     * ⚠️ 用 renameTo 而非 copy：只有源/目标同在 /data 挂载点才成立（Android File.renameTo
     *    限定同 mount point，跨 mount 静默返回 false）。目标若改到 /sdcard 或应用私有目录，
     *    迁移会全量失败，日志里只留一行 moved=0，看不出是路径问题
     */
    private static void migrateLegacyFolder(File target) {
        try {
            File source = legacyDataFolder();
            if (source == null) return;
            if (!target.exists() && !target.mkdirs()) return;
            int moved = 0;
            File[] files = source.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.isFile() || !f.getName().endsWith(".json")) continue;
                    if (f.renameTo(new File(target, f.getName()))) moved++;
                }
            }
            File[] left = source.listFiles();
            if (left != null && left.length == 0) {
                source.delete();
                new File(LEGACY_ID_FILE_PATH).delete();
            }
            log("Migrated " + moved + " legacy record(s) to " + target, null);
        } catch (Throwable t) {
            log("Migrate legacy records folder failed", t);
        }
    }

    /**
     * 解析记录目录（固定路径）
     * @param ctx Context
     * @param canManage true=system_server（有 /data/misc 写权限，负责建目录与迁移旧目录）；false=UI 进程（只读）
     * @return 目录路径
     */
    private static String resolveFolderPath(Context ctx, boolean canManage) {
        if (folderPath != null) return folderPath;
        if (canManage && !new File(FOLDER_PATH).exists()) migrateLegacyFolder(new File(FOLDER_PATH));
        folderPath = FOLDER_PATH;
        return folderPath;
    }

    /** 获取当前异常记录数据目录 */
    private static File errorsInfoDataFolder() {
        String p = folderPath != null ? folderPath : resolveFolderPath(context, isSystemServerMode);
        return p != null ? new File(p) : null;
    }

    /** 获取当前全部异常记录数据文件（按修改时间倒序） */
    private static List<File> errorsInfoDataFiles() {
        List<File> list = jsonFilesIn(errorsInfoDataFolder());
        if (list.isEmpty()) {
            // 热重载窗口：system_server 还在跑旧代码、写的是随机目录 → 只读兜底（下个版本删）
            list = jsonFilesIn(legacyDataFolder());
        }
        return list;
    }

    private static List<File> jsonFilesIn(File folder) {
        if (folder == null) return new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return list;
    }

    /** 初始化异常记录数据目录 */
    private static void initializeDataDirectory() {
        try {
            File folder = errorsInfoDataFolder();
            if (folder == null) return;
            if (!folder.exists() || folder.isFile()) {
                folder.delete();
                folder.mkdirs();
            }
            // system_server 写、模块 UI（普通 uid）读同一目录 → 目录必须可读可执行
            try {
                folder.setReadable(true, false);
                folder.setWritable(true, false);
                folder.setExecutable(true, false);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("Can't create directory \"" + FOLDER_PATH + "\", there will be problems with the app errors records function", t);
        }
    }

    /** 从文件读取全部异常记录数据 */
    private static CopyOnWriteArrayList<AppErrorsInfoBean> readAllDataFromFiles() {
        CopyOnWriteArrayList<AppErrorsInfoBean> result = new CopyOnWriteArrayList<>();
        try {
            for (File f : errorsInfoDataFiles()) {
                try {
                    String json = readFile(f);
                    AppErrorsInfoBean bean = gson.fromJson(json, AppErrorsInfoBean.class);
                    if (bean != null) {
                        bean.recordKey = f.getName();
                        result.add(bean);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            log("Read app errors records failed", t);
        }
        return result;
    }

    /** 读文件内容（UTF-8） */
    private static String readFile(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            try {
                byte[] data = new byte[(int) file.length()];
                int len = fis.read(data);
                return new String(data, 0, Math.max(len, 0), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                fis.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** 写文件内容（UTF-8），异常由调用方捕获 */
    private static void writeFile(File file, String content) throws java.io.IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
        try {
            fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
    }

    /** 读取磁盘上的最新记录（权威数据源）
     *  ⚠️ 供 system_server 广播回传（GET_ERRORS）用：热重载会导致内存 allData 分裂（新旧 class 各一份），
     *     读磁盘文件可避开分裂的内存态，保证任何 receiver 都能拿到最新崩溃记录 */
    public static java.util.List<AppErrorsInfoBean> latestFromFiles() {
        return new java.util.ArrayList<>(readAllDataFromFiles());
    }

    /** 摘要字段上限：列表回传只带这些，堆栈等大字段走详情/分页通道单取 */
    private static final int SUMMARY_MESSAGE_MAX = 512;
    private static final int SUMMARY_ACTIVITY_MAX = 256;

    /**
     * 供广播回传的摘要列表：剥离 stackTrace（单条可达几十 KB，200 条累加必然撑爆 Binder 事务），
     * 其余列表渲染所需字段全保留。完整记录由 {@link #findByKey(String)} / {@link #fullPageFromFiles} 单取。
     */
    public static java.util.List<AppErrorsInfoBean> summaryListFromFiles() {
        java.util.List<AppErrorsInfoBean> out = new java.util.ArrayList<>();
        for (AppErrorsInfoBean bean : latestFromFiles()) out.add(toSummary(bean));
        return out;
    }

    /** 分页读完整记录：只解析本页涉及的文件，导出时避免一次解析全部 */
    public static java.util.List<AppErrorsInfoBean> fullPageFromFiles(int offset, int limit) {
        java.util.List<AppErrorsInfoBean> out = new java.util.ArrayList<>();
        List<File> files = errorsInfoDataFiles();
        int from = Math.max(offset, 0);
        int to = limit <= 0 ? files.size() : Math.min(files.size(), from + limit);
        for (int i = from; i < to; i++) {
            File f = files.get(i);
            try {
                String json = readFile(f);
                if (json == null) continue;
                AppErrorsInfoBean bean = gson.fromJson(json, AppErrorsInfoBean.class);
                if (bean != null) {
                    bean.recordKey = f.getName();
                    out.add(bean);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** 按磁盘文件名取单条完整记录 */
    public static AppErrorsInfoBean findByKey(String key) {
        if (key == null || key.isEmpty()) return null;
        File folder = errorsInfoDataFolder();
        if (folder == null) return null;
        File file = new File(folder, key);
        // 只认同目录下的裸文件名，挡掉 ../ 之类的路径穿越
        if (!key.equals(file.getName()) || !file.isFile()) return null;
        try {
            String json = readFile(file);
            if (json == null) return null;
            AppErrorsInfoBean bean = gson.fromJson(json, AppErrorsInfoBean.class);
            if (bean != null) bean.recordKey = file.getName();
            return bean;
        } catch (Throwable t) {
            log("Read one app errors record failed", t);
            return null;
        }
    }

    private static AppErrorsInfoBean toSummary(AppErrorsInfoBean src) {
        AppErrorsInfoBean s = new AppErrorsInfoBean();
        s.pid = src.pid;
        s.userId = src.userId;
        s.cpuAbi = src.cpuAbi;
        s.packageName = src.packageName;
        s.versionName = src.versionName;
        s.versionCode = src.versionCode;
        s.targetSdk = src.targetSdk;
        s.minSdk = src.minSdk;
        s.isNativeCrash = src.isNativeCrash;
        s.exceptionClassName = src.exceptionClassName;
        s.exceptionMessage = clamp(src.exceptionMessage, SUMMARY_MESSAGE_MAX);
        s.throwFileName = src.throwFileName;
        s.throwClassName = src.throwClassName;
        s.throwMethodName = src.throwMethodName;
        s.throwLineNumber = src.throwLineNumber;
        s.stackTrace = "";
        s.runningActivity = clamp(src.runningActivity, SUMMARY_ACTIVITY_MAX);
        s.isBackgroundCrash = src.isBackgroundCrash;
        s.timestamp = src.timestamp;
        s.recordKey = src.recordKey;
        return s;
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** system_server 初始化（首次 hook 拿到 Context 后；建固定目录并按需迁移旧随机目录） */
    public static void init(Context context) {
        AppErrorsRecordData.context = context;
        isSystemServerMode = true;
        resolveFolderPath(context, true);
        initializeDataDirectory();
        allData = readAllDataFromFiles();
    }

    /** 添加新的异常记录数据（写文件） */
    public static void add(AppErrorsInfoBean bean) {
        allData.add(0, bean);
        File folder = errorsInfoDataFolder();
        if (folder == null) {
            log("App errors records folder not ready", null);
            return;
        }
        try {
            File file = new File(folder.getAbsolutePath(), bean.getJsonFileName());
            writeFile(file, gson.toJson(bean));
            // 模块 UI（普通 uid）也要读 → 文件 0666
            try {
                file.setReadable(true, false);
                file.setWritable(true, false);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("Save app errors records failed", t);
        }
        trimToLimit();
    }

    /** 超出保留上限时丢弃最旧记录（磁盘文件 + 内存 allData 同步裁剪，保持两者一致） */
    private static void trimToLimit() {
        try {
            // 磁盘：errorsInfoDataFiles() 已按 lastModified 倒序 → 尾部即最旧，删到只剩 MAX_RECORDS
            List<File> files = errorsInfoDataFiles();
            for (int i = MAX_RECORDS; i < files.size(); i++) {
                File old = files.get(i);
                if (old != null && old.exists()) old.delete();
            }
            // 内存：allData 头部是最新（add 在 0 插入；readAllDataFromFiles 倒序插入）→ 删尾部
            while (allData.size() > MAX_RECORDS) {
                allData.remove(allData.size() - 1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 移除指定的异常记录数据 */
    public static void remove(AppErrorsInfoBean bean) {
        allData.remove(bean);
        File folder = errorsInfoDataFolder();
        if (folder == null) return;
        try {
            File file = new File(folder.getAbsolutePath(), bean.getJsonFileName());
            file.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 清除全部异常记录数据 */
    public static void clearAll() {
        allData.clear();
        File folder = errorsInfoDataFolder();
        if (folder != null) {
            try {
                if (folder.exists()) deleteRecursively(folder);
                initializeDataDirectory();
            } catch (Throwable ignored) {
            }
        }
        // 旧随机目录不清掉的话，读取兜底会把刚清空的记录又读回来（下个版本删）
        File legacy = legacyDataFolder();
        if (legacy != null) {
            try {
                deleteRecursively(legacy);
                new File(LEGACY_ID_FILE_PATH).delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    /**
     * UI 进程读取：经广播从 system_server 拉取记录
     * （普通 uid 无 /data/misc 权限不能直读文件；原版用 dataChannel 广播中转，这里用标准广播等价实现）
     *  ⚠️ 热重载可能导致 system_server 有多个旧/新 receiver 并存、各自回传一份结果（有空的也有非空的）。
     *     因此这里「收到非空结果才立即采用；收到空结果继续等一小段非空回传，超时才用空结果兜底」。
     *  ⚠️ 自动重试（2026-09-05 方案A）：system_server 通道在开机早期可能尚未注册成功（AMS 未 publish，
     *     模块 registerErrorChannel 3s 轮询重试中）→ 单次 5s 超时即提示「模块未完全激活」误导用户重启。
     *     这里改为：单次 5s 收不到回传 → 静默重试（重新注册 receiver + 重发广播），最多 FETCH_RETRY_MAX 次，
     *     总窗口约 15s 覆盖启动早期通道就绪时间；全部尝试完仍无回传才回调 ACTION_ERRORS_TIMEOUT（提示稍后重试）。
     * @param context UI Context
     * @param resultReceiver 收到记录后的回调（可能在非主线程；超时收到 ACTION_ERRORS_TIMEOUT intent）
     */
    public static void fetchFromSystemServer(final android.content.Context context,
                                             final android.content.BroadcastReceiver resultReceiver) {
        fetchFromSystemServerInternal(context, resultReceiver, 0);
    }

    /** fetchFromSystemServer 内部实现（带重试调度） */
    private static void fetchFromSystemServerInternal(final android.content.Context context,
                                                      final android.content.BroadcastReceiver resultReceiver,
                                                      final int attempt) {
        final boolean isLastAttempt = attempt >= FETCH_RETRY_MAX - 1;
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction("io.github.vstory.apperrors.action.ERRORS_RESULT");
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] finished = {false};       // 是否已最终采用并回调
            final boolean[] gotEmpty = {false};       // 是否收到过空结果
            final android.content.BroadcastReceiver[] holder = new android.content.BroadcastReceiver[1];
            // ⚠️ 绝对超时兜底（2026-09-05 修复）：system_server 通道未通（模块未注入 / 未重启 / 通道注册未成功）时
            //    收不到任何回传 → 原实现永不回调 → UI 历史页无限转圈（真机实证）。
            //    ⚠️ 自动重试（方案A）：前几次超时不直接失败，静默延迟后重新发起（register+send）；最后一次才回调超时。
            final Runnable timeoutTask = () -> {
                if (finished[0]) return;
                finished[0] = true;
                try { if (holder[0] != null) context.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                if (!isLastAttempt) {
                    // 通道可能尚未就绪（开机早期 AMS 未 publish / registerErrorChannel 重试中）→ 静默重试
                    log("Retry fetch app errors (" + (attempt + 1) + "/" + FETCH_RETRY_MAX + "), "
                            + (FETCH_RETRY_MAX - attempt - 1) + " left", null);
                    handler.postDelayed(() -> fetchFromSystemServerInternal(context, resultReceiver, attempt + 1),
                            FETCH_RETRY_INTERVAL_MS);
                    return;
                }
                // 最终失败：置空 + 回调带 ACTION_ERRORS_TIMEOUT 标记的 intent，UI 提示用户稍后重试
                allData = new CopyOnWriteArrayList<>();
                if (resultReceiver != null) {
                    android.content.Intent timeoutIntent =
                            new android.content.Intent("io.github.vstory.apperrors.action.ERRORS_TIMEOUT");
                    resultReceiver.onReceive(context, timeoutIntent);
                }
            };
            handler.postDelayed(timeoutTask, FETCH_TIMEOUT_MS);   // 单次尝试超时：收没收到都结束本次
            // 动态注册临时 receiver；收到「非空」结果立即采用 + 解绑；收到空结果时再等一小段兜底
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    if (intent == null) return;
                    Object extra = FunctionFactoryKt.getSerializableExtraCompat(intent, "errors");
                    // ⚠️ 必须用 List 判断：system_server 回传的是 CopyOnWriteArrayList，
                    //    它不是 java.util.ArrayList 的子类 → 用 instanceof ArrayList 会误判为 false，
                    //    导致历史列表页永远拉不到数据（单条详情直传 bean 不受影响）。
                    if (!(extra instanceof java.util.List)) return;   // 非 List 直接忽略
                    java.util.List<?> raw = (java.util.List<?>) extra;
                    // 过滤出有效的 AppErrorsInfoBean
                    final java.util.List<AppErrorsInfoBean> accepted = new java.util.ArrayList<>();
                    for (Object o : raw) if (o instanceof AppErrorsInfoBean) accepted.add((AppErrorsInfoBean) o);
                    // 非空 → 立即采用（覆盖之前任何空结果）
                    if (!accepted.isEmpty()) {
                        allData = new CopyOnWriteArrayList<>(accepted);
                        if (!finished[0]) {
                            finished[0] = true;
                            handler.removeCallbacks(timeoutTask);
                            try { ctx.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                            if (resultReceiver != null) resultReceiver.onReceive(ctx, intent);
                        }
                        return;
                    }
                    // 空结果：先记下来，等一小段（给非空 receiver 机会），超时才用空结果兜底
                    gotEmpty[0] = true;
                    handler.postDelayed(() -> {
                        if (!finished[0]) {
                            finished[0] = true;
                            handler.removeCallbacks(timeoutTask);
                            try { if (holder[0] != null) context.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                            // 超时兜底：若有任何空结果回调，把 allData 置空
                            allData = new CopyOnWriteArrayList<>();
                            if (resultReceiver != null) resultReceiver.onReceive(context, null);
                        }
                    }, 600L);   // 600ms 等待窗口：期间若收到非空则覆盖
                }
            };
            holder[0] = receiver;
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            // 发请求广播给 system_server
            // ⚠️ 不能 setPackage()：定向广播只会投递给 io.github.vstory.apperrors 包内 receiver，
            //    system_server 进程里的动态 receiver 不属于该包 → 永远收不到（真机实证转圈）
            android.content.Intent request = new android.content.Intent("io.github.vstory.apperrors.action.GET_ERRORS");
            context.sendBroadcast(request);
        } catch (Throwable t) {
            log("Fetch app errors records from system server failed", t);
            if (resultReceiver != null) resultReceiver.onReceive(null, null);
        }
    }

    /** 供日志展示当前目录路径（未解析返回 null） */
    public static String getFolderPathForLog() {
        return folderPath;
    }

    /** 动态广播拉取单次超时（ms） */
    private static final long FETCH_TIMEOUT_MS = 5000L;

    /** 动态广播拉取最大尝试次数（含首次）：总窗口约 5s + (N-1)×3s，覆盖 system_server 启动早期通道就绪时间 */
    private static final int FETCH_RETRY_MAX = 4;

    /** 重试间隔（ms） */
    private static final long FETCH_RETRY_INTERVAL_MS = 3000L;

    /**
     * UI 进程查询「应用总数」（统计弹窗用）：经广播请 system_server 特权枚举后回传一个 int。
     *  ⚠️ 不能走 UI 进程 pm.getInstalledPackages：Android 11+ 包可见性限制之上，国产 ROM（ColorOS 等）还有一层
     *     「读取应用列表」应用级权限开关，被拒时其静默返回空列表（只剩自身），不报错也不提示；
     *     用户授权后持久有效、重启不失效（真机实测 2026-10-01）。
     *     system_server（uid=1000）枚举全量应用不受该权限管辖。
     *  ⚠️ 通道超时兜底：多次重试（总窗口约 14s）收不到回传 → 回调 -1，UI 用 0 保底显示（占比 0%），不阻塞弹窗。
     *     （方案A 2026-09-05：开机早期通道未就绪时静默重试，避免误报失败。）
     * @param context UI Context
     * @param callback 收到应用总数（int；失败为 -1，可能在主线程回调）
     */
    public static void fetchAppTotalFromSystemServer(final android.content.Context context,
                                                     final java.util.function.Consumer<Integer> callback) {
        fetchAppTotalInternal(context, callback, 0);
    }

    /** fetchAppTotalFromSystemServer 内部实现（带重试调度） */
    private static void fetchAppTotalInternal(final android.content.Context context,
                                              final java.util.function.Consumer<Integer> callback,
                                              final int attempt) {
        final boolean isLastAttempt = attempt >= FETCH_RETRY_MAX - 1;
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction("io.github.vstory.apperrors.action.APP_TOTAL_RESULT");
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] finished = {false};
            final android.content.BroadcastReceiver[] holder = new android.content.BroadcastReceiver[1];
            // 单次尝试超时：通道未通（模块未注入/未重启/启动早期 registerErrorChannel 重试中）时收不到回传。
            //    ⚠️ 方案A：前几次超时不直接失败，静默延迟后重新发起；最后一次才回调 -1。
            final Runnable timeoutTask = () -> {
                if (finished[0]) return;
                finished[0] = true;
                try { if (holder[0] != null) context.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                if (!isLastAttempt) {
                    log("Retry fetch app total (" + (attempt + 1) + "/" + FETCH_RETRY_MAX + "), "
                            + (FETCH_RETRY_MAX - attempt - 1) + " left", null);
                    handler.postDelayed(() -> fetchAppTotalInternal(context, callback, attempt + 1),
                            FETCH_RETRY_INTERVAL_MS);
                    return;
                }
                if (callback != null) callback.accept(-1);
            };
            handler.postDelayed(timeoutTask, FETCH_TIMEOUT_MS);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    if (finished[0]) return;
                    finished[0] = true;
                    handler.removeCallbacks(timeoutTask);
                    try { ctx.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                    int total = intent != null ? intent.getIntExtra("total_apps", -1) : -1;
                    if (callback != null) callback.accept(total);
                }
            };
            holder[0] = receiver;
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            // 发请求广播给 system_server（不能 setPackage：system_server 进程的动态 receiver 不属于本包）
            android.content.Intent request = new android.content.Intent("io.github.vstory.apperrors.action.GET_APP_TOTAL");
            context.sendBroadcast(request);
        } catch (Throwable t) {
            log("Fetch app total from system server failed", t);
            if (callback != null) callback.accept(-1);
        }
    }

    // ===== UI → system_server 一次性请求（详情 / 分页完整记录） =====

    private static final String ACTION_GET_ERRORS = "io.github.vstory.apperrors.action.GET_ERRORS";
    private static final String ACTION_ERRORS_RESULT = "io.github.vstory.apperrors.action.ERRORS_RESULT";
    // 分页走独立 action：列表刷新与导出可能同时在跑，共用一个回传会互相收到对方的结果
    private static final String ACTION_GET_ERRORS_PAGE = "io.github.vstory.apperrors.action.GET_ERRORS_PAGE";
    private static final String ACTION_ERRORS_PAGE_RESULT = "io.github.vstory.apperrors.action.ERRORS_PAGE_RESULT";
    private static final String ACTION_GET_ERROR_DETAIL = "io.github.vstory.apperrors.action.GET_ERROR_DETAIL";
    private static final String ACTION_ERROR_DETAIL_RESULT = "io.github.vstory.apperrors.action.ERROR_DETAIL_RESULT";
    private static final String EXTRA_RECORD_KEY = "record_key";
    private static final String EXTRA_OFFSET = "offset";
    private static final String EXTRA_LIMIT = "limit";

    /** 单次请求-回传回调；超时或通道异常时 intent 为 null */
    public interface RequestCallback {
        void onResult(android.content.Context context, android.content.Intent intent);
    }

    /** 单次请求等待上限（ms）：详情与分页都是用户可见操作，超时即失败、不静默重试 */
    private static final long ONESHOT_TIMEOUT_MS = 3000L;

    /** UI 侧：按 recordKey 取单条完整记录 */
    public static void requestErrorDetail(android.content.Context context, final String recordKey,
                                         RequestCallback callback) {
        requestOnce(context, ACTION_GET_ERROR_DETAIL, ACTION_ERROR_DETAIL_RESULT,
                req -> req.putExtra(EXTRA_RECORD_KEY, recordKey), callback);
    }

    /** UI 侧：分页取完整记录（导出全部用，一次一页避免超 Binder 事务） */
    public static void fetchFullPage(android.content.Context context, final int offset, final int limit,
                                     RequestCallback callback) {
        requestOnce(context, ACTION_GET_ERRORS_PAGE, ACTION_ERRORS_PAGE_RESULT, req -> {
            req.putExtra(EXTRA_OFFSET, offset);
            req.putExtra(EXTRA_LIMIT, limit);
        }, callback);
    }

    private static void requestOnce(final android.content.Context context, String requestAction, String resultAction,
                                    java.util.function.Consumer<android.content.Intent> filler,
                                    final RequestCallback callback) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] done = {false};
        final android.content.BroadcastReceiver[] holder = new android.content.BroadcastReceiver[1];
        final Runnable timeoutTask = () -> {
            if (done[0]) return;
            done[0] = true;
            try { if (holder[0] != null) context.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
            if (callback != null) callback.onResult(context, null);
        };
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(resultAction);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    if (done[0]) return;
                    done[0] = true;
                    handler.removeCallbacks(timeoutTask);
                    try { ctx.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                    if (callback != null) callback.onResult(ctx, intent);
                }
            };
            holder[0] = receiver;
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            handler.postDelayed(timeoutTask, ONESHOT_TIMEOUT_MS);
            android.content.Intent request = new android.content.Intent(requestAction);
            if (filler != null) filler.accept(request);
            // 不能 setPackage：system_server 里的动态 receiver 不属于本包
            context.sendBroadcast(request);
        } catch (Throwable t) {
            log("Request " + requestAction + " failed", t);
            if (!done[0]) {
                done[0] = true;
                handler.removeCallbacks(timeoutTask);
                if (callback != null) callback.onResult(context, null);
            }
        }
    }

    /** 从详情回传 intent 取完整记录（失败为 null） */
    public static AppErrorsInfoBean beanFromResult(android.content.Intent intent) {
        if (intent == null) return null;
        Object extra = FunctionFactoryKt.getSerializableExtraCompat(intent, "bean");
        return extra instanceof AppErrorsInfoBean ? (AppErrorsInfoBean) extra : null;
    }

    /** 从列表/分页回传 intent 取记录数组（失败为空表） */
    public static java.util.List<AppErrorsInfoBean> listFromResult(android.content.Intent intent) {
        java.util.List<AppErrorsInfoBean> out = new java.util.ArrayList<>();
        if (intent == null) return out;
        Object extra = FunctionFactoryKt.getSerializableExtraCompat(intent, "errors");
        if (!(extra instanceof java.util.List)) return out;
        for (Object o : (java.util.List<?>) extra) if (o instanceof AppErrorsInfoBean) out.add((AppErrorsInfoBean) o);
        return out;
    }

    // ===== UI → system_server 操作广播（clear / remove 需在 system_server 侧执行） =====

    /** 请求 system_server 清除全部记录 */
    public static void requestClearAll(android.content.Context context) {
        try {
            android.content.Intent req = new android.content.Intent("io.github.vstory.apperrors.action.CLEAR_ERRORS");
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request clear all failed", t);
        }
    }

    /** 按磁盘文件名删除单条记录（UI 回传的是摘要 bean，只能靠文件名定位） */
    public static void removeByKey(String key) {
        if (key == null || key.isEmpty()) return;
        File folder = errorsInfoDataFolder();
        if (folder == null) return;
        File file = new File(folder, key);
        if (!key.equals(file.getName())) return;
        for (AppErrorsInfoBean b : allData) {
            if (key.equals(b.recordKey)) {
                allData.remove(b);
                break;
            }
        }
        try {
            file.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 请求 system_server 移除指定记录 */
    public static void requestRemove(android.content.Context context, String recordKey) {
        try {
            android.content.Intent req = new android.content.Intent("io.github.vstory.apperrors.action.REMOVE_ERROR");
            req.putExtra("record_key", recordKey);
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request remove failed", t);
        }
    }

    private AppErrorsRecordData() {}
}
