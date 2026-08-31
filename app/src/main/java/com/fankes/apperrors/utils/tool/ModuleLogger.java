/*
 * AppErrorsTracking (api102 重构版) - 模块内存日志 (Java 化)
 */
package com.fankes.apperrors.utils.tool;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** 模块内存日志（替代 YukiHookAPI YLog） */
public class ModuleLogger {

    public static final String PREFS_GROUP = "app_errors_logs";

    private static final String LOCAL_PREFS_NAME = "com.fankes.apperrors_logs";

    private static final String KEY_LOGS = "logs";

    /** 内存保留条数上限 */
    private static final int MAX_LOGS = 200;

    /** 日志数据 */
    public static class LogData {
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

    /** system_server / UI 初始化 */
    public static void init(SharedPreferences prefs) {
        ModuleLogger.prefs = prefs;
        load();
    }

    /** UI 本地 fallback 初始化 */
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
            if (prefs != null) prefs.edit().putString(KEY_LOGS, gson.toJson(list)).apply();
        } catch (Exception ignored) {
        }
    }

    private ModuleLogger() {}
}
