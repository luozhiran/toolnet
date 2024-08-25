package com.itg.net.download

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itg.net.Download
import com.itg.net.download.data.DEBUG_TAG
import com.itg.net.download.data.ERROR_TAG_7
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.download.operations.DownloadEndNotify
import com.itg.net.download.operations.HoldActivityCallbackMap

class TaskBuilder {
    private val task by lazy { Task() }
    private val iProgressCallback by lazy {
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
                    Log.e(DEBUG_TAG,"重新开始下载")
                    return
                }
                DownloadEndNotify.failNotify(task, error)
            }

        }
    }

    // 持有activity引用的回调
    private var holdActivityRef: IProgressCallback? = null

    fun path(path: String): TaskBuilder {
        task.path = path
        return this
    }

    fun url(url: String): TaskBuilder {
        task.url = url
        return this
    }

    fun tryAgainCount(count: Int): TaskBuilder {
        task.tryAgainCount = count
        return this
    }

    //自动移除持有activity引用的回调,无法取消下载任务(需要调用取消方法，取消下载任务)
    fun autoRemoveActivity(activity: FragmentActivity): TaskBuilder {
        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    holdActivityRef?.apply {
                        HoldActivityCallbackMap.removeProgressCallback(
                            task,
                            this
                        )
                    }
                    activity.lifecycle.removeObserver(this)
                }
            }
        })
        return this
    }

    fun setDownloadListener(progressBack: IProgressCallback): TaskBuilder {
        holdActivityRef = progressBack
        return this
    }


    fun start(): Task {
        val taskState = Download.instance.dispatchTool.getTaskState()
        // 校验任务是否为无效任务
        if (taskState.isInvalidTask(task)) {
            holdActivityRef?.onFail(ERROR_TAG_7, task)
            return task
        }
        holdActivityRef?.apply { HoldActivityCallbackMap.setProgressCallback(task, this) }
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
            task.iProgressCallback = iProgressCallback
            Download.instance.dispatchTool.appendDownload(task)
            return task
        }
        task.iProgressCallback = iProgressCallback
        Download.instance.dispatchTool.download(task)
        return task
    }


}