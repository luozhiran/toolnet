package com.itg.net.download.request

import com.itg.net.download.data.Task
import com.itg.net.download.implement.OnDownloadListenerImpl
import com.itg.net.download.operations.TaskState

/**
 * 直接下载任务
 * @property task DTask
 * @constructor
 */
class DirectRequest(private val task: Task, taskStateInstance: TaskState) : BaseRequest(task,taskStateInstance) {

    override fun start(){
        val okHttpCallback = OnDownloadListenerImpl(onResponse = { _, response ->
            val code = response.code
            if (code == 200) {
                handleResponse(response)
            } else {
                failureCallback?.invoke(task,"请求失败：response.code=${code}")
            }
        }, onFailure = { _, ioException ->
            failureCallback?.invoke(task,ioException.message.toString())
        })
        getBuilder().send(okHttpCallback,task)
    }
}