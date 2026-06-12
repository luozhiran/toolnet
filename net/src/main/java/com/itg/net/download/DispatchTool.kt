package com.itg.net.download

import android.os.HandlerThread
import android.os.Message
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.LockData
import com.itg.net.download.data.MSG_START_NEXT_DOWNLOAD
import com.itg.net.download.data.RESULT_DOWNLOAD_FAILED
import com.itg.net.download.data.RESULT_DOWNLOAD_SUCCESS
import com.itg.net.download.data.Task
import com.itg.net.download.implement.ReceiverHandler
import com.itg.net.download.operations.TaskState
import com.itg.net.download.interfaces.Dispatch
import com.itg.net.download.request.BreakpointContinuationRequest
import com.itg.net.download.request.DirectRequest
import com.itg.net.tools.TaskTools


class DispatchTool : Dispatch {

    private val taskStateInstance by lazy { TaskState() }

    @Volatile
    private var handler: ReceiverHandler? = null

    private val lock by lazy { LockData() }

    init {
        val thread = HandlerThread("itg-net-download")
        thread.start()
        handler = ReceiverHandler(thread.looper) { execNextDownloadRequest(it) }
    }

    /**
     * 从任务队列中获取下载任务
     */
    private fun downloadNextTask() {
        synchronized(lock) {
            if (taskStateInstance.runningQueueCanAcceptTask()) {
                taskStateInstance.getTaskFromWaitQueue(null)?.apply {
                    if (taskStateInstance.isBreakpointContinuation(this)) {
                        immediatelyBreakpointContinuationRequest(this)
                    } else {
                        immediatelyDownload(this)
                    }
                }
            }
        }
    }

    /**
     * 任务有重试次数，在一次上次失败的任务
     */
    private fun tryAgainDownloadTask(preTask: Task) {
        if (taskStateInstance.exitRunningTask(preTask)) {
            if (taskStateInstance.isBreakpointContinuation(preTask)) {
                logisticsBreakpointContinuation(preTask)
            } else {
                logisticsDownload(preTask)
            }
        } else {
            downloadNextTask()
        }
    }

    override fun download(task: Task) {
        if (taskStateInstance.runningQueueCanAcceptTask()) {
            synchronized(lock) {
                if (taskStateInstance.runningQueueCanAcceptTask()) {
                    immediatelyDownload(task)
                    return
                }
            }
        }
        pendingDownload(task)
    }


    /**
     * 立刻下载数据
     * @param task DTask
     */
    private fun immediatelyDownload(task: Task) {
        synchronized(lock) {
            // 从等待队列中取得下载任务
            val downloadTask = taskStateInstance.getTaskFromWaitQueue(task) ?: return
            // 把任务存储到下载队列
            if (!taskStateInstance.addRunningTask(downloadTask)) return
            // 开始下载
            logisticsDownload(downloadTask)
        }
    }

    /**
     * 转发下载
     * @param task DTask
     */
    private fun logisticsDownload(task: Task) {
        task.tryAgainCount = task.tryAgainCount - 1
        task.iProgressCallback?.onConnecting(task)
        DirectRequest(task, taskStateInstance)
            .setFailCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }

    /**
     * 等待未来下载数据
     */
    private fun pendingDownload(task: Task) {
        if (taskStateInstance.addWaitTask(task) && taskStateInstance.canNextTask()) {
            sendMsg(null, MSG_START_NEXT_DOWNLOAD)
        }
    }

    /**
     * 断点续传下载
     * @param task DTask
     */
    override fun appendDownload(task: Task) {
        if (taskStateInstance.runningQueueCanAcceptTask()) {
            synchronized(lock) {
                if (taskStateInstance.runningQueueCanAcceptTask()) {
                    immediatelyBreakpointContinuationRequest(task)
                    return
                }
            }
        }
        pendingDownload(task)
    }

    /**
     * 发起断点位置请求
     * @param task DTask
     */
    private fun immediatelyBreakpointContinuationRequest(task: Task) {
        synchronized(lock) {
            // 从等待队列中取得下载任务
            val downloadTask = taskStateInstance.getTaskFromWaitQueue(task) ?: return
            // 把任务存储到下载队列
            if (!taskStateInstance.addRunningTask(downloadTask)) return
            logisticsBreakpointContinuation(downloadTask)
        }
    }

    /**
     * 转发断点续传
     * @param task DTask
     */
    private fun logisticsBreakpointContinuation(task: Task) {
        task.tryAgainCount = task.tryAgainCount - 1
        task.iProgressCallback?.onConnecting(task)
        BreakpointContinuationRequest(task, taskStateInstance)
            .setFailCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }


    private fun handleResult(task: Task, type: Int, tag: String) {
        if (type == RESULT_DOWNLOAD_FAILED) {
            if (!task.cancelUrl.isNullOrBlank() && task.cancelUrl == task.url) {
                task.iProgressCallback?.onFail(ERROR_DOWNLOAD_CANCELED, task)
                taskStateInstance.deleteRunningTask(task)
            } else if (task.tryAgainCount > 0) {
                task.iProgressCallback?.onFail(ERROR_DOWNLOAD_RETRYING, task)
            } else {
                task.iProgressCallback?.onFail(tag, task)
                taskStateInstance.deleteRunningTask(task)
            }
        } else if (type == RESULT_DOWNLOAD_SUCCESS) {
            val progress = TaskTools.getDownloadProgress(task)
            task.iProgressCallback?.onProgress(task, progress == 100)
            taskStateInstance.deleteRunningTask(task)
        }
        sendMsg(task, type)
    }

    private fun sendMsg(task: Task?, type: Int) {
        val msg = Message.obtain()
        msg.obj = task
        msg.what = type
        handler?.sendMessage(msg)
    }

    private fun execNextDownloadRequest(message: Message): Boolean {
        if (message.what == RESULT_DOWNLOAD_FAILED && isAgainDownload(message.obj)) {
            tryAgainDownloadTask(message.obj as Task)
        } else {
            downloadNextTask()
        }
        return true
    }

    private fun isAgainDownload(obj: Any?): Boolean {
        val task = obj as? Task ?: return false
        return task.tryAgainCount > 0
    }

    fun continueDownload() {
        sendMsg(null, MSG_START_NEXT_DOWNLOAD)
    }

    fun getTaskState(): TaskState {
        return taskStateInstance
    }

}
