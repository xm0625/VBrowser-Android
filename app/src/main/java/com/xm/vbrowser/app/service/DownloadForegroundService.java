package com.xm.vbrowser.app.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.activity.MainActivity;
import com.xm.vbrowser.app.event.StopDownloadFregroundServiceEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class DownloadForegroundService extends Service {
    private static final int ONGOING_NOTIFICATION_ID = 1;

    public DownloadForegroundService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = new Notification.Builder(MainApplication.mainApplication)
                .setContentTitle("前台任务")
                .setContentText("正在下载")
                .setSmallIcon(R.mipmap.download_default)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.download_default))
                .build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
