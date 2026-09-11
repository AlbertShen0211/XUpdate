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

import com.xuexiang.xupdate.easy.EasyUpdate;
import com.xuexiang.xupdate.easy.config.UpdateConfig;
import com.xuexiang.xupdate.easy.service.OkHttpUpdateHttpServiceImpl;
import com.xuexiang.xupdate.proxy.IUpdateHttpService;

/**
 * 使用Aria实现断点续传下载功能
 *
 * @author xuexiang
 * @since 2020/12/23 1:58 AM
 */
public final class AriaDownloader {

    private AriaDownloader() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    /**
     * 开启Aria功能（默认4线程分片下载）
     *
     * @param context 上下文
     */
    public static void enable(Context context) {
        enable(context, 4);
    }

    /**
     * 开启Aria功能并指定下载线程数
     *
     * @param context   上下文
     * @param threadNum 下载线程数，建议 2~8。
     *                  注意：Aria 规定当文件总大小小于 1MB 时，线程数设置不会生效，将回退到单线程模式。
     */
    public static void enable(Context context, int threadNum) {
        EasyUpdate.enableDownloadProxy(context, new AriaDownloadServiceProxyImpl(context, threadNum));
    }

    /**
     * 获取版本更新网络请求服务API（默认4线程）
     *
     * @param context 上下文
     */
    public static IUpdateHttpService getUpdateHttpService(Context context) {
        return getUpdateHttpService(context, 4);
    }

    /**
     * 获取版本更新网络请求服务API（指定线程数）
     *
     * @param context   上下文
     * @param threadNum 下载线程数，建议 2~8
     */
    public static IUpdateHttpService getUpdateHttpService(Context context, int threadNum) {
        UpdateConfig config = EasyUpdate.getUpdateConfig(context);
        return new OkHttpUpdateHttpServiceImpl(config.getTimeout(), config.isPostJson(),
                new AriaDownloadServiceProxyImpl(context, threadNum));
    }

}
