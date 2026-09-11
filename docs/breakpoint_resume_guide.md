# XUpdate 断点续传与分片下载使用指南

> 基于 `xupdate-downloader-aria` 模块，结合案例 2（URL 方式）和案例 3（UpdateEntity 方式）说明。

---

## 一、断点续传 API 用法

断点续传由 `AriaDownloader.getUpdateHttpService()` 提供，通过替换默认 `IUpdateHttpService` 实现，**无需修改任何框架核心代码**。

### 1. Case 2：URL 方式（服务器下发更新信息）

适用于常规在线检查版本场景，`updateUrl` 返回 JSON 后自动触发下载。

```java
XUpdate.newBuild(getActivity())
        .updateUrl(Constants.CUSTOM_UPDATE_URL)           // 版本检查接口
        .updateHttpService(AriaDownloader.getUpdateHttpService(getActivity()))  // 关键：注入 Aria 服务
        .update();
```

### 2. Case 3：UpdateEntity 方式（直接传入实体）

适用于本地解析 JSON / assets 文件后直接发起更新，或自定义 `IUpdateParser` 的场景。

```java
XUpdate.newBuild(getActivity())
        .supportBackgroundUpdate(true)                     // 可选：支持后台下载
        .updateHttpService(AriaDownloader.getUpdateHttpService(getActivity()))  // 关键：注入 Aria 服务
        .build()
        .update(getUpdateEntityFromAssets());              // 传入已解析好的 UpdateEntity
```

### 3. 重载签名（指定线程数）

```java
// 默认 4 线程
AriaDownloader.getUpdateHttpService(context)

// 自定义线程数（建议 2~8）
AriaDownloader.getUpdateHttpService(context, 4)
```

---

## 二、分片下载 API 用法

分片下载（多线程块下载）由 **Aria 内部配置**控制，有两种设置方式：

### 方式一：代码设置线程数（推荐）

在调用 `getUpdateHttpService()` 时传入线程数，此设置对**新建任务**生效：

```java
// 使用 4 线程分片下载
AriaDownloader.getUpdateHttpService(getActivity(), 4)
```

`AriaDownloader.getUpdateHttpService(context, threadNum)` 内部执行：

```java
// AriaDownloadServiceProxyImpl 构造函数中
AriaConfig config = AriaConfig.getInstance();
config.getDConfig().setThreadNum(threadNum);  // 全局生效
```

### 方式二：`aria_config.xml` 配置

在 `assets/aria_config.xml` 中修改 `threadNum`（需在 `Application.onCreate()` 中 `Aria.init()` 前设置，或通过代码覆盖）：

```xml
<!-- xupdate-downloader-aria/src/main/assets/aria_config.xml -->
<download>
    <!-- 默认值 1，修改为多线程分片数 -->
    <threadNum value="4"/>
    <!-- 多线程是否使用块下载模式（不预占空间） -->
    <useBlock value="false"/>
</download>
```

> **注意**：`threadNum > 1` 时才启用分片，`threadNum = 1` 时为单线程断点续传（非分片）。

---

## 三、使用前提条件

### 3.1 服务端条件

| 条件 | 说明 | 验证方式 |
|------|------|----------|
| **支持 HTTP Range 请求** | 服务端必须返回 `Accept-Ranges: bytes`，否则 Aria 无法断点续传 | `curl -sI <url> | grep Accept-Ranges` |
| **URL 可访问** | 下载地址必须能正常返回 200，404 会直接触发 `onError` | 手动访问 URL 确认 |

当前测试用的阿里云 OSS 地址：
```
https://xuexiangjys.oss-cn-shanghai.aliyuncs.com/apk/xupdate_demo_1.0.2.apk
```
响应头包含 `Accept-Ranges: bytes`，✅ 满足条件。

### 3.2 客户端条件

| 条件 | 说明 | 排查方法 |
|------|------|----------|
| **已通过 `.build()` 创建 UpdateManager** | `build()` 是 `update(UpdateEntity)` 的必要前置，否则抛出 NPE | 检查 Builder 链 |
| **`updateHttpService` 已设置** | `build()` 内 `requireNonNull(updateHttpService)`，不设置会抛异常 | 日志中搜索 `requireNonNull` |
| **网络可用** | Aria 配置 `<netCheck value="true"/>`，断网时直接失败并返回 `网络未连接` | 检查设备网络状态 |
| **APK 缓存目录可写** | 默认路径为 `{externalCacheDir}/xupdate`，需有写权限 | 检查存储权限 |

### 3.3 配置文件条件（`aria_config.xml`）

```xml
<app>
    <!-- 下载前是否检查网络，true=断网直接失败（日志显示"启动任务失败，网络未连接"） -->
    <netCheck value="true"/>
    <!-- 断网时是否重试，false=直接走 onError 回调 -->
    <notNetRetry value="false"/>
</app>
<download>
    <!-- 线程数，>1 时启用分片下载；< 1MB 时自动回退单线程 -->
    <threadNum value="4"/>
    <!-- 失败重试次数 -->
    <reTryNum value="5"/>
    <!-- 进度刷新间隔（ms） -->
    <updateInterval value="1000"/>
</download>
```

---

## 四、断点续传原理

```
调用 AriaDownloader.getUpdateHttpService(context, threadNum)
    └── 创建 OkHttpUpdateHttpServiceImpl(ariaProxy)
            └── ariaProxy = new AriaDownloadServiceProxyImpl(context, threadNum)
                    ├── threadNum > 1：多线程分片下载（分片下载）
                    └── 下载中断后重新发起：
                            getTaskIdByUrl(url, filePath) → 找到已有任务
                                    ├── 文件存在 → continueDownload() 恢复下载（断点续传）
                                    └── 文件不存在 → firstDownload() 全新下载
```

Aria 通过 SQLite 持久化任务进度，进程重启后仍可恢复。

---

## 五、已知问题与解决方案

### 5.1 下载失败后"Upgrade"按钮无响应（Bug）

**现象**：下载失败（网络异常、404、断网等）后，弹窗关闭，再次进入页面点击下载，"升级"按钮点击无反应，无任何日志。

**根因**（两个问题叠加）：

```
第一步：下载失败触发 handleError()
    └── ignoreDownloadError = false（默认）
        └── dismissDialog()
            ├── clearIPrompterProxy() → mPrompterProxy = null   ← 代理被清空
            └── dismissAllowingStateLoss()

第二步：用户再次点击"升级"按钮（InstallApp）
    ├── UpdateUtils.isApkDownloaded(entity) = false（文件不存在）
    ├── mPrompterProxy != null? → false（已被 clear 为 null）
    └── ❌ if 块跳过，无任何操作，按钮无响应
```

**此外**：即使弹窗未关闭，`_XUpdate.isAppUpdating("")` 仍为 `true`（因为 `setIsPrompterShow` 未被正确重置），导致新一次 `update()` 调用被 `CHECK_UPDATING` 拦截。

**解决方案**：在三个 Dialog 的错误处理中，`ignoreDownloadError=true` 分支需先清理代理，再恢复按钮：

**`UpdateDialog.java`** (line ~384)：
```java
@Override
public void handleError(Throwable throwable) {
    if (isShowing()) {
        if (mPromptEntity.isIgnoreDownloadError()) {
+           clearIPrompterProxy();       // 添加这行
            refreshUpdateButton();
        } else {
            dismiss();
        }
    }
}
```

**`UpdateDialogFragment.java`** (line ~449)：
```java
@Override
public void handleError(Throwable throwable) {
    if (!UpdateDialogFragment.this.isRemoving()) {
        if (mPromptEntity.isIgnoreDownloadError()) {
+           clearIPrompterProxy();       // 添加这行
            refreshUpdateButton();
        } else {
            dismissDialog();
        }
    }
}
```

**`UpdateDialogActivity.java`** (line ~400)：
```java
@Override
public void handleError(Throwable throwable) {
    if (!isFinishing()) {
        if (mPromptEntity.isIgnoreDownloadError()) {
+           clearIPrompterProxy();       // 添加这行
            refreshUpdateButton();
        } else {
            dismissDialog();
        }
    }
}
```

### 5.2 配置 `promptIgnoreDownloadError` 启用错误容忍

在 case 3 中开启，可让下载失败后弹窗不关闭，按钮恢复可重试：

```java
case 3:
    XUpdate.newBuild(getActivity())
            .supportBackgroundUpdate(true)
            .updateHttpService(AriaDownloader.getUpdateHttpService(getActivity()))
            .promptIgnoreDownloadError(true)   // ✅ 下载失败不关闭弹窗
            .build()
            .update(getUpdateEntityFromAssets());
    break;
```

开启后，`handleError` 走 `refreshUpdateButton()` 分支（恢复按钮可见），而非 `dismissDialog()`（关闭弹窗）。

> **注意**：开启此选项后，若不调用上述 Bug 修复（添加 `clearIPrompterProxy()`），多次失败后 `mPrompterProxy` 可能残留脏状态，建议同时修复 Bug。

### 5.3 下载 URL 失效导致 404

**现象**：日志显示 `下载失败!(request failed, response's code is : 404)`。

**原因**：`assets/update_test.json` 中的 `DownloadUrl` 指向已失效的腾讯 CDN 地址。

**解决方案**：将 `app/src/main/assets/update_test.json` 中的 `DownloadUrl` 和 `ApkMd5` 与 `jsonapi/update_test.json` 保持一致：

```json
{
  "DownloadUrl": "https://xuexiangjys.oss-cn-shanghai.aliyuncs.com/apk/xupdate_demo_1.0.2.apk",
  "ApkSize": 1689241,
  "ApkMd5": "E4B79A36EFB9F17DF7E3BB161F9BCFD8"
}
```

验证方法：
```bash
# 确认 URL 可访问且支持 Range
curl -sI https://xuexiangjys.oss-cn-shanghai.aliyuncs.com/apk/xupdate_demo_1.0.2.apk | grep -E "HTTP|Accept-Ranges"

# 确认本地 APK 与服务器一致
md5sum apk/xupdate_demo_1.0.2.apk
# 应输出：e4b79a36efb9f17df7e3bb161f9bcfd8
```

### 5.4 文件大小小于 1MB 时分片不生效

**现象**：设置 `threadNum=4`，但小文件仍单线程下载。

**原因**：Aria 内部限制，文件总大小 < 1MB 时自动降级为单线程（`aria_config.xml` 注释有说明）。

---

## 六、完整配置示例

```java
// Activity/Fragment 中发起更新
XUpdate.newBuild(this)
        .supportBackgroundUpdate(true)
        // ① 断点续传：使用 Aria HTTP Service
        .updateHttpService(AriaDownloader.getUpdateHttpService(this, 4))
        // ② 下载失败不关闭弹窗（可选，便于重试）
        .promptIgnoreDownloadError(true)
        .build()
        .update(parseUpdateEntityFromAssets());
```

**同时更新 `assets/aria_config.xml`（可选）**：

```xml
<download>
    <threadNum value="4"/>           <!-- 分片线程数 -->
    <useBlock value="false"/>        <!-- 是否使用块下载模式 -->
    <reTryNum value="5"/>            <!-- 失败重试次数 -->
    <reTryInterval value="5000"/>    <!-- 重试间隔 ms -->
    <netCheck value="true"/>         <!-- 下载前检查网络 -->
    <notNetRetry value="true"/>      <!-- 断网时不重试（直接失败） -->
</download>
```

---

## 七、流程总结

```
调用 AriaDownloader.getUpdateHttpService(context, threadNum)
    └── 创建 OkHttpUpdateHttpServiceImpl(ariaProxy)
            ├── threadNum > 1：多线程分片下载（分片下载）
            └── 下载中断后重新发起：
                    getTaskIdByUrl(url, filePath) → 找到已有任务
                            ├── 文件存在 → continueDownload() 恢复下载（断点续传）
                            └── 文件不存在 → firstDownload() 全新下载
```

---

## 八、日志对照说明

| 日志关键字 | 含义 | 是否正常 |
|-----------|------|---------|
| `设置全局更新网络请求服务:OkHttpUpdateHttpServiceImpl` | Aria 服务已注入 | ✅ |
| `设置请求超时响应时间:20000ms, 是否使用json:false` | OkHttp 初始化完成 | ✅ |
| `创建表的sql：CREATE TABLE DownloadEntity` | Aria DB 初始化完成 | ✅ |
| `启动任务失败，网络未连接` | `<netCheck value="true"/>` 触发，断网 | ⚠️ 检查网络 |
| `下载失败!(request failed , response's code is : 404)` | URL 失效 | ❌ 更换有效 URL |
| `is匿名内部类或局部类，将使用其主类的对象` | Aria 注册监听器的警告，无害 | ℹ️ 可忽略 |
