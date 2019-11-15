package com.lxz.capture_h284.comm;

import android.app.Application;

import com.iflytek.log.Lg;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Lg.init(this, "h264-", 10, true);
    }
}
