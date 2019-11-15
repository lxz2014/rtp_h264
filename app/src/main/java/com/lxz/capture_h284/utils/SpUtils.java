package com.lxz.capture_h284.utils;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by lxz on 2016/11/7.
 */

public class SpUtils {
    /**
     * 保存在手机里面的文件名
     */
    private static final String FILE_NAME = "share_date";

    private static Application application;
    public static void init(Application app) {
        if (application == null) {
            application = app;
        }
    }
    /**
     * 向 SharedPreferences 中写入int类型数据
     *
     * @param key 键
     * @param value 值
     */
    public static void putValue(String key,
                                int value) {
        Context context = getAppContext();
        SharedPreferences.Editor sp = getEditor(context, FILE_NAME);
        sp.putInt(key, value);
        sp.commit();
    }

    private static Context getAppContext() {
        return application;
    }

    /**
     * 向SharedPreferences中写入boolean类型的数据
     *
     * @param key 键
     * @param value 值
     */
    public static void putValue( String key,
                                boolean value) {
        Context context = getAppContext();
        SharedPreferences.Editor sp = getEditor(context, FILE_NAME);
        sp.putBoolean(key, value);
        sp.commit();
    }

    /**
     * 向SharedPreferences中写入String类型的数据
     *
     * @param key 键
     * @param value 值
     */
    public static void putValue(String key,
                                String value) {
        Context context = getAppContext();
        SharedPreferences.Editor sp = getEditor(context, FILE_NAME);
        sp.putString(key, value);
        sp.commit();
    }

    /**
     * 向SharedPreferences中写入float类型的数据

     * @param key 键
     * @param value 值
     */
    public static void putValue(String key,
                                float value) {
        Context context = getAppContext();
        SharedPreferences.Editor sp = getEditor(context, FILE_NAME);
        sp.putFloat(key, value);
        sp.commit();
    }

    /**
     * 向SharedPreferences中写入long类型的数据
     *
     * @param key 键
     * @param value 值
     */
    public static void putValue(String key,
                                long value) {
        Context context = getAppContext();
        SharedPreferences.Editor sp = getEditor(context, FILE_NAME);
        sp.putLong(key, value);
        sp.commit();
    }

    /**
     * 从SharedPreferences中读取int类型的数据
     *
     * @param key 键
     * @param defValue 如果读取不成功则使用默认值
     * @return 返回读取的值
     */
    public static int getValue(String key,
                               int defValue) {
        Context context = getAppContext();
        SharedPreferences sp = getSharedPreferences(context, FILE_NAME);
        int value = sp.getInt(key, defValue);
        return value;
    }

    /**
     * 从SharedPreferences中读取boolean类型的数据
     *
     * @param key 键
     * @param defValue 如果读取不成功则使用默认值
     * @return 返回读取的值
     */
    public static boolean getValue(String key,
                                   boolean defValue) {
        Context context = getAppContext();
        SharedPreferences sp = getSharedPreferences(context, FILE_NAME);
        boolean value = sp.getBoolean(key, defValue);
        return value;
    }

    /**
     * 从SharedPreferences中读取String类型的数据
     *
     * @param key 键
     * @param defValue 如果读取不成功则使用默认值
     * @return 返回读取的值
     */
    public static String getValue(String key,
                                  String defValue) {
        Context context = getAppContext();
        SharedPreferences sp = getSharedPreferences(context, FILE_NAME);
        String value = sp.getString(key, defValue);
        return value;
    }

    /**
     * 从SharedPreferences中读取float类型的数据
     * @param key 键
     * @param defValue 如果读取不成功则使用默认值
     * @return 返回读取的值
     */
    public static float getValue(String key,
                                 float defValue) {
        Context context = getAppContext();
        SharedPreferences sp = getSharedPreferences(context, FILE_NAME);
        float value = sp.getFloat(key, defValue);
        return value;
    }

    /**
     * 从SharedPreferences中读取long类型的数据
     *
     
     * @param key 键
     * @param defValue 如果读取不成功则使用默认值
     * @return 返回读取的值
     */
    public static long getValue(String key,
                                long defValue) {
        Context context = getAppContext();
        SharedPreferences sp = getSharedPreferences(context, FILE_NAME);
        long value = sp.getLong(key, defValue);
        return value;
    }

    //获取Editor实例
    private static SharedPreferences.Editor getEditor(Context context, String name) {
        return getSharedPreferences(context, name).edit();
    }

    //获取SharedPreferences实例
    private static SharedPreferences getSharedPreferences(Context context, String name) {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
}
