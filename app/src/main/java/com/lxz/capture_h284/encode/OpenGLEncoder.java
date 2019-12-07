package com.lxz.capture_h284.encode;

import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.view.Surface;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.OutputBufferInfo;
import com.lxz.capture_h284.ScreenAvcEncoder;
import com.lxz.capture_h284.comm.glec.EGLRender;
import com.lxz.capture_h284.stream.IH264Stream;

public class OpenGLEncoder extends BaseEncoder {
    private static final String TAG = "OpenGLEncoder";
    private EGLRender eglRender;
    private byte[] pps = null;
    private long current_time;
    private int count = 1;
    private long time;

    public OpenGLEncoder(IH264Stream outStream, int screenWidth, int screenHeight) {
        super(outStream, screenWidth, screenHeight);
        avcEncoder = new ScreenAvcEncoder(screenWidth, screenHeight , Config.encodeFps, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        eglRender = new EGLRender(avcEncoder.getSurface(), screenWidth, screenHeight, 15);
        eglRender.setup();
    }

    private void update() {
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
    public Surface getSurface() {
        return eglRender.getDecodeSurface();
    }

    @Override
    public void outputEncodeData() {
        eglRender.makeCurrent(1);
        eglRender.awaitNewImage();
        current_time = System.currentTimeMillis();
        if (current_time - time >= eglRender.getVideo_interval()) {
            Lg.i(TAG, "draw img time %d", (current_time - time));
            eglRender.drawImage();
            update();
            eglRender.setPresentationTime();
            eglRender.swapBuffers();
            time = current_time;
        }
        else {
            Lg.i(TAG, "丢弃 ");
        }
    }

    @Override
    public void release() {
        super.release();
    }
}
