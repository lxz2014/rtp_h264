package com.lxz.capture_h284.stream;

public interface IH264Stream {

    void startRecvFrame(IRecvFrameCallback callback);

    void writeFrame(byte[] h264Data);
}
