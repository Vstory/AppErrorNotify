/*
 * AppErrorsTracking (api102 重构版) - 模块内存日志 (Java 化)
 */
package io.github.vstory.apperrors.utils.tool;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块内存日志（替代 YukiHookAPI YLog）。
 *
 * ⚠️ 存储形态（2026-09-30 A1 收口）：日志**只归本进程私有文件**（{@link #LOCAL_PREFS_NAME}），
 *    不再往框架侧 RemotePreferences 写第二份。三条理由：
 *    ① 那份没有**跨进程读者** —— system_server 侧从不初始化本类的 prefs（恒为 null），
 *       它的日志只存内存、由 UI 经广播拉取（见 LoggerActivity.refreshData）⇒ 写过去纯属开销；
 *    ② libxposed 的 RemotePreferences.apply() 是**后台线程异步提交**（详见 api102开发实战 §24.1）
 *       ⇒「点完打印堆栈立刻清后台」会丢写，且失败被 catch 吞掉、不留任何线索；
 *    ③ 更糟：绑定那一刻会把这份**旧账**读回来覆盖内存里的日志（init → load）⇒ 日志页表现为
 *       「先显示本地这份、绑上后突然跳变、少掉几条（正是绑前刚记的）」。去掉这条路径后**结构性消失**。
 *    ⇒ 因此本类**不再提供** init(SharedPreferences) 重载：拿不到框架侧存储，就不可能再写过去。
 */
public class ModuleLogger {

    private static final String LOCAL_PREFS_NAME = "io.github.vstory.apperrors_logs";

    private static final String KEY_LOGS = "logs";

    /** 内存保留条数上限 */
    private static final int MAX_LOGS = 200;

    /** 日志数据 */
    public static class LogData implements java.io.Serializable {
        public String priority;
        public String tag;
        public String msg;
        public String throwable;
        public long timestamp;

        public LogData(String priority, String tag, String msg, String throwable, long timestamp) {
            this.priority = priority;
            this.tag = tag;
            this.msg = msg;
            this.throwable = throwable;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "[" + priority + "] " + tag + ": " + msg;
        }

        public String getPriority() { return priority; }
        public String getTag() { return tag; }
        public String getMsg() { return msg; }
        public String getThrowable() { return throwable; }
        public long getTimestamp() { return timestamp; }
    }

    private static final Gson gson = new Gson();

    private static SharedPreferences prefs;

    private static final List<LogData> inMemory = new ArrayList<>();

    /** 本地存储初始化（日志恒用本进程私有文件；见类注释） */
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    /** 记录日志 */
    public static void log(String priority, String tag, String msg, Throwable e) {
        LogData data = new LogData(priority, tag, msg != null ? msg : "", e != null ? e.toString() : null, System.currentTimeMillis());
        synchronized (inMemory) {
            inMemory.add(data);
            if (inMemory.size() > MAX_LOGS) inMemory.remove(0);
        }
        persist();
    }

    /** 获取全部日志（内存顺序） */
    public static List<LogData> allData() {
        synchronized (inMemory) {
            return new ArrayList<>(inMemory);
        }
    }

    /** 清空日志 */
    public static void clear() {
        synchronized (inMemory) { inMemory.clear(); }
        persist();
    }

    /** 导出文本 */
    public static String contents(List<LogData> data) {
        if (data == null) data = allData();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(data.get(i).toString());
        }
        return sb.toString();
    }

    private static void load() {
        String json = prefs != null ? prefs.getString(KEY_LOGS, null) : null;
        if (json == null) return;
        try {
            Type type = new TypeToken<ArrayList<LogData>>() {}.getType();
            ArrayList<LogData> list = gson.fromJson(json, type);
            if (list == null) return;
            synchronized (inMemory) {
                inMemory.clear();
                int start = Math.max(0, list.size() - MAX_LOGS);
                inMemory.addAll(list.subList(start, list.size()));
            }
        } catch (Exception ignored) {
        }
    }

    private static void persist() {
        try {
            List<LogData> list;
            synchronized (inMemory) { list = new ArrayList<>(inMemory); }
            SharedPreferences p = prefs;
            if (p == null) return;
            /**
             * ⚠️ 这里用 apply() 是**正解**，不要照搬 ConfigData 那条「写必须 commit()」的铁律：
             *    那条铁律针对的是 libxposed 的 RemotePreferences（apply 丢后台线程、进程被杀就丢）；
             *    **本地文件** prefs 的 apply() 由 Android 自己兜底（QueuedWork 会在界面/组件停止时等它落盘），
             *    而 commit() 会把「整份日志的序列化 + 落盘」搬到调用线程 —— log() 会被 UI 线程调用
             *    （打印堆栈按钮就在 UI 线程），日志含整条堆栈、上限 200 条，攒满时是几百 KB 级 JSON
             *    ⇒ 反而凭空制造卡顿。故此处保持 apply()。
             */
            p.edit().putString(KEY_LOGS, gson.toJson(list)).apply();
        } catch (Exception ignored) {
        }
    }

    /** 广播 action：UI 请求日志 / system_server 回传日志 */
    public static final String ACTION_GET_LOGS = "io.github.vstory.apperrors.action.GET_LOGS";
    public static final String ACTION_LOGS_RESULT = "io.github.vstory.apperrors.action.LOGS_RESULT";
    public static final String EXTRA_LOGS = "logs";

    /**
     * UI 进程读取：经广播从 system_server 拉取模块日志（system_server 侧日志只存内存/RemotePreferences 只读，
     *  UI 进程无法直读，必须经 system_server 广播回传）
     * @param context UI Context
     * @param callback 收到日志后的回调（可能在非主线程）
     */
    public static void fetchFromSystemServer(final android.content.Context context,
                                             final Runnable callback) {
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(ACTION_LOGS_RESULT);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                    }
                    // ⚠️ SDK 33+ (targetSdk=37) getSerializableExtra(String) 旧签名可能因 ClassLoader 加载失败返回 null
                    //    → 用新签名 getSerializableExtra(String, Class) 兼容（内联，避免与本项目 FunctionFactoryKt 循环依赖）
                    Object extra = null;
                    if (intent != null) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            extra = intent.getSerializableExtra(EXTRA_LOGS, java.io.Serializable.class);
                        } else {
                            extra = intent.getSerializableExtra(EXTRA_LOGS);
                        }
                    }
                    // ⚠️ 知识库规范: 接收端判断集合用 instanceof java.util.List 勿用 ArrayList
                    //   (CopyOnWriteArrayList 非 ArrayList 子类, 用 ArrayList 会误判 false)
                    if (extra instanceof java.util.List) {
                        java.util.List<?> raw = (java.util.List<?>) extra;
                        java.util.ArrayList<LogData> remote = new java.util.ArrayList<>();
                        for (Object o : raw) if (o instanceof LogData) remote.add((LogData) o);
                        synchronized (inMemory) {
                            inMemory.clear();
                            inMemory.addAll(remote);
                        }
                    }
                    if (callback != null) callback.run();
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            android.content.Intent request = new android.content.Intent(ACTION_GET_LOGS);
            context.sendBroadcast(request);
        } catch (Throwable t) {
            if (callback != null) callback.run();
        }
    }

    private ModuleLogger() {}
}
