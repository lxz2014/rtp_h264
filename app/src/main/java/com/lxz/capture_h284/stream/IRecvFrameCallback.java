package com.lxz.capture_h284.stream;

public interface IRecvFrameCallback {
    void onFrame(byte[] bytes);

    void onStart();

    void onEnd();
}
