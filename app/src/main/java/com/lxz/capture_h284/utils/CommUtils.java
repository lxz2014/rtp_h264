package com.lxz.capture_h284.utils;

import android.app.Activity;
import android.content.Context;
import android.media.MediaCodec;
import android.util.DisplayMetrics;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.MainActivity;
import com.lxz.capture_h284.stream.IntPools;

import java.io.Closeable;
import java.io.IOException;

import okio.BufferedSource;

public class CommUtils {
    private static final String TAG = "CommUtils";

    public static void sleep(int i) {
        try {
            Thread.sleep(i == 0 ? 0 : i);
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

    /**
     * KMP算法
     *
     * @param ss 主串
     * @param ps 模式串
     * @return 如果找到，返回在主串中第一个字符出现的下标，否则为-1
     */
    public static int KMP(byte[] ss, byte[] ps, int offset) {
        byte[] s = ss;
        byte[] p = ps;

        int i = offset; // 主串的位置
        int j = 0; // 模式串的位置
        int[] next = getNext(ps);
        while (i < s.length && j < p.length) {
            //①如果j=-1，或者当前字符匹配成功（即S[i]==P[j]），都令i++，j++
            if (j == -1 || s[i] == p[j]) { // 当j为-1时，要移动的是i，当然j也要归0
                i++;
                j++;
            } else {
                //②如果j!=-1，且当前字符匹配失败（即S[i]!=P[j]），则令i不变，j=next[j]，j右移j-next[j]
                j = next[j];
            }
        }
        IntPools.recycle(next);
        if (j == p.length) {
            return i - j;
        } else {
            return -1;
        }
    }

    public static int[] getNext(byte[] ps) {
        byte[] p = ps;
        int[] next = IntPools.obtain(ps.length);
        next[0] = -1;
        int j = 0;
        int k = -1;
        while (j < p.length - 1) {
            //p[k]表示前缀，p[j]表示后缀
            if (k == -1 || p[k] == p[j]) {
                next[++j] = ++k;//即当p[k] == p[j]时，next[j+1] == next[j] + 1=k+1
            } else {
                k = next[k];
            }
        }
        return next;
    }

}
