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
    private int m_fps;
    private byte[] m_info = null;

    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private byte[] yuv420 = null;
    private byte[] nv21 = null;

  //  private MediaFormat mediaFormat;

    private Surface surface;

    @SuppressLint("NewApi")
    public ScreenAvcEncoder(int width, int height, int framerate, int bitrate) {
        m_width = width;
        m_height = height;
        m_fps= framerate;
        Log.v("xmc", "ScreenAvcEncoder:" + m_width + "+" + m_height);
        yuv420 = new byte[width * height * 3 / 2];
        nv21 = new byte[width * height * 3 / 2];
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Config.KEY_I_FRAME_INTERVAL);//关键帧间隔时间 单位s
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        this.surface = mediaCodec.createInputSurface();
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
            generateIndex += 1;
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

    long pts = 0;
    long generateIndex = 0;

    /**
     * 硬编码器，需要喂NV12的数据，如果不是的话，需要提前转换。
     * @param input
     * @param output
     * @return
     */
    @SuppressLint("NewApi")
    public int offerEncoder(byte[] input, byte[] output) {
        //Log.e("offerEncoder","start");
        int pos = 0;
        long t1 = System.currentTimeMillis();
        /**
        下面4个方法功能是一样的，适用于基于RGBA_8888的图片流编码。（RGBA_8888实现的数值顺序是BGRA）
        分别采用JAVA实现、纯C实现、FFMPEG实现、LibYUV实现。1080P测试效果：
        前3者性能相当，都比较差。大约80ms或以上可转一帧，不实用
        LibYUV实现性能相对较好。大约20-40ms可转一帧，基本实用。但仍然不够理想
         **/
        //rgb2YCbCr420(input, nv21, m_width, m_height);
        //ColorHelper.rgb2YCbCr420(input,nv21,m_width,m_height);
        // ColorHelper.ffmpegRGB2YUV(input,nv21,m_width,m_height);
        //ColorHelper.BGRA2YUV(input, nv21, m_width, m_height);
        //这个转换适用于相机。大约10ms可转一帧，已经可实用了。
        //NV21ToNV12(nv21,yuv420,m_width,m_height);
        long t2 = System.currentTimeMillis();
        Lg.d(TAG, "rgba -> nv21 time: %d, rgb.size:%d -> nv21.size:%d", (t2 - t1), input.length, nv21.length);

        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                pts = computePresentationTime(generateIndex);
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(nv21);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, nv21.length, pts, 0);
                generateIndex += 1;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);

            if(outputBufferIndex < 0){
                Log.e("offerEncoder","------------");
            }
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (m_info != null) {
                    System.arraycopy(outData, 0, output, pos, outData.length);
                    pos += outData.length;

                } else {//保存pps sps 只有开始时 第一个帧里有， 保存起来后面用
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[outData.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    } else {
                        return -1;
                    }
                }
                if ((output[4] & 0x1F) == 5) {//key frame 编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上
                    System.arraycopy(m_info, 0, output, 0, m_info.length);
                    System.arraycopy(outData, 0, output, m_info.length, outData.length);
                    pos += m_info.length;
                }
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable t) {
            Lg.e(TAG, " " + t);
        }
        //Log.e("offerEncoder","end");
        return pos;
    }

    private long computePresentationTime(long frameIndex) {
        return System.nanoTime();
    }

}

