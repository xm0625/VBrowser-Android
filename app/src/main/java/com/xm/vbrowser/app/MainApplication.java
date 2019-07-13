package com.xm.vbrowser.app;

import android.app.Application;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.xm.vbrowser.app.entity.AppConfig;
import com.xm.vbrowser.app.event.ShowToastMessageEvent;
import com.xm.vbrowser.app.service.DownloadForegroundService;

import fi.iki.elonen.SimpleWebServer;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

/**
 * Created by xm on 17-8-16.
 */
public class MainApplication  extends Application {

    public static MainApplication mainApplication = null;
    public static AppConfig appConfig = null;
    public static DownloadManager downloadManager = null;
    public static WebServerManager webServerManager;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        EventBus.getDefault().register(this);
        onAppInit();
    }

    private void onAppInit(){
        mainApplication = this;
        appConfig = new AppConfig();
        downloadManager = new DownloadManager();
        webServerManager = new WebServerManager();
        webServerManager.startServer(appConfig.webServerPort, appConfig.rootDataPath);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onShowToastMessageEvent(ShowToastMessageEvent showToastMessageEvent){
        Toast.makeText(MainApplication.mainApplication, showToastMessageEvent.getMessage(), Toast.LENGTH_SHORT).show();
    }

    public void startDownloadForegroundService(){
        Intent intent = new Intent(MainApplication.mainApplication,DownloadForegroundService.class);
        startService(intent);
    }

    public void stopDownloadForegroundService(){
        Intent intent = new Intent(MainApplication.mainApplication,DownloadForegroundService.class);
        stopService(intent);
    }



}
