# 4. 文件下载

如何使用 Net 库进行文件下载，包括基础下载、断点续传、进度监听、生命周期绑定、全局监听和重试配置。

## 适用条件

- 已完成初始化配置（见 [01-快速开始](./01-quick-start.md)）
- 文件可通过 HTTP/HTTPS URL 访问
- 断点续传需要服务器支持 Range 请求

## 推荐做法

### 基础下载

```kotlin
val task = Net.instance.newDownload()
    .savePath("${filesDir}/video.mp4")    // 文件保存路径
    .url("https://example.com/video.mp4") // 下载地址
    .listener(object : IProgressCallback {
        override fun onConnecting(task: Task) {
            // 正在与服务器建立连接
        }

        override fun onProgress(task: Task, complete: Boolean) {
            val percent = task.downloadSize * 100 / maxOf(task.contentLength, 1L)
            Log.d("TAG", "进度: $percent%")
            if (complete) {
                Log.d("TAG", "下载完成！路径: ${task.path}")
            }
        }

        override fun onFail(error: String?, task: Task) {
            Log.e("TAG", "下载失败: $error")
        }

        override fun onFinish(task: Task) {
            // 无论成功失败都会触发（重试中的 onFail 不会触发）
            Log.d("TAG", "下载任务结束: ${task.url}")
        }
    })
    .start()
```

### 断点续传下载

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/large_file.zip")
    .url("https://example.com/large_file.zip")
    .supportCheckpoint()  // 开启断点续传
    .listener(callback)
    .start()
```

> 依赖服务器支持 Range 请求。如果下载中断后重新发起相同请求（相同 URL 和保存路径），会从断点位置继续下载。

### 覆盖已存在文件

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .overwrite(true)  // 目标文件存在时覆盖
    .listener(callback)
    .start()
```

> `overwrite(false)`（默认）时，目标文件已存在会通过 `onFail` 回调 `"目标文件已存在，未开启覆盖"`。

### 重试次数

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .retryCount(3)  // 失败后最多重试 3 次，默认 1
    .listener(callback)
    .start()
```

### 绑定 Activity 生命周期

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .bindActivity(this)  // Activity 销毁时自动取消+释放监听器
    .listener(callback)
    .start()
```

> **重要**：未调用 `bindActivity()` 且未取消的下载任务，后台会继续执行。此时必须手动调用 `removeDownloadListeners(task)` 释放监听器，否则回调中持有的 Activity 引用会导致内存泄露。

### 全局下载进度监听

```kotlin
val globalListener = object : IProgressCallback {
    override fun onConnecting(task: Task) { /* ... */ }
    override fun onProgress(task: Task, complete: Boolean) { /* ... */ }
    override fun onFail(error: String?, task: Task) { /* ... */ }
    override fun onFinish(task: Task) { /* ... */ }
}

// 注册
Net.instance.addGlobalDownloadListener(globalListener)
// 移除
Net.instance.removeGlobalDownloadListener(globalListener)
```

### 手动释放监听器

```kotlin
// 移除某个任务的所有监听器
Net.instance.removeDownloadListeners(task)

// 移除某个任务的特定监听器
Net.instance.removeDownloadListener(task, specificCallback)
```

### 查询下载状态

```kotlin
if (Net.instance.isDownloadQueued("https://example.com/file.zip")) {
    // 该 URL 正在下载或排队中
}
```

## Task 属性

| 属性 | 类型 | 说明 |
|---|---|---|
| `url` | `String?` | 下载地址 |
| `path` | `String?` | 保存路径 |
| `downloadSize` | `Long` | 已下载字节数 |
| `contentLength` | `Long` | 文件总字节数 |
| `append` | `Boolean` | 是否断点续传 |
| `overwrite` | `Boolean` | 是否覆盖已存在文件 |
| `tryAgainCount` | `Int` | 最大重试次数 |
| `cancelUrl` | `String?` | 被取消时的 URL |
| `uniqueId` | `String` | 任务唯一标识 |

## TaskBuilder 完整方法

| 方法 | 说明 |
|---|---|
| `savePath(path)` | 文件保存路径 |
| `url(url)` | 下载地址 |
| `retryCount(count)` | 最大重试次数（≥1） |
| `overwrite(bool)` | 目标文件存在时是否覆盖 |
| `supportCheckpoint()` | 开启断点续传 |
| `bindActivity(activity)` | 绑定 FragmentActivity 生命周期 |
| `listener(callback)` | 下载进度监听器 |
| `noUseGlobalParams()` | 跳过全局参数 |
| `start(): Task` | 启动下载，返回 Task 实例 |

## 辅助工具

### AbstractProgressCallback

Java 友好适配器，只需覆写关心的回调：

```kotlin
builder.listener(object : AbstractProgressCallback() {
    override fun onProgress(task: Task, complete: Boolean) {
        // 只处理进度
    }
})
```

### TaskTools 进度计算

```kotlin
val percent = TaskTools.getDownloadProgress(task)  // 返回 0..100
```

## 关键说明

- 下载默认最大并行数为 3，可在 `configure {}` 中通过 `maxDownloadNum()` 调整
- 断点续传依赖服务器支持 HTTP Range 请求（返回 206 Partial Content）
- `IProgressCallback` 回调在后台线程执行，更新 UI 需切换到主线程
- 使用 Flow 方式获取下载进度可自动在主线程接收，详见 [08-下载进度 Flow](../../net-flow/doc/02-flow-download.md)
- `onFinish` 在无论成功失败都会触发（但重试中的临时 `onFail` 不会触发），适合做清理工作

## 验证方式

- 下载完成后检查文件是否存在且大小正确
- 通过 MD5 校验确认文件完整性
- 断点续传：下载到一半杀掉进程，重新启动后确认从断点继续
- 使用 `isDownloadQueued(url)` 确认下载状态

[返回 README](../../README.md)
