/*
 * AppErrorsTracking - Gson 格式化工厂 (Java 化, 保持 GsonFormatFactoryKt 类名)
 */
package com.fankes.apperrors.utils.factory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/** Gson 格式化工厂（原 GsonFormatFactory.kt 顶层函数） */
public class GsonFormatFactoryKt {

    private GsonFormatFactoryKt() {}

    /** 创建 Gson 实例 */
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** 实体类转 Json 字符串 */
    public static String toJson(Object obj) {
        String json = GSON.toJson(obj);
        return json != null ? json : "";
    }

    /** 实体类转 Json 字符串 or null */
    public static String toJsonOrNull(Object obj) {
        try {
            return toJson(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /** Json 字符串转实体类 */
    public static <T> T toEntity(String json, Type type) {
        if (json == null || json.trim().isEmpty()) return null;
        return GSON.fromJson(json, type);
    }

    /** Json 字符串转实体类 or null */
    public static <T> T toEntityOrNull(String json, Type type) {
        try {
            return toEntity(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    /** Json 字符串转实体类（by Class） */
    public static <T> T toEntity(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        return GSON.fromJson(json, clazz);
    }
}
