package com.itg.net.download.request


import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_UN_FOUND_RESOURCE
import com.itg.net.download.data.Task
import com.itg.net.download.operations.DownloadQueueState
import com.itg.net.monitor.MonitorEvent
import com.itg.net.request.base.ParamsBuilder
import java.io.*

/**
 * @property task DTask
 * @constructor
 */
internal class BreakpointContinuationRequest(private val task: Task, taskStateInstance: DownloadQueueState) : BaseRequest(task,taskStateInstance) {

    private fun getBreakpointContinuationBuilder(range:String?) : ParamsBuilder {
        val builder = getBuilder()
        builder.addHeader("Range", "bytes=${range}")
        return builder
    }

    private fun getLocalFileStart(): Long {
        val file = File(task.path + ".tmp")
        return if (file.exists()) file.length() else 0L
    }

    private fun breakpointRequest(start: Long){
        task.startTime = System.currentTimeMillis()
        if (isTaskCanceled()) {
            reportDownloadEvent(0, MonitorEvent.ErrorType.CANCELLED, ERROR_DOWNLOAD_CANCELED, null)
            failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
            return
        }
        val okHttpCallback = DownloadRequestCallback(onResponse = { _, response ->
            when {
                response.code == 206 -> handleResponse(response)
                response.code == 200 && start == 0L -> handleResponse(response)
                else -> {
                    response.use {
                        failureCallback?.invoke(task, ERROR_UN_FOUND_RESOURCE)
                    }
                }
            }
        }, onFailure = { call, ioException ->
            task.endTime = System.currentTimeMillis()
            val message = if (call.isCanceled() || isTaskCanceled()) {
                ERROR_DOWNLOAD_CANCELED
            } else {
                ioException.message ?: ioException.javaClass.simpleName
            }
            failureCallback?.invoke(task, message)
        })
        getBreakpointContinuationBuilder("${start}-").send(okHttpCallback,task)
    }

    override fun start(){
        breakpointRequest(getLocalFileStart())
    }


}
