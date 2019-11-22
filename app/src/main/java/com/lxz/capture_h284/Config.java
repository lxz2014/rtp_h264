package com.lxz.capture_h284;

import android.graphics.ImageFormat;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Environment;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.utils.SpUtils;

import java.io.File;
import java.util.Arrays;

public class Config {
    public static final int DATA_LEN = 1480;
    public static final int KEY_I_FRAME_INTERVAL = 1;
    private static final String EXTRA_IP = "Config";
    public static int fps = 15;

    public static String getIp() {
        String ip = SpUtils.getValue(EXTRA_IP, "192.168.1.115");
        return ip;
    }

    public static void setIp(String ip) {
        SpUtils.putValue(EXTRA_IP, ip);
    }

    public static File getSaveFile() {
        return new File(Environment.getExternalStorageDirectory().getPath(), "save.h264");
    }

    public static int getSupportColorFormat() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals("video/avc")) {
                    System.out.println("found");
                    found = true;
                }
            }
            if (!found)
                continue;
            codecInfo = info;
        }

        Lg.e("AvcEncoder", "Found " + codecInfo.getName() + " supporting " + "video/avc");

        // Find a color profile that the codec supports
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        Lg.e("AvcEncoder",
                "length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));

        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            switch (capabilities.colorFormats[i]) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    Lg.e("AvcEncoder", "supported color format::" + capabilities.colorFormats[i]);
                    break;

                default:
                    Lg.e("AvcEncoder", "other color format " + capabilities.colorFormats[i]);
                    break;
            }
        }
        //return capabilities.colorFormats[i];
        return 0;
    }
}
