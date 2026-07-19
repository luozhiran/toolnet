package com.itg.net.flow

import com.itg.net.Net
import com.itg.net.download.TaskBuilder
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 下载进度阶段
 */
enum class DownloadPhase {
    /** 正在与服务器建立连接 */
    Connecting,
    /** 下载中，数据持续接收 */
    Downloading,
    /** 下载完成 */
    Complete,
    /** 下载失败 */
    Failed
}

/**
 * 下载进度数据类
 *
 * @property task 下载任务实例，包含 url、path、downloadSize、contentLength 等信息
 * @property phase 当前下载阶段
 */
data class DownloadProgress(
    val task: Task,
    val phase: DownloadPhase
)

/**
 * 将下载任务转为 [Flow]<[DownloadProgress]>
 *
 * 通过 [callbackFlow] 桥接 [IProgressCallback] 回调，将下载事件
 * 作为 [DownloadProgress] 发射。Flow 收集取消时自动取消下载任务。
 *
 * ## 使用示例
 * ```
 * lifecycleScope.launch {
 *     Net.instance.newDownload()
 *         .savePath("${filesDir}/video.mp4")
 *         .url("https://example.com/video.mp4")
 *         .flow()
 *         .catch { e -> Log.e("TAG", "下载失败", e) }
 *         .collect { progress ->
 *             when (progress.phase) {
 *                 DownloadPhase.Connecting -> showConnecting()
 *                 DownloadPhase.Downloading -> updateProgress(progress.task)
 *                 DownloadPhase.Complete -> onComplete(progress.task)
 *                 DownloadPhase.Failed -> onFail(progress.task)
 *             }
 *         }
 * }
 * ```
 *
 * @receiver [TaskBuilder] 下载任务构建器
 * @return 持续发射下载进度的冷流，下载结束或失败后自动完成
 */
fun TaskBuilder.flow(): Flow<DownloadProgress> = callbackFlow {
    val taskRef = this@flow
    val terminalReached = AtomicBoolean(false)

    // 注入自定义进度回调，将下载事件桥接到 Flow
    val flowCallback = object : IProgressCallback {
        override fun onConnecting(task: Task) {
            trySend(DownloadProgress(task, DownloadPhase.Connecting))
        }

        override fun onProgress(task: Task, complete: Boolean) {
            if (complete) {
                terminalReached.set(true)
                trySend(DownloadProgress(task, DownloadPhase.Complete))
                close()
            } else {
                trySend(DownloadProgress(task, DownloadPhase.Downloading))
            }
        }

        override fun onFail(error: String?, task: Task) {
            terminalReached.set(true)
            trySend(DownloadProgress(task, DownloadPhase.Failed))
            close(NetFlowException(null, error))
        }

        override fun onFinish(task: Task) {
            // onFinish 已经在 onProgress(complete=true) 或 onFail 之后调用，
            // Flow 已经在那些时机 close，此处不需要额外处理
        }
    }

    // 注入回调并启动下载
    taskRef.addDownloadListener(flowCallback)
    val task = taskRef.start()
    if (task.cancelUrl == task.url && task.url != null) {
        terminalReached.set(true)
        trySend(DownloadProgress(task, DownloadPhase.Failed))
        close(NetFlowException(null, ERROR_DOWNLOAD_CANCELED))
    }

    awaitClose {
        if (!terminalReached.get()) {
            Net.instance.cancelDownload(task)
        }
    }
}
