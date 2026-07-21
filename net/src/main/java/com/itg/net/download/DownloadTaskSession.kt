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

    private enum class SessionState {
        NEW,
        PREPARED,
        LISTENING,
        TERMINATED
    }

    private val sessionLock = Any()
    private val terminalReached = AtomicBoolean(false)

    @Volatile
    private var state = SessionState.NEW

    @Volatile
    private var activityBinding: ActivityLifecycleBinding? = null

    private var activityBoundProgressCallback: IProgressCallback? = activityBoundProgressCallback
    private var extensionProgressCallback: IProgressCallback? = extensionProgressCallback

    fun prepare(): Boolean {
        val lifecycle = lifecycle ?: run {
            synchronized(sessionLock) {
                if (state == SessionState.NEW) {
                    state = SessionState.PREPARED
                }
            }
            return true
        }
        val bound = ThreadTool.runOnUIThreadBlocking {
            synchronized(sessionLock) {
                if (state == SessionState.TERMINATED) {
                    return@runOnUIThreadBlocking
                }
                if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    state = SessionState.TERMINATED
                    task.cancelUrl = task.url
                    PrintLog.logd("Activity已经销毁，无法绑定Activity")
                    return@runOnUIThreadBlocking
                }
                if (state != SessionState.NEW) {
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
                state = SessionState.PREPARED
            }
        }
        if (!bound) {
            synchronized(sessionLock) {
                state = SessionState.TERMINATED
            }
            task.cancelUrl = task.url
            PrintLog.logd { "bind lifecycle timeout, cancel download ${task.url}" }
        }
        return state != SessionState.TERMINATED
    }

    fun isLifecycleDestroyed(): Boolean = state == SessionState.TERMINATED

    fun startListening() {
        synchronized(sessionLock) {
            if (state != SessionState.PREPARED) {
                return
            }
            state = SessionState.LISTENING
            currentTaskListeners().forEach {
                Download.instance.listenerRegistry.addTaskListener(task, it)
            }
            task.progressCallback = this
        }
    }

    fun finishBeforeStart(error: String?) {
        val listeners = synchronized(sessionLock) {
            if (state == SessionState.TERMINATED) {
                return
            }
            state = SessionState.TERMINATED
            currentTaskListeners()
        }
        if (!terminalReached.compareAndSet(false, true)) return
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
        finishOnce(FinishReason.FAILED) {
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
        cleanup()
        PrintLog.logSubd { "自动取消下载任务 ${task.url}" }
        Download.instance.cancel(task)
        PrintLog.logSubd("销毁Activity 资源释放完成")
    }

    private fun currentTaskListeners(): List<IProgressCallback> {
        return listOfNotNull(activityBoundProgressCallback, extensionProgressCallback)
    }

    private fun cleanup() {
        val binding: ActivityLifecycleBinding?
        synchronized(sessionLock) {
            if (state == SessionState.TERMINATED &&
                activityBinding == null &&
                activityBoundProgressCallback == null &&
                extensionProgressCallback == null &&
                task.progressCallback !== this
            ) {
                return
            }
            state = SessionState.TERMINATED
            Download.instance.listenerRegistry.removeTaskListeners(task)
            binding = activityBinding
            activityBinding = null
            activityBoundProgressCallback = null
            extensionProgressCallback = null
            if (task.progressCallback === this) {
                task.progressCallback = null
            }
        }
        removeActivityLifecycleObserver(binding)
    }

    private fun removeActivityLifecycleObserver(binding: ActivityLifecycleBinding? = activityBinding) {
        binding ?: return
        ThreadTool.runOnUIThread {
            binding.lifecycle.removeObserver(binding.observer)
        }
    }
}
