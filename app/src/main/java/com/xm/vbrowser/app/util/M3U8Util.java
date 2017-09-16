package com.xm.vbrowser.app.util;

import android.util.Log;
import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.net.URL;

/**
 * Created by xm on 17/8/17.
 */
public class M3U8Util {
    public static double figureM3U8Duration(String url) throws IOException {
        String m3U8Content = HttpRequestUtil.getResponseString(HttpRequestUtil.sendGetRequest(url));
        boolean isSubFileFound = false;
        double totalDuration = 0d;
        for(String lineString:m3U8Content.split("\n")){
            lineString = lineString.trim();
            if(isSubFileFound){
                if(lineString.startsWith("#")){
                    //格式错误 直接返回时长0
                    Log.d("M3U8Util", "格式错误1");
                    return 0d;
                }else{
                    String subFileUrl = new URL(new URL(url), lineString).toString();
                    return figureM3U8Duration(subFileUrl);
                }
            }
            if(lineString.startsWith("#")){
                if(lineString.startsWith("#EXT-X-STREAM-INF")){
                    isSubFileFound = true;
                    continue;
                }
                if(lineString.startsWith("#EXTINF:")){
                    int sepPosition = lineString.indexOf(",");
                    if(sepPosition<="#EXTINF:".length()){
                        sepPosition = lineString.length();
                    }
                    double duration = 0d;
                    try {
                        duration = Double.parseDouble(lineString.substring("#EXTINF:".length(), sepPosition).trim());
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                        //格式错误 直接返回时长0
                        Log.d("M3U8Util", "格式错误3");
                        return 0d;
                    }
                    totalDuration += duration;
                }
            }

        }
        return totalDuration;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("start");
        System.out.println(figureM3U8Duration("http://pl-ali.youku.com/playlist/m3u8?ids=%7B%22a1%22%3A%22746535159_mp4%22%2C%22v%22%3A%22XMzAwNzM0NzQ0OA%3D%3D_mp4%22%7D&&ups_client_netip=114.222.108.2&ups_ts=1504594399&utid=OyY1EjMURAwCAXLebAJBRcEs&ccode=0501&psid=18a1a49d4af3dc3b0d07580a085ac3fa&duration=70&expire=18000&ups_key=dd10f71f035b9b0b6471afbb3e8f2248"));
        System.out.println("stop");
    }
}
