package com.itg.net.download.dispatcher

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.itg.net.download.DownloadTaskSession
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.MSG_START_NEXT_DOWNLOAD
import com.itg.net.download.data.RESULT_DOWNLOAD_FAILED
import com.itg.net.download.data.RESULT_DOWNLOAD_SUCCESS
import com.itg.net.download.operations.DownloadQueueState
import com.itg.net.download.request.BreakpointContinuationRequest
import com.itg.net.download.request.DirectRequest
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool

internal class DownloadScheduler : Dispatch {

    private val queueStateHolder by lazy { DownloadQueueState() }

    @Volatile
    private var handler: Handler? = null

    init {
        val thread = HandlerThread("itg-net-download")
        thread.start()
        handler = Handler(thread.looper) { execNextDownloadRequest(it) }
    }

    override fun download(session: DownloadTaskSession): Boolean {
        return when (queueStateHolder.enqueue(session)) {
            DownloadQueueState.ScheduleResult.RUNNING -> {
                startDirectDownload(session)
                true
            }
            DownloadQueueState.ScheduleResult.WAITING -> true
            DownloadQueueState.ScheduleResult.REJECTED -> false
        }
    }

    override fun appendDownload(session: DownloadTaskSession): Boolean {
        return when (queueStateHolder.enqueue(session)) {
            DownloadQueueState.ScheduleResult.RUNNING -> {
                startBreakpointDownload(session)
                true
            }
            DownloadQueueState.ScheduleResult.WAITING -> true
            DownloadQueueState.ScheduleResult.REJECTED -> false
        }
    }

    fun continueDownload() {
        sendMsg(null, MSG_START_NEXT_DOWNLOAD)
    }

    fun getQueueState(): DownloadQueueState {
        return queueStateHolder
    }

    private fun downloadNextTask() {
        val session = queueStateHolder.pollNextSessionToRun() ?: return
        if (queueStateHolder.isBreakpointContinuation(session.task)) {
            startBreakpointDownload(session)
        } else {
            startDirectDownload(session)
        }
    }

    private fun retryDownloadTask(session: DownloadTaskSession) {
        if (!queueStateHolder.containsRunning(session)) {
            downloadNextTask()
            return
        }
        if (queueStateHolder.isBreakpointContinuation(session.task)) {
            startBreakpointDownload(session)
        } else {
            startDirectDownload(session)
        }
    }

    private fun startDirectDownload(session: DownloadTaskSession) {
        val task = session.task
        task.consumeDownloadAttempt()
        dispatchCallback { session.onConnecting() }
        DirectRequest(task, queueStateHolder)
            .setFailCallback { _, msg -> handleResult(session, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { _, msg -> handleResult(session, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }

    private fun startBreakpointDownload(session: DownloadTaskSession) {
        val task = session.task
        task.consumeDownloadAttempt()
        dispatchCallback { session.onConnecting() }
        BreakpointContinuationRequest(task, queueStateHolder)
            .setFailCallback { _, msg -> handleResult(session, RESULT_DOWNLOAD_FAILED, msg) }
            .setSuccessCallback { _, msg -> handleResult(session, RESULT_DOWNLOAD_SUCCESS, msg) }
            .start()
    }

    private fun handleResult(session: DownloadTaskSession, type: Int, tag: String) {
        PrintLog.logd { "download task finished, result=$type" }
        queueStateHolder.debugPrint()
        if (type == RESULT_DOWNLOAD_FAILED) {
            if (tag == ERROR_DOWNLOAD_CANCELED || session.task.cancelUrl == session.task.url) {
                dispatchCallback { session.cancel(ERROR_DOWNLOAD_CANCELED) }
                queueStateHolder.removeRunning(session)
                PrintLog.logd("download task canceled and removed")
            } else if (session.task.canRetryDownload()) {
                dispatchCallback { session.onRetry(ERROR_DOWNLOAD_RETRYING) }
                PrintLog.logd { "download task retrying, remaining=${session.task.tryAgainCount}" }
            } else {
                dispatchCallback { session.onFailure(tag) }
                queueStateHolder.removeRunning(session)
                PrintLog.logd("download task failed and removed")
            }
        } else if (type == RESULT_DOWNLOAD_SUCCESS) {
            dispatchCallback { session.onSuccess() }
            queueStateHolder.removeRunning(session)
            PrintLog.logd("download task complete and removed")
        }
        queueStateHolder.debugPrint()
        sendMsg(session, type)
    }

    private fun sendMsg(session: DownloadTaskSession?, type: Int) {
        val msg = Message.obtain()
        msg.obj = session
        msg.what = type
        handler?.sendMessage(msg)
    }

    private fun execNextDownloadRequest(message: Message): Boolean {
        if (message.what == RESULT_DOWNLOAD_FAILED && isAgainDownload(message.obj)) {
            retryDownloadTask(message.obj as DownloadTaskSession)
        } else {
            downloadNextTask()
        }
        return true
    }

    private fun isAgainDownload(obj: Any?): Boolean {
        val session = obj as? DownloadTaskSession ?: return false
        return session.task.canRetryDownload()
    }

    private fun dispatchCallback(callback: () -> Unit) {
        ThreadTool.runOnUIThread(Runnable { callback() })
    }
}

