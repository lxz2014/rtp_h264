package com.lxz.capture_h284.tcp;

import com.iflytek.log.Lg;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import okio.BufferedSink;
import okio.Okio;

public class SenderThread extends Thread {
    private static final String TAG = "SenderThread";
    private String ip;
    private int port;

    public SenderThread(String ip, int port) {
        super("sender-");
        this.ip = ip;
        this.port = port;
    }

    @Override
    public void run() {
        SocketAddress address = new InetSocketAddress(ip, port);
        Socket socket = new Socket();
        try {
            socket.connect(address);
        } catch (IOException e) {
            Lg.e(TAG, "send connec error " + e);
        }

        BufferedSink sink = null;
        try {
            sink = Okio.buffer(Okio.sink(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true) {

        }
    }
}
