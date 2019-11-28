package com.lxz.capture_h284;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.utils.CommUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenAvcEncoder {
    private static final String TAG = "ScreenAvcEncoder";
    private MediaCodec mediaCodec;
    private int m_width;
    private int m_height;
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private Surface surface;

    public ScreenAvcEncoder(int width, int height, int framerate, int keyColorFormat) {
        this(width, height, framerate, width * height * 3/*编码比特率*/, keyColorFormat);
    }

    @SuppressLint("NewApi")
    public ScreenAvcEncoder(int width, int height, int framerate, int bitrate, int keyColorFormat) {
        m_width = width;
        m_height = height;
        Log.v("xmc", "ScreenAvcEncoder:" + m_width + "+" + m_height);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, keyColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Config.KEY_I_FRAME_INTERVAL);//关键帧间隔时间 单位s
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (keyColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            this.surface = mediaCodec.createInputSurface();
        }
        mediaCodec.start();
    }

    public Surface getSurface() {
        return this.surface;
    }

    @SuppressLint("NewApi")
    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void queueInputBuffer(byte [] input) {
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime(), 0);
        }
    }

    public OutputBufferInfo dequeueOutputBuffer() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        OutputBufferInfo info = new OutputBufferInfo(outputBufferIndex, bufferInfo.size);
        return info;
    }

    public byte[] getOutputBuffer(int size, int outputBufferIndex) {
        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
        byte[] output = new byte[size];
        outputBuffer.get(output);
        if ((output[4] & 0x1F) == 5) {//key frame 编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上
            Lg.e(TAG, "IDR 帧");
        }
        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
        return output;
    }
}

