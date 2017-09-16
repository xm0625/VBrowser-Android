package com.xm.vbrowser.app.event;

import com.xm.vbrowser.app.entity.VideoInfo;

/**
 * Created by xm on 17-8-18.
 */
public class AddNewDownloadTaskEvent {
    private VideoInfo videoInfo;

    public AddNewDownloadTaskEvent(VideoInfo videoInfo) {
        this.videoInfo = videoInfo;
    }

    public VideoInfo getVideoInfo() {
        return videoInfo;
    }

    public void setVideoInfo(VideoInfo videoInfo) {
        this.videoInfo = videoInfo;
    }
}
