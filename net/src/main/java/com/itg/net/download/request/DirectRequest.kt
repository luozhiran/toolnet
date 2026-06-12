package com.itg.net.download.request

import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState

class DirectRequest(private val task: Task, taskStateInstance: TaskState) : BaseRequest(task, taskStateInstance) {

    override fun start() {
        if (isTaskCanceled()) {
            failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
            return
        }
        val okHttpCallback = DownloadRequestCallback(onResponse = { _, response ->
            val code = response.code
            if (code == 200) {
                handleResponse(response)
            } else {
                try {
                    failureCallback?.invoke(task,"请求失败：response.code=${code}")
                } finally {
                    response.close()
                }
            }
        }, onFailure = { call, ioException ->
            val message = if (call.isCanceled() || isTaskCanceled()) {
                ERROR_DOWNLOAD_CANCELED
            } else {
                ioException.message.toString()
            }
            failureCallback?.invoke(task, message)
        })
        getBuilder().send(okHttpCallback, task)
    }
}
