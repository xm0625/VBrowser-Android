package com.xm.vbrowser.app.activity;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;

import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.VideoSniffer;
import com.xm.vbrowser.app.entity.DetectedVideoInfo;
import com.xm.vbrowser.app.entity.DownloadTask;
import com.xm.vbrowser.app.entity.VideoFormat;
import com.xm.vbrowser.app.entity.VideoInfo;
import com.xm.vbrowser.app.event.AddNewDownloadTaskEvent;
import com.xm.vbrowser.app.event.NewVideoItemDetectedEvent;
import com.xm.vbrowser.app.event.RefreshGoBackButtonStateEvent;
import com.xm.vbrowser.app.event.ShowToastMessageEvent;
import com.xm.vbrowser.app.event.WebViewProgressUpdateEvent;
import com.xm.vbrowser.app.util.*;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import org.xwalk.core.*;
import q.rorbin.badgeview.Badge;
import q.rorbin.badgeview.QBadgeView;

public class MainActivity extends Activity {
    private static final String HOME_URL = "http://go.uc.cn/page/subpage/shipin?uc_param_str=dnfrpfbivecpbtntla";
    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1";

    private XWalkView mainWebView;
    private View itemBadgeView;
    private Badge badge;
    private View bottomGoBackButton;
    private View bottomNewItemButton;
    private View bottomDownloadButton;
    private View bottomRefreshButton;
    private View bottomHomeButton;
    private View newItemPage;
    private View newItemPageMainView;
    private ListView newItemListView;
    private View newItemBottomCancelButton;
    private View pageTitleButton;
    private TextView pageTitleView;
    private View searchInputPage;
    private View searchInputPageCancelButton;
    private TextView searchInput;
    private View webViewProgressVIew;


    private Thread refreshGoBackButtonStateThread;

    private LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue = new LinkedBlockingQueue<DetectedVideoInfo>();
    private SortedMap<String, VideoInfo> foundVideoInfoMap = Collections.synchronizedSortedMap(new TreeMap<String, VideoInfo>());
    private VideoSniffer videoSniffer;

    private String currentTitle = "";
    private String currentUrl = "";

    private boolean pageAnimationLock = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        mainInit();
    }

    private void initView() {
        mainWebView = (XWalkView)findViewById(R.id.mainWebView);
        itemBadgeView = findViewById(R.id.itemBadgeView);
        bottomGoBackButton = findViewById(R.id.bottomGoBackButton);
        bottomNewItemButton = findViewById(R.id.bottomNewItemButton);
        bottomDownloadButton = findViewById(R.id.bottomDownloadButton);
        bottomRefreshButton = findViewById(R.id.bottomRefreshButton);
        bottomHomeButton = findViewById(R.id.bottomHomeButton);
        newItemListView = (ListView)findViewById(R.id.newItemListView);
        newItemPage = findViewById(R.id.newItemPage);
        newItemPageMainView = findViewById(R.id.newItemPageMainView);
        newItemBottomCancelButton = findViewById(R.id.newItemBottomCancelButton);
        pageTitleButton =  findViewById(R.id.pageTitleButton);
        pageTitleView = (TextView) findViewById(R.id.pageTitleView);
        searchInputPage = findViewById(R.id.searchInputPage);
        searchInputPageCancelButton = findViewById(R.id.searchInputPageCancelButton);
        searchInput = (TextView) findViewById(R.id.searchInput);
        webViewProgressVIew = findViewById(R.id.webViewProgressVIew);
    }

    private void mainInit() {
        initWebView();

        badge = new QBadgeView(this).bindTarget(itemBadgeView).setBadgeGravity(Gravity.CENTER).setBadgeNumber(0);

        bottomGoBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mainWebView.getNavigationHistory().canGoBack()){
                    refreshGoBackButtonStatus();
                    return;
                }else{
                    mainWebView.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
                    refreshGoBackButtonStatus();
                }
            }
        });

        bottomRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mainWebView.reload(XWalkView.RELOAD_IGNORE_CACHE);
            }
        });

        bottomHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadOrSearch(HOME_URL);
                mainWebView.getNavigationHistory().clear();
            }
        });

        bottomNewItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(foundVideoInfoMap.isEmpty()){
                    return;
                }
                NewItemAdapter newItemAdapter = (NewItemAdapter)newItemListView.getAdapter();
                newItemAdapter.notifyDataSetChanged();
                hideNewItemPage(false);
            }
        });
        bottomNewItemButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                foundVideoInfoMap.clear();
                refreshNewItemButtonStatus();
                return true;
            }
        });

        videoSniffer=new VideoSniffer(detectedTaskUrlQueue, foundVideoInfoMap, MainApplication.appConfig.videoSnifferThreadNum, MainApplication.appConfig.videoSnifferRetryCountOnFail);

//        newItemListView.setAdapter();
        newItemListView.setAdapter(new NewItemAdapter(this, foundVideoInfoMap));

        newItemBottomCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideNewItemPage(true);
            }
        });

        pageTitleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideSearchPage(false);
            }
        });

        searchInputPageCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideSearchPage(true);
            }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_GO || (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    //do something;
                    Map<String, String> quickStart = new HashMap<String, String>();
                    quickStart.put("tbl", "https://tumblr.com/");
                    quickStart.put("avp", "http://www.avpapa.co/");
                    quickStart.put("ytb", "https://www.youtube.com/");
                    quickStart.put("5s", "http://dy.lol5s.com/");
                    quickStart.put("sm", "http://wap.smdyy.cc/");
                    if(quickStart.containsKey(textView.getText().toString())){
                        loadOrSearch(quickStart.get(textView.getText().toString()));
                    }else{
                        loadOrSearch(textView.getText().toString());
                    }
                    hideSearchPage(true);
                    return true;
                }
                return false;
            }
        });

        bottomDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DownloadCenterActivity.class);
                startActivity(intent);
            }
        });


    }

    private void initWebView() {
        //开启调式,支持谷歌浏览器调式
        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);

        XWalkSettings webSettings = mainWebView.getSettings();
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(IPHONE_UA);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        mainWebView.requestFocus();

        mainWebView.setResourceClient(new MainXWalkResourceClient(mainWebView));
        mainWebView.setUIClient(new MainXWalkUIClient(mainWebView));
        XWalkCookieManager xm = new XWalkCookieManager();
        xm.setAcceptCookie(true);

        loadOrSearch(HOME_URL);
    }

    private void loadOrSearch(String content){
        if(TextUtils.isEmpty(content)){
            return;
        }
        pageTitleView.setText(content);
        searchInput.setText(content);
        if(content.startsWith("http")){
            mainWebView.loadUrl(content);
            return;
        }
        pageTitleView.setText(content);
        String encodedContent = "";
        try {
            encodedContent = URLEncoder.encode(content, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        mainWebView.loadUrl("https://m.baidu.com/s?word="+encodedContent);
    }

    private void startRefreshGoBackButtonStateThread(){
        stopRefreshGoBackButtonStateThread();
        refreshGoBackButtonStateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :start");
                while(!Thread.currentThread().isInterrupted()){
                    try {
                        EventBus.getDefault().post(new RefreshGoBackButtonStateEvent());
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :Interrupted");
                        return;
                    }
                }
                Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :exit");
            }
        });
        refreshGoBackButtonStateThread.start();
    }

    private void stopRefreshGoBackButtonStateThread(){
        try {
            refreshGoBackButtonStateThread.interrupt();
        }catch (Exception e){
            Log.d("MainActivity", "RefreshGoBackButtonStateThread线程已中止, Pass");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        videoSniffer.startSniffer();
        if (mainWebView != null) {
            mainWebView.resumeTimers();
            mainWebView.onShow();
        }
        startRefreshGoBackButtonStateThread();
    }

    @Override
    protected void onStop() {
        stopRefreshGoBackButtonStateThread();
        if (mainWebView != null) {
            mainWebView.pauseTimers();
            mainWebView.onHide();
        }
        videoSniffer.stopSniffer();
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNewVideoItemDetectedEvent(NewVideoItemDetectedEvent newVideoItemDetectedEvent){
        refreshNewItemButtonStatus();

        NewItemAdapter newItemAdapter = (NewItemAdapter)newItemListView.getAdapter();
        newItemAdapter.notifyDataSetChanged();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshGoBackButtonStateEvent(RefreshGoBackButtonStateEvent refreshGoBackButtonStateEvent){
        refreshGoBackButtonStatus();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWebViewProgressUpdateEvent(WebViewProgressUpdateEvent webViewProgressUpdateEvent){
        int percent = webViewProgressUpdateEvent.getProgress();
        if(percent==100){
            webViewProgressVIew.setVisibility(View.INVISIBLE);
        }else{
            webViewProgressVIew.setVisibility(View.VISIBLE);
        }
        float weight = (100-percent)>0?((float)percent/(100-percent)):999999;
        webViewProgressVIew.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));

        if(HOME_URL.equals(mainWebView.getUrl())){
            pageTitleView.setText("搜索或输入网址");
            searchInput.setText("");
        }else{
            pageTitleView.setText(TextUtils.isEmpty(currentTitle) ? (TextUtils.isEmpty(currentUrl) ? "搜索或输入网址" : currentUrl) : currentTitle);
            searchInput.setText(currentUrl);
        }
    }
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onAddNewDownloadTaskEvent(AddNewDownloadTaskEvent addNewDownloadTaskEvent){
        VideoInfo videoInfo = addNewDownloadTaskEvent.getVideoInfo();
        DownloadTask downloadTask = new DownloadTask(
                UUIDUtil.genUUID(),videoInfo.getFileName(),
                ("m3u8".equals(videoInfo.getVideoFormat().getName())?"m3u8":"normal"),
                videoInfo.getVideoFormat().getName(),
                videoInfo.getUrl(),
                videoInfo.getSourcePageUrl(),
                videoInfo.getSourcePageTitle(),
                videoInfo.getSize());
        MainApplication.downloadManager.addTask(downloadTask);
    }


    private void refreshGoBackButtonStatus() {
        boolean canGoBack = mainWebView.getNavigationHistory().canGoBack();
        if(canGoBack){
            updateBottomButtonStatus(bottomGoBackButton, false);
        }else{
            updateBottomButtonStatus(bottomGoBackButton, true);
        }
    }

    private void refreshNewItemButtonStatus() {
        int newItemCount = foundVideoInfoMap.size();
        badge.setBadgeNumber(newItemCount);
        if(newItemCount>0) {
            updateBottomButtonStatus(bottomNewItemButton, false);
        }else{
            updateBottomButtonStatus(bottomNewItemButton, true);
        }
    }

    private void updateBottomButtonStatus(View buttonView, boolean isDisabled){
        if (!(buttonView instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) buttonView;
        if(viewGroup.getChildCount()<2){
            return;
        }
        if(isDisabled){
            viewGroup.getChildAt(0).setVisibility(View.INVISIBLE);
            viewGroup.getChildAt(1).setVisibility(View.VISIBLE);
        }else{
            viewGroup.getChildAt(0).setVisibility(View.VISIBLE);
            viewGroup.getChildAt(1).setVisibility(View.INVISIBLE);
        }
    }

    private void hideNewItemPage(boolean hide){
        if(pageAnimationLock){
            return;
        }
        pageAnimationLock = true;
        if(hide){
            if(newItemPage.getVisibility() != View.VISIBLE){
                pageAnimationLock = false;
                return;
            }
            YoYo.with(Techniques.SlideOutDown)
                    .duration(500).onEnd(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    newItemPage.setVisibility(View.GONE);
                    pageAnimationLock = false;
                }
            }).playOn(newItemPageMainView);
        }else{
            if(newItemPage.getVisibility() == View.VISIBLE){
                pageAnimationLock = false;
                return;
            }
            YoYo.with(Techniques.SlideInUp).onStart(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    newItemPage.setVisibility(View.VISIBLE);
                }
            }).onEnd(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    pageAnimationLock = false;
                }
            }).duration(500).playOn(newItemPageMainView);
        }
    }


    private void hideSearchPage(boolean hide){
        if(hide){
            if(searchInputPage.getVisibility() != View.VISIBLE){
                return;
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0); //强制隐藏键盘
            searchInputPage.setVisibility(View.GONE);
        }else{
            if(searchInputPage.getVisibility() == View.VISIBLE){
                return;
            }
            searchInputPage.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchInput,InputMethodManager.SHOW_FORCED);
        }
    }

    class MainXWalkResourceClient extends XWalkResourceClient{

        public MainXWalkResourceClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
            Log.d("MainActivity", "onLoadStarted url:" + url);

            WeakReference<LinkedBlockingQueue> detectedTaskUrlQueueWeakReference = new WeakReference<LinkedBlockingQueue>(detectedTaskUrlQueue);
            Log.d("MainActivity", "shouldInterceptLoadRequest hint url:" + url);
            LinkedBlockingQueue  detectedTaskUrlQueue = detectedTaskUrlQueueWeakReference.get();
            if(detectedTaskUrlQueue != null){
                detectedTaskUrlQueue.add(new DetectedVideoInfo(url,currentUrl,currentTitle));
                Log.d("MainActivity", "shouldInterceptLoadRequest detectTaskUrlList.add(url):" + url);
            }
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {
            super.onLoadFinished(view, url);
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
            Log.d("MainActivity", "onProgressChanged progressInPercent=" + progressInPercent);
            EventBus.getDefault().post(new WebViewProgressUpdateEvent(progressInPercent));
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            XWalkWebResourceResponse xWalkWebResourceResponse = super.shouldInterceptLoadRequest(view, request);
            String url = request.getUrl().toString();
            Log.d("MainActivity", "shouldInterceptLoadRequest url:" + url);
//
//            if(VideoFormatUtil.detectVideoUrl(url)){
//                Log.d("MainActivity", "shouldInterceptLoadRequest detectVideoUrl url:" + url);
//                try {
//                    xWalkWebResourceResponse = createXWalkWebResourceResponse("text/html","utf-8",new ByteArrayInputStream("".getBytes("UTF-8")),404,"blocked", new HashMap<String, String>());
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                    Log.d("MainActivity", "shouldInterceptLoadRequest UnsupportedEncodingException url:" + url);
//                }
//            }
            return xWalkWebResourceResponse;
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
            if (!(url.startsWith("http") || url.startsWith("https"))) {
                //非http https协议 不动作
                return true;
            }

            //http https协议 在本webView中加载

            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if(VideoFormatUtil.containsVideoExtension(extension)){
                detectedTaskUrlQueue.add(new DetectedVideoInfo(url,currentUrl,currentTitle));
                Log.d("MainActivity", "shouldOverrideUrlLoading detectTaskUrlList.add(url):" + url);
                return true;
            }

            Log.d("MainActivity", "shouldOverrideUrlLoading url="+url);
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

    class MainXWalkUIClient extends XWalkUIClient{

        public MainXWalkUIClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onReceivedTitle(XWalkView view, String title) {
            super.onReceivedTitle(view, title);
            Log.d("MainActivity", "onReceivedTitle title=" + title);
            currentTitle = title;
        }

        @Override
        public void onPageLoadStarted(XWalkView view, String url) {
            super.onPageLoadStarted(view, url);
            Log.d("MainActivity", "onPageLoadStarted url=" + url);
            currentUrl = url;
        }

        @Override
        public void onPageLoadStopped(XWalkView view, String url, LoadStatus status) {
            super.onPageLoadStopped(view, url, status);
            Log.d("MainActivity", "onPageLoadStopped url=" + url);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            super.onShowCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            super.onHideCustomView();
        }


    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch(keyCode){
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS://耳机三个按键是的上键，注意并不是耳机上的三个按键的物理位置的上下。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_PREVIOUS");
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE://耳机单按键的按键或三按键耳机的中间按键。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_PLAY_PAUSE");
            case KeyEvent.KEYCODE_HEADSETHOOK://耳机单按键的按键或三按键耳机的中间按键。与上面的按键可能是相同的，具体得看驱动定义。
                Log.d("MainActivity", "onKeyDown KEYCODE_HEADSETHOOK");
            case KeyEvent.KEYCODE_MEDIA_NEXT://耳机三个按键是的下键。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_NEXT");
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if(searchInputPage.getVisibility() == View.VISIBLE){
                hideSearchPage(true);
                return true;
            }
            if(newItemPage.getVisibility() == View.VISIBLE){
                hideNewItemPage(true);
                return true;
            }
            if(mainWebView.getNavigationHistory().canGoBack()){
                mainWebView.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
                refreshGoBackButtonStatus();
                return true;
            }
            // 创建退出对话框
            AlertDialog exitAlertDialog = new AlertDialog.Builder(this).create();
            // 设置对话框标题
            exitAlertDialog.setTitle("系统提示");
            // 设置对话框消息
            exitAlertDialog.setMessage("确定要退出吗?");
            // 添加选择按钮并注册监听
            exitAlertDialog.setButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MainApplication.mainApplication.stopDownloadForegroundService();
                    if (mainWebView != null) {
                        mainWebView.onDestroy();
                    }
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            });
            exitAlertDialog.setButton2("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            // 显示对话框
            exitAlertDialog.show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private static class ViewHolder{
        TextView itemNewItemTitle;
        TextView itemNewItemVideoType;
        TextView itemNewItemFileInfo;
        View itemNewItemDownloadImageView;
        View itemNewItemDoneImageView;
        View itemNewItemDownloadButton;
    }


    public class NewItemAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private SortedMap<String, VideoInfo> foundVideoInfoMap;
        private String[] foundVideoInfoMapKeyArray;

        public NewItemAdapter(Context context, SortedMap<String, VideoInfo> foundVideoInfoMap){
            this.mInflater = LayoutInflater.from(context);
            this.foundVideoInfoMap = foundVideoInfoMap;
            prepareData();
        }

        @Override
        public void notifyDataSetChanged() {
            prepareData();
            super.notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetInvalidated() {
            prepareData();
            super.notifyDataSetInvalidated();
        }

        private void prepareData(){
            Set<String> strings = this.foundVideoInfoMap.keySet();
            this.foundVideoInfoMapKeyArray = strings.toArray(new String[strings.size()]);
        }

        @Override
        public int getCount() {
            return foundVideoInfoMapKeyArray.length;
        }

        @Override
        public Object getItem(int arg0) {
            return foundVideoInfoMapKeyArray[arg0];
        }

        @Override
        public long getItemId(int arg0) {
            return foundVideoInfoMapKeyArray[arg0].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;
            if (convertView == null) {

                holder=new ViewHolder();

                convertView = mInflater.inflate(R.layout.item_new_item, null);
                holder.itemNewItemTitle = (TextView) convertView.findViewById(R.id.itemNewItemTitle);
                holder.itemNewItemFileInfo = (TextView) convertView.findViewById(R.id.itemNewItemFileInfo);
                holder.itemNewItemDownloadImageView = convertView.findViewById(R.id.itemNewItemDownloadImageView);
                holder.itemNewItemDoneImageView = convertView.findViewById(R.id.itemNewItemDoneImageView);
                holder.itemNewItemDownloadButton = convertView.findViewById(R.id.downloadingItemDeleteButton);
                holder.itemNewItemVideoType = (TextView)convertView.findViewById(R.id.itemNewItemVideoType);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder)convertView.getTag();
            }

            VideoInfo videoInfo = foundVideoInfoMap.get(foundVideoInfoMapKeyArray[position]);
            VideoFormat videoFormat = videoInfo.getVideoFormat();
            holder.itemNewItemTitle.setText(TextUtils.isEmpty(videoInfo.getSourcePageTitle())?videoInfo.getFileName()+"."+videoFormat.getName():videoInfo.getSourcePageTitle()+"."+videoFormat.getName());
            holder.itemNewItemVideoType.setText(videoFormat.getName().toUpperCase());
            if("m3u8".equals(videoFormat.getName())){
                holder.itemNewItemFileInfo.setText(TimeUtil.formatTime((int)videoInfo.getDuration()));
            }else{
                holder.itemNewItemFileInfo.setText(FileUtil.getFormatedFileSize(videoInfo.getSize()));
            }

            holder.itemNewItemDownloadButton.setTag(videoInfo);
            holder.itemNewItemDownloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    VideoInfo videoInfo = (VideoInfo) v.getTag();
                    EventBus.getDefault().post(new AddNewDownloadTaskEvent(videoInfo));
                    EventBus.getDefault().post(new ShowToastMessageEvent("下载任务添加成功"));
                }
            });

            return convertView;
        }

    }


}
