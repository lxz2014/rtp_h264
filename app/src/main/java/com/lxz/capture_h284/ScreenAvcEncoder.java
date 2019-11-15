package com.lxz.capture_h284;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.iflytek.log.Lg;

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
        //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);//关键帧间隔时间 单位s
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

    public int getOutputBuffer(byte[] output) {
        Log.v("xmc", "getOutputBuffer");
        int pos = 0;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

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

        Log.v("xmc", "getOutputBuffer+pos:" + pos);
        return pos;
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

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize + 1];
            nv12[framesize + j + 1] = nv21[j + framesize];
        }
    }

    //网友提供的，如果swapYV12toI420方法颜色不对可以试下这个方法，不同机型有不同的转码方式
    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        Log.v("xmc", "NV21toI420SemiPlanar:::" + width + "+" + height);
        final int iSize = width * height;
        System.arraycopy(nv21bytes, 0, i420bytes, 0, iSize);

        for (int iIndex = 0; iIndex < iSize / 2; iIndex += 2) {
            i420bytes[iSize + iIndex / 2 + iSize / 4] = nv21bytes[iSize + iIndex]; // U
            i420bytes[iSize + iIndex / 2] = nv21bytes[iSize + iIndex + 1]; // V
        }
    }

    //yv12 转 yuv420p  yvu -> yuv
    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        Log.v("xmc", "swapYV12toI420:::" + width + "+" + height);
        Log.v("xmc", "swapYV12toI420:::" + yv12bytes.length + "+" + i420bytes.length + "+" + width * height);
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }


    /**
     * RGB图片转YUV420数据
     * 宽、高不能为奇数
     * 这个方法太耗时了，需要优化。用C实现（C实现的结果并没有JAVA的性能好。奇怪。）
     *
     * @param width  宽
     * @param height 高
     * @return
     */
    public void rgb2YCbCr420(byte[] byteArgb, byte[] yuv, int width, int height) {
        Log.e("xmc", "encodeYUV420SP start");
        int len = width * height;
        int r, g, b, y, u, v, c;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                c = (i * width + j) * 4;
                r = byteArgb[c] & 0xFF;
                g = byteArgb[c + 1] & 0xFF;
                b = byteArgb[c + 2] & 0xFF;
                //   int a = byteArgb[(i * width + j) * 4 + 3]&0xFF;
                //套用公式
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                //调整
                y = y < 16 ? 16 : (y > 255 ? 255 : y);
                u = u < 0 ? 0 : (u > 255 ? 255 : u);
                v = v < 0 ? 0 : (v > 255 ? 255 : v);
                //赋值
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1) + 0] = (byte) u;
                yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        Log.e("xmc", "encodeYUV420SP end");
    }
}

