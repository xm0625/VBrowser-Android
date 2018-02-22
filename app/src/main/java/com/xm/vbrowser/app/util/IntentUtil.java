package com.xm.vbrowser.app.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.xm.vbrowser.app.BuildConfig;

import java.io.File;

/**
 * Created by xm on 17/8/21.
 */
public class IntentUtil {

    private static final String AUTHORITY_STRING = BuildConfig.APPLICATION_ID + ".fileProvider";

    /** 使用系统API，根据url获得对应的MIME类型 */
    public static String getMimeTypeFromUrl(String url) {
        String type = null;
        //使用系统API，获取URL路径中文件的后缀名（扩展名）
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            //使用系统API，获取MimeTypeMap的单例实例，然后调用其内部方法获取文件后缀名（扩展名）所对应的MIME类型
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (type == null){
                if ("m3u8".equals(extension)){
                    //m3u8只是Unicode版本的m3u而已,目前(Android O)MimeTypeMap里没有m3u8对应的mimetype
                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension("m3u");
                }
            }
        }
        Log.i("bqt", "系统定义的MIME类型为：" + type);
        return type;
    }
    /** 使用系统API打开文件, Android 7.0后API有所变化 */
    public static void openFileByUri(Activity activityFrom, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);//Set if the activity should be an option选项 for the default action to perform on a piece of data
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//If set, this activity will become the start of a new task on this history stack.
            intent.setDataAndType(Uri.parse(url), getMimeTypeFromUrl(url));//Set the data for the intent along with an explicit指定的、明确的 MIME data type
        }else{
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            File file = new File(Uri.parse(url).getPath());
            Uri contentUri = FileProvider.getUriForFile(activityFrom.getApplicationContext(), AUTHORITY_STRING, file);
            intent.setDataAndType(contentUri, getMimeTypeFromUrl(url));//Set the data for the intent along with an explicit指定的、明确的 MIME data type
        }
        activityFrom.startActivity(intent);
    }
}
