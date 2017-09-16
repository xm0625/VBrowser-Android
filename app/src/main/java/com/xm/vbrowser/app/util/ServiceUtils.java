package com.xm.vbrowser.app.util;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;

/**
 * Created by xm on 15/8/3.
 */
public class ServiceUtils {

    private static final String TAG = "ServiceUtils";

    public static boolean isServiceRunning(Context mContext,String className) {
        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList
                = activityManager.getRunningServices(30);
        if (!(serviceList.size()>0)) {
            return false;
        }
        for (int i=0; i<serviceList.size(); i++) {
            //LogUtils.d(TAG,serviceList.get(i).service.getClassName());
            if (serviceList.get(i).service.getClassName().equals(className)) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }
}
