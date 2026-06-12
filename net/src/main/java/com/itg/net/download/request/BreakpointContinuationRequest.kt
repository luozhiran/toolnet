package com.itg.net.download.request


import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.ERROR_RANGE_NOT_SUPPORTED
import com.itg.net.download.data.Task
import com.itg.net.download.implement.OnDownloadListenerImpl
import com.itg.net.download.operations.TaskState
import com.itg.net.reqeust.base.ParamsBuilder
import java.io.*

/**
 * 断点续传下载
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
        val okHttpCallback = OnDownloadListenerImpl(onResponse = { _, response ->
            when {
                response.code == 206 -> handleResponse(response)
                response.code == 200 && start == 0L -> handleResponse(response)
                else -> {
                    try {
                        failureCallback?.invoke(task, ERROR_RANGE_NOT_SUPPORTED)
                    } finally {
                        response.close()
                    }
                }
            }
        }, onFailure = { _, ioException ->
            failureCallback?.invoke(task,ioException.message.toString())
        })
        getBreakpointContinuationBuilder(task, "${start}-").send(okHttpCallback,task)
    }

    override fun start(){
        breakpointRequest(getLocalFileStart())
    }


}
