package com.xm.vbrowser.app.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

/**
 * Created by xm on 17/8/21.
 */
public class IntentUtil {
    /** 使用系统API，根据url获得对应的MIME类型 */
    public static String getMimeTypeFromUrl(String url) {
        String type = null;
        //使用系统API，获取URL路径中文件的后缀名（扩展名）
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            //使用系统API，获取MimeTypeMap的单例实例，然后调用其内部方法获取文件后缀名（扩展名）所对应的MIME类型
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        Log.i("bqt", "系统定义的MIME类型为：" + type);
        return type;
    }
    /** 使用系统API打开文件 */
    public static void openFileByUri(Activity activityFrom, String url) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//If set, this activity will become the start of a new task on this history stack.
        intent.setAction(Intent.ACTION_VIEW);// it is the generic action you can use on a piece of data to get the most reasonable合适的 thing to occur
        intent.addCategory(Intent.CATEGORY_DEFAULT);//Set if the activity should be an option选项 for the default action to perform on a piece of data
        intent.setDataAndType(Uri.parse(url), getMimeTypeFromUrl(url));//Set the data for the intent along with an explicit指定的、明确的 MIME data type
        activityFrom.startActivity(intent);
    }
}
