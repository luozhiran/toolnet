# 4. 文件下载

本文说明如何使用 `net` 核心库下载文件、监听进度、绑定生命周期，并解释本次调整后的下载事件分发模型。

## 场景选择

| 使用场景 | 推荐 API | 适用条件 | 选择理由 |
| --- | --- | --- | --- |
| 普通文件下载 | `Net.instance.newDownload().savePath(...).url(...).listener(...).start()` | 需要回调式下载进度 | API 简单，适合 Activity/Fragment 中直接更新业务状态 |
| 断点续传 | `supportCheckpoint()` | 服务端支持 `Range` 请求，目标文件允许追加写入 | 失败或暂停后可以从已有字节继续下载 |
| 页面销毁自动取消 | `bindActivity(activity)` | 下载只服务于当前页面 | 页面销毁时自动取消任务并释放监听器，降低内存泄露风险 |
| 全局监听下载 | `Net.instance.addGlobalDownloadListener(listener)` | 需要统一展示通知栏、悬浮窗或调试日志 | 所有下载任务复用同一个全局监听入口 |
| 协程 Flow 下载 | [`TaskBuilder.flow()`](../../net-flow/doc/02-flow-download.md) | 项目使用 Kotlin 协程 | 将下载事件转换成 `Flow<DownloadProgress>`，取消协程即可取消下载 |
| 下载监控上报 | `monitor()` / `skipMonitor()` / `monitorExtra(...)` | 项目开启网络质量监控 | 单个下载任务可以覆盖全局监控策略 |

## 最小示例

```kotlin
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.util.TaskTools

val task = Net.instance.newDownload()
    .savePath("${filesDir}/demo.zip")
    .url("https://example.com/demo.zip")
    .overwrite(true)
    .listener(object : AbstractProgressCallback() {
        override fun onConnecting(task: Task) {
            showStatus("正在连接")
        }

        override fun onProgress(task: Task, complete: Boolean) {
            if (complete) {
                showStatus("下载完成")
            } else {
                val percent = TaskTools.getDownloadProgress(task)
                showProgress(percent)
            }
        }

        override fun onFail(error: String?, task: Task) {
            showStatus("下载失败: $error")
        }

        override fun onFinish(task: Task) {
            hideLoading()
        }
    })
    .start()
```

## 断点续传

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/large.apk")
    .url("https://example.com/large.apk")
    .supportCheckpoint()
    .retryCount(3)
    .overwrite(false)
    .listener(callback)
    .start()
```

`supportCheckpoint()` 会让任务按断点续传流程执行。目标文件已经存在且没有开启 `overwrite(true)` 时，任务会通过 `onFail(ERROR_TARGET_FILE_EXISTS, task)` 返回失败。

## 生命周期绑定

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/page-resource.zip")
    .url("https://example.com/page-resource.zip")
    .bindActivity(this)
    .listener(callback)
    .start()
```

`bindActivity(activity)` 适合“页面离开后下载也没有意义”的场景。Activity 销毁时库会取消下载任务，并移除与该任务绑定的监听器。

如果下载不绑定 Activity，并且任务会在后台继续执行，业务侧应在不再需要监听时调用：

```kotlin
Net.instance.removeDownloadListeners(task)
```

或只移除某一个监听器：

```kotlin
Net.instance.removeDownloadListener(task, callback)
```

## 下载事件模型

本次调整后，下载回调统一通过事件分发链路处理：

```text
Task.progressCallback
  -> DownloadEventPublisher
  -> Download.publishDownloadEvent()
  -> DownloadListenerRegistry
  -> 全局监听器 / 任务监听器 / Flow 监听器
```

这套模型替代了旧的 `DownloadEndNotify`、`GlobalDownloadProgressCache`、`HoldActivityCallbackMap` 和 `setProgressCallback`。使用者不需要直接接触这些内部类，只需要使用公开 API。

## 公开监听 API

| API | 作用 | 何时清理 |
| --- | --- | --- |
| `listener(callback)` | 给当前下载任务注册进度监听 | 任务结束、绑定 Activity 销毁、取消任务或手动移除 |
| `Net.instance.addGlobalDownloadListener(callback)` | 监听所有下载任务 | 不再需要全局监听时手动移除 |
| `Net.instance.removeGlobalDownloadListener(callback)` | 移除指定全局监听器 | 全局监听组件销毁时 |
| `Net.instance.removeDownloadListeners(task)` | 移除某个任务的全部监听器 | 后台下载不再需要 UI 监听时 |
| `Net.instance.removeDownloadListener(task, callback)` | 移除某个任务的指定监听器 | 单个监听器失效时 |

`TaskBuilder.addDownloadListener(callback)` 是扩展模块使用的内部桥接 API，目前用于 `net-flow` 将下载进度转换为 Flow。普通业务代码推荐使用 `listener(callback)`。

## 其他下载 API

```kotlin
// 跳过全局参数（下载第三方 CDN 文件时避免泄露内部参数）
Net.instance.newDownload()
    .savePath(path)
    .url("https://cdn.example.com/file.zip")
    .noUseGlobalParams()
    .start()

// 查询下载状态
if (Net.instance.isDownloadQueued("https://example.com/file.zip")) {
    // 该 URL 正在下载或排队中
}
```

| 方法 | 说明 |
|------|------|
| `TaskBuilder.noUseGlobalParams()` | 下载 URL 不附加全局参数 |
| `Net.instance.isDownloadQueued(url)` | 判断指定 URL 是否正在下载或排队 |

## 回调语义

| 回调 | 含义 | 说明 |
| --- | --- | --- |
| `onConnecting(task)` | 开始连接服务器 | 每次实际下载尝试前触发 |
| `onProgress(task, false)` | 正在下载 | `task.downloadSize` 和 `task.contentLength` 会持续更新 |
| `onProgress(task, true)` | 下载完成 | 完成后会继续触发 `onFinish(task)` |
| `onFail(error, task)` | 最终失败、取消或任务无效 | 重试过程中的中间失败不会作为最终失败分发 |
| `onFinish(task)` | 任务终结 | 成功或最终失败后都会触发，用于收尾 |

无效任务会返回 `ERROR_INVALID_DOWNLOAD_TASK`。当前任务要求 `url` 和 `savePath` 都有效，否则不会发起真实下载。

## 监控字段

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/report.zip")
    .url("https://example.com/report.zip")
    .monitor()
    .monitorExtra("scene=preload;module=home")
    .listener(callback)
    .start()
```

- `monitor()`：强制对当前下载任务开启监控上报。
- `skipMonitor()`：强制跳过当前下载任务的监控上报。
- `monitorExtra(extra)`：给监控事件附加业务字段，便于服务端排查具体场景。

## 常见问题

- 不要在普通业务代码里使用 `addDownloadListener()`，它是为扩展模块保留的桥接能力。
- 不要只依赖 Activity 销毁释放监听器。后台下载场景应主动取消任务或移除监听器。
- 不要把 `onProgress(task, true)` 和 `onFinish(task)` 当成同一个事件。前者表示下载完成，后者表示任务收尾。
- 下载大文件时不要在每次 `onProgress` 中做复杂 JSON、数据库或文件扫描操作，避免拖慢下载线程。

## 验证方式

- 下载一个小文件，应按 `onConnecting -> onProgress(false) -> onProgress(true) -> onFinish` 顺序触发。
- 下载一个不存在的地址，应最终触发 `onFail -> onFinish`。
- 绑定 Activity 后关闭页面，应取消下载并释放任务监听器。
- 开启 `supportCheckpoint()` 后中断再恢复，应从已有字节继续下载。

[返回 README](../../README.md)
