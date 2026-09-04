
package io.github.vstory.apperrors.utils.tool;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class ModuleLogger {

    public static final String PREFS_GROUP = "app_errors_logs";

    private static final String LOCAL_PREFS_NAME = "io.github.vstory.apperrors_logs";

    private static final String KEY_LOGS = "logs";

    
    private static final int MAX_LOGS = 200;

    
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

    
    public static void init(SharedPreferences prefs) {
        ModuleLogger.prefs = prefs;
        load();
    }

    
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    
    public static void log(String priority, String tag, String msg, Throwable e) {
        LogData data = new LogData(priority, tag, msg != null ? msg : "", e != null ? e.toString() : null, System.currentTimeMillis());
        synchronized (inMemory) {
            inMemory.add(data);
            if (inMemory.size() > MAX_LOGS) inMemory.remove(0);
        }
        persist();
    }

    
    public static List<LogData> allData() {
        synchronized (inMemory) {
            return new ArrayList<>(inMemory);
        }
    }

    
    public static void clear() {
        synchronized (inMemory) { inMemory.clear(); }
        persist();
    }

    
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

    
    public static final String ACTION_GET_LOGS = "io.github.vstory.apperrors.action.GET_LOGS";
    public static final String ACTION_LOGS_RESULT = "io.github.vstory.apperrors.action.LOGS_RESULT";
    public static final String EXTRA_LOGS = "logs";

    
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
                    
                    
                    Object extra = null;
                    if (intent != null) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            extra = intent.getSerializableExtra(EXTRA_LOGS, java.io.Serializable.class);
                        } else {
                            extra = intent.getSerializableExtra(EXTRA_LOGS);
                        }
                    }
                    
                    
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
