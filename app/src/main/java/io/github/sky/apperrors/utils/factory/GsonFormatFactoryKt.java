
package io.github.sky.apperrors.utils.factory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;


public class GsonFormatFactoryKt {

    private GsonFormatFactoryKt() {}

    
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    
    public static String toJson(Object obj) {
        String json = GSON.toJson(obj);
        return json != null ? json : "";
    }

    
    public static String toJsonOrNull(Object obj) {
        try {
            return toJson(obj);
        } catch (Exception e) {
            return null;
        }
    }

    
    public static <T> T toEntity(String json, Type type) {
        if (json == null || json.trim().isEmpty()) return null;
        return GSON.fromJson(json, type);
    }

    
    public static <T> T toEntityOrNull(String json, Type type) {
        try {
            return toEntity(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    
    public static <T> T toEntity(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        return GSON.fromJson(json, clazz);
    }
}
