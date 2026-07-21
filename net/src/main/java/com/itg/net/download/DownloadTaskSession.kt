package com.itg.net.download

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.Task
import com.itg.net.download.operations.DownloadEvent
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个下载任务的生命周期会话。
 *
 * 负责监听器注册、生命周期取消、下载事件发布和终态资源释放；调度器和队列状态不再
 * 直接管理业务监听器，避免终态回调异步派发时 listener 被提前移除。
 */
internal class DownloadTaskSession(
    val task: Task,
    private val lifecycle: Lifecycle?,
    activityBoundProgressCallback: IProgressCallback?,
    extensionProgressCallback: IProgressCallback?
) : AbstractProgressCallback() {

    enum class FinishReason {
        SUCCESS,
        FAILED,
        CANCELED,
        INVALID_TASK,
        TARGET_FILE_EXISTS
    }

    private class ActivityLifecycleBinding(
        val lifecycle: Lifecycle,
        val observer: LifecycleEventObserver
    )

    private val terminalReached = AtomicBoolean(false)

    @Volatile
    private var lifecycleDestroyed = false

    @Volatile
    private var activityBinding: ActivityLifecycleBinding? = null

    private var activityBoundProgressCallback: IProgressCallback? = activityBoundProgressCallback
    private var extensionProgressCallback: IProgressCallback? = extensionProgressCallback

    fun prepare(): Boolean {
        val lifecycle = lifecycle ?: return true
        val bound = ThreadTool.runOnUIThreadBlocking {
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                lifecycleDestroyed = true
                task.cancelUrl = task.url
                PrintLog.logd("Activity已经销毁，无法绑定Activity")
                return@runOnUIThreadBlocking
            }
            PrintLog.logd("绑定Activity")
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    onLifecycleDestroyed()
                }
            }
            activityBinding = ActivityLifecycleBinding(lifecycle, observer)
            lifecycle.addObserver(observer)
        }
        if (!bound) {
            lifecycleDestroyed = true
            task.cancelUrl = task.url
            PrintLog.logd { "bind lifecycle timeout, cancel download ${task.url}" }
        }
        return !lifecycleDestroyed
    }

    fun isLifecycleDestroyed(): Boolean = lifecycleDestroyed

    fun startListening() {
        currentTaskListeners().forEach {
            Download.instance.listenerRegistry.addTaskListener(task, it)
        }
        task.progressCallback = this
    }

    fun finishBeforeStart(error: String?) {
        if (!terminalReached.compareAndSet(false, true)) return
        val listeners = currentTaskListeners()
        try {
            listeners.forEach { it.onFail(error, task) }
            listeners.forEach { it.onFinish(task) }
        } finally {
            cleanup()
        }
    }

    fun onConnecting() {
        Download.instance.eventDispatcher.dispatch(DownloadEvent.Connecting(task))
    }

    fun onProgress() {
        Download.instance.eventDispatcher.dispatch(DownloadEvent.Progress(task, complete = false))
    }

    fun onSuccess() {
        finishOnce(FinishReason.SUCCESS) {
            Download.instance.eventDispatcher.dispatch(DownloadEvent.Progress(task, complete = true))
        }
    }

    fun onFailure(error: String?) {
        finishOnce(if (error == null) FinishReason.FAILED else FinishReason.FAILED) {
            Download.instance.eventDispatcher.dispatch(DownloadEvent.Failed(task, error))
        }
    }

    fun onRetry(error: String?) {
        if (error != ERROR_DOWNLOAD_RETRYING) {
            PrintLog.logd { "download retry event ignored with unexpected error=$error" }
        }
    }

    fun cancel(reason: String?) {
        finishOnce(FinishReason.CANCELED) {
            Download.instance.eventDispatcher.dispatch(DownloadEvent.Failed(task, reason))
        }
    }

    override fun onConnecting(task: Task) {
        onConnecting()
    }

    override fun onProgress(task: Task, complete: Boolean) {
        if (complete) {
            onSuccess()
        } else {
            onProgress()
        }
    }

    override fun onFail(error: String?, task: Task) {
        if (error == ERROR_DOWNLOAD_RETRYING) {
            onRetry(error)
        } else {
            onFailure(error)
        }
    }

    override fun onFinish(task: Task) {
        finishOnce(FinishReason.SUCCESS)
    }

    private fun finishOnce(reason: FinishReason, publishTerminalEvent: () -> Unit = {}) {
        if (!terminalReached.compareAndSet(false, true)) return
        PrintLog.logd { "download task finish reason=$reason" }
        try {
            publishTerminalEvent()
            Download.instance.eventDispatcher.dispatch(DownloadEvent.Finished(task))
        } finally {
            cleanup()
        }
    }

    private fun onLifecycleDestroyed() {
        PrintLog.logd("销毁Activity 开始释放资源")
        lifecycleDestroyed = true
        activityBoundProgressCallback?.let {
            PrintLog.logSubd("释放下载时注册的回调监听(监听内持有Activity引用)")
            Download.instance.listenerRegistry.debugPrint()
            Download.instance.listenerRegistry.removeTaskListener(task, it)
            PrintLog.logSubd("释放下完成")
            Download.instance.listenerRegistry.debugPrint()
        }
        activityBoundProgressCallback = null
        PrintLog.logSubd { "自动取消下载任务 ${task.url}" }
        Download.instance.cancel(task)
        removeActivityLifecycleObserver()
        PrintLog.logSubd("销毁Activity 资源释放完成")
    }

    private fun currentTaskListeners(): List<IProgressCallback> {
        return listOfNotNull(activityBoundProgressCallback, extensionProgressCallback)
    }

    private fun cleanup() {
        Download.instance.listenerRegistry.removeTaskListeners(task)
        removeActivityLifecycleObserver()
        activityBoundProgressCallback = null
        extensionProgressCallback = null
        if (task.progressCallback === this) {
            task.progressCallback = null
        }
    }

    private fun removeActivityLifecycleObserver() {
        val binding = activityBinding ?: return
        activityBinding = null
        ThreadTool.runOnUIThread {
            binding.lifecycle.removeObserver(binding.observer)
        }
    }
}