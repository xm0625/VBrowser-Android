package com.xm.vbrowser.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.activity.MainActivity;

public class DownloadForegroundService extends Service {
    private static final int ONGOING_NOTIFICATION_ID = 1;

    public DownloadForegroundService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel();
        }

        Notification notification = new NotificationCompat.Builder(MainApplication.mainApplication, channelId)
                .setContentTitle("前台任务")
                .setContentText("正在下载")
                .setSmallIcon(R.mipmap.download_default)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.download_default)).build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(){
        String channelId = "VBrowserNotification";
        String channelName = "前台下载通知";
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH);
        chan.setLightColor(Color.BLUE);
        chan.setImportance(NotificationManager.IMPORTANCE_NONE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
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
