package com.lxz.capture_h284;

import androidx.annotation.Nullable;
import rx.functions.Action1;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.comm.BaseActivity;
import com.lxz.capture_h284.encode.EncoderUtils;
import com.lxz.capture_h284.encode.IEncoder;
import com.lxz.capture_h284.encode.ImageReaderEncoder;
import com.lxz.capture_h284.encode.ImageReaderEncoder2;
import com.lxz.capture_h284.encode.SurfaceEncoder;
import com.lxz.capture_h284.stream.H264StreamFactory;
import com.lxz.capture_h284.stream.IH264Stream;
import com.lxz.capture_h284.utils.CommUtils;
import com.lxz.capture_h284.utils.SpUtils;
import com.tbruyelle.rxpermissions.RxPermissions;

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
    private int dpi;
    private TextView choose;
    private ImageReader imageReader;
    private TextView textView;
    private boolean isStart = false;
    private Thread timeThread;

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
        Lg.d(TAG, "dpi %d, density:%f, w:%d, h:%d" , dpi, dm.density,screenWidth , screenHeight);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        editIp.setText(Config.getIp());
        showModel();
        textView = findViewById(R.id.txt_time);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isStart = !isStart;
                startTime();
            }
        });

        EncoderUtils.findEncoder();
    }

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

    private IEncoder encoder;
    private void exeRecord() {
        IH264Stream outputStream = H264StreamFactory.createH264Stream();
        encoder = new ImageReaderEncoder2(outputStream, screenWidth, screenHeight);//new SurfaceEncoder(debugStream, screenWidth, screenHeight);
        this.virtualDisplay = this.mediaProjection.createVirtualDisplay(
                "Recording Display"
                , screenWidth
                , screenHeight
                , 1
                , DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                , encoder.getSurface()
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
            @Override
            public void run() {
                int fpsTime = 1000 / Config.encodeFps;
                while (virtualDisplay != null) {
                    long t1 = System.currentTimeMillis();
                    encoder.outputEncodeData();
                    long t2 = System.currentTimeMillis();
                    long dt = t2 - t1;
                    //Lg.i(TAG, "time dt %d", dt);
                    CommUtils.sleep(fpsTime <= dt ? 0 : (int) (fpsTime - dt));
                }

                showToast("停止录制");
                Lg.e(TAG, "停止录制");
                if (encoder != null) {
                    encoder.release();
                }
            }
        }).start();
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
                showToast("录制权限竟然被拒绝了！！");
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
