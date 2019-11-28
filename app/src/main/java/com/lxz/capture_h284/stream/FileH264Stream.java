package com.lxz.capture_h284.stream;

import android.os.Environment;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.utils.CommUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class FileH264Stream extends BaseStream{
    private static final int SIZE = 1024;
    private File saveFile;
    private BufferedSource source;
    private BufferedSink sink;
    private IRecvFrameCallback callback;
    private AtomicBoolean isRecvEnd = new AtomicBoolean(true);
    private byte[] startCode = new byte[]{(byte)0x0, (byte)0x0, (byte)0x0, (byte)0x1};

    //private BufferedSink lenSink ;
    private String lenFile;

    public FileH264Stream() {
        saveFile = Config.getSaveFile();
        lenFile = Environment.getExternalStorageDirectory().getPath() + "/lenfile.txt";
    }

    private byte[] readNextFrame() {
        try {
            byte [] size = new byte[4];
            source.readFully(size);
            int len = CommUtils.bytes2int(size);
            byte[] data = source.readByteArray(len);
            //Lg.i(TAG, "read len %d, data.len:%d" , len, data.length);
            //logFrame(data);
            return data;
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

    private int rowcount = 0;
    @Override
    public void writeFrame(byte[] h264Data) {
        if (sink == null) {
            try {
                if (saveFile.exists()) {
                    saveFile.delete();
                    Lg.e(TAG, "delete old file ");
                }
                sink = Okio.buffer(Okio.sink(saveFile));
                //lenSink = Okio.buffer(Okio.sink(new File(lenFile)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        //Lg.i(TAG, "frame len =" + h264Data.length);
        //logFrame(h264Data);
        try {
            byte[] lenByte = CommUtils.int2bytes(h264Data.length);
            //Lg.i(TAG, "frame len %d -> byteint %d" , h264Data.length, CommUtils.bytes2int(lenByte));
//            lenSink.writeUtf8(String.format("%d,", h264Data.length));
//            if (rowcount++ % 100 == 0) {
//                lenSink.writeUtf8("\n");
//            }
            sink.write(lenByte);
            sink.write(h264Data);
            sink.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
