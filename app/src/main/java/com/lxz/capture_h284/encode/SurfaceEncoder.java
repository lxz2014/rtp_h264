package com.lxz.capture_h284.encode;

import android.media.MediaCodecInfo;
import android.view.Surface;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.OutputBufferInfo;
import com.lxz.capture_h284.ScreenAvcEncoder;
import com.lxz.capture_h284.stream.IH264Stream;

public class SurfaceEncoder extends BaseEncoder {
    private static final String TAG = "SurfaceEncoder";
    private byte[] pps = null;

    public SurfaceEncoder(IH264Stream outStream, int screenWidth, int screenHeight) {
        super(outStream, screenWidth, screenHeight);
        avcEncoder = new ScreenAvcEncoder(screenWidth, screenHeight , Config.encodeFps, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    @Override
    public Surface getSurface() {
        return avcEncoder.getSurface();
    }

    @Override
    public void outputEncodeData() {
        OutputBufferInfo outputInfo = avcEncoder.dequeueOutputBuffer();
        while (outputInfo.outputBufferIndex >= 0) {
            byte[] h264Data = avcEncoder.getOutputBuffer(outputInfo.size, outputInfo.outputBufferIndex);
            if (h264Data != null) {
                Lg.i(TAG, "getOutputBuffer frame len %d, next", h264Data.length);
                if (frameHead(h264Data) == 7 && pps == null) {
                    Lg.e(TAG, "pps frame..");
                    pps = new byte[h264Data.length];
                    System.arraycopy(h264Data, 0, pps, 0, h264Data.length);
                }
                else if (frameHead(h264Data) == 5 && pps != null) {
                    Lg.e(TAG, "IDR frame, write pps");
                    outStream.writeFrame(pps);
                }
                outStream.writeFrame(h264Data);
            }
            outputInfo = avcEncoder.dequeueOutputBuffer();
        }
    }

    @Override
    public void release() {
        if (avcEncoder != null) {
            avcEncoder.close();
        }
    }
}
