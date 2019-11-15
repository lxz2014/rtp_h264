package com.lxz.capture_h284;

import android.graphics.PixelFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.comm.BaseActivity;
import com.lxz.capture_h284.comm.SurfaceHolderCallbackAdapter;
import com.lxz.capture_h284.stream.H264StreamFactory;
import com.lxz.capture_h284.stream.IH264Stream;
import com.lxz.capture_h284.stream.IRecvFrameCallback;
import com.lxz.capture_h284.utils.CommUtils;

import java.nio.ByteBuffer;

import androidx.annotation.Nullable;

public class PlayMainActivity extends BaseActivity {
    private static final int TIME_INTERNAL = 1000;
    private SurfaceView surfaceView ;

    private static final String MIME_TYPE = "video/avc";
    private static final String TAG = "LocalPlayMainActivity";

    private int width = 720;
    private int height = 1280;
    private int fps = 15;//每秒帧率
    private int bitrate = width * height * 3;//编码比特率，
    private MediaCodec decode;
    private long timeoutUs = 1000;
    private IH264Stream debug;
    private long mCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_play);
        surfaceView = findViewById(R.id.surface);
//        DisplayMetrics d = CommUtils.getDispay(this);
//        width = d.widthPixels;
//        height = d.heightPixels;

        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(new SurfaceHolderCallbackAdapter() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initDecode(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                destroyDecode(holder);
            }
        });
        //holder.setFormat(PixelFormat.TRANSPARENT);

        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlayThread();
            }
        });

        debug = H264StreamFactory.createH264Stream();
    }

    private void startPlayThread() {
        final int sleep = 1000 / fps;
        debug.startRecvFrame(new IRecvFrameCallback() {
            @Override
            public void onFrame(byte[] frame) {
                if (decode == null) {
                    return;
                }

                if (frame != null ) {
                    long t1 = System.currentTimeMillis();
                    offerDecoder(frame, frame.length);
                    long t2 = System.currentTimeMillis();
                    Lg.i(TAG, "frame time: %d", (t2 - t1));
                }
                CommUtils.sleep(sleep );
            }
        });
    }

    //解码h264数据
    private void offerDecoder(byte[] input, int length) {
        try {
            int inputBufferIndex = decode.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decode.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                try {
                    inputBuffer.put(input, 0, length);
                } catch (Exception e) {
                    Lg.e(TAG, "offerDecoder input buffer error " + e);
                }
                decode.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime(), 0);
                mCount++;
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, timeoutUs);
            Lg.i(TAG, "outputBufferIndex %d ", outputBufferIndex);
            while (outputBufferIndex >= 0) {
                decode.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, timeoutUs);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String toType(int flags) {
        switch (flags) {
            case 0:
                return "frame";
            case MediaCodec.BUFFER_FLAG_KEY_FRAME:
                return "BUFFER_FLAG_KEY_FRAME";
            case MediaCodec.BUFFER_FLAG_CODEC_CONFIG:
                return "BUFFER_FLAG_CODEC_CONFIG";
            case MediaCodec.BUFFER_FLAG_END_OF_STREAM:
                return "BUFFER_FLAG_END_OF_STREAM";
            case MediaCodec.BUFFER_FLAG_PARTIAL_FRAME:
                return "BUFFER_FLAG_PARTIAL_FRAME";
        }
        return "";
    }

    private void initDecode(SurfaceHolder holder) {
        try {
            decode = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (Exception e) {
            Lg.e(TAG, "init decode error " + e);
        }
        Lg.d(TAG, "");
        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
//        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
//        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        //format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_Format25bitARGB1888);
        decode.configure(format, holder.getSurface(), null, 0);
        decode.start();
    }

    private void destroyDecode(SurfaceHolder holder) {
        if (decode != null) {
            decode.release();
        }
        decode = null;
    }
}
