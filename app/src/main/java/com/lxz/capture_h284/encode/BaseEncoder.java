package com.lxz.capture_h284.encode;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.ScreenAvcEncoder;
import com.lxz.capture_h284.stream.IH264Stream;
import com.lxz.capture_h284.utils.CommUtils;

import java.util.Arrays;
import java.util.List;

public abstract class BaseEncoder implements IEncoder {
    private static final String TAG = "BaseEncoder";
    protected ScreenAvcEncoder avcEncoder;
    protected IH264Stream outStream;
    protected int screenWidth;
    protected int screenHeight;

    public BaseEncoder(IH264Stream outStream, int screenWidth, int screenHeight) {
        this.outStream = outStream;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    protected int frameHead(byte[] h264Data) {
        return h264Data[4] & 0x1f;
    }


}
