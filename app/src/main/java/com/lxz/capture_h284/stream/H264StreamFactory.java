package com.lxz.capture_h284.stream;

public class H264StreamFactory {
    public enum Model{
        rtp, file
    }

    public static Model model = Model.file;
    public static IH264Stream createH264Stream() {
        if (model.equals(Model.file)) {
            return new FileH264Stream();
        }
        else {
            return new RtpH264Stream();
        }
    }
}
