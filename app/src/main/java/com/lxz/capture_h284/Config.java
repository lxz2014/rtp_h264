package com.lxz.capture_h284;

import android.os.Environment;

import com.lxz.capture_h284.utils.SpUtils;

import java.io.File;

public class Config {
    public static final int DATA_LEN = 1480;
    private static final String EXTRA_IP = "Config";

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
}
