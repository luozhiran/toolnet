package com.itg.net.download.request


import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_UN_FOUND_RESOURCE
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.request.base.ParamsBuilder
import java.io.*

/**
 * @property task DTask
 * @constructor
 */
class BreakpointContinuationRequest(private val task: Task, taskStateInstance: TaskState) : BaseRequest(task,taskStateInstance) {

    private fun getBreakpointContinuationBuilder(task: Task, range:String?) : ParamsBuilder {
        val builder = Net.instance.builder(ModeType.Get).url(task.url)
        builder.addHeader("Range", "bytes=${range}")
        return builder
    }

    private fun getLocalFileStart(): Long {
        val file = File(task.path + ".tmp")
        return if (file.exists()) file.length() else 0L
    }

    private fun breakpointRequest(start: Long){
        if (isTaskCanceled()) {
            failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
            return
        }
        val okHttpCallback = DownloadRequestCallback(onResponse = { _, response ->
            when {
                response.code == 206 -> handleResponse(response)
                response.code == 200 && start == 0L -> handleResponse(response)
                else -> {
                    response.use { response ->
                        failureCallback?.invoke(task, ERROR_UN_FOUND_RESOURCE)
                    }
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
        getBreakpointContinuationBuilder(task, "${start}-").send(okHttpCallback,task)
    }

    override fun start(){
        breakpointRequest(getLocalFileStart())
    }


}
