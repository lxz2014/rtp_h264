package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;

public abstract class BaseStream implements IH264Stream {
    public final String TAG = getClass().getSimpleName();

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

    public void log(byte[] h264Data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5 && i < h264Data.length; i++) {
            String dex = Integer.toHexString(h264Data[i]);
            sb.append(",").append(dex);
        }
        sb.append(" | ");
        for (int i = h264Data.length - 1; i >= h264Data.length - 5; i--) {
            String dex = Integer.toHexString(h264Data[i]);
            sb.append(",").append(dex);
        }

        if ((h264Data[4] & 0x1f) == 1) {
            Lg.i(TAG, "framelen %d, head %s", h264Data.length, sb.toString().replace("f", ""));
        }
        else {
            Lg.e(TAG, "framelen %d, head %s", h264Data.length, sb.toString().replace("f", ""));
        }
    }
}
