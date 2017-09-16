package com.xm.vbrowser.app.event;

/**
 * Created by xm on 17/8/21.
 */
public class WebViewProgressUpdateEvent {
    private int progress;

    public WebViewProgressUpdateEvent(int progress) {
        this.progress = progress;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }
}
