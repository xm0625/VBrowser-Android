package com.xm.vbrowser.app;

import android.text.TextUtils;
import android.util.Log;

import com.xm.vbrowser.app.entity.DownloadTask;
import com.xm.vbrowser.app.event.ShowToastMessageEvent;
import com.xm.vbrowser.app.util.FileUtil;
import com.xm.vbrowser.app.util.HttpRequestUtil;
import com.xm.vbrowser.app.util.RandomUtil;
import com.xm.vbrowser.app.util.ThreadUtil;
import com.xm.vbrowser.app.util.UUIDUtil;

import org.greenrobot.eventbus.EventBus;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by xm on 17/8/19.
 */
public class DownloadManager {
    //最大同时进行任务数 maxConcurrentTask
    private SortedMap<String, DownloadTask> allDownloadTaskMap = Collections.synchronizedSortedMap(new TreeMap<String, DownloadTask>());//添加任务时, 先进这个map
    private LinkedBlockingQueue<DownloadTask> downloadTaskLinkedBlockingQueue = new LinkedBlockingQueue<DownloadTask>();

    private Hashtable<String, Thread> taskThreadMap = new Hashtable<String, Thread>();
    private ReentrantLock downloadWorkThreadCheckLock = new ReentrantLock();

    public void addTask(DownloadTask downloadTask){
        downloadWorkThreadCheckLock.lock();
        try{
            allDownloadTaskMap.put(downloadTask.getTaskId(), downloadTask);

            if(taskThreadMap.size()<MainApplication.appConfig.maxConcurrentTask){
                Thread taskThread = getDownloadTaskThread(downloadTask);
                taskThreadMap.put(downloadTask.getTaskId(), taskThread);
                taskThread.start();
            }else{
                downloadTaskLinkedBlockingQueue.add(downloadTask);
            }
            MainApplication.mainApplication.startDownloadForegroundService();
        }finally {
            downloadWorkThreadCheckLock.unlock();
        }
    }

    private Thread getDownloadTaskThread(DownloadTask downloadTask){
        Thread taskThread;
        if("m3u8".equals(downloadTask.getVideoType())){
            taskThread = new M3u8DownloadTaskThread(downloadTask);
        }else{
            taskThread = new NormalFileDownloadTaskThread(downloadTask);
        }
        return taskThread;
    }

    public void cancelAllTask(){
        downloadWorkThreadCheckLock.lock();
        try{
            MainApplication.mainApplication.stopDownloadForegroundService();
            downloadTaskLinkedBlockingQueue.clear();
            for(Thread taskTread:taskThreadMap.values()){
                try {
                    taskTread.interrupt();
                }catch (Exception e){
                    Log.d("DownloadManager", "线程已中止, Pass");
                }
            }
            taskThreadMap.clear();
            allDownloadTaskMap.clear();
        }finally {
            downloadWorkThreadCheckLock.unlock();
        }
    }


    public void cancelTask(String taskId){
        try {
            taskThreadMap.get(taskId).interrupt();
        }catch (Exception e){
            Log.d("DownloadManager", "线程已中止, Pass");
        }
        taskFinished(taskId);
    }

    public void taskFinished(String taskId){
        downloadWorkThreadCheckLock.lock();
        try{
            if(!taskThreadMap.containsKey(taskId)){
                return;
            }
            taskThreadMap.remove(taskId);
            allDownloadTaskMap.remove(taskId);

            if(!downloadTaskLinkedBlockingQueue.isEmpty()){
                DownloadTask downloadTask = downloadTaskLinkedBlockingQueue.remove();
                Thread taskThread = getDownloadTaskThread(downloadTask);
                taskThreadMap.put(downloadTask.getTaskId(), taskThread);
                taskThread.start();
            }
            EventBus.getDefault().post(new ShowToastMessageEvent("任务已结束"));

            if(taskThreadMap.size()==0){
                MainApplication.mainApplication.stopDownloadForegroundService();
            }
        }finally {
            downloadWorkThreadCheckLock.unlock();
        }
    }

    public void taskFailed(String taskId){
        downloadWorkThreadCheckLock.lock();
        try{
            if(!taskThreadMap.containsKey(taskId)){
                return;
            }
            taskThreadMap.remove(taskId);

            if(!downloadTaskLinkedBlockingQueue.isEmpty()){
                DownloadTask downloadTask = downloadTaskLinkedBlockingQueue.remove();
                Thread taskThread = getDownloadTaskThread(downloadTask);
                taskThreadMap.put(downloadTask.getTaskId(), taskThread);
                taskThread.start();
            }

            if(taskThreadMap.size()==0){
                MainApplication.mainApplication.stopDownloadForegroundService();
            }
        }finally {
            downloadWorkThreadCheckLock.unlock();
        }
    }


    class M3u8DownloadTaskThread extends Thread{
        private DownloadTask downloadTask;
        private LinkedBlockingQueue<Map<String, String>> sizeDetectQueue = new LinkedBlockingQueue<Map<String, String>>();
        private LinkedBlockingQueue<Map<String, String>> downloadQueue = new LinkedBlockingQueue<Map<String, String>>();
        private List<Thread> workerThread = new ArrayList<Thread>(MainApplication.appConfig.m3U8DownloadThreadNum);
        private boolean isWorkerThreadFailed = false;
        private Thread speedCheckerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Thread.sleep(1000);
                        long lastClearSpeedTime = downloadTask.getLastClearSpeedTime();
                        downloadTask.setLastClearSpeedTime(System.currentTimeMillis());
                        long lastDurationDownloadSize = downloadTask.getLastDurationDownloadSize().getAndSet(0);
                        long timePass = System.currentTimeMillis() - lastClearSpeedTime;
                        if(timePass<=0){
                            continue;
                        }
                        downloadTask.setCurrentSpeed(lastDurationDownloadSize*1000/timePass);
                    } catch (InterruptedException e) {
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") :Interrupted");
                        break;
                    }
                }
            }
        });

        M3u8DownloadTaskThread(DownloadTask downloadTask) {
            this.downloadTask = downloadTask;
        }

        @Override
        public void run() {
            super.run();

            downloadTask.setStatus("loading");
            String downloadTempDir = MainApplication.appConfig.rootDataPath +File.separator+downloadTask.getFileName()+".temp";
            File downloadTempDirFile = new File(downloadTempDir);


            String downloadDir =MainApplication.appConfig.rootDataPath +File.separator+downloadTask.getFileName();

            if (downloadTempDirFile.exists()) {
                if (downloadTempDirFile.isDirectory()) {
                    //目录已存在 删除
                    FileUtil.deleteDirs(downloadTempDir);
                } else {
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("目录创建失败, 存在同名文件");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
            }
            boolean makeDirsSuccess = downloadTempDirFile.mkdirs();
            if(!makeDirsSuccess){
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("目录创建失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }

            String videoTitleFilePath = downloadTempDir+File.separator+"videoTitle";
            String videoName = TextUtils.isEmpty(downloadTask.getSourcePageTitle())?downloadTask.getFileName():downloadTask.getSourcePageTitle();
            try {
                FileUtil.stringToFile(videoName, videoTitleFilePath);
            } catch (IOException e) {
                e.printStackTrace();
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("视频名称保存失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }

            try {
                parseM3u8(downloadTask.getUrl(),"index.m3u8", downloadTask.getFileName() ,downloadTempDir, sizeDetectQueue, downloadQueue);
            } catch (IOException e) {
                e.printStackTrace();
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("解析M3U8文件失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }

            workerThread.clear();
            for(int i=0;i<MainApplication.appConfig.m3U8DownloadThreadNum;i++){
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") :start");
                        while(!Thread.currentThread().isInterrupted()){
                            Map<String, String> taskMap;
                            try {
                                taskMap = sizeDetectQueue.remove();
                            }catch (NoSuchElementException e){
                                Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :exited");
                                break;
                            }
                            String taskUrl = taskMap.get("url");
                            if(Thread.currentThread().isInterrupted()){
                                Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :return early");
                                return;
                            }
                            Log.d("DownloadManager", "start detect size taskUrl=" + taskUrl);
                            int failCount = 0;
                            while(!detectSize(taskUrl)){
                                //如果检测失败
                                if(Thread.currentThread().isInterrupted()){
                                    Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :return early");
                                    return;
                                }
                                failCount++;
                                if(failCount>= MainApplication.appConfig.m3U8DownloadSizeDetectRetryCountOnFail){
                                    isWorkerThreadFailed = true;
                                    return;
                                }
                            }
                        }
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :interrupted");
                    }

                    private boolean detectSize(String url){
                        try {
                            HttpRequestUtil.HeadRequestResponse headRequestResponse = HttpRequestUtil.performHeadRequest(url);
                            Map<String, List<String>> headerMap = headRequestResponse.getHeaderMap();
                            if (headerMap == null || !headerMap.containsKey("Content-Length") || headerMap.get("Content-Length").size()==0) {
                                //检测失败，未找到Content-Type
                                Log.d("DownloadManager", "fail 未找到Content-Length taskUrl=" + url);
                                return false;
                            }
                            long size = 0;
                            try {
                                size = Long.parseLong(headerMap.get("Content-Length").get(0));
                            }catch (NumberFormatException e){
                                e.printStackTrace();
                                Log.d("DownloadManager", "NumberFormatException", e);
                            }
                            downloadTask.getSize().addAndGet(size);
                            return true;
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d("DownloadManager", "fail IO错误 taskUrl=" + url);
                            return false;
                        }
                    }
                });
                workerThread.add(thread);
                thread.start();
            }
            try {
                for(Thread thread:workerThread){
                    thread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                ThreadUtil.interruptThreadList(workerThread);
                return;
            }
            if(isWorkerThreadFailed){
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("获取文件大小失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }

            workerThread.clear();
            downloadTask.setStatus("running");
            speedCheckerThread.start();
            for(int i=0;i<MainApplication.appConfig.m3U8DownloadThreadNum;i++){
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") :start");
                        while(!Thread.currentThread().isInterrupted()){
                            Map<String, String> taskMap;
                            try {
                                taskMap = downloadQueue.remove();
                            }catch (NoSuchElementException e){
                                Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :exited");
                                break;
                            }
                            String taskUrl = taskMap.get("url");
                            String downloadPath = taskMap.get("downloadPath");
                            if(Thread.currentThread().isInterrupted()){
                                Log.d("DownloadManager", "download thread (" + downloadTask.getTaskId() +") :return early");
                                return;
                            }
                            Log.d("DownloadManager", "start download taskUrl=" + taskUrl);
                            int failCount = 0;
                            while(!Thread.currentThread().isInterrupted() && !downloadFile(taskUrl, downloadPath)){
                                //如果检测失败
                                failCount++;
                                if(failCount>= MainApplication.appConfig.downloadSubFileRetryCountOnFail){
                                    isWorkerThreadFailed = true;
                                    return;
                                }
                            }
                        }
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :interrupted");
                    }
                    private boolean downloadFile(String url, String downloadPath){
                        try {
                            save2File(HttpRequestUtil.sendGetRequest(url), downloadPath);
                            return true;
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d("DownloadManager", "fail IO错误 taskUrl=" + url);
                            return false;
                        }
                    }

                    private void save2File(URLConnection urlConnection,String saveFilePath) throws IOException {
                        DataInputStream dis = null;
                        FileOutputStream fos = null;

                        try {
                            dis = new DataInputStream(urlConnection.getInputStream());
                            //建立一个新的文件
                            fos = new FileOutputStream(new File(saveFilePath));
                            byte[] buffer = new byte[1024];
                            int length;
                            //开始填充数据
                            while ((length = dis.read(buffer)) > 0) {
                                if (Thread.currentThread().isInterrupted()) {
                                    Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") save2File :return early");
                                    return;
                                }
                                downloadTask.getLastDurationDownloadSize().addAndGet(length);
                                downloadTask.getTotalDownloaded().addAndGet(length);
                                fos.write(buffer, 0, length);
                            }
                        }finally {
                            if(dis != null){
                                dis.close();
                            }
                            if(fos != null){
                                fos.close();
                            }
                        }
                    }
                });

                workerThread.add(thread);
                thread.start();
            }
            try {
                for(Thread thread:workerThread){
                    thread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                ThreadUtil.interruptThreadList(workerThread);
                return;
            }
            speedCheckerThread.interrupt();
            if(isWorkerThreadFailed){
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("下载失败:1");
                taskFailed(downloadTask.getTaskId());
                return;
            }
            boolean moveDir = FileUtil.renameDir(downloadTempDir, downloadDir);
            if(!moveDir) {
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("下载失败:2");
                taskFailed(downloadTask.getTaskId());
                return;
            }
            taskFinished(downloadTask.getTaskId());
        }


        private void parseM3u8(String m3u8Url, String newM3u8FileName, String relativePath,String outputPath, LinkedBlockingQueue<Map<String, String>> sizeDetectQueue, LinkedBlockingQueue<Map<String, String>> downloadQueue) throws IOException {
            String m3U8Content = HttpRequestUtil.getResponseString(HttpRequestUtil.sendGetRequest(m3u8Url));
            String newM3u8FileContent = "";
            boolean subFile = false;
            for(String lineStr:m3U8Content.split("\n")){
                if(lineStr.startsWith("#")){
                    if(lineStr.startsWith("#EXT-X-KEY:")){
                        Matcher searchKeyUri = Pattern.compile("URI=\"(.*?)\"").matcher(lineStr);
                        if(!searchKeyUri.find()){
                            throw new IOException("EXT-X-KEY解析失败");
                        }
                        String keyUri = searchKeyUri.group(1);
                        String keyUrl;
                        if(keyUri.startsWith("http://") || keyUri.startsWith("https://")){
                            keyUrl = keyUri;
                        }else{
                            keyUrl = new URL(new URL(m3u8Url), keyUri.trim()).toString();
                        }
                        String uuidStr = UUIDUtil.genUUID();
                        String keyPath = outputPath + File.separator + uuidStr + ".key";
                        HashMap<String, String> hashMap = new HashMap<String, String>();
                        hashMap.put("url", keyUrl);
                        hashMap.put("downloadPath", keyPath);
                        downloadQueue.add(hashMap);
                        String newLineStr = Pattern.compile("URI=\"(.*?)\"").matcher(lineStr).replaceAll("URI=\""+ "/" + relativePath + "/" + uuidStr + ".key\"");
                        lineStr = newLineStr;
                    }
                    if(lineStr.startsWith("#EXT-X-STREAM-INF")){
                        subFile = true;
                    }
                    newM3u8FileContent = newM3u8FileContent + lineStr + "\n";
                }else{
                    String uuidStr = UUIDUtil.genUUID();
                    String videoUri = lineStr.trim();
                    String fileUrl;
                    if(videoUri.startsWith("http://") || videoUri.startsWith("https://")){
                        fileUrl = videoUri;
                    }else{
                        fileUrl = new URL(new URL(m3u8Url), videoUri).toString();
                    }
                    if(subFile){
                        subFile = false;
                        parseM3u8(fileUrl, uuidStr+".m3u8", relativePath, outputPath,sizeDetectQueue, downloadQueue);
                        newM3u8FileContent = newM3u8FileContent + "/" + relativePath + "/" + uuidStr + ".m3u8\n";
                    }else{
                        String videoFilePath = outputPath + File.separator + uuidStr + ".ts";
                        HashMap<String, String> hashMap = new HashMap<String, String>();
                        hashMap.put("url", fileUrl);
                        hashMap.put("downloadPath", videoFilePath);
                        sizeDetectQueue.add(hashMap);
                        downloadQueue.add(hashMap);
                        newM3u8FileContent = newM3u8FileContent + "/" + relativePath + "/" + uuidStr + ".ts\n";
                    }
                }
            }
            FileUtil.stringToFile(newM3u8FileContent, outputPath+File.separator+newM3u8FileName);
        }
    }


    class NormalFileDownloadTaskThread extends Thread{
        private DownloadTask downloadTask;
        private int splitNum = 0;
        private LinkedBlockingQueue<Map<String, String>> downloadQueue = new LinkedBlockingQueue<Map<String, String>>();
        private List<Thread> workerThread = new ArrayList<Thread>(MainApplication.appConfig.normalFileDownloadThreadNum);
        private boolean isWorkerThreadFailed = false;
        private Thread speedCheckerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Thread.sleep(1000);
                        long lastClearSpeedTime = downloadTask.getLastClearSpeedTime();
                        downloadTask.setLastClearSpeedTime(System.currentTimeMillis());
                        long lastDurationDownloadSize = downloadTask.getLastDurationDownloadSize().getAndSet(0);
                        long timePass = System.currentTimeMillis() - lastClearSpeedTime;
                        if(timePass<=0){
                            continue;
                        }
                        downloadTask.setCurrentSpeed(lastDurationDownloadSize*1000/timePass);
                    } catch (InterruptedException e) {
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") :Interrupted");
                        break;
                    }
                }
            }
        });

        NormalFileDownloadTaskThread(DownloadTask downloadTask) {
            this.downloadTask = downloadTask;
        }

        @Override
        public void run() {
            super.run();


            downloadTask.setStatus("loading");

            String downloadFilePath = MainApplication.appConfig.rootDataPath +File.separator+downloadTask.getFileName()+"."+downloadTask.getFileExtension();

            String downloadTempDir = downloadFilePath+".temp";
            File downloadTempDirFile = new File(downloadTempDir);

            String downloadTempFilePath = downloadFilePath +".download";
            File downloadTempFile = new File(downloadTempFilePath);

            String finalDir = MainApplication.appConfig.rootDataPath +File.separator+downloadTask.getFileName();
            File finalDirFile = new File(finalDir);


            if (downloadTempDirFile.exists()) {
                if (downloadTempDirFile.isDirectory()) {
                    //目录已存在 删除
                    FileUtil.deleteDirs(downloadTempDir);
                } else {
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("目录创建失败, 存在同名文件");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
            }
            boolean makeDirsSuccess = downloadTempDirFile.mkdirs();
//            if(!makeDirsSuccess){
//                downloadTask.setStatus("error");
//                downloadTask.setFailedReason("目录创建失败");
//                taskFailed(downloadTask.getTaskId());
//                return;
//            }


            if (finalDirFile.exists()) {
                if (finalDirFile.isDirectory()) {
                    //目录已存在 删除
                    FileUtil.deleteDirs(finalDir);
                } else {
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("目录创建失败, 存在同名文件finalDir");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
            }
            finalDirFile.mkdirs();


            if(downloadTempFile.exists()) {
                if (downloadTempFile.isDirectory()) {
                    //目录已存在 删除
                    FileUtil.deleteDirs(downloadTempFilePath);
                } else {
                    downloadTempFile.delete();
                }
            }
            try {
                downloadTempFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("文件创建失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }

            boolean isFileSupportRange = false;
            int failCount = 0;
            while(!Thread.currentThread().isInterrupted()){
                try {
                    isFileSupportRange = detectFileSupportRange(downloadTask.getUrl());
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    failCount++;
                    if(failCount>= MainApplication.appConfig.downloadSubFileRetryCountOnFail){
                        downloadTask.setStatus("error");
                        downloadTask.setFailedReason("文件信息获取失败");
                        taskFailed(downloadTask.getTaskId());
                        return;
                    }
                }
            }

            downloadTask.setStatus("running");
            speedCheckerThread.start();
            if(!isFileSupportRange){
                try {
                    save2File(HttpRequestUtil.sendGetRequest(downloadTask.getUrl()), downloadTempFilePath);
                } catch (IOException e) {
                    e.printStackTrace();
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("文件下载失败");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
                speedCheckerThread.interrupt();
            }else{
                long totalSize = downloadTask.getSize().get();
                long normalFileSplitSize = MainApplication.appConfig.normalFileSplitSize;
                int n = 0;
                while(n*normalFileSplitSize<totalSize){
                    if(Thread.currentThread().isInterrupted()){
                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") split file :return early");
                        return;
                    }
                    HashMap<String, String> hashMap = new HashMap<String, String>();
                    hashMap.put("url", downloadTask.getUrl());
                    hashMap.put("rangeHeader", "bytes="+String.valueOf(n*normalFileSplitSize)+"-"+String.valueOf((n+1)*normalFileSplitSize-1));
                    hashMap.put("downloadPath", downloadTempDir+File.separator+downloadTask.getFileName()+"."+String.valueOf(n));
                    downloadQueue.add(hashMap);
                    n++;
                }


                for(int i=0;i<MainApplication.appConfig.normalFileDownloadThreadNum;i++){
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") :start");
                            while(!Thread.currentThread().isInterrupted()){
                                Map<String, String> taskMap;
                                try {
                                    taskMap = downloadQueue.remove();
                                }catch (NoSuchElementException e){
                                    Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :exited");
                                    break;
                                }
                                String taskUrl = taskMap.get("url");
                                String rangeHeader = taskMap.get("rangeHeader");
                                String downloadPath = taskMap.get("downloadPath");
                                if(Thread.currentThread().isInterrupted()){
                                    Log.d("DownloadManager", "download thread (" + downloadTask.getTaskId() +") :return early");
                                    return;
                                }
                                Log.d("DownloadManager", "start download taskUrl=" + taskUrl);
                                int failCount = 0;
                                while(!Thread.currentThread().isInterrupted() && !downloadFile(taskUrl,rangeHeader, downloadPath)){
                                    //如果检测失败
                                    failCount++;
                                    if(failCount>= MainApplication.appConfig.downloadSubFileRetryCountOnFail){
                                        isWorkerThreadFailed = true;
                                        return;
                                    }
                                }
                            }
                            Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() +") :interrupted");
                        }
                        private boolean downloadFile(String url, String rangeHeader, String downloadPath){
                            try {
                                HashMap<String, String> hashMap = new HashMap<String, String>();
                                hashMap.put("Range", rangeHeader);
                                save2File(HttpRequestUtil.sendGetRequest(url,null,hashMap), downloadPath);
                                return true;
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d("DownloadManager", "fail IO错误 taskUrl=" + url);
                                return false;
                            }
                        }

                        private void save2File(URLConnection urlConnection,String saveFilePath) throws IOException {
                            DataInputStream dis = null;
                            FileOutputStream fos = null;

                            try {
                                dis = new DataInputStream(urlConnection.getInputStream());
                                //建立一个新的文件
                                fos = new FileOutputStream(new File(saveFilePath));
                                byte[] buffer = new byte[1024];
                                int length;
                                //开始填充数据
                                while ((length = dis.read(buffer)) > 0) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") save2File :return early");
                                        return;
                                    }
                                    downloadTask.getLastDurationDownloadSize().addAndGet(length);
                                    downloadTask.getTotalDownloaded().addAndGet(length);
                                    fos.write(buffer, 0, length);
                                }
                            }finally {
                                if(dis != null) {
                                    dis.close();
                                }
                                if(fos != null) {
                                    fos.close();
                                }
                                ((HttpURLConnection) urlConnection).disconnect();
                            }
                        }
                    });

                    workerThread.add(thread);
                    thread.start();
                }
                try {
                    for(Thread thread:workerThread){
                        thread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    ThreadUtil.interruptThreadList(workerThread);
                    return;
                }
                speedCheckerThread.interrupt();
                if(isWorkerThreadFailed){
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("下载失败:1");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
                downloadTask.setStatus("saving");
                try {
                    FileOutputStream outputFileStream = null;
                    FileInputStream fromFileStream = null;
                    FileChannel fcout = null;
                    FileChannel fcin = null;
                    try {
                        File outputFile = downloadTempFile;
                        File fromFile;
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        outputFileStream = new FileOutputStream(outputFile);
                        fcout = outputFileStream.getChannel();
                        for (int i = 0; i < n; i++) {
                            fromFile = new File(downloadTempDir + File.separator + downloadTask.getFileName() + "." + String.valueOf(i));
                            fromFileStream = new FileInputStream(fromFile);
                            fcin = fromFileStream.getChannel();
                            try {
                                while (true) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        Log.d("DownloadManager", "thread (" + downloadTask.getTaskId() + ") save2File :return early");
                                        return;
                                    }
                                    // clear方法重设缓冲区，使它可以接受读入的数据
                                    buffer.clear();
                                    // 从输入通道中将数据读到缓冲区
                                    int r = -1;
                                    r = fcin.read(buffer);
                                    // read方法返回读取的字节数，可能为零，如果该通道已到达流的末尾，则返回-1
                                    if (r == -1) {
                                        break;
                                    }
                                    // flip方法让缓冲区可以将新读入的数据写入另一个通道
                                    buffer.flip();
                                    // 从输出通道中将数据写入缓冲区
                                    fcout.write(buffer);
                                }
                            } finally {
                                if(fcin != null) {
                                    fcin.close();
                                }
                                fromFileStream.close();
                            }
                        }
                    } finally {
                        if(fcout != null){
                            fcout.close();
                        }
                        if(outputFileStream != null){
                            outputFileStream.close();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    downloadTask.setStatus("error");
                    downloadTask.setFailedReason("下载失败:2");
                    taskFailed(downloadTask.getTaskId());
                    return;
                }
                FileUtil.deleteDirs(downloadTempDir);
            }

            downloadTempFile.renameTo(new File(finalDir+File.separator+"video."+downloadTask.getFileExtension()));

            String videoTitleFilePath = finalDir+File.separator+"videoTitle";
            String videoName = TextUtils.isEmpty(downloadTask.getSourcePageTitle())?downloadTask.getFileName():downloadTask.getSourcePageTitle();
            try {
                FileUtil.stringToFile(videoName, videoTitleFilePath);
            } catch (IOException e) {
                e.printStackTrace();
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("视频名称保存失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }
            String normalVideoTypeFilePath = finalDir+File.separator+"normalVideoType";
            try {
                FileUtil.stringToFile(downloadTask.getFileExtension(), normalVideoTypeFilePath);
            } catch (IOException e) {
                e.printStackTrace();
                downloadTask.setStatus("error");
                downloadTask.setFailedReason("视频类型信息保存失败");
                taskFailed(downloadTask.getTaskId());
                return;
            }


            taskFinished(downloadTask.getTaskId());
        }


        private void save2File(URLConnection urlConnection,String saveFilePath) throws IOException {

            DataInputStream dis = null;
            FileOutputStream fos = null;

            try {
                dis = new DataInputStream(urlConnection.getInputStream());
                //建立一个新的文件
                fos = new FileOutputStream(new File(saveFilePath));
                byte[] buffer = new byte[1024];
                int length;
                //开始填充数据
                while (!Thread.currentThread().isInterrupted() && ((length = dis.read(buffer)) > 0)) {
                    downloadTask.getLastDurationDownloadSize().addAndGet(length);
                    downloadTask.getTotalDownloaded().addAndGet(length);
                    fos.write(buffer, 0, length);
                }
            }finally {
                if(dis != null){
                    dis.close();
                }
                if(fos != null){
                    fos.close();
                }
            }
        }

        private boolean detectFileSupportRange(String url) throws IOException {
            HttpRequestUtil.HeadRequestResponse headRequestResponse = HttpRequestUtil.performHeadRequest(url);
            Map<String, List<String>> headerMap = headRequestResponse.getHeaderMap();
            if (headerMap == null) {
                //检测失败，未找到Content-Type
                Log.d("DownloadManager", "fail 未找到Content-Length taskUrl=" + url);
                throw new IOException("headerMap is null");
            }
            return headerMap.containsKey("Accept-Ranges") && headerMap.get("Accept-Ranges").size() > 0 && "bytes".equals(headerMap.get("Accept-Ranges").get(0).trim());
        }
    }


    public SortedMap<String, DownloadTask> getAllDownloadTaskMap() {
        return allDownloadTaskMap;
    }

    public void setAllDownloadTaskMap(SortedMap<String, DownloadTask> allDownloadTaskMap) {
        this.allDownloadTaskMap = allDownloadTaskMap;
    }

    public LinkedBlockingQueue<DownloadTask> getDownloadTaskLinkedBlockingQueue() {
        return downloadTaskLinkedBlockingQueue;
    }

    public void setDownloadTaskLinkedBlockingQueue(LinkedBlockingQueue<DownloadTask> downloadTaskLinkedBlockingQueue) {
        this.downloadTaskLinkedBlockingQueue = downloadTaskLinkedBlockingQueue;
    }

    public Hashtable<String, Thread> getTaskThreadMap() {
        return taskThreadMap;
    }

    public void setTaskThreadMap(Hashtable<String, Thread> taskThreadMap) {
        this.taskThreadMap = taskThreadMap;
    }
}
