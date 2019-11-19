package com.lxz.capture_h284.utils;

import android.app.Activity;
import android.content.Context;
import android.media.MediaCodec;
import android.util.DisplayMetrics;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.MainActivity;

import java.io.Closeable;
import java.io.IOException;

import okio.BufferedSource;

public class CommUtils {
    private static final String TAG = "CommUtils";

    public static void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 整型转换成字节数组
     */
    public static byte[] int2bytes(int i){
        byte[] bt = new byte[4];
        bt[0] = (byte) (0xff & i);
        bt[1] = (byte) ((0xff00 & i) >> 8);
        bt[2] = (byte) ((0xff0000 & i) >> 16);
        bt[3] = (byte) ((0xff000000 & i) >> 24);
        return bt;
    }

    /**
     * 字节数组转成int
     */
    public static int bytes2int(byte[] bytes){
        int value, offset = 0;
        value = (int) ((bytes[offset] & 0xFF)
                | ((bytes[offset + 1] << 8) & 0xFF00)
                | ((bytes[offset + 2] << 16) & 0xFF0000)
                | ((bytes[offset + 3] << 24) & 0xFF000000));
        return value;
    }

    public static int getDpi(Activity context) {
        DisplayMetrics dm = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.densityDpi;
    }

    public static DisplayMetrics getDispay(Activity context) {
        DisplayMetrics dm = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    public static void closeIo(Closeable source) {
        if (source != null) {
            try {
                source.close();
            } catch (IOException e) {
                Lg.e(TAG, "close fail:" + e);
            }
        }
    }

    public static void logFrame(byte[] h264Data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6 && i < h264Data.length; i++) {
            String dex = Integer.toHexString(h264Data[i]);
            sb.append(",").append(dex);
        }
        if ((h264Data[4] & 0x1f) == 1) {
            Lg.i(TAG, "head %s", sb.toString().replace("f", ""));
        }
        else {
            Lg.e(TAG, "head %s", sb.toString().replace("f", ""));
        }
    }

    public static String toType(int flags) {
        switch (flags) {
            case 0:
                return "frame";
            case MediaCodec.BUFFER_FLAG_KEY_FRAME:
                return "BUFFER_FLAG_KEY_FRAME";
            case MediaCodec.BUFFER_FLAG_CODEC_CONFIG:
                return "BUFFER_FLAG_CODEC_CONFIG";
            case MediaCodec.BUFFER_FLAG_END_OF_STREAM:
                return "BUFFER_FLAG_END_OF_STREAM";
            case MediaCodec.BUFFER_FLAG_PARTIAL_FRAME:
                return "BUFFER_FLAG_PARTIAL_FRAME";
        }
        return "";
    }
}