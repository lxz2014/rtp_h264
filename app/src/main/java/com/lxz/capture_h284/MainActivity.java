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
    private int screenWidth = 1280;//1920;
    private int screenHeight = 720;//1080;
    private ScreenAvcEncoder avcEncoder;
    private byte[] h264;
    private IH264Stream debugStream;
    private int dpi;
    private TextView choose;

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

        int bitrate = screenHeight * screenWidth * 3;//编码比特率，
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        avcEncoder = new ScreenAvcEncoder(screenWidth, screenHeight , 15, bitrate);
        h264 = new byte[screenWidth * screenHeight * 3];

        editIp.setText(Config.getIp());
        showModel();
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

    private void exeRecord() {
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
            @Override
            public void run() {
                debugStream = H264StreamFactory.createH264Stream();
                while (virtualDisplay != null) {
                    int ret = avcEncoder.getOutputBuffer(h264);
                    if (ret > 0) {
                        //实时发送数据流
                        byte[] h264Data = new byte[ret];
                        System.arraycopy(h264, 0, h264Data, 0, ret);
                        debugStream.writeFrame(h264Data);
                    }
                    CommUtils.sleep(50);
                }
                showToast("停止录制");
                Lg.e(TAG, "停止录制");
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
