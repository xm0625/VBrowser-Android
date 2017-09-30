package com.xm.vbrowser.app;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.xm.vbrowser.app.entity.DetectedVideoInfo;
import com.xm.vbrowser.app.entity.VideoFormat;
import com.xm.vbrowser.app.entity.VideoInfo;
import com.xm.vbrowser.app.event.NewVideoItemDetectedEvent;
import com.xm.vbrowser.app.util.HttpRequestUtil;
import com.xm.vbrowser.app.util.M3U8Util;
import com.xm.vbrowser.app.util.UUIDUtil;
import com.xm.vbrowser.app.util.VideoFormatUtil;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by xm on 17-8-17.
 */
public class VideoSniffer {
    private LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue;
    private SortedMap<String, VideoInfo> foundVideoInfoMap;
    private int threadPoolSize;
    private int retryCountOnFail;

    private List<Thread> threadList = new ArrayList<Thread>();


    public VideoSniffer(LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue, SortedMap<String, VideoInfo> foundVideoInfoMap, int threadPoolSize, int retryCountOnFail) {
        this.detectedTaskUrlQueue = detectedTaskUrlQueue;
        this.foundVideoInfoMap = foundVideoInfoMap;
        this.threadPoolSize = threadPoolSize;
        this.retryCountOnFail = retryCountOnFail;
    }

    public void startSniffer(){
        stopSniffer();
        threadList = new ArrayList<Thread>();
        for(int i=0;i< threadPoolSize;i++){
            WorkerThread workerThread = new WorkerThread(detectedTaskUrlQueue, foundVideoInfoMap, retryCountOnFail);
            threadList.add(workerThread);
        }
        for(Thread thread:threadList){
            try {
                thread.start();
            }catch (IllegalThreadStateException e){
                Log.d("VideoSniffer", "线程已启动, Pass");
            }
        }
    }


    public void stopSniffer(){
        for(Thread thread:threadList){
            try {
                thread.interrupt();
            }catch (Exception e){
                Log.d("VideoSniffer", "线程已中止, Pass");
            }
        }
    }

    private class WorkerThread extends Thread{
        private LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue;
        private SortedMap<String, VideoInfo> foundVideoInfoMap;
        private int retryCountOnFail;

        WorkerThread(LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue, SortedMap<String, VideoInfo> foundVideoInfoMap, int retryCountOnFail) {
            this.detectedTaskUrlQueue = detectedTaskUrlQueue;
            this.foundVideoInfoMap = foundVideoInfoMap;
            this.retryCountOnFail = retryCountOnFail;
        }

        @Override
        public void run() {
            super.run();
            Log.d("WorkerThread", "thread (" + Thread.currentThread().getId() + ") :start");
            while(!Thread.currentThread().isInterrupted()){
                try {
                    DetectedVideoInfo detectedVideoInfo = detectedTaskUrlQueue.take();
                    Log.d("WorkerThread", "start taskUrl=" + detectedVideoInfo.getUrl());
                    int failCount = 0;
                    while(!detectUrl(detectedVideoInfo)){
                        //如果检测失败
                        failCount++;
                        if(failCount>= retryCountOnFail){
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.d("WorkerThread", "thread (" + Thread.currentThread().getId() +") :Interrupted");
                    return;
                }
            }
            Log.d("WorkerThread", "thread (" + Thread.currentThread().getId() +") :exited");
        }

        private boolean detectUrl(DetectedVideoInfo detectedVideoInfo){
            String url = detectedVideoInfo.getUrl();
            String sourcePageUrl = detectedVideoInfo.getSourcePageUrl();
            String sourcePageTitle = detectedVideoInfo.getSourcePageTitle();
            try {
                HttpRequestUtil.HeadRequestResponse headRequestResponse = HttpRequestUtil.performHeadRequest(url);
                url = headRequestResponse.getRealUrl();
                detectedVideoInfo.setUrl(url);
                Map<String, List<String>> headerMap = headRequestResponse.getHeaderMap();
                if (headerMap == null || !headerMap.containsKey("Content-Type")) {
                    //检测失败，未找到Content-Type
                    Log.d("WorkerThread", "fail 未找到Content-Type:" + JSON.toJSONString(headerMap) + " taskUrl=" + url);
                    return false;
                }
                Log.d("WorkerThread", "Content-Type:" + headerMap.get("Content-Type").toString() + " taskUrl=" + url);
                VideoFormat videoFormat = VideoFormatUtil.detectVideoFormat(url, headerMap.get("Content-Type").toString());
                if (videoFormat == null) {
                    //检测成功，不是视频
                    Log.d("WorkerThread", "fail not video taskUrl=" + url);
                    return true;
                }
                VideoInfo videoInfo = new VideoInfo();
                if("m3u8".equals(videoFormat.getName())){
                    double duration = M3U8Util.figureM3U8Duration(url);
                    if(duration<=0){
                        //检测成功，不是m3u8的视频
                        Log.d("WorkerThread", "fail not m3u8 taskUrl=" + url);
                        return true;
                    }
                    videoInfo.setDuration(duration);
                }else{
                    long size = 0;
                    Log.d("WorkerThread", JSON.toJSONString(headerMap));
                    if (headerMap.containsKey("Content-Length") && headerMap.get("Content-Length").size()>0) {
                        try {
                            size = Long.parseLong(headerMap.get("Content-Length").get(0));
                        }catch (NumberFormatException e){
                            e.printStackTrace();
                            Log.d("WorkerThread", "NumberFormatException", e);
                        }
                    }
                    videoInfo.setSize(size);
                }
                videoInfo.setUrl(url);
                videoInfo.setFileName(UUIDUtil.genUUID());
                videoInfo.setVideoFormat(videoFormat);
                videoInfo.setSourcePageTitle(sourcePageTitle);
                videoInfo.setSourcePageUrl(sourcePageUrl);

                foundVideoInfoMap.put(url, videoInfo);
                EventBus.getDefault().post(new NewVideoItemDetectedEvent());
                //检测成功，是视频
                Log.d("WorkerThread", "found video taskUrl=" + url);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("WorkerThread", "fail IO错误 taskUrl=" + url);
                return false;
            }
        }


    }
}
