# 8. 下载进度 Flow

如何使用 `net-flow` 模块将文件下载转为 `Flow<DownloadProgress>`，获取结构化的下载进度事件。

## 适用条件

- 项目已引入 `net-flow` 模块
- 使用 Kotlin 协程
- 需要通过 Flow 获取下载进度（替代 `IProgressCallback` 回调）

## 推荐做法

### TaskBuilder.flow()

```kotlin
import com.itg.net.flow.DownloadPhase

lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .flow()  // → Flow<DownloadProgress>
        .catch { e -> Log.e("TAG", "下载失败", e) }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> {
                    progressBar.isIndeterminate = true
                    statusText.text = "正在连接服务器..."
                }
                DownloadPhase.Downloading -> {
                    progressBar.isIndeterminate = false
                    val pct = TaskTools.getDownloadProgress(progress.task)
                    progressBar.progress = pct
                    statusText.text = "下载中 $pct%"
                }
                DownloadPhase.Complete -> {
                    progressBar.progress = 100
                    statusText.text = "下载完成！"
                }
                DownloadPhase.Failed -> {
                    statusText.text = "下载失败"
                }
            }
        }
}
```

### DownloadProgress 结构

```kotlin
data class DownloadProgress(
    val task: Task,           // 下载任务（url, path, downloadSize, contentLength 等）
    val phase: DownloadPhase  // 当前阶段
)

enum class DownloadPhase {
    Connecting,    // 连接中（发射 1 次）
    Downloading,   // 下载中（持续多次发射）
    Complete,      // 下载完成（发射后 Flow 关闭）
    Failed         // 下载失败（Flow 以 NetFlowException 关闭）
}
```

### 生命周期流程

```
   ┌─ Connecting ─→ Downloading ─→ Downloading ─→ ... ─→ Complete ──→ Flow 关闭
   │                                        │
   └─ Connecting ─→ Failed ─────────────────────→ Flow 关闭（抛 NetFlowException）
```

### 取消下载

```kotlin
val job = lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/large.zip")
        .url("https://example.com/large.zip")
        .flow()
        .collect { /* ... */ }
}

// 点击取消 → 协程取消 → Flow 关闭 → 下载任务自动取消
btnCancel.setOnClickListener { job.cancel() }
```

### 使用 flowDownload 便捷方法

```kotlin
Net.instance.flowDownload {
    savePath("${filesDir}/file.zip")
    url("https://example.com/file.zip")
    overwrite(true)
    retryCount(3)
}
    .catch { e -> showError(e.message) }
    .collect { progress -> updateUI(progress) }
```

## 关键说明

- `Connecting` 只发射一次
- `Downloading` 持续多次发射直到下载完成
- `Complete` 发射后 Flow 自动关闭
- `Failed` 时 Flow 以 `NetFlowException` 关闭（可通过 `catch` 操作符捕获）
- Flow 收集被取消时（协程被取消），底层下载任务自动取消
- 下载失败后重试期间，中间的 `onFail` 不会导致 Flow 关闭（最终失败才会）

## 线程模型

Flow 的 `collect {}` 默认在主线程执行（取决于 `launch` 的协程调度器），可以直接更新 UI：

| 调用方式 | 结果所在线程 | 能否直接操作 UI |
|---|---|---|
| `TaskBuilder.flow().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |
| `TaskBuilder.listener(IProgressCallback)` | DdNet 下载线程池 | ❌ 需切换线程 |

## 验证方式

- 下载一个大文件，观察进度回调是否持续触发
- 在下载过程中取消协程，确认下载任务被取消且 `catch` 捕获到 `CancellationException`
- 确认 `Connecting` → `Downloading` → `Complete` 的完整生命周期按顺序触发

[返回 README](../../README.md)
