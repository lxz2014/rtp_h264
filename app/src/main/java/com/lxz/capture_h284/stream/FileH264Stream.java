package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.utils.CommUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;

public class FileH264Stream extends BaseStream{
    private static final int SIZE = 1024;
    private File saveFile;
    private BufferedSource source;
    private BufferedSink sink;
    private IRecvFrameCallback callback;
    private AtomicBoolean isRecvEnd = new AtomicBoolean(true);
    byte[] startCode = new byte[]{(byte)0x0, (byte)0x0, (byte)0x0, (byte)0x1};

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
        //Lg.i(TAG, "frame len =" + h264Data.length);
        try {
            int startIndex = 0;
            int endIndex = 0;
            int countFrame = 0;
            while (true) {
                startIndex = KMP(h264Data, startCode, startIndex);
                endIndex   = KMP(h264Data, startCode, startIndex + 1);
                endIndex = endIndex == -1 ? h264Data.length - 1 : endIndex;
                countFrame++;
                int len = endIndex - startIndex;
                byte[] lenByte = CommUtils.int2bytes( len);
                Lg.i(TAG, "sindex:%d, eindex:%d, len %d , frame:%d", startIndex, endIndex, len, countFrame);
                sink.write(lenByte);
                sink.write(h264Data, startIndex, len);
                sink.flush();
                if (endIndex == h264Data.length -1) {
                    break;
                }
                startIndex++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
