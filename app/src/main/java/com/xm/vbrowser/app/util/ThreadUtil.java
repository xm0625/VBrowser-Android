package com.xm.vbrowser.app.util;

import android.util.Log;

import java.util.List;

/**
 * Created by xm on 17-8-21.
 */
public class ThreadUtil {

    public static void  interruptThread(Thread thread){
        try{
            thread.interrupt();
        }catch (Exception e){
        }
    }


    public static void  interruptThreadList(List<Thread> threadList){
        for(Thread thread:threadList){
            interruptThread(thread);
        }
    }
}
