package com.itg.net.download.dispatcher

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.MSG_START_NEXT_DOWNLOAD
import com.itg.net.download.data.RESULT_DOWNLOAD_FAILED
import com.itg.net.download.data.RESULT_DOWNLOAD_SUCCESS
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.download.request.BreakpointContinuationRequest
import com.itg.net.download.request.DirectRequest
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool

class DispatchTool : Dispatch {

    private val taskStateInstance by lazy { TaskState() }

    @Volatile
    private var handler: Handler? = null

    init {
        val thread = HandlerThread("itg-net-download")
        thread.start()
        handler = Handler(thread.looper) { execNextDownloadRequest(it) }
    }

    override fun download(task: Task): Boolean {
        return when (taskStateInstance.scheduleTask(task)) {
            TaskState.ScheduleResult.RUNNING -> {
                startDirectDownload(task)
                true
            }
            TaskState.ScheduleResult.WAITING -> true
            TaskState.ScheduleResult.REJECTED -> false
        }
    }

    override fun appendDownload(task: Task): Boolean {
        return when (taskStateInstance.scheduleTask(task)) {
            TaskState.ScheduleResult.RUNNING -> {
                startBreakpointDownload(task)
                true
            }
            TaskState.ScheduleResult.WAITING -> true
            TaskState.ScheduleResult.REJECTED -> false
        }
    }

    fun continueDownload() {
        sendMsg(null, MSG_START_NEXT_DOWNLOAD)
    }

    fun getTaskState(): TaskState {
        return taskStateInstance
    }

    private fun downloadNextTask() {
        val task = taskStateInstance.pollNextTaskToRun() ?: return
        if (taskStateInstance.isBreakpointContinuation(task)) {
            startBreakpointDownload(task)
        } else {
            startDirectDownload(task)
        }
    }

    private fun retryDownloadTask(task: Task) {
        if (!taskStateInstance.exitRunningTask(task)) {
            downloadNextTask()
            return
        }
        if (taskStateInstance.isBreakpointContinuation(task)) {
            startBreakpointDownload(task)
        } else {
            startDirectDownload(task)
        }
    }

    private fun startDirectDownload(task: Task) {
        task.consumeDownloadAttempt()
        dispatchCallback { task.progressCallback?.onConnecting(task) }
        DirectRequest(task, taskStateInstance)
            .setFailCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }

    private fun startBreakpointDownload(task: Task) {
        task.consumeDownloadAttempt()
        dispatchCallback { task.progressCallback?.onConnecting(task) }
        BreakpointContinuationRequest(task, taskStateInstance)
            .setFailCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { tk, msg -> handleResult(tk, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }

    private fun handleResult(task: Task, type: Int, tag: String) {
        PrintLog.logd { "download task finished, result=$type" }
        taskStateInstance.debugPrint()
        if (type == RESULT_DOWNLOAD_FAILED) {
            if (tag == ERROR_DOWNLOAD_CANCELED || task.cancelUrl == task.url) {
                dispatchCallback { task.progressCallback?.onFail(ERROR_DOWNLOAD_CANCELED, task) }
                taskStateInstance.deleteRunningTask(task)
                PrintLog.logd("download task canceled and removed")
            } else if (task.canRetryDownload()) {
                dispatchCallback { task.progressCallback?.onFail(ERROR_DOWNLOAD_RETRYING, task) }
                PrintLog.logd { "download task retrying, remaining=${task.tryAgainCount}" }
            } else {
                dispatchCallback { task.progressCallback?.onFail(tag, task) }
                taskStateInstance.deleteRunningTask(task)
                PrintLog.logd("download task failed and removed")
            }
        } else if (type == RESULT_DOWNLOAD_SUCCESS) {
            dispatchCallback { task.progressCallback?.onProgress(task, true) }
            taskStateInstance.deleteRunningTask(task)
            PrintLog.logd("download task complete and removed")
        }
        taskStateInstance.debugPrint()
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
            retryDownloadTask(message.obj as Task)
        } else {
            downloadNextTask()
        }
        return true
    }

    private fun isAgainDownload(obj: Any?): Boolean {
        val task = obj as? Task ?: return false
        return task.canRetryDownload()
    }

    private fun dispatchCallback(callback: () -> Unit) {
        ThreadTool.runOnUIThread(Runnable { callback() })
    }
}
