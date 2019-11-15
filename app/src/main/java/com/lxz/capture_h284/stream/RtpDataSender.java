package com.lxz.capture_h284.stream;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.lxz.capture_h284.Config;
import com.lxz.capture_h284.comm.jlibrtp.DataFrame;
import com.lxz.capture_h284.comm.jlibrtp.Participant;
import com.lxz.capture_h284.comm.jlibrtp.RTPAppIntf;
import com.lxz.capture_h284.comm.jlibrtp.RTPSession;

import java.net.DatagramSocket;

/**
 * Created by xhrong on 2019/9/29.
 */

public class RtpDataSender implements RTPAppIntf {

    public RTPSession rtpSession = null;
    private static final String TAG = "RtpDataSend";

    private static final int MSG_INIT=0x01;
    private static final int MSG_SEND=0x02;
    private static final int MSG_CLOSE=0x03;

    private Handler handler;
    private Object lockObj=new Object();


    private byte[][] dataToSend;
    private boolean[]marks;


    public RtpDataSender() {
        HandlerThread handlerThread = new HandlerThread("sender");
        handlerThread.start();
        handler =new Handler(handlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                int what = message.what;
                switch (what) {
                    case MSG_INIT:
                        doInit();
                        break;
                    case MSG_SEND:
                        doSend();
                        break;
                    case MSG_CLOSE:
                        doClose();
                        break;
                }
                return false;
            }
        });
        init();
    }

    private void init(){
        handler.sendEmptyMessage(MSG_INIT);
    }

    public void  close(){
        handler.sendEmptyMessage(MSG_CLOSE);
    }

    public void sendData(byte[][] data,boolean[] marks){
        synchronized (lockObj){
            this.dataToSend = data;
            this.marks =marks;
        }
        handler.sendEmptyMessage(MSG_SEND);
    }


    private void doInit(){
        DatagramSocket rtpSocket = null;
        DatagramSocket rtcpSocket = null;

        try {
            rtpSocket = new DatagramSocket(4003);
            rtcpSocket = new DatagramSocket(4004);
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }

        //建立会话
        rtpSession = new RTPSession(rtpSocket, rtcpSocket);
        rtpSession.RTPSessionRegister(this, null, null);
        //设置参与者（目标IP地址，RTP端口，RTCP端口）
        Participant p = new Participant(Config.getIp(), 4003, 4004);
        rtpSession.addParticipant(p);
        rtpSession.payloadType(96);
    }

    private void doSend(){
        synchronized (lockObj){
            rtpSession.sendData(dataToSend, null, marks, -1, null);
        }
    }


    private void doClose(){
        if (rtpSession!=null){
            rtpSession.endSession();
        }
    }

    @Override
    public void receiveData(DataFrame frame, Participant participant) {
        // TODO Auto-generated method stub

    }

    @Override
    public void userEvent(int type, Participant[] participant) {
        // TODO Auto-generated method stub

    }

    @Override
    public int frameSize(int payloadType) {
        // TODO Auto-generated method stub
        return 1;
    }
}
