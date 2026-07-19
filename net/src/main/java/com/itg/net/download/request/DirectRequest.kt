package com.itg.net.download.request

import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.monitor.MonitorEvent
import com.itg.net.util.PrintLog

class DirectRequest(private val task: Task, taskStateInstance: TaskState) : BaseRequest(task, taskStateInstance) {

    override fun start() {
        task.startTime = System.currentTimeMillis()
        if (isTaskCanceled()) {
            reportDownloadEvent(0, MonitorEvent.ErrorType.CANCELLED, ERROR_DOWNLOAD_CANCELED, null)
            failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
            return
        }
        val okHttpCallback = DownloadRequestCallback(onResponse = { _, response ->
            val code = response.code
            if (code == 200) {
                PrintLog.logd { "下载成功 ${task.url} " }
                handleResponse(response)
            } else {
                response.use {
                    val message = "请求失败：response.code=${code}"
                    PrintLog.logd { "下载失败 ${task.url} $message" }
                    failureCallback?.invoke(task, message)
                }
            }
        }, onFailure = { call, ioException ->
            task.endTime = System.currentTimeMillis()
            val message = if (call.isCanceled() || isTaskCanceled()) {
                ERROR_DOWNLOAD_CANCELED
            } else {
                ioException.message ?: ioException.javaClass.simpleName
            }
            PrintLog.logd { "下载失败 ${task.url} $message" }
            failureCallback?.invoke(task, message)
        })
        getBuilder().send(okHttpCallback, task)
    }
}
