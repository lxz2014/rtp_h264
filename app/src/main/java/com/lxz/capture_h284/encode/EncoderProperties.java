package com.lxz.capture_h284.encode;

public class EncoderProperties {
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
