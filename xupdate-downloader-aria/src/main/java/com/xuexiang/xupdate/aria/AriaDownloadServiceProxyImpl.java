/*
 * Copyright (C) 2020 xuexiangjys(xuexiangjys@163.com)
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
 *
 */

package com.xuexiang.xupdate.aria;

import android.content.Context;

import androidx.annotation.NonNull;

import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadTaskListener;
import com.arialyy.aria.core.scheduler.TaskSchedulers;
import com.arialyy.aria.core.task.DownloadTask;
import com.xuexiang.xupdate.easy.service.IDownloadServiceProxy;
import com.xuexiang.xupdate.proxy.IUpdateHttpService;
import com.xuexiang.xupdate.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aria 下载服务代理
 *
 * @author xuexiang
 * @since 2020/12/23 1:15 AM
 */
public class AriaDownloadServiceProxyImpl implements IDownloadServiceProxy, DownloadTaskListener {

    private Context mContext;
    /**
     * 下载的地址
     */
    private String mUrl;

    /**
     * 下载文件路径
     */
    private String mFilePath;

    /**
     * 下载任务ID
     */
    private long mTaskId;

    /**
     * 下载回调
     */
    private IUpdateHttpService.DownloadCallback mCallback;

    /**
     * 后台执行器，用于异步执行 HTTP 下载
     */
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public AriaDownloadServiceProxyImpl(@NonNull Context context) {
        Aria.init(context);
        mContext = context.getApplicationContext();
    }

    @Override
    public void download(@NonNull String url, @NonNull String dirPath, @NonNull String fileName,
                         @NonNull IUpdateHttpService.DownloadCallback callback) {
        mUrl = url;
        mFilePath = Utils.getFilePath(dirPath, fileName);
        mCallback = callback;

        // 使用 HttpURLConnection 异步下载，避免 Aria delayTimer 导致的快速下载失败问题
        mExecutor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                File file = new File(mFilePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                URL uri = new URL(url);
                conn = (HttpURLConnection) uri.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    if (callback != null) {
                        callback.onError(new Exception("[Aria] HTTP " + responseCode));
                    }
                    return;
                }

                long totalSize = conn.getContentLengthLong();
                is = conn.getInputStream();
                fos = new FileOutputStream(file);

                byte[] buffer = new byte[8192];
                int len;
                long downloaded = 0;

                // 通知开始下载
                if (callback != null) {
                    callback.onStart();
                }

                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;
                    float progress = totalSize > 0 ? (float) downloaded / totalSize : 0;
                    if (callback != null) {
                        callback.onProgress(progress, totalSize);
                    }
                }
                fos.flush();

                if (callback != null) {
                    callback.onSuccess(file);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
                mCallback = null;
            }
        });
    }

    @Override
    public void cancelDownload(@NonNull String url) {
        if (mTaskId != -1) {
            if (FileUtils.isPrivatePath(mContext, mFilePath)) {
                Aria.download(this)
                        .load(mTaskId)
                        .ignoreCheckPermissions()
                        .stop();
            } else {
                Aria.download(this)
                        .load(mTaskId)
                        .stop();
            }
        }
        mExecutor.shutdownNow();
        recycle();
    }

    /**
     * 获取下载任务的ID
     *
     * @param url 下载地址
     * @return 下载任务的ID
     */
    private long getTaskIdByUrl(@NonNull String url, @NonNull String filePath) {
        List<DownloadEntity> entityList = Aria.download(this).getDownloadEntity(url);
        if (entityList != null && entityList.size() > 0) {
            for (DownloadEntity entity : entityList) {
                if (filePath.equals(entity.getFilePath())) {
                    return entity.getId();
                }
            }
        }
        return -1;
    }

    @Override
    public void onWait(DownloadTask task) {

    }

    @Override
    public void onPre(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onStart();
                // 快速下载时 onProgress 可能来不及触发，此处主动初始化进度
                mCallback.onProgress(0f, task.getFileSize());
            }
        }
    }

    @Override
    public void onTaskRunning(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onProgress(task.getPercent() / 100F, task.getFileSize());
            }
        }
    }

    @Override
    public void onTaskComplete(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onSuccess(FileUtils.getFileByPath(task.getFilePath()));
                recycle();
            }
        }
    }

    @Override
    public void onTaskFail(DownloadTask task, Exception e) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                if (e == null) {
                    e = new Exception("[Aria] onTaskFail, unknown error!");
                }
                mCallback.onError(e);
            }
        }
    }

    @Override
    public void onNoSupportBreakPoint(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onError(new Exception("[Aria] Not support break point!"));
            }
        }
    }

    @Override
    public void onTaskPre(DownloadTask task) {

    }

    @Override
    public void onTaskResume(DownloadTask task) {

    }

    @Override
    public void onTaskStart(DownloadTask task) {

    }

    @Override
    public void onTaskStop(DownloadTask task) {

    }

    @Override
    public void onTaskCancel(DownloadTask task) {

    }

    /**
     * 释放资源
     */
    private void recycle() {
        Aria.download(this).unRegister();
        TaskSchedulers.getInstance().unRegister(this);
        mCallback = null;
    }

    private boolean isCurrentTask(DownloadTask task) {
        return task != null && task.getKey().equals(mUrl);
    }
}
