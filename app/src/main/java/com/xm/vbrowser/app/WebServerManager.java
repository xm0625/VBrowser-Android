package com.xm.vbrowser.app;

import fi.iki.elonen.SimpleWebServer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xm on 17-8-21.
 */
public class WebServerManager {
    private SimpleWebServer simpleWebServer;

    private ReentrantLock reentrantLock = new ReentrantLock();

    WebServerManager(){
    }

    public void startServer(int port, String rootPath){
        reentrantLock.lock();
        try {
            stopServer();
            simpleWebServer = new SimpleWebServer("0.0.0.0", port, new File(rootPath), true);
            try {
                simpleWebServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }finally {
            reentrantLock.unlock();
        }
    }

    public void stopServer(){
        try{
            simpleWebServer.stop();
        }catch (Exception e){
        }
    }

    public SimpleWebServer getSimpleWebServer() {
        return simpleWebServer;
    }

    public void setSimpleWebServer(SimpleWebServer simpleWebServer) {
        this.simpleWebServer = simpleWebServer;
    }
}
