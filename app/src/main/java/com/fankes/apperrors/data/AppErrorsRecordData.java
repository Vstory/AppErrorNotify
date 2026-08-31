/*
 * AppErrorsTracking (api102 重构版) - 异常记录存储控制类 (Java 化)
 * 原版 YukiHookAPI 存储架构：system_server 直接写文件 /data/misc/
 * ⚠️ 不能用 RemotePreferences 写：libxposed 被注入进程（system_server）拿到的
 *    RemotePreferences 是只读实现（LSPosedRemotePreferences.edit() 抛 UnsupportedOperationException）！
 *    原版就是这么做的（system_server uid=1000 有 /data/misc 写权限）
 *
 * 目录名设计（防检测/防冲突）：
 *   /data/misc/apperrors_<random16>/  —— random16 是**运行时动态生成**的随机字符
 *   - 首次安装并激活生效时（system_server 侧 ensureHostContext → init）生成并持久化
 *   - 之后只要模块不被卸载就一直复用；卸载重装（firstInstallTime 变化）→ 重新生成
 *   - 随机串存于 id 文件 /data/misc/apperrors_dir_id，内容 "<firstInstallTime>:<random>"
 *   - system_server（uid=1000）可写；UI 进程只读该文件（文件 0666）
 */
package com.fankes.apperrors.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.fankes.apperrors.bean.AppErrorsInfoBean;
import com.fankes.apperrors.wrapper.BuildConfigWrapper;
import com.google.gson.Gson;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异常记录存储控制类（system_server 写文件 /data/misc/apperrors_&lt;random&gt;/，模块 UI 读同一目录）
 */
public class AppErrorsRecordData {

    /** 基础目录（/data/misc 下） */
    private static final String FOLDER_BASE = "/data/misc/";

    /** 目录名前缀 */
    private static final String FOLDER_PREFIX = "apperrors_";

    /** 随机串持久化文件（存 "<firstInstallTime>:<random>"；system_server 写 0666，UI 只读） */
    private static final String ID_FILE_PATH = FOLDER_BASE + "apperrors_dir_id";

    /** 随机串长度 */
    private static final int RANDOM_LENGTH = 16;

    /** 随机字符表 */
    private static final char[] RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private static final Gson gson = new Gson();

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
        android.util.Log.i("AppErrorsTracking", msg != null ? msg : "", e);
    }

    // ===== 随机串 / 目录名解析 =====

    /** 生成随机字符串（SecureRandom，长度 16） */
    private static String generateRandomId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) sb.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]);
        return sb.toString();
    }

    /** 模块自身首次安装时间（用于校验：升级不变 → 目录不变；卸载重装 → 变化 → 重新生成）
     *  ⚠️ 必须用模块包名 BuildConfigWrapper.APPLICATION_ID！
     *     不能用 ctx.getPackageName()：system_server 的 context 返回 "android"，
     *     导致取到系统包 firstInstallTime 甚至 -1 → 校验失败 → 每次重启都新建目录（真机实证出现两个目录） */
    private static long moduleFirstInstallTime(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(BuildConfigWrapper.APPLICATION_ID, 0);
            return pi.firstInstallTime;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    /** 读 id 文件内容 */
    private static String readIdFile() {
        try {
            File f = new File(ID_FILE_PATH);
            if (!f.exists()) return null;
            String content = readFile(f);
            if (content == null) return null;
            String trimmed = content.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 写 id 文件（仅 system_server 有权限；失败返回 false） */
    private static boolean writeIdFile(String content) {
        try {
            File f = new File(ID_FILE_PATH);
            writeFile(f, content);
            try {
                f.setReadable(true, false);
                f.setWritable(true, false);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 解析目录路径（关键：随机后缀动态生成）
     * @param ctx Context
     * @param canGenerate true=system_server（有 /data/misc 写权限，可生成）；false=UI 进程（只读）
     * @return 目录路径，或 null（UI 且 id 文件尚未生成）
     */
    private static String resolveFolderPath(Context ctx, boolean canGenerate) {
        // system_server 进程内缓存
        if (canGenerate && folderPath != null) return folderPath;
        String id = null;
        String idContent = null;
        // 1. 读 id 文件（先看是否已有权威随机串）
        String content = readIdFile();
        if (content != null) {
            String[] parts = content.split(":", 2);
            if (parts.length == 2 && parts[1] != null && parts[1].matches("[A-Za-z0-9]{8,32}")) {
                id = parts[1];
                idContent = content;
            }
        }
        // 2. 有 → 复用（校验 firstInstallTime：升级不变则继续复用；卸载重装变化则重新生成）
        if (id != null && canGenerate) {
            long installTime = moduleFirstInstallTime(ctx);
            if (installTime >= 0 && idContent != null && idContent.startsWith(installTime + ":")) {
                folderPath = FOLDER_BASE + FOLDER_PREFIX + id + "/";
                return folderPath;
            }
            // firstInstallTime 不匹配（卸载重装）→ 尝试兜底复用已有目录（见步骤 3.5）
        }
        // 3. UI 进程且无权威 id → 无法生成（无写权限），返回 null（等 system_server 激活后生成）
        if (!canGenerate) {
            if (id != null) return FOLDER_BASE + FOLDER_PREFIX + id + "/";
            return null;
        }
        // 3.5 system_server：id 文件缺失/失效 → 先扫描已有 apperrors_* 目录，有记录就复用（避免无限新建）
        String existing = findExistingDataFolder();
        if (existing != null) {
            folderPath = existing;
            // 重新持久化 id（修复 id 文件丢失/失效；firstInstallTime 用当前模块的）
            long installTime = moduleFirstInstallTime(ctx);
            if (installTime >= 0) {
                String suffix = existing.substring(FOLDER_BASE.length() + FOLDER_PREFIX.length());
                if (suffix.endsWith("/")) suffix = suffix.substring(0, suffix.length() - 1);
                writeIdFile(installTime + ":" + suffix);
            }
            return folderPath;
        }
        // 4. system_server：无任何已有目录 → 生成新随机串并持久化
        String newId = generateRandomId();
        long installTime = moduleFirstInstallTime(ctx);
        String newContent = installTime + ":" + newId;
        // 写入 id 文件；若写入失败（极端情况）也继续用本次生成的路径
        writeIdFile(newContent);
        folderPath = FOLDER_BASE + FOLDER_PREFIX + newId + "/";
        log("App errors records folder created: " + folderPath, null);
        return folderPath;
    }

    /** 扫描 /data/misc/ 下已有 apperrors_* 目录，返回含记录数据的最新目录（无则 null）
     *  兜底逻辑：id 文件丢失/校验失败时复用已有目录，避免重复新建 */
    private static String findExistingDataFolder() {
        try {
            File base = new File(FOLDER_BASE);
            File[] dirs = base.listFiles();
            if (dirs == null) return null;
            String best = null;
            long bestTime = -1;
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                String name = d.getName();
                if (!name.startsWith(FOLDER_PREFIX)) continue;
                // 只复用"有记录数据"的目录（非空）
                File[] children = d.listFiles();
                if (children == null || children.length == 0) continue;
                if (d.lastModified() > bestTime) {
                    bestTime = d.lastModified();
                    best = d.getAbsolutePath() + "/";
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 获取当前异常记录数据目录（解析失败返回 null） */
    private static File errorsInfoDataFolder() {
        String p = folderPath != null ? folderPath : resolveFolderPath(context, isSystemServerMode);
        return p != null ? new File(p) : null;
    }

    /** 获取当前全部异常记录数据文件（按修改时间倒序） */
    private static List<File> errorsInfoDataFiles() {
        File folder = errorsInfoDataFolder();
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
            log("Can't create directory \"" + FOLDER_BASE + "\", there will be problems with the app errors records function", t);
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
                    if (bean != null) result.add(bean);
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

    /** system_server 初始化（首次 hook 拿到 Context 后；动态生成随机目录并持久化） */
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
        if (folder == null) return;
        try {
            if (folder.exists()) deleteRecursively(folder);
            initializeDataDirectory();
        } catch (Throwable ignored) {
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
     * @param context UI Context
     * @param callback 收到记录后的回调（可能在非主线程）
     */
    public static void fetchFromSystemServer(final android.content.Context context,
                                             final android.content.BroadcastReceiver resultReceiver) {
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction("com.fankes.apperrors.action.ERRORS_RESULT");
            // 动态注册临时 receiver，收到结果后解绑
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                    }
                    Object extra = intent != null ? intent.getSerializableExtra("errors") : null;
                    if (extra instanceof java.util.ArrayList) {
                        java.util.ArrayList<?> raw = (java.util.ArrayList<?>) extra;
                        allData = new CopyOnWriteArrayList<>();
                        for (Object o : raw) if (o instanceof AppErrorsInfoBean) allData.add((AppErrorsInfoBean) o);
                    }
                    if (resultReceiver != null) resultReceiver.onReceive(ctx, intent);
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            // 发请求广播给 system_server
            // ⚠️ 不能 setPackage()：定向广播只会投递给 com.fankes.apperrors 包内 receiver，
            //    system_server 进程里的动态 receiver 不属于该包 → 永远收不到（真机实证转圈）
            android.content.Intent request = new android.content.Intent("com.fankes.apperrors.action.GET_ERRORS");
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

    // ===== UI → system_server 操作广播（clear / remove 需在 system_server 侧执行） =====

    /** 请求 system_server 清除全部记录 */
    public static void requestClearAll(android.content.Context context) {
        try {
            android.content.Intent req = new android.content.Intent("com.fankes.apperrors.action.CLEAR_ERRORS");
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request clear all failed", t);
        }
    }

    /** 请求 system_server 移除指定记录 */
    public static void requestRemove(android.content.Context context, AppErrorsInfoBean bean) {
        try {
            android.content.Intent req = new android.content.Intent("com.fankes.apperrors.action.REMOVE_ERROR");
            req.putExtra("bean", bean);
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request remove failed", t);
        }
    }

    private AppErrorsRecordData() {}
}
