package com.lxz.capture_h284.encode;

import android.view.Surface;

public interface IEncoder {
    Surface getSurface();

    void outputEncodeData();

    void release();

    boolean isStopEncode();
}
