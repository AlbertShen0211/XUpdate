/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xupdatedemo.utils;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.text.TextUtils;

/**
 * Created by Vector on 2016/8/12 0012.
 */
public class HProgressDialogUtils {
    private static ProgressDialog sHorizontalProgressDialog;
    // 记录总大小，用于计算百分比进度
    private static long sTotalSize = 0;

    private HProgressDialogUtils() {
        throw new UnsupportedOperationException("cannot be instantiated");
    }

    @SuppressLint("NewApi")
    public static void showHorizontalProgressDialog(Context context, String msg, boolean isShowSize) {
        cancel();

        if (sHorizontalProgressDialog == null) {
            sHorizontalProgressDialog = new ProgressDialog(context);
            sHorizontalProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            sHorizontalProgressDialog.setCancelable(false);
            if (isShowSize) {
                sHorizontalProgressDialog.setProgressNumberFormat("%2dMB/%1dMB");
            }
            // 默认按百分比显示（0-100），不依赖已知总大小
            sHorizontalProgressDialog.setMax(100);
        }
        if (!TextUtils.isEmpty(msg)) {
            sHorizontalProgressDialog.setMessage(msg);
        }
        sHorizontalProgressDialog.show();
    }

    /**
     * 设置进度条最大值（总字节数）
     * 调用此方法后，进度将按实际字节比例显示
     */
    public static void setMax(long total) {
        if (sHorizontalProgressDialog != null && total > 0) {
            sTotalSize = total;
            sHorizontalProgressDialog.setMax(100);
        }
    }

    /**
     * 设置进度（百分比 0-100）
     */
    public static void setProgress(int current) {
        if (sHorizontalProgressDialog == null) {
            return;
        }
        sHorizontalProgressDialog.setProgress(current);
        if (sHorizontalProgressDialog.getProgress() >= sHorizontalProgressDialog.getMax()) {
            sHorizontalProgressDialog.dismiss();
            sHorizontalProgressDialog = null;
            sTotalSize = 0;
        }
    }

    /**
     * 根据字节数更新进度条
     *
     * @param total   总字节数
     * @param current 当前已下载字节数
     */
    public static void onLoading(long total, long current) {
        if (sHorizontalProgressDialog == null) {
            return;
        }
        if (total > 0) {
            sTotalSize = total;
            sHorizontalProgressDialog.setMax(100);
        }
        int percent = sTotalSize > 0 ? (int) (current * 100 / sTotalSize) : Math.round(current);
        sHorizontalProgressDialog.setProgress(percent);
        if (percent >= 100) {
            sHorizontalProgressDialog.dismiss();
            sHorizontalProgressDialog = null;
            sTotalSize = 0;
        }
    }

    public static void cancel() {
        if (sHorizontalProgressDialog != null) {
            sHorizontalProgressDialog.dismiss();
            sHorizontalProgressDialog = null;
        }
        sTotalSize = 0;
    }
}
