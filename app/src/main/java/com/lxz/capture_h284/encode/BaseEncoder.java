package com.lxz.capture_h284.encode;

import com.lxz.capture_h284.ScreenAvcEncoder;
import com.lxz.capture_h284.stream.IH264Stream;

public abstract class BaseEncoder implements IEncoder {
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
