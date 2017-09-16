package com.xm.vbrowser.app.entity;

/**
 * Created by xm on 2017/9/5.
 */
public class DetectedVideoInfo {
    private String url;
    private String sourcePageUrl;//原网页url
    private String sourcePageTitle;//原网页标题

    public DetectedVideoInfo(String url, String sourcePageUrl, String sourcePageTitle) {
        this.url = url;
        this.sourcePageUrl = sourcePageUrl;
        this.sourcePageTitle = sourcePageTitle;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourcePageUrl() {
        return sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getSourcePageTitle() {
        return sourcePageTitle;
    }

    public void setSourcePageTitle(String sourcePageTitle) {
        this.sourcePageTitle = sourcePageTitle;
    }
}
