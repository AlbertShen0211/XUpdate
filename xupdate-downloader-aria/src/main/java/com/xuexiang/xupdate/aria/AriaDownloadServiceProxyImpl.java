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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.AriaConfig;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadTaskListener;
import com.arialyy.aria.core.download.target.HttpNormalTarget;
import com.arialyy.aria.core.scheduler.TaskSchedulers;
import com.arialyy.aria.core.task.DownloadTask;
import com.xuexiang.xupdate.easy.service.IDownloadServiceProxy;
import com.xuexiang.xupdate.proxy.IUpdateHttpService;
import com.xuexiang.xupdate.utils.FileUtils;

import java.util.List;

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
     * 下载线程数，通过此参数控制断点续传的多线程并发数
     */
    private final int mThreadNum;

    /**
     * 进度轮询 Handler，用于补偿 Aria 不主动回调 onTaskRunning 的问题
     */
    @Nullable
    private final Handler mProgressHandler = new Handler(Looper.getMainLooper());

    /**
     * 上次上报的进度百分比（用于去重，避免重复回调相同进度）
     */
    private int mLastReportedPercent = -1;

    /**
     * 进度轮询任务
     */
    @Nullable
    private Runnable mProgressRunnable;

    public AriaDownloadServiceProxyImpl(@NonNull Context context) {
        this(context, 1);
    }

    /**
     * 构造方法
     *
     * @param context  上下文
     * @param threadNum 下载线程数，建议 2~8，线程数过大可能触发服务端限流
     */
    public AriaDownloadServiceProxyImpl(@NonNull Context context, int threadNum) {
        Aria.init(context);
        mContext = context.getApplicationContext();
        mThreadNum = Math.max(1, threadNum);
        // 通过全局配置设置下载线程数，Aria 会在创建任务时读取该值
        AriaConfig config = AriaConfig.getInstance();
        config.getDConfig().setThreadNum(mThreadNum);
    }

    @Override
    public void download(@NonNull String url, @NonNull String dirPath, @NonNull String fileName, @NonNull IUpdateHttpService.DownloadCallback callback) {
        // 注册监听，Aria 通过回调接口通知任务状态
        Aria.download(this).register();
        mUrl = url;
        mFilePath = Utils.getFilePath(dirPath, fileName);
        mCallback = callback;
        mTaskId = getTaskIdByUrl(url, mFilePath);
        if (mTaskId == -1 || !FileUtils.isFileExists(mFilePath)) {
            // 第一次下载：创建新任务
            firstDownload(url);
        } else {
            // 断点续传：恢复已存在的任务
            continueDownload();
        }
    }

    private void firstDownload(@NonNull String url) {
        // 从完整文件路径中提取父目录，确保创建的是目录而非文件
        String parentDir = Utils.getParentDirPath(mFilePath);
        if (TextUtils.isEmpty(parentDir) || !Utils.createOrExistsDir(parentDir)) {
            mCallback.onError(new Exception("[Aria] create dir failed: " + parentDir));
            return;
        }
        // 创建前先停止同一 URL 的旧任务，避免脏数据导致下载异常
        long existingId = getTaskIdByUrl(url, mFilePath);
        if (existingId != -1) {
            tryStopTask(existingId);
        }
        // 创建多线程断点续传任务，线程数由构造时传入并通过 AriaConfig 全局配置
        if (FileUtils.isPrivatePath(mContext, mFilePath)) {
            mTaskId = Aria.download(this)
                    .load(url)
                    .setFilePath(mFilePath)
                    .ignoreCheckPermissions()
                    .create();
        } else {
            mTaskId = Aria.download(this)
                    .load(url)
                    .setFilePath(mFilePath)
                    .create();
        }
        // 启动进度轮询，补偿 Aria 不主动回调 onTaskRunning 的问题
        startProgressPolling();
    }

    private void continueDownload() {
        if (FileUtils.isPrivatePath(mContext, mFilePath)) {
            Aria.download(this).load(mTaskId)
                    .ignoreCheckPermissions()
                    .resume();
        } else {
            Aria.download(this).load(mTaskId)
                    .resume();
        }
        // 恢复下载后同样需要轮询进度
        startProgressPolling();
    }

    private void startProgressPolling() {
        // 注意：不在这里重置 mLastReportedPercent，仅在 download() 入口处初始化一次
        // 否则每次 onPre/onTaskRunning 触发后重启轮询都会清空进度记录，导致真实进度跳变被去重丢弃
        mProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCallback == null || mTaskId == -1) {
                    stopProgressPolling();
                    return;
                }
                HttpNormalTarget target = Aria.download(this).load(mTaskId);
                if (target == null || !target.taskExists()) {
                    stopProgressPolling();
                    return;
                }
                // 用字节数而非百分比来驱动回调：
                // getPercent() 在 fileSize 未确定时始终返回 0，导致快速下载只能看到 0→100 两跳；
                // getCurrentProgress() 是真实的已下载字节数，每次变化都能触发回调；
                // UI 层（CustomUpdatePrompter / HProgressDialogUtils）已对 total<=0 做兼容处理，
                // 会直接用百分比更新，无需等待 fileSize 确认。
                long downloaded = target.getCurrentProgress();
                long totalSize = target.getFileSize();
                float progress = totalSize > 0 ? downloaded / (float) totalSize : 0f;
                mCallback.onProgress(progress, totalSize);
                // ARIA updateInterval 配置为 1000ms，轮询间隔需小于该值才能捕捉到 currentProgress 变化；
                // 设为 20ms 可确保在 ARIA 定时器触发的间隙也能读到最新进度，实现实时百分比更新。
                mProgressHandler.postDelayed(this, 20);
            }
        };
        mProgressHandler.post(mProgressRunnable);
    }

    private void stopProgressPolling() {
        if (mProgressRunnable != null) {
            mProgressHandler.removeCallbacks(mProgressRunnable);
            mProgressRunnable = null;
        }
        mLastReportedPercent = -1;
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
        recycle();
    }

    /**
     * 获取下载任务的ID
     *
     * @param url      下载地址
     * @param filePath 文件完整路径
     * @return 任务ID，未找到返回 -1
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

    /**
     * 停止指定任务，清理可能的脏数据
     */
    private void tryStopTask(long taskId) {
        if (taskId == -1) return;
        try {
            if (FileUtils.isPrivatePath(mContext, mFilePath)) {
                Aria.download(this)
                        .load(taskId)
                        .ignoreCheckPermissions()
                        .stop();
            } else {
                Aria.download(this).load(taskId).stop();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWait(DownloadTask task) {
    }

    @Override
    public void onPre(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onStart();
                // onPre 时进度可能为 0，主动通知避免进度条初始闪烁
                mCallback.onProgress(task.getPercent() / 100F, task.getFileSize());
            }
            // 任务进入 PRE 状态后立即启动轮询，避免依赖 onTaskRunning 的触发时机
            startProgressPolling();
        }
    }

    @Override
    public void onTaskRunning(DownloadTask task) {
        if (isCurrentTask(task)) {
            if (mCallback != null) {
                mCallback.onProgress(task.getPercent() / 100F, task.getFileSize());
            }
            // onTaskRunning 触发时也启动轮询作为兜底（有些场景下 onPre 不会回调）
            startProgressPolling();
        }
    }

    @Override
    public void onTaskComplete(DownloadTask task) {
        if (isCurrentTask(task)) {
            stopProgressPolling();
            if (mCallback != null) {
                mCallback.onSuccess(FileUtils.getFileByPath(task.getFilePath()));
                recycle();
            }
        }
    }

    @Override
    public void onTaskFail(DownloadTask task, Exception e) {
        if (isCurrentTask(task)) {
            stopProgressPolling();
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
        stopProgressPolling();
    }

    @Override
    public void onTaskCancel(DownloadTask task) {
    }

    /**
     * 释放资源
     */
    private void recycle() {
        stopProgressPolling();
        Aria.download(this).unRegister();
        TaskSchedulers.getInstance().unRegister(this);
        mCallback = null;
    }

    private boolean isCurrentTask(DownloadTask task) {
        return task != null && task.getKey().equals(mUrl);
    }
}
