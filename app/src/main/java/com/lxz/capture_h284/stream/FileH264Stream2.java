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
import okio.ByteString;
import okio.Okio;

public class FileH264Stream2 extends BaseStream{
    private static final String TAG = "FileH264Stream2";
    private static final int SIZE = 1024;
    private File saveFile;
    private BufferedSource source;
    private BufferedSink sink;
    private IRecvFrameCallback callback;
    private AtomicBoolean isRecvEnd = new AtomicBoolean(true);
    private ByteString startCode = ByteString.of((byte)0x0, (byte)0x0, (byte)0x0, (byte)0x1);

    public FileH264Stream2() {
        saveFile = Config.getSaveFile();
    }

    private byte[] readNextFrame() {
        try {
            long startIndex = source.indexOf(startCode);
            long endIndex  = source.indexOf(startCode, startIndex + 1);
            if (startIndex >= 0 && endIndex > 0) {
                long len = endIndex - startIndex;
                Lg.i(TAG, "startindex %d, endindex :%d, len:%d", startIndex, endIndex, len);
                return source.readByteArray(len);
            }
            else if (startIndex >= 0 && endIndex < 0) {
                Lg.i(TAG, "2 startindex %d, endindex :%d", startIndex, endIndex);
                return source.readByteArray();
            }
        } catch (IOException e) {
            Lg.e(TAG, "read next frame error " + e);
        }
        return null;
    }

    @Override
    public void startRecvFrame(final IRecvFrameCallback callback1) {
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
                if (callback != null) {
                    callback.onStart();
                }
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
                if (callback != null) {
                    callback.onEnd();
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
            logFrame(h264Data);
            sink.write(h264Data);
            sink.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
