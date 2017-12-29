package com.xm.vbrowser.app.util;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

/**
 * Created by xm on 17/8/17.
 */
public class ViewUtil {
    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    /**
     * 弹出一个带确认和取消的dialog
     * @param context
     * @param title
     * @param msg
     * @param okbutton
     * @param ok 点击确定事件
     * @param nobutton
     * @param no 点击取消事件
     * @return
     */
    public static AlertDialog openConfirmDialog(Context context, String title,
                                                String msg, String okbutton, DialogInterface.OnClickListener ok, String nobutton,
                                                DialogInterface.OnClickListener no) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage("\n" + msg + "\n");
        builder.setNegativeButton(okbutton, ok);
        builder.setNeutralButton(nobutton, no);
        AlertDialog loadWaitDialog = builder.create();
        loadWaitDialog.setCanceledOnTouchOutside(false);
        loadWaitDialog.show();
        return loadWaitDialog;
    }
}
