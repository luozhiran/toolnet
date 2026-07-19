# 2. 下载进度 Flow

本文说明如何用 `net-flow` 将文件下载转换为 `Flow<DownloadProgress>`，并解释它和 `net` 下载监听体系的关系。

## 场景选择

| 使用场景 | 推荐 API | 适用条件 | 选择理由 |
| --- | --- | --- | --- |
| 页面用协程观察下载进度 | `TaskBuilder.flow()` | Activity/Fragment/ViewModel 已使用协程 | `collect` 的协程上下文决定 UI 更新线程，调用更贴近 Kotlin 使用习惯 |
| 取消下载 | `job.cancel()` | 下载和页面或业务任务绑定 | `awaitClose` 会取消底层下载任务 |
| 普通回调下载 | [`listener(callback)`](../../net/doc/04-file-download.md) | 项目未使用协程，或已有回调式封装 | 避免为了单个下载引入 Flow |
| 快速创建下载 Flow | `Net.instance.flowDownload { ... }` | 希望在一个 DSL 块中配置下载 | 配置和收集逻辑分离，适合封装业务方法 |

## 最小示例

```kotlin
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.itg.net.Net
import com.itg.net.flow.DownloadPhase
import com.itg.net.flow.flow
import com.itg.net.util.TaskTools
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .overwrite(true)
        .flow()
        .catch { error ->
            Log.e("Download", "下载失败", error)
            showStatus("下载失败")
        }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> {
                    showStatus("正在连接")
                }
                DownloadPhase.Downloading -> {
                    val percent = TaskTools.getDownloadProgress(progress.task)
                    showProgress(percent)
                }
                DownloadPhase.Complete -> {
                    showProgress(100)
                    showStatus("下载完成")
                }
                DownloadPhase.Failed -> {
                    showStatus("下载失败")
                }
            }
        }
}
```

## 快捷 DSL

```kotlin
import com.itg.net.Net
import com.itg.net.flow.flowDownload

lifecycleScope.launch {
    Net.instance.flowDownload {
        savePath("${filesDir}/large.zip")
        url("https://example.com/large.zip")
        supportCheckpoint()
        retryCount(3)
        overwrite(false)
    }
        .catch { showStatus("下载失败") }
        .collect { progress -> updateDownloadUi(progress) }
}
```

## 数据结构

```kotlin
data class DownloadProgress(
    val task: Task,
    val phase: DownloadPhase
)

enum class DownloadPhase {
    Connecting,
    Downloading,
    Complete,
    Failed
}
```

- `task`：当前下载任务，包含 `url`、`path`、`downloadSize`、`contentLength` 等字段。
- `phase`：当前下载阶段，用于驱动 UI 状态。

## 和 net 下载监听的关系

`TaskBuilder.flow()` 没有另起一套下载实现。它通过 `TaskBuilder.addDownloadListener(...)` 接入 `net` 核心库的统一下载监听注册表：

```text
TaskBuilder.flow()
  -> addDownloadListener(flowCallback)
  -> TaskBuilder.start()
  -> DownloadEventPublisher
  -> DownloadListenerRegistry
  -> Flow<DownloadProgress>
```

因此 Flow 下载和回调下载共享同一个下载队列、断点续传、取消、监控和错误处理语义。

`addDownloadListener(...)` 是扩展模块桥接 API，普通业务代码不需要直接调用。回调式下载请使用 `listener(callback)`，协程下载请使用 `flow()`。

## 事件生命周期

```text
成功:
Connecting -> Downloading... -> Complete -> Flow close

失败:
Connecting -> Downloading... -> Failed -> Flow close(NetFlowException)

收集端取消:
job.cancel() -> awaitClose -> Net.instance.cancelDownload(task)
```

`Failed` 会通过 `NetFlowException` 关闭 Flow，业务侧应使用 `catch {}` 接收。下载重试期间的中间失败不会立即关闭 Flow，只有最终失败才会发出 `Failed`。

## 线程模型

| 调用方式 | 回调或收集线程 | 是否可直接更新 UI |
| --- | --- | --- |
| `TaskBuilder.flow().collect {}` | 取决于启动 `collect` 的协程上下文 | 在 `lifecycleScope.launch {}` 默认主线程中可以 |
| `TaskBuilder.listener(callback)` | 下载线程回调 | 需要切换到主线程 |

Flow 本身不强制切换线程。如果在 `Dispatchers.IO` 中收集，请自行切回主线程更新 UI。

## 常见问题

- 不要忘记 `savePath(...)` 和 `url(...)`。缺少任一字段会被视为无效下载任务。
- 不要在 Activity 销毁后继续收集页面 Flow。页面场景推荐使用 `lifecycleScope` 或 `repeatOnLifecycle`。
- 不要期望每次重试失败都会进入 `catch`。只有最终失败才会关闭 Flow。
- 下载大文件时，`collect` 中应只做轻量 UI 更新，复杂统计可以节流或放到后台线程。

## 验证方式

- 下载小文件，应收到 `Connecting -> Downloading -> Complete`。
- 请求不存在的文件，应进入 `catch`，异常类型为 `NetFlowException`。
- 在下载中执行 `job.cancel()`，底层下载任务应被取消。
- 使用 `supportCheckpoint()` 中断后重试，应能继续已有进度。

[返回模块 README](../README.md)
