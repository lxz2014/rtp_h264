package com.lxz.capture_h284.encode;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodecInfo;
import android.util.Log;
import android.view.Surface;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.OutputBufferInfo;
import com.lxz.capture_h284.ScreenAvcEncoder;
import com.lxz.capture_h284.stream.IH264Stream;

import java.nio.ByteBuffer;

public class ImageReaderEncoder extends BaseEncoder {
    private static final String TAG = "ImageReaderEncoder";
    private Object lock = new Object();
    private byte[] bytes;
    private ImageReader imageReader;
    private byte[] pps;
    private byte[] nv21;

    public ImageReaderEncoder(IH264Stream outStream, int screenWidth, int screenHeight) {
        super(outStream, screenWidth, screenHeight);
        avcEncoder = new ScreenAvcEncoder(screenWidth, screenHeight
                , Config.encodeFps
                , MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        nv21 = new byte[screenHeight * screenWidth * 3 / 2];
        startReadBuffer();
    }

    private void startReadBuffer() {
        this.imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3);
        this.imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();

                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    //Lg.d("AvailableListener","img came " + planes.length + "," + image.getFormat());
                    //因为我们要求的是RGBA格式的数据，所以全部的存储在planes[0]中
                    Image.Plane plane = planes[0];
                    //由于Image中的缓冲区存在数据对齐，所以其大小不一定是我们生成ImageReader实例时指定的大小，
                    //ImageReader会自动为画面每一行最右侧添加一个padding，以进行对齐，对齐多少字节可能因硬件而异，
                    //所以我们在取出数据时需要忽略这一部分数据。
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;
                    int padding = rowPadding / pixelStride;

                    ByteBuffer buffer = plane.getBuffer();
                    synchronized (lock) {
                        Lg.i(TAG, "copy data %d, padding %d , pixelStride %d rowStride %d rowPadding %d"
                                , buffer.remaining()
                                , padding
                                , pixelStride
                                , rowStride
                                , rowPadding);
                        if (bytes == null) {
                            bytes = new byte[buffer.remaining()];
                        }
                        buffer.get(bytes, 0, bytes.length);//将数据读到数组中
                    }
                    image.close();
                }
            }
        }, null);
    }

    @Override
    public Surface getSurface() {
        return imageReader.getSurface();
    }

    @Override
    public void outputEncodeData() {
        synchronized (lock) {
            if (bytes == null) {
                return;
            }
            Lg.i(TAG, "queueInputBuffer %d", bytes.length);
            rgb2YCbCr420(bytes, nv21, screenWidth, screenHeight);
            avcEncoder.queueInputBuffer(nv21);
        }

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
