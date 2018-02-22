package com.xm.vbrowser.app.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.entity.LocalVideoInfo;
import com.xm.vbrowser.app.event.RefreshLocalVideoListEvent;
import com.xm.vbrowser.app.util.FileUtil;
import com.xm.vbrowser.app.util.IntentUtil;
import com.xm.vbrowser.app.util.RandomUtil;
import com.xm.vbrowser.app.util.VideoFormatUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileListActivity extends Activity {
    private ListView listView;
    private View goBackButton;

    private List<LocalVideoInfo> localVideoInfoArrayList = Collections.synchronizedList(new ArrayList<LocalVideoInfo>());

    private Thread refreshListDataThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        initView();
        mainInit();
    }

    private void initView() {
        listView = (ListView)findViewById(R.id.listView);
        goBackButton = findViewById(R.id.goBackButton);
    }

    private void mainInit() {
        goBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        listView.setAdapter(new LocalVideoListAdapter(this));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ViewHolder viewHolder = (ViewHolder) view.getTag();
                LocalVideoInfo localVideoInfo = viewHolder.localVideoInfo;
                String localPath = null;
                String fileName = null;
                if ("m3u8".equals(localVideoInfo.getVideoType())) {
                    localPath = localVideoInfo.getLocalPath();
                    fileName = "index.m3u8";
                } else {
                    String fullPath = localVideoInfo.getLocalPath();
                    if (!TextUtils.isEmpty(fullPath)){
                        int splitIndex = fullPath.lastIndexOf(File.separator);
                        if (splitIndex>-1){
                            localPath = fullPath.substring(0,splitIndex);
                            fileName = fullPath.substring(splitIndex);
                        }
                    }
                }
                int port = RandomUtil.getRandom(10625, 21011);
                MainApplication.webServerManager.startServer(port, localPath);

                IntentUtil.openFileByUri(FileListActivity.this, "http://127.0.0.1:" + String.valueOf(port) + File.separator +fileName);
            }
        });
    }

    private void startRefreshListDataThread() {
        stopRefreshListDataThread();
        refreshListDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        Thread.sleep(1000);

                        List<LocalVideoInfo>  localVideoList = new ArrayList<LocalVideoInfo>();
                        String[] strings = new File(MainApplication.appConfig.rootPath).list();
                        if(strings!=null) {
                            for (String itemName : strings) {
                                if (itemName.endsWith(".temp")) {
                                    continue;
                                }

                                String currentItemPath = MainApplication.appConfig.rootPath + File.separator + itemName;
                                File currentItem = new File(currentItemPath);
                                if (currentItem.isFile()) {
                                    String extension = FileUtil.getExtension(itemName);
                                    if (VideoFormatUtil.containsVideoExtension(extension)) {
                                        LocalVideoInfo localVideoInfo = new LocalVideoInfo();
                                        localVideoInfo.setFileName(FileUtil.getName(itemName));
                                        localVideoInfo.setVideoType(extension);
                                        localVideoInfo.setSize(currentItem.length());
                                        localVideoInfo.setLocalPath(currentItemPath);
                                        localVideoList.add(localVideoInfo);
                                    }
                                }
                                if (currentItem.isDirectory()) {
                                    List<String> fileNameList = Arrays.asList(new File(currentItemPath).list());
                                    if (fileNameList.contains("index.m3u8") && fileNameList.contains("videoTitle")) {
                                        long size = FileUtil.getFolderSize(currentItem);
                                        LocalVideoInfo localVideoInfo = new LocalVideoInfo();
                                        localVideoInfo.setFileName(FileUtil.fileToString(currentItemPath + File.separator + "videoTitle"));
                                        localVideoInfo.setVideoType("m3u8");
                                        localVideoInfo.setSize(size);
                                        localVideoInfo.setLocalPath(currentItemPath);
                                        localVideoList.add(localVideoInfo);
                                    }
                                }
                            }
                        }
                        localVideoInfoArrayList.clear();
                        localVideoInfoArrayList.addAll(localVideoList);

                        EventBus.getDefault().post(new RefreshLocalVideoListEvent());
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        refreshListDataThread.start();
    }

    private void stopRefreshListDataThread() {
        try {
            refreshListDataThread.interrupt();
        }catch (Exception e){
            Log.d("FileListActivity", "refreshListDataThread线程已中止, Pass");
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        startRefreshListDataThread();
    }


    @Override
    protected void onStop() {
        stopRefreshListDataThread();
        EventBus.getDefault().unregister(this);
        super.onStop();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshLocalVideoListEvent(RefreshLocalVideoListEvent refreshLocalVideoListEvent){
        ((LocalVideoListAdapter)listView.getAdapter()).notifyDataSetChanged();
    }


    private static class ViewHolder{
        TextView localItemTitle;
        TextView localItemFileSize;
        TextView localItemVideoType;
        View localItemDeleteButton;

        LocalVideoInfo localVideoInfo;
    }

    class LocalVideoListAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public LocalVideoListAdapter(Context context){
            this.mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return localVideoInfoArrayList.size();
        }

        @Override
        public Object getItem(int arg0) {
            return localVideoInfoArrayList.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return localVideoInfoArrayList.get(arg0).getFileName().hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;
            if (convertView == null) {

                holder=new ViewHolder();

                convertView = mInflater.inflate(R.layout.item_video_file, null);
                holder.localItemTitle = (TextView) convertView.findViewById(R.id.localItemTitle);
                holder.localItemFileSize = (TextView) convertView.findViewById(R.id.localItemFileSize);
                holder.localItemVideoType = (TextView) convertView.findViewById(R.id.localItemVideoType);
                holder.localItemDeleteButton = convertView.findViewById(R.id.localItemDeleteButton);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder)convertView.getTag();
            }

            LocalVideoInfo localVideoInfo = localVideoInfoArrayList.get(position);
            holder.localVideoInfo = localVideoInfo;
            holder.localItemTitle.setText(localVideoInfo.getFileName());
            holder.localItemFileSize.setText(FileUtil.getFormatedFileSize(localVideoInfo.getSize()));
            holder.localItemVideoType.setText(localVideoInfo.getVideoType());

            holder.localItemDeleteButton.setTag(localVideoInfo);
            holder.localItemDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("FileListActivity", "localItemDeleteButton clicked");
                    LocalVideoInfo localVideoInfo = (LocalVideoInfo) v.getTag();
                    Log.d("FileListActivity", "localVideoInfo:"+ JSON.toJSONString(localVideoInfo));
                    File localVideoItem = new File(localVideoInfo.getLocalPath());
                    if(localVideoItem.isDirectory()){
                        FileUtil.deleteDirs(localVideoInfo.getLocalPath());
                    }
                    if(localVideoItem.isFile()){
                        boolean delete = localVideoItem.delete();
                        Log.d("FileListActivity", "delete:"+ delete);
                    }
                }
            });

            return convertView;
        }
    }
}
