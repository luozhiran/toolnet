# 04. 文件下载

这个文档说明文件下载、断点续传、监听和取消。

## 最小下载

```kotlin
Net.instance.newDownload()
    .url("https://example.com/file.zip")
    .savePath(File(filesDir, "file.zip").absolutePath)
    .listener(object : IProgressCallback {
        override fun onConnecting(task: Task) {
            // 已进入连接阶段。
        }

        override fun onProgress(task: Task, complete: Boolean) {
            if (complete) {
                // 文件已经写入完成。
            } else {
                val total = task.contentLength
                val current = task.downloadSize
            }
        }

        override fun onFail(error: String?, task: Task) {
            // 最终失败，不再重试。
        }

        override fun onFinish(task: Task) {
            // 成功或最终失败都会触发，用于收尾。
        }
    })
    .start()
```

## 必填项

- `url(...)`：下载地址。
- `savePath(...)`：最终保存路径。

缺少任意一个都会失败，不会写入 `"null.tmp"` 这类错误路径。

## 断点续传

```kotlin
Net.instance.newDownload()
    .url("https://example.com/video.mp4")
    .savePath(File(filesDir, "video.mp4").absolutePath)
    .supportCheckpoint()
    .retryCount(2)
    .start()
```

断点续传依赖服务端支持 Range 请求。服务端不支持时，库会按普通下载处理。

## 无 Content-Length 的下载

有些 CDN 使用 chunked transfer encoding，不返回 `Content-Length`。这种情况下：

- `task.contentLength` 可能为 `0` 或未知值。
- 不能准确计算百分比。
- 下载完成后仍会触发 `onProgress(task, complete = true)` 和 `onFinish(task)`。

UI 上不要只依赖百分比。更稳妥的展示是“已下载字节数 + 完成状态”。

## 绑定生命周期

```kotlin
Net.instance.newDownload()
    .url(url)
    .savePath(path)
    .bindActivity(activity)
    .start()
```

`bindActivity(activity)` 表示 Activity 销毁时取消下载。只适合页面级临时下载；后台下载不要绑定 Activity。

## 取消下载

```kotlin
val task = Net.instance.newDownload()
    .url(url)
    .savePath(path)
    .start()

Net.cancelDownload(task)
Net.cancelDownloadByUrl(url)
Net.cancelAll()
```

## 常见错误

- 没有设置 `savePath`：任务会失败，不会创建错误文件。
- 在进度回调里只用百分比：无 `Content-Length` 时百分比不可用。
- Activity 退出后仍期待下载继续：如果绑定了 Activity，销毁会自动取消。
- 把普通接口大 JSON 当下载：普通请求受 `maxResponseBodyBytes` 限制，文件场景应该使用下载 API。

[返回模块 README](../README.md)
