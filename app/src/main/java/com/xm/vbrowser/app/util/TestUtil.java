package com.xm.vbrowser.app.util;

import java.io.File;
import java.util.Arrays;

/**
 * Created by xm on 17-8-21.
 */
public class TestUtil {

    public static void main(String[] args) {
        String[] strings = new File("/mnt/data2/Sync/Projects/m3u8_cache/output").list();
        System.out.println(Arrays.toString(strings));
    }
}
