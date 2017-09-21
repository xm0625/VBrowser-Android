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

    public static VideoFormat detectVideoFormat(String url, String mime){
        try {
            String path = new URL(url).getPath();
            String extension = FileUtil.getExtension(path);
            if("mp4".equals(extension)){
                mime = "video/mp4";
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }

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
}
