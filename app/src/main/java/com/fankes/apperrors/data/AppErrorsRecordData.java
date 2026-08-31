/*
 * AppErrorsTracking (api102 重构版) - 异常记录存储控制类 (Java 化)
 */
package com.fankes.apperrors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fankes.apperrors.bean.AppErrorsInfoBean;
import com.fankes.apperrors.hook.HookEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异常记录存储控制类（RemotePreferences 单 key JSON 数组，跨进程）
 */
public class AppErrorsRecordData {

    /** RemotePreferences 组名 */
    public static final String PREFS_GROUP = "app_errors_records";

    /** 本地 fallback 文件名 */
    private static final String LOCAL_PREFS_NAME = "com.fankes.apperrors_records";

    /** 单 key：全部异常记录 JSON 数组 */
    private static final String KEY_DATA = "records";

    private static final Gson gson = new Gson();

    /** 当前存储 */
    private static SharedPreferences prefs;

    /** 已记录的全部 APP 异常信息数组 */
    public static CopyOnWriteArrayList<AppErrorsInfoBean> allData = new CopyOnWriteArrayList<>();

    private static void log(String msg, Throwable e) {
        if (HookEntry.isReady()) HookEntry.getInstance().log(Log.INFO, HookEntry.TAG, msg, e);
    }

    /** system_server 初始化（RemotePreferences） */
    public static void init(SharedPreferences prefs) {
        AppErrorsRecordData.prefs = prefs;
        allData = readAllDataFromPrefs();
    }

    /** 模块 UI 初始化（本地 fallback） */
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        allData = readAllDataFromPrefs();
    }

    /** 模块 UI 连接 XposedService 后切换到远程存储 */
    public static void initService(io.github.libxposed.service.XposedService service) {
        prefs = service.getRemotePreferences(PREFS_GROUP);
        allData = readAllDataFromPrefs();
    }

    /** 从存储读取全部记录 */
    private static CopyOnWriteArrayList<AppErrorsInfoBean> readAllDataFromPrefs() {
        String json = prefs != null ? prefs.getString(KEY_DATA, null) : null;
        if (json == null) return new CopyOnWriteArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<AppErrorsInfoBean>>() {}.getType();
            ArrayList<AppErrorsInfoBean> list = gson.fromJson(json, type);
            return new CopyOnWriteArrayList<>(list != null ? list : new ArrayList<AppErrorsInfoBean>());
        } catch (Exception e) {
            log("Read app errors records failed", e);
            return new CopyOnWriteArrayList<>();
        }
    }

    /** 持久化全部记录 */
    private static void persist() {
        try {
            if (prefs != null) prefs.edit().putString(KEY_DATA, gson.toJson(allData)).apply();
        } catch (Exception e) {
            log("Save app errors records failed", e);
        }
    }

    /** 添加新的异常记录数据 */
    public static void add(AppErrorsInfoBean bean) {
        allData.add(0, bean);
        persist();
    }

    /** 移除指定的异常记录数据 */
    public static void remove(AppErrorsInfoBean bean) {
        allData.remove(bean);
        persist();
    }

    /** 清除全部异常记录数据 */
    public static void clearAll() {
        allData.clear();
        persist();
    }

    private AppErrorsRecordData() {}
}
