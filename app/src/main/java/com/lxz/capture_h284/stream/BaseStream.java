package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.utils.CommUtils;

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
        return CommUtils.KMP(ss, ps, offset);
    }

    public void logFrame(byte[] h264Data) {
        StringBuilder sb = new StringBuilder();
        int offset = 5;
        for (int i = 0; i < offset && i < h264Data.length; i++) {
            String dex = Integer.toHexString(h264Data[i]);
            sb.append(",").append(dex);
        }
        sb.append(" | ");
        for (int i = h264Data.length - 1; i >= h264Data.length - offset; i--) {
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
