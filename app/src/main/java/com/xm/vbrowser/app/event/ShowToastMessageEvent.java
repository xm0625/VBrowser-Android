package com.xm.vbrowser.app.event;

/**
 * Created by xm on 17/8/21.
 */
public class ShowToastMessageEvent {
    private String message;

    public ShowToastMessageEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
