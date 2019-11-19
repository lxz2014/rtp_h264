package com.lxz.capture_h284.stream;

import com.iflytek.log.Lg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntPools {
    private static final String TAG = "intPools";
    private static final int SIZE = 5;
    private static Object sPoolSync = new Object();
    private static List<int[]> list = new ArrayList<>(SIZE);
    public static int count = 5;
    public static int[] obtain(int size) {
        synchronized (sPoolSync) {
            count--;
            for (int i = 0; i < list.size(); i++) {
                int[] d = list.get(i);
                if (d != null && d.length == size) {
                   // Lg.i(TAG, "return recy int ");
                    return list.remove(i);
                }
            }
        }
        Lg.i(TAG, "new int " + list.size());
        return new int[size];
    }

    public static void recycle(int[] d) {
        synchronized (sPoolSync) {
            count++;
            if (list.size() < SIZE) {
                Arrays.fill(d, -1);
                list.add(d);
                //Lg.i(TAG, "push recy int ");
            }
        }
    }
}
