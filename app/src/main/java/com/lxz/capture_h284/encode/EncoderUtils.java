package com.lxz.capture_h284.encode;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import com.iflytek.log.Lg;

import java.util.Arrays;
import java.util.List;

public class EncoderUtils {
    private static final String TAG = "EncoderUtils";
    private static final String H264_MIME_TYPE = "video/avc";
    // NV12 color format supported by QCOM codec, but not declared in MediaCodec -
    // see /hardware/qcom/media/mm-core/inc/OMX_QCOMExtns.h
    private static final int
            COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 0x7FA30C04;

    private static final String[] H264_HW_EXCEPTION_MODELS = new String[]{
            // HW H.264 encoder on below devices has poor bitrate control - actual
            // bitrates deviates a lot from the target value.
            "SAMSUNG-SGH-I337",
            "Nexus 7",
            "Nexus 4"
    };
    // List of supported HW H.264 codecs.
    private static final String[] supportedH264HwCodecPrefixes =
            {"OMX.qcom.", "OMX.allwinner.", "OMX.IMG.", "OMX.hisi"};
    // Allowable color formats supported by codec - in order of preference.
    private static final int[] supportedColorList = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
            COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m
    };

    public static void findEncoder() {
        EncoderProperties properties = findHwEncoder(H264_MIME_TYPE, supportedH264HwCodecPrefixes,
                supportedColorList);
        Lg.d(TAG, "find encoder " + properties);
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

            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mime);
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
}
