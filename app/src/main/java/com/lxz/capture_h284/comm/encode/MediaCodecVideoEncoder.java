/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package com.lxz.capture_h284.comm.encode;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;

import com.iflytek.log.Lg;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

// Java-side of peerconnection_jni.cc:MediaCodecVideoEncoder.
// This class is an implementation detail of the Java PeerConnection API.
@TargetApi(19)
@SuppressWarnings("deprecation")
public class MediaCodecVideoEncoder {
    // This class is constructed, operated, and destroyed by its C++ incarnation,
    // so the class and its methods have non-public visibility.  The API this
    // class exposes aims to mimic the webrtc::VideoEncoder API as closely as
    // possibly to minimize the amount of translation work necessary.

    private static final String TAG = "MediaCodecVideoEncoder";

    // Tracks webrtc::VideoCodecType.
    public enum VideoCodecType {
        VIDEO_CODEC_VP8,
        VIDEO_CODEC_VP9,
        VIDEO_CODEC_H264
    }

    private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000; // Timeout for codec releasing.
    private static final int DEQUEUE_TIMEOUT = 0;  // Non-blocking, no wait.
    // Active running encoder instance. Set in initEncode() (called from native code)
    // and reset to null in release() call.
    private static MediaCodecVideoEncoder runningInstance = null;
    private static MediaCodecVideoEncoderErrorCallback errorCallback = null;
    private static int codecErrors = 0;
    // List of disabled codec types - can be set from application.
    private static Set<String> hwEncoderDisabledTypes = new HashSet<String>();

    private Thread mediaCodecThread;
    private MediaCodec mediaCodec;
    private ByteBuffer[] outputBuffers;
    //private EglBase14 eglBase;
    private int width;
    private int height;
    //private Surface inputSurface;
    //private GlRectDrawer drawer;
    private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
    private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
    private static final String H264_MIME_TYPE = "video/avc";
    // List of supported HW VP8 codecs.
    private static final String[] supportedVp8HwCodecPrefixes =
            {"OMX.qcom.", "OMX.Intel."};
    // List of supported HW VP9 decoders.
    private static final String[] supportedVp9HwCodecPrefixes =
            {"OMX.qcom."};
    // List of supported HW H.264 codecs.
    private static final String[] supportedH264HwCodecPrefixes =
            {"OMX.qcom.", "OMX.allwinner.", "OMX.IMG.", "OMX.hisi"};
    // List of devices with poor H.264 encoder quality.
    private static final String[] H264_HW_EXCEPTION_MODELS = new String[]{
            // HW H.264 encoder on below devices has poor bitrate control - actual
            // bitrates deviates a lot from the target value.
            "SAMSUNG-SGH-I337",
            "Nexus 7",
            "Nexus 4"
    };

    // Bitrate modes - should be in sync with OMX_VIDEO_CONTROLRATETYPE defined
    // in OMX_Video.h
    private static final int VIDEO_ControlRateConstant = 2;
    // NV12 color format supported by QCOM codec, but not declared in MediaCodec -
    // see /hardware/qcom/media/mm-core/inc/OMX_QCOMExtns.h
    private static final int
            COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 0x7FA30C04;
    // Allowable color formats supported by codec - in order of preference.
    private static final int[] supportedColorList = {
            CodecCapabilities.COLOR_FormatYUV420Planar,
            CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
            COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m
    };
    private static final int[] supportedSurfaceColorList = {
            CodecCapabilities.COLOR_FormatSurface
    };
    private VideoCodecType type;
    private int colorFormat;  // Used by native code.

    // SPS and PPS NALs (Config frame) for H.264.
    private ByteBuffer configData = null;

    // MediaCodec error handler - invoked when critical error happens which may prevent
    // further use of media codec API. Now it means that one of media codec instances
    // is hanging and can no longer be used in the next call.
    public static interface MediaCodecVideoEncoderErrorCallback {
        void onMediaCodecVideoEncoderCriticalError(int codecErrors);
    }

    public static void setErrorCallback(MediaCodecVideoEncoderErrorCallback errorCallback) {
        Lg.d(TAG, "Set error callback");
        MediaCodecVideoEncoder.errorCallback = errorCallback;
    }

    // Functions to disable HW encoding - can be called from applications for platforms
    // which have known HW decoding problems.
    public static void disableVp8HwCodec() {
        Lg.w(TAG, "VP8 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(VP8_MIME_TYPE);
    }

    public static void disableVp9HwCodec() {
        Lg.w(TAG, "VP9 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(VP9_MIME_TYPE);
    }

    public static void disableH264HwCodec() {
        Lg.w(TAG, "H.264 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(H264_MIME_TYPE);
    }

    // Functions to query if HW encoding is supported.
    public static boolean isVp8HwSupported() {
        Lg.d(TAG, "isVp8HwSupported");
        return !hwEncoderDisabledTypes.contains(VP8_MIME_TYPE) &&
                (findHwEncoder(VP8_MIME_TYPE, supportedVp8HwCodecPrefixes, supportedColorList) != null);
    }

    public static boolean isVp9HwSupported() {
        Lg.d(TAG, "isVp9HwSupported");
        return !hwEncoderDisabledTypes.contains(VP9_MIME_TYPE) &&
                (findHwEncoder(VP9_MIME_TYPE, supportedVp9HwCodecPrefixes, supportedColorList) != null);
    }

    public static boolean isH264HwSupported() {
        Lg.d(TAG, "isH264HwSupported");
        return !hwEncoderDisabledTypes.contains(H264_MIME_TYPE) &&
                (findHwEncoder(H264_MIME_TYPE, supportedH264HwCodecPrefixes, supportedColorList) != null);
    }

    public static boolean isVp8HwSupportedUsingTextures() {
        Lg.d(TAG, "isVp8HwSupportedUsingTextures");
        return !hwEncoderDisabledTypes.contains(VP8_MIME_TYPE) && (findHwEncoder(
                VP8_MIME_TYPE, supportedVp8HwCodecPrefixes, supportedSurfaceColorList) != null);
    }

    public static boolean isVp9HwSupportedUsingTextures() {
        Lg.d(TAG, "isVp9HwSupportedUsingTextures");
        return !hwEncoderDisabledTypes.contains(VP9_MIME_TYPE) && (findHwEncoder(
                VP9_MIME_TYPE, supportedVp9HwCodecPrefixes, supportedSurfaceColorList) != null);
    }

    public static boolean isH264HwSupportedUsingTextures() {
        Lg.d(TAG, "isH264HwSupportedUsingTextures");
        return !hwEncoderDisabledTypes.contains(H264_MIME_TYPE) && (findHwEncoder(
                H264_MIME_TYPE, supportedH264HwCodecPrefixes, supportedSurfaceColorList) != null);
    }

    // Helper struct for findHwEncoder() below.
    private static class EncoderProperties {
        public EncoderProperties(String codecName, int colorFormat) {
            this.codecName = codecName;
            this.colorFormat = colorFormat;
        }

        public final String codecName; // OpenMax component name for HW codec.
        public final int colorFormat;  // Color format supported by codec.

        @Override
        public String toString() {
            return "EncoderProperties{" +
                    "codecName='" + codecName + '\'' +
                    ", colorFormat=" + colorFormat +
                    '}';
        }
    }

    private static EncoderProperties findHwEncoder(
            String mime, String[] supportedHwCodecPrefixes, int[] colorList) {
        // MediaCodec.setParameters is missing for JB and below, so bitrate
        // can not be adjusted dynamically.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }
        Lg.d(TAG, "findHwEncoder start minme:%s ", mime);
        // Check if device is in H.264 exception list.
        if (mime.equals(H264_MIME_TYPE)) {
            List<String> exceptionModels = Arrays.asList(H264_HW_EXCEPTION_MODELS);
            if (exceptionModels.contains(Build.MODEL)) {
                Lg.w(TAG, "Model: " + Build.MODEL + " has black listed H.264 encoder.");
                return null;
            }
        }

        for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String name = null;
            for (String mimeType : info.getSupportedTypes()) {
                if (mimeType.equals(mime)) {
                    name = info.getName();
                    break;
                }
            }
            if (name == null) {
                continue;  // No HW support in this codec; try the next one.
            }
            Lg.d(TAG, "Found candidate encoder " + name);

            // Check if this is supported HW encoder.
            boolean supportedCodec = false;
            for (String hwCodecPrefix : supportedHwCodecPrefixes) {
                if (name.startsWith(hwCodecPrefix)) {
                    supportedCodec = true;
                    break;
                }
            }

            if (!supportedCodec) {
                continue;
            }

            CodecCapabilities capabilities = info.getCapabilitiesForType(mime);
            for (int colorFormat : capabilities.colorFormats) {
                Lg.d(TAG, "mime:" + mime +" ->  Color: 0x" + Integer.toHexString(colorFormat));
            }

            for (int supportedColorFormat : colorList) {
                for (int codecColorFormat : capabilities.colorFormats) {
                    if (codecColorFormat == supportedColorFormat) {
                        // Found supported HW encoder.
                        Lg.w(TAG, "Found target encoder for mime " + mime + " : " + name +
                                ". Color: 0x" + Integer.toHexString(codecColorFormat));
                        return new EncoderProperties(name, codecColorFormat);
                    }
                }
            }
        }
        return null;  // No HW encoder.
    }

    private void checkOnMediaCodecThread() {
        if (mediaCodecThread.getId() != Thread.currentThread().getId()) {
            throw new RuntimeException(
                    "MediaCodecVideoEncoder previously operated on " + mediaCodecThread +
                            " but is now called on " + Thread.currentThread());
        }
    }

    private static void printStackTrace() {
        if (runningInstance != null && runningInstance.mediaCodecThread != null) {
            StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
            if (mediaCodecStackTraces.length > 0) {
                Lg.d(TAG, "MediaCodecVideoEncoder stacks trace:");
                for (StackTraceElement stackTrace : mediaCodecStackTraces) {
                    Lg.d(TAG, stackTrace.toString());
                }
            }
        }
    }

    public static MediaCodec createByCodecName(String codecName) {
        try {
            // In the L-SDK this call can throw IOException so in order to work in
            // both cases catch an exception.
            return MediaCodec.createByCodecName(codecName);
            //return MediaCodec.createEncoderByType(H264_MIME_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean initEncode(VideoCodecType type, int width, int height, int kbps, int fps) {
        Lg.d(TAG, "Java initEncode: " + type + " : " + width + " x " + height +
                ". @ " + kbps + " kbps. Fps: " + fps);

        this.width = width;
        this.height = height;
        if (mediaCodecThread != null) {
            throw new RuntimeException("Forgot to release()?");
        }
        EncoderProperties properties = null;
        String mime = null;
        int keyFrameIntervalSec = 0;
        if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = VP8_MIME_TYPE;
            properties = findHwEncoder(VP8_MIME_TYPE, supportedVp8HwCodecPrefixes,
                    supportedColorList);
            keyFrameIntervalSec = 100;
        } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = VP9_MIME_TYPE;
            properties = findHwEncoder(VP9_MIME_TYPE, supportedH264HwCodecPrefixes,
                    supportedColorList);
            keyFrameIntervalSec = 100;
        } else if (type == VideoCodecType.VIDEO_CODEC_H264) {
            mime = H264_MIME_TYPE;
            properties = findHwEncoder(H264_MIME_TYPE, supportedH264HwCodecPrefixes,
                    supportedColorList);
            keyFrameIntervalSec = 20;
        }
        if (properties == null) {
            throw new RuntimeException("Can not find HW encoder for " + type);
        }
        Lg.d(TAG, "init encode proper:" + properties.toString() + ", type : " + type);
        runningInstance = this; // Encoder is now running and can be queried for stack traces.
        colorFormat = properties.colorFormat;
        Lg.d(TAG, "Color format: " + colorFormat);

        mediaCodecThread = Thread.currentThread();
        try {
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * kbps);
            format.setInteger("bitrate-mode", VIDEO_ControlRateConstant);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, /*properties.colorFormat*/CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            //format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameIntervalSec);
            Lg.d(TAG, "  Format: " + format);
            mediaCodec = createByCodecName(properties.codecName);
            this.type = type;
            if (mediaCodec == null) {
                Lg.e(TAG, "Can not create media encoder");
                return false;
            }
            mediaCodec.configure(
                    format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

      /*
      if (useSurface) {
        eglBase = new EglBase14(sharedContext, EglBase.CONFIG_RECORDABLE);
        // Create an input surface and keep a reference since we must release the surface when done.
        inputSurface = mediaCodec.createInputSurface();
        eglBase.createSurface(inputSurface);
        drawer = new GlRectDrawer();
      }
      */
            mediaCodec.start();
            outputBuffers = mediaCodec.getOutputBuffers();
            Lg.d(TAG, "Output buffers: " + outputBuffers.length);

        } catch (IllegalStateException e) {
            Lg.e(TAG, "initEncode failed" + e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    public ByteBuffer getInputBuffers(int inputIndex) {
        //return mediaCodec.getInputBuffers()[intputIndex];
        return mediaCodec.getInputBuffer(inputIndex);
    }

    public boolean encodeBuffer(
            boolean isKeyframe, int inputBuffer, int size,
            long presentationTimestampUs) {
        checkOnMediaCodecThread();
        try {
            if (isKeyframe) {
                // Ideally MediaCodec would honor BUFFER_FLAG_SYNC_FRAME so we could
                // indicate this in queueInputBuffer() below and guarantee _this_ frame
                // be encoded as a key frame, but sadly that flag is ignored.  Instead,
                // we request a key frame "soon".
                Lg.i(TAG, "Sync frame request");
                Bundle b = new Bundle();
                b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                mediaCodec.setParameters(b);
            }
            mediaCodec.queueInputBuffer(
                    inputBuffer, 0, size, presentationTimestampUs, 0);
            return true;
        } catch (IllegalStateException e) {
            Lg.e(TAG, "encodeBuffer failed" + e.getLocalizedMessage());
            return false;
        }
    }

    public void release() {
        Lg.d(TAG, "Java releaseEncoder");
        checkOnMediaCodecThread();

        // Run Mediacodec stop() and release() on separate thread since sometime
        // Mediacodec.stop() may hang.
        final CountDownLatch releaseDone = new CountDownLatch(1);

        Runnable runMediaCodecRelease = new Runnable() {
            @Override
            public void run() {
                try {
                    Lg.d(TAG, "Java releaseEncoder on release thread");
                    mediaCodec.stop();
                    mediaCodec.release();
                    Lg.d(TAG, "Java releaseEncoder on release thread done");
                } catch (Exception e) {
                    Lg.e(TAG, "Media encoder release failed" + e.getLocalizedMessage());
                }
                releaseDone.countDown();
            }
        };
        new Thread(runMediaCodecRelease).start();

        if (!ThreadUtils.awaitUninterruptibly(releaseDone, MEDIA_CODEC_RELEASE_TIMEOUT_MS)) {
            Lg.e(TAG, "Media encoder release timeout");
            codecErrors++;
            if (errorCallback != null) {
                Lg.e(TAG, "Invoke codec error callback. Errors: " + codecErrors);
                errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
            }
        }

        mediaCodec = null;
        mediaCodecThread = null;
    /*
    if (drawer != null) {
      drawer.release();
      drawer = null;
    }
    if (eglBase != null) {
      eglBase.release();
      eglBase = null;
    }
    if (inputSurface != null) {
      inputSurface.release();
      inputSurface = null;
    }
    */
        runningInstance = null;
        Lg.d(TAG, "Java releaseEncoder done");
    }

    private boolean setRates(int kbps, int frameRateIgnored) {
        // frameRate argument is ignored - HW encoder is supposed to use
        // video frame timestamps for bit allocation.
        checkOnMediaCodecThread();
        Lg.d(TAG, "setRates: " + kbps + " kbps. Fps: " + frameRateIgnored);
        try {
            Bundle params = new Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, 1000 * kbps);
            mediaCodec.setParameters(params);
            return true;
        } catch (IllegalStateException e) {
            Lg.e(TAG, "setRates failed" + e.getLocalizedMessage());
            return false;
        }
    }

    // Dequeue an input buffer and return its index, -1 if no input buffer is
    // available, or -2 if the codec is no longer operative.
    private long start1 = 0;
    public int dequeueInputBuffer() {
        start1 = System.currentTimeMillis();
        checkOnMediaCodecThread();
        try {
            return mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT);
        } catch (IllegalStateException e) {
            Lg.e(TAG, "dequeueIntputBuffer failed" + e.getLocalizedMessage());
            return -2;
        }
    }

    // Helper struct for dequeueOutputBuffer() below.
    public static class OutputBufferInfo {
        public OutputBufferInfo(
                int index, ByteBuffer buffer,
                boolean isKeyFrame, long presentationTimestampUs) {
            this.index = index;
            this.buffer = buffer;
            this.isKeyFrame = isKeyFrame;
            this.presentationTimestampUs = presentationTimestampUs;
        }

        public final int index;
        public final ByteBuffer buffer;
        public final boolean isKeyFrame;
        public final long presentationTimestampUs;

        @Override
        public String toString() {
            return "OutputBufferInfo{" +
                    "index=" + index +
                    ", isKeyFrame=" + isKeyFrame +
                    ", presentationTimestampUs=" + presentationTimestampUs +
                    '}';
        }
    }

    // Dequeue and return an output buffer, or null if no output is ready.  Return
    // a fake OutputBufferInfo with index -1 if the codec is no longer operable.
    public OutputBufferInfo dequeueOutputBuffer() {
        checkOnMediaCodecThread();
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int result = mediaCodec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT);
            // Check if this is config frame and save configuration data.
            if (result >= 0) {
                boolean isConfigFrame =
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (isConfigFrame) {
                    Lg.i(TAG, "Config frame generated. Offset: " + info.offset +
                            ". Size: " + info.size);
                    configData = ByteBuffer.allocateDirect(info.size);
                    outputBuffers[result].position(info.offset);
                    outputBuffers[result].limit(info.offset + info.size);
                    configData.put(outputBuffers[result]);
                    // Release buffer back.
                    mediaCodec.releaseOutputBuffer(result, false);
                    // Query next output.
                    result = mediaCodec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT);
                }
            }
            if (result >= 0) {
                // MediaCodec doesn't care about Buffer position/remaining/etc so we can
                // mess with them to get a slice and avoid having to pass extra
                // (BufferInfo-related) parameters back to C++.
                ByteBuffer outputBuffer = outputBuffers[result].duplicate();
                outputBuffer.position(info.offset);
                outputBuffer.limit(info.offset + info.size);
                // Check key frame flag.
                boolean isKeyFrame =
                        (info.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0;
                if (isKeyFrame) {
                    Lg.i(TAG, "Sync frame generated");
                }
                if (isKeyFrame && type == VideoCodecType.VIDEO_CODEC_H264) {
                    Lg.i(TAG, "Appending config frame of size " + configData.capacity() +
                            " to output buffer with offset " + info.offset + ", size " +
                            info.size);
                    // For H.264 key frame append SPS and PPS NALs at the start
                    ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(
                            configData.capacity() + info.size);
                    configData.rewind();
                    keyFrameBuffer.put(configData);
                    keyFrameBuffer.put(outputBuffer);
                    keyFrameBuffer.position(0);
                    long end1 = System.currentTimeMillis();
                    Lg.i(TAG, "1 output frame %d", (end1 - start1));
                    return new OutputBufferInfo(result, keyFrameBuffer,
                            isKeyFrame, info.presentationTimeUs);
                } else {
                    long end1 = System.currentTimeMillis();
                    Lg.i(TAG, "2 output frame %d", (end1 - start1));
                    return new OutputBufferInfo(result, outputBuffer.slice(),
                            isKeyFrame, info.presentationTimeUs);
                }
            } else if (result == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mediaCodec.getOutputBuffers();
                Lg.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                return dequeueOutputBuffer();
            } else if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Lg.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                return dequeueOutputBuffer();
            } else if (result == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return null;
            }
            throw new RuntimeException("dequeueOutputBuffer: " + result);
        } catch (IllegalStateException e) {
            Lg.e(TAG, "dequeueOutputBuffer failed" + e.getLocalizedMessage());
            return new OutputBufferInfo(-1, null, false, -1);
        }
    }

    // Release a dequeued output buffer back to the codec for re-use.  Return
    // false if the codec is no longer operable.
    public boolean releaseOutputBuffer(int index) {
        checkOnMediaCodecThread();
        try {
            mediaCodec.releaseOutputBuffer(index, false);
            return true;
        } catch (IllegalStateException e) {
            Lg.e(TAG, "releaseOutputBuffer failed" + e.getLocalizedMessage());
            return false;
        }
    }
}
