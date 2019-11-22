package com.lxz.capture_h284;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
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
    private static final String TAG = "PlayMainActivity";

    private int width = 720;
    private int height = 1280;
    private int fps = 15;//每秒帧率
    private int bitrate = width * height * 3;//编码比特率，
    private MediaCodec decode;
    private long timeoutUs = 100;
    private IH264Stream debug;
    private long outputBufferCount = 0;
    private long frameCount = 0;

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

//        int color = Color.parseColor("#64297be8");
//        FrameLayout frameLayout = (FrameLayout) getWindow().getDecorView();
//        frameLayout.setForeground(new ColorDrawable(color));
    }

    private void initDecode(SurfaceHolder holder) {
        try {
            decode = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (Exception e) {
            Lg.e(TAG, "init decode error " + e);
        }
        Lg.d(TAG, "");
        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, Config.fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Config.KEY_I_FRAME_INTERVAL);
        decode.configure(format, holder.getSurface(), null, 0);
        decode.start();
    }

    private void startPlayThread() {
        final int sleep = 1000 / fps;
        debug.startRecvFrame(new IRecvFrameCallback() {
            private long startPlayTime = 0;
            private boolean isFirst;
            @Override
            public void onFrame(byte[] frame) {
                if (decode == null) {
                    return;
                }

                long t1 = System.currentTimeMillis();
                if (frame != null ) {
                    offerDecoder(frame, frame.length);
                    //Lg.i(TAG, "frame time: %d", (t2 - t1));
                }
                long t2 = System.currentTimeMillis();

                CommUtils.sleep((int) (sleep - (t2 - t1)));
            }

            @Override
            public void onStart() {
                outputBufferCount = 0;
                frameCount = 0;
                startPlayTime = System.currentTimeMillis();
                decode.flush();
            }

            @Override
            public void onEnd() {
                Lg.e(TAG, "play time %d outputBufferCount %d, frameCount %d"
                        , (System.currentTimeMillis() - startPlayTime)
                        , outputBufferCount
                        , frameCount);
            }
        });
    }

    //解码h264数据
    private void offerDecoder(byte[] input, int length) {
        try {
            frameCount++;
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
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, timeoutUs);
            while (outputBufferIndex >= 0) {
                outputBufferCount++;
                Lg.i(TAG, "outputBufferIndex %d , count:%d, frame:%d, bufferInfo : %s"
                        , outputBufferIndex, outputBufferCount, frameCount
                        , logBufferIInfo(bufferInfo));
                decode.releaseOutputBuffer(outputBufferIndex, true);

                outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, timeoutUs);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String logBufferIInfo(MediaCodec.BufferInfo bufferInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("size:").append(bufferInfo.size)
        .append(",flag:").append(bufferInfo.flags)
        .append(",offset:").append(bufferInfo.offset)
        .append(",presentationTimeUs:").append(bufferInfo.presentationTimeUs);
        return sb.toString();
    }

    private void destroyDecode(SurfaceHolder holder) {
        if (decode != null) {
            decode.release();
        }
        decode = null;
    }
}
