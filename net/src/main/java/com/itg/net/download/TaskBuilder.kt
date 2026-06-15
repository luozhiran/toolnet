package com.itg.net.download

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itg.net.Download
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.operations.DownloadEndNotify
import com.itg.net.download.operations.HoldActivityCallbackMap
import java.io.File

class TaskBuilder {
    private val task by lazy { Task() }
    private val progressCallback by lazy {
        object : IProgressCallback {
            override fun onConnecting(task: Task) {
                DownloadEndNotify.connectNotify(task)
            }

            override fun onProgress(task: Task, complete: Boolean) {
                if (complete) {
                    DownloadEndNotify.completeNotify(task)

                } else {
                    DownloadEndNotify.progressNotify(task)
                }
            }

            override fun onFail(error: String?, task: Task) {
                if (Download.instance.dispatchTool.getTaskState()
                        .isTryAgainDownload(error)
                ) {
                    return
                }
                DownloadEndNotify.failNotify(task, error)
            }

        }
    }

    // 持有activity引用的回调
    private var holdActivityRef: IProgressCallback? = null

    @Volatile
    private var lifecycleDestroyed = false

    fun path(path: String): TaskBuilder {
        task.path = path
        return this
    }

    fun savePath(path: String): TaskBuilder = path(path)

    fun url(url: String): TaskBuilder {
        task.url = url
        return this
    }

    fun tryAgainCount(count: Int): TaskBuilder {
        task.tryAgainCount = count.coerceAtLeast(1)
        return this
    }

    fun retryCount(count: Int): TaskBuilder = tryAgainCount(count)

    fun overwrite(overwrite: Boolean): TaskBuilder {
        task.overwrite = overwrite
        return this
    }

    fun autoRemoveActivity(activity: FragmentActivity): TaskBuilder {
        if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            lifecycleDestroyed = true
            task.cancelUrl = task.url
            return this
        }
        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycleDestroyed = true
                    holdActivityRef?.let {
                        HoldActivityCallbackMap.removeProgressCallback(
                            task,
                            it
                        )
                    }
                    holdActivityRef = null
                    Download.instance.cancel(task)
                    source.lifecycle.removeObserver(this)
                }
            }
        })
        return this
    }

    fun setDownloadListener(progressBack: IProgressCallback): TaskBuilder {
        holdActivityRef = progressBack
        return this
    }

    fun listener(progressBack: IProgressCallback): TaskBuilder = setDownloadListener(progressBack)


    fun start(): Task {
        val taskState = Download.instance.dispatchTool.getTaskState()
        if (lifecycleDestroyed) {
            task.cancelUrl = task.url
            return task
        }
        // 校验任务是否为无效任务
        if (taskState.isInvalidTask(task)) {
            holdActivityRef?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            return task
        }
        if (!task.overwrite && File(task.path.orEmpty()).exists()) {
            holdActivityRef?.onFail(ERROR_TARGET_FILE_EXISTS, task)
            return task
        }


        // 校验请求地址是否正在下载
        if (taskState.exitRunningUrl(task.url)) {
            return task
        }
        // 校验请求地址是否已经在任务队列
        if (taskState.exitWaitUrl(task.url)) {
            return task
        }

        // 下载任务是否启动断点续传
        if (taskState.isBreakpointContinuation(task)) {
            task.progressCallback = progressCallback
            Download.instance.dispatchTool.appendDownload(task)
            return task
        }
        holdActivityRef?.apply { HoldActivityCallbackMap.setProgressCallback(task, this) }
        task.progressCallback = progressCallback
        Download.instance.dispatchTool.download(task)
        return task
    }


}
