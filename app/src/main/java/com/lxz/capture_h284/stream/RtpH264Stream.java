package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;
import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.comm.jlibrtp.DataFrame;
import com.lxz.capture_h284.comm.jlibrtp.Participant;
import com.lxz.capture_h284.comm.jlibrtp.RTPAppIntf;
import com.lxz.capture_h284.comm.jlibrtp.RTPSession;

import java.net.DatagramSocket;

public class RtpH264Stream implements IH264Stream {
    private static final String TAG = "RtpH264Stream";
    private static final int DATA_LENGTH = Config.DATA_LEN;
    private RtpDataSender sender;
    private RtpDataReceive receive;
    @Override
    public void startRecvFrame(final IRecvFrameCallback callback) {
        if (receive != null) {
            Lg.e(TAG, "已经启动");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                Lg.d(TAG, "start rtp recv");
                receive = new RtpDataReceive(callback);
            }
        }).start();
    }

    @Override
    public void writeFrame(byte[] h264Data) {
        if (sender == null) {
            sender = new RtpDataSender();
        }
        Lg.i(TAG, "frame len =" + h264Data.length);
        int dataLength = (h264Data.length - 1) / DATA_LENGTH + 1;
        byte [][]sendData = new byte[dataLength][];
        boolean []marks = new boolean[dataLength];
        
        marks[marks.length - 1] = true;
        int x = 0, y = 0;
        int length = h264Data.length;
        for (int i = 0; i < length; i++) {
            if (y == 0) {
                sendData[x] = new byte[length - i > DATA_LENGTH ? DATA_LENGTH : length - i];
            }
            sendData[x][y] = h264Data[i];
            y++;
            if (y == sendData[x].length) {
                y = 0;
                x++;
            }
        }
        sender.sendData(sendData, marks);
    }

    public class RtpDataReceive implements RTPAppIntf {
        private IRecvFrameCallback frameCallback;
        public RTPSession rtpSession = null;
        private byte[] buf;

        public RtpDataReceive(IRecvFrameCallback callback) {
            this.frameCallback = callback;
            DatagramSocket rtpSocket = null;
            DatagramSocket rtcpSocket = null;

            try {
                rtpSocket = new DatagramSocket(4003);
                rtcpSocket = new DatagramSocket(4004);
            } catch (Exception e) {
                Lg.e(TAG, "接收创建会话异常抛出:" + e);
            }
            Lg.d(TAG, "create rtp recv");
            //建立会话
            rtpSession = new RTPSession(rtpSocket, rtcpSocket);
            rtpSession.naivePktReception(true);
            rtpSession.RTPSessionRegister(this, null, null);
        }


        @Override
        public void receiveData(DataFrame frame, Participant participant) {
            if (buf == null) {
                buf = frame.getConcatenatedData();
            } else {
                buf = merge(buf, frame.getConcatenatedData());
            }
            Lg.d(TAG, "recv data %d", buf.length);
            //合包
            if (frame.marked()) {
                if (frameCallback != null) {
                    frameCallback.onFrame(buf);
                }
                buf = null;
            }
        }

        @Override
        public void userEvent(int type, Participant[] participant) {
            if (participant != null && participant.length > 0) {
                Lg.d(TAG, "userEvent type:%d, par:%s", type, participant[0]);
            }
            else {
                Lg.d(TAG, "userEvent type:%d, par: null", type);
            }
        }

        @Override
        public int frameSize(int payloadType) {

            return 1;
        }


        private byte[] merge(byte[] bt1, byte[] bt2) {
            byte[] bt3 = new byte[bt1.length + bt2.length];
            System.arraycopy(bt1, 0, bt3, 0, bt1.length);
            System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
            return bt3;
        }
    }
}
