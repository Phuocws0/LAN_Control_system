package com.lancontrol.server.util;
import com.google.gson.Gson;
public class JsonUtil {
    private static final Gson gson = new Gson();
    public static String toJson(Object o) { return gson.toJson(o); }
    public static <T> T fromJson(String j, Class<T> c) { try { return gson.fromJson(j, c); } catch(Exception e) { return null; } }
}