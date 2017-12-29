package com.xm.vbrowser.app.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.entity.DownloadTask;
import com.xm.vbrowser.app.event.RefreshDownloadingListEvent;
import com.xm.vbrowser.app.util.FileUtil;
import com.xm.vbrowser.app.util.ViewUtil;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Set;

public class DownloadCenterActivity extends Activity {

    private ListView listView;
    private View goBackButton;
    private View goFileListButton;

    private Thread refreshListDataThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_center);

        initView();
        mainInit();
    }

    private void initView() {
        listView = (ListView)findViewById(R.id.listView);
        goBackButton = findViewById(R.id.goBackButton);
        goFileListButton = findViewById(R.id.goFileListButton);
    }

    private void mainInit() {
        goBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        goFileListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DownloadCenterActivity.this, FileListActivity.class);
                startActivity(intent);
            }
        });

        listView.setAdapter(new DownloadingListAdapter(this));

        startRefreshListDataThread();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshDownloadingListEvent(RefreshDownloadingListEvent refreshDownloadingListEvent){
        ((DownloadingListAdapter)listView.getAdapter()).notifyDataSetChanged();
    }

    private void startRefreshListDataThread() {
        stopRefreshListDataThread();
        refreshListDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        Thread.sleep(1000);
                        EventBus.getDefault().post(new RefreshDownloadingListEvent());
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
            Log.d("DownloadCenterActivity", "refreshListDataThread线程已中止, Pass");
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



    private static class ViewHolder{
        TextView downloadingItemTitle;
        TextView downloadingItemVideoType;
        View downloadingItemProgressFinishedView;
        TextView downloadingItemDownloadedSize;
        TextView downloadingItemTotalSize;
        TextView downloadingItemDownloadInfo;
        View downloadingItemCancelButton;
    }

    class DownloadingListAdapter extends BaseAdapter{

        private LayoutInflater mInflater;
        private String[] allDownloadTaskKeyArray;

        public DownloadingListAdapter(Context context){
            this.mInflater = LayoutInflater.from(context);
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
            Set<String> strings = MainApplication.downloadManager.getAllDownloadTaskMap().keySet();
            this.allDownloadTaskKeyArray = strings.toArray(new String[strings.size()]);
        }

        @Override
        public int getCount() {
            return allDownloadTaskKeyArray.length;
        }

        @Override
        public Object getItem(int arg0) {
            return allDownloadTaskKeyArray[arg0];
        }

        @Override
        public long getItemId(int arg0) {
            return allDownloadTaskKeyArray[arg0].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;
            if (convertView == null) {

                holder=new ViewHolder();

                convertView = mInflater.inflate(R.layout.item_downloading, null);
                holder.downloadingItemTitle = (TextView) convertView.findViewById(R.id.downloadingItemTitle);
                holder.downloadingItemVideoType = (TextView) convertView.findViewById(R.id.downloadingItemVideoType);
                holder.downloadingItemProgressFinishedView = convertView.findViewById(R.id.downloadingItemProgressFinishedView);
                holder.downloadingItemDownloadedSize = (TextView) convertView.findViewById(R.id.downloadingItemDownloadedSize);
                holder.downloadingItemTotalSize = (TextView) convertView.findViewById(R.id.downloadingItemTotalSize);
                holder.downloadingItemDownloadInfo = (TextView)convertView.findViewById(R.id.downloadingItemDownloadInfo);
                holder.downloadingItemCancelButton = convertView.findViewById(R.id.downloadingItemDeleteButton);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder)convertView.getTag();
            }

            DownloadTask downloadTask = MainApplication.downloadManager.getAllDownloadTaskMap().get(allDownloadTaskKeyArray[position]);
            if(downloadTask==null){
                return convertView;
            }
            holder.downloadingItemTitle.setText(TextUtils.isEmpty(downloadTask.getSourcePageTitle())?downloadTask.getFileName()+"."+downloadTask.getFileExtension():downloadTask.getSourcePageTitle()+"."+downloadTask.getFileExtension());
            holder.downloadingItemVideoType.setText(downloadTask.getFileExtension());
            if("running".equals(downloadTask.getStatus())){
                float percent = downloadTask.getSize().get()>0?(downloadTask.getTotalDownloaded().floatValue()*100/downloadTask.getSize().floatValue()):0f;
                float weight = (100-percent)>0?(percent/(100-percent)):999999;
                holder.downloadingItemProgressFinishedView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
            }
            holder.downloadingItemDownloadedSize.setText(FileUtil.getFormatedFileSize(downloadTask.getTotalDownloaded().get()>downloadTask.getSize().get()?downloadTask.getSize().get():downloadTask.getTotalDownloaded().get()));
            holder.downloadingItemTotalSize.setText(FileUtil.getFormatedFileSize(downloadTask.getSize().get()));
            holder.downloadingItemDownloadInfo.setText("--");
            if("ready".equals(downloadTask.getStatus())){
                holder.downloadingItemDownloadInfo.setText("队列中");
            }
            if("loading".equals(downloadTask.getStatus())){
                holder.downloadingItemDownloadInfo.setText("计算文件大小");
            }
            if("running".equals(downloadTask.getStatus())){
                holder.downloadingItemDownloadInfo.setText(FileUtil.getFormatedFileSize(downloadTask.getCurrentSpeed())+"/s");
            }
            if("saving".equals(downloadTask.getStatus())){
                holder.downloadingItemDownloadInfo.setText("保存中");
            }
            if("error".equals(downloadTask.getStatus())){
                holder.downloadingItemDownloadInfo.setText("失败:"+downloadTask.getFailedReason());
            }
            holder.downloadingItemCancelButton.setTag(downloadTask);
            holder.downloadingItemCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DownloadTask downloadTaskClicked = (DownloadTask) v.getTag();
                    MainApplication.downloadManager.cancelTask(downloadTaskClicked.getTaskId());
                }
            });

            return convertView;
        }
    }
}
