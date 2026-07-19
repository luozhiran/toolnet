# 02. Flow 下载

这个文档说明如何把文件下载转成 Flow，并处理进度、失败和取消。

## 最小示例

```kotlin
lifecycleScope.launch {
    Net.instance.newDownload()
        .url("https://example.com/file.zip")
        .savePath(File(filesDir, "file.zip").absolutePath)
        .supportCheckpoint()
        .flow()
        .catch { e ->
            showError(e.message)
        }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> showConnecting()
                DownloadPhase.Downloading -> updateBytes(progress.task.downloadSize)
                DownloadPhase.Complete -> showComplete(progress.task.path)
                DownloadPhase.Failed -> showError("下载失败")
            }
        }
}
```

## DownloadPhase

- `Connecting`：任务开始连接服务器。
- `Downloading`：正在写入文件。
- `Complete`：下载成功完成。
- `Failed`：最终失败，不再重试。

## 取消行为

`TaskBuilder.flow()` 使用 `callbackFlow` 桥接下载回调：

- collect 协程取消时，如果下载还没有进入 `Complete` 或 `Failed`，库会取消下载任务。
- 下载已经完成后，Flow 正常关闭，不会再取消已完成文件。
- 如果启动时任务已经被取消，会发射失败并关闭 Flow。

## 无 Content-Length

无 `Content-Length` 的下载无法计算准确百分比，但仍会发射完成阶段。

```kotlin
val total = progress.task.contentLength
val current = progress.task.downloadSize

if (total > 0L) {
    val percent = current * 100 / total
    updatePercent(percent)
} else {
    updateBytes(current)
}
```

## 快捷 API

```kotlin
lifecycleScope.launch {
    Net.instance.flowDownload {
        url("https://example.com/file.zip")
        savePath(File(filesDir, "file.zip").absolutePath)
    }.collect { progress ->
        // 处理 DownloadProgress
    }
}
```

[返回模块 README](../README.md)
