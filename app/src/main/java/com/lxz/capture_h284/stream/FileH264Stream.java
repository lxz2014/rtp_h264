package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.utils.CommUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class FileH264Stream implements IH264Stream{
    private static final String TAG = "OkIoH264Stream";
    private static final int SIZE = 1024;
    private File saveFile;
    private BufferedSource source;
    private BufferedSink sink;
    private IRecvFrameCallback callback;
    private AtomicBoolean isRecvEnd = new AtomicBoolean(true);

    public FileH264Stream() {
        saveFile = Config.getSaveFile();
    }

    private byte[] readNextFrame() {
        try {
            byte [] size = new byte[4];
            int ret = source.read(size, 0, 4);
            if (ret > 0) {
                int len = CommUtils.bytes2int(size);
                Lg.i(TAG, "read len " + len);
                byte[] data = source.readByteArray(len);
                log(data);
                return data;
            }
        } catch (IOException e) {
            Lg.e(TAG, "read next frame error " + e);
        }
        return null;
    }

    @Override
    public void startRecvFrame(IRecvFrameCallback callback1) {
        if (!isRecvEnd.get()) {
            Lg.e(TAG, "未结束");
            return;
        }

        try {
            source = Okio.buffer(Okio.source(saveFile));
        } catch (FileNotFoundException e) {
            Lg.e(TAG, "open file fail:" + e);
        }

        isRecvEnd.set(false);
        this.callback = callback1;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    byte[] frame = readNextFrame();
                    if (frame == null) {
                        Lg.e(TAG, "read end..");
                        break;
                    }

                    if (callback != null) {
                        callback.onFrame(frame);
                    }
                }
                CommUtils.closeIo(source);
                isRecvEnd.set(true);
            }
        }).start();
    }

    @Override
    public void writeFrame(byte[] h264Data) {
        if (sink == null) {
            try {
                sink = Okio.buffer(Okio.sink(saveFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        Lg.i(TAG, "frame len =" + h264Data.length);
        try {
            log(h264Data);
            byte[] lenByte = CommUtils.int2bytes(h264Data.length);
            Lg.i(TAG, "len %d -> int2byte %d", h264Data.length, CommUtils.bytes2int(lenByte));
            sink.write(lenByte);
            sink.write(h264Data);
            sink.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(byte[] h264Data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6 && i < h264Data.length; i++) {
            String dex = Integer.toHexString(h264Data[i]);
            sb.append(",").append(dex);
        }
        Lg.i(TAG, "head %s", sb.toString().replace("f", ""));
    }
}
