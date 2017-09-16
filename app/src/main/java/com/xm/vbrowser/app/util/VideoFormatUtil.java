package com.xm.vbrowser.app.util;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.xm.vbrowser.app.entity.VideoFormat;
import com.xm.vbrowser.app.event.NewVideoItemDetectedEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by xm on 17-8-16.
 */
public class VideoFormatUtil {

    private static final List<String> videoExtensionList = Arrays.asList(
            "m3u8", "mp4", "flv", "mpeg"
    );

    private static final List<VideoFormat> videoFormatList = Arrays.asList(
            new VideoFormat("m3u8", Arrays.asList("application/octet-stream", "application/vnd.apple.mpegurl", "application/mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")),
            new VideoFormat("mp4", Arrays.asList("video/mp4","application/mp4","video/h264")),
            new VideoFormat("flv", Arrays.asList("video/x-flv")),
            new VideoFormat("f4v", Arrays.asList("video/x-f4v")),
            new VideoFormat("mpeg", Arrays.asList("video/vnd.mpegurl"))
            );


    public static VideoFormat detectVideoFormat(String mime){
        mime = mime.toLowerCase();
        for(VideoFormat videoFormat:videoFormatList){
            if(!TextUtils.isEmpty(mime)) {
                for (String mimePattern : videoFormat.getMimeList()) {
                    if (mime.contains(mimePattern)) {
                        return videoFormat;
                    }
                }
            }
        }
        return null;
    }

    public static boolean containsVideoExtension(String url){
        for(String videoExtension:videoExtensionList){
            if(!TextUtils.isEmpty(url)) {
                if (url.contains(videoExtension)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isLikeVideo(String fullUrl){
        try {
            URL urlObject = new URL(fullUrl);
            String extension = FileUtil.getExtension(urlObject.getPath());
            if(TextUtils.isEmpty(extension)){
                return true;
            }
            if(videoExtensionList.contains(extension.toLowerCase())){
                return true;
            }
            return false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean detectVideoUrl(String url){
        try {
            Map<String, List<String>> headerMap = HttpRequestUtil.performHeadRequest(url);
            if (headerMap == null || !headerMap.containsKey("Content-Type")) {
                //检测失败，未找到Content-Type
                Log.d("VideoFormatUtil", "fail 未找到Content-Type:" + JSON.toJSONString(headerMap) + " taskUrl=" + url);
                return false;
            }
            Log.d("VideoFormatUtil", "Content-Type:" + headerMap.get("Content-Type").toString() + " taskUrl=" + url);
            VideoFormat videoFormat = VideoFormatUtil.detectVideoFormat(headerMap.get("Content-Type").toString());
            if (videoFormat == null) {
                //检测成功，不是视频
                Log.d("VideoFormatUtil", "fail not video taskUrl=" + url);
                return false;
            }
            Log.d("VideoFormatUtil", "found video taskUrl=" + url);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("VideoFormatUtil", "fail IO错误 taskUrl=" + url);
            return false;
        }
    }
}
