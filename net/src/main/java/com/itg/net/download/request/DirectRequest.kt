package com.itg.net.download.request

import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState

class DirectRequest(private val task: Task, taskStateInstance: TaskState) : BaseRequest(task, taskStateInstance) {

    override fun start() {
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
        }, onFailure = { _, ioException ->
            failureCallback?.invoke(task, ioException.message.toString())
        })
        getBuilder().send(okHttpCallback, task)
    }
}
