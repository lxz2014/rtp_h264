package com.lxz.capture_h284;

import androidx.annotation.Nullable;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import rx.functions.Action1;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.comm.BaseActivity;
import com.lxz.capture_h284.stream.H264StreamFactory;
import com.lxz.capture_h284.stream.IH264Stream;
import com.lxz.capture_h284.utils.CommUtils;
import com.lxz.capture_h284.utils.SpUtils;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends BaseActivity implements View.OnClickListener {
    private static final int STATE_START = 1;
    private static final int STATE_STOP = 2;
    private static final int REQUEST_CODE = 11;
    private static final int ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION = 300;
    private static final String TAG = "MainActivity";
    public static final int DATA_LENGTH = 1480;

    Button start;
    Button stop;
    EditText editIp;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    private int state = STATE_STOP;
    private VirtualDisplay virtualDisplay;
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    private ScreenAvcEncoder avcEncoder;
    //private byte[] h264;
    private IH264Stream debugStream;
    private int dpi;
    private TextView choose;
    private int bitrate;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_realtimescreen);
        SpUtils.init(getApplication());
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        editIp = findViewById(R.id.edit_ip);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        choose = findViewById(R.id.choose);
        findViewById(R.id.show).setOnClickListener(this);
        findViewById(R.id.choose).setOnClickListener(this);
        RxPermissions.getInstance(MainActivity.this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)//这里填写所需要的权限多个的话可以逗号隔开
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        if (aBoolean) {//true表示获取权限成功（注意这里在android6.0以下默认为true）

                        } else {//表示权限被拒绝
                            Lg.e("permissions", Manifest.permission.READ_CALENDAR + "：获取失败"  );
                        }
                    }
                });
        //
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        dpi = dm.densityDpi;
//        screenHeight = dm.heightPixels / 2;//(int) (Math.max(dm.widthPixels, dm.heightPixels));
//        screenWidth = dm.widthPixels / 2;//(int) (Math.min(dm.widthPixels, dm.heightPixels));

        Lg.d(TAG, "dpi %d, density:%f, w:%d, h:%d" , dpi, dm.density,screenWidth , screenHeight);

        bitrate = screenHeight * screenWidth * 3;//编码比特率，
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        editIp.setText(Config.getIp());
        showModel();
        getMediaFormatFrom();

        textView = findViewById(R.id.txt_time);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isStart = !isStart;
                startTime();
            }
        });
    }
    private TextView textView;

    private boolean isStart = false;
    private Thread timeThread;
    private void startTime() {
        if (timeThread != null) {
            timeThread.interrupt();
        }
        timeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Lg.e(TAG, "开始计时器");
                Thread t = Thread.currentThread();
                while (t != null && !t.isInterrupted() && isStart) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("" + System.currentTimeMillis());
                        }
                    });
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Lg.e(TAG, "停止计时器");
            }
        });
        timeThread.start();
    }


    private void showModel() {
        choose.setText("切换模式,当前模式" + H264StreamFactory.model.name());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start:
                startCapture();
                break;
            case R.id.stop:
                stopCapture();
                test();
                break;
            case R.id.show:
                Intent it = new Intent(this, PlayMainActivity.class);
                startActivity(it);
                break;
            case R.id.choose:
                if (H264StreamFactory.model.equals(H264StreamFactory.Model.file)) {
                    H264StreamFactory.model = H264StreamFactory.Model.rtp;
                }
                else if (H264StreamFactory.model.equals(H264StreamFactory.Model.rtp)) {
                    H264StreamFactory.model = H264StreamFactory.Model.file;
                }
                choose.setText(H264StreamFactory.model .name());
                break;
        }
    }

    private void test() {
//        Buffer src = new Buffer();
//        Buffer dst = new Buffer();
//
//        byte [] d1 = new byte[20];
//        byte [] d2 = new byte[20];
//        Arrays.fill(d1, (byte)1);
//        Arrays.fill(d2, (byte)2);
//
//        src.write(d1);
//        dst.write(d2);
//
//        dst.write(src, 12);

        byte [] mm = new byte[8192 * 2];
        Arrays.fill(mm, (byte)55);
        Buffer max = new Buffer();
        max.write(mm);
        Lg.d(TAG, "XXXX");
    }

    private void getMediaFormatFrom() {
        MediaExtractor extractor = new MediaExtractor();
        try {
            File file=new File(Environment.getExternalStorageDirectory(),"save.h264");
            Lg.d(TAG,file.getAbsolutePath());
            extractor.setDataSource(file.getAbsolutePath());
        } catch (IOException e) {
            Lg.e(TAG, "getMediaFormatFrom error " + e);
        }
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Lg.d(TAG, "mime="+mime);
        }
        extractor.release();
    }

    private void stopCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        virtualDisplay = null;
    }

    private void startCapture() {
        Config.setIp(editIp.getText().toString());
        // 申请相关权限成功后，要向用户申请录屏对话框
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            startActivityForResult(intent, REQUEST_CODE);
        } else {
            showToast("不支持录屏");
        }
    }

    private void exeRecord() {
        avcEncoder = new ScreenAvcEncoder(screenWidth, screenHeight , Config.fps, bitrate);
        this.virtualDisplay = this.mediaProjection.createVirtualDisplay(
                "Recording Display"
                , screenWidth
                , screenHeight
                , 1
                , DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                , avcEncoder.getSurface()
                , new VirtualDisplay.Callback() {
                    @Override
                    public void onPaused() {
                        Lg.d(TAG, "onPause");
                    }

                    @Override
                    public void onResumed() {
                        Lg.d(TAG, "onResumed");
                    }

                    @Override
                    public void onStopped() {
                        Lg.d(TAG, "onStopped");
                    }
                },
                new Handler());
        //
        new Thread(new Runnable() {
            private byte[] pps = null;
            @Override
            public void run() {
                debugStream = H264StreamFactory.createH264Stream();
                while (virtualDisplay != null) {
                    OutputBufferInfo outputInfo = avcEncoder.dequeueOutputBuffer();
                    while (outputInfo.outputBufferIndex >= 0) {
                        byte[] h264Data = avcEncoder.getOutputBuffer(outputInfo.size, outputInfo.outputBufferIndex);
                        if (h264Data != null) {
                            //int nextIndex = CommUtils.KMP(h264Data, new byte[]{0,0,0,1}, 1);
                            Lg.i(TAG, "getOutputBuffer frame len %d, next", h264Data.length);
                            if (frameHead(h264Data) == 7 && pps == null) {
                                Lg.e(TAG, "pps frame..");
                                pps = new byte[h264Data.length];
                                System.arraycopy(h264Data, 0, pps, 0, h264Data.length);
                            }
                            else if (frameHead(h264Data) == 5 && pps != null) {
                                Lg.e(TAG, "IDR frame, write pps");
                                debugStream.writeFrame(pps);
                            }
                            debugStream.writeFrame(h264Data);
                        }
                        outputInfo = avcEncoder.dequeueOutputBuffer();
                    }
                    CommUtils.sleep(50);
                }
                showToast("停止录制");
                Lg.e(TAG, "停止录制");
                if (avcEncoder != null) {
                    avcEncoder.close();
                }
                pps = null;
            }
        }).start();
    }

    private int frameHead(byte[] h264Data) {
        return h264Data[4] & 0x1f;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                if (state == STATE_STOP) {
                    exeRecord();
                }
            } else {
                showToast("请求录制被拒绝");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }


}
