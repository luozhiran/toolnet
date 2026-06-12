package com.itg.net.download.request


import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.ERROR_TAG_1
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
        builder.addHeader("RANGE", "bytes=${range}")
        return builder
    }

    private fun getLocalFileRange(fileSize:Long?):String{
        val file = File(task.path + ".tmp")
        val start = if (file.exists()) file.length() else 0L
        val end = (fileSize ?: 0L) - 1L
        return "${start}-${end}"
    }

    private fun breakpointRequest(range:String?){
        val okHttpCallback = OnDownloadListenerImpl(onResponse = { _, response ->
            if (response.code == 206) {
                handleResponse(response)
            } else {
                try {
                    failureCallback?.invoke(task, ERROR_TAG_1)
                } finally {
                    response.close()
                }
            }
        }, onFailure = { _, ioException ->
            failureCallback?.invoke(task,ioException.message.toString())
        })
        getBreakpointContinuationBuilder(task, range).send(okHttpCallback,task)
    }

    override fun start(){
        val okHttpCallback = OnDownloadListenerImpl(onResponse = { _, response ->
            val code = response.code
            if (code == 200) {
                val contentLength = response.body?.contentLength()
                response.close()
                breakpointRequest(getLocalFileRange(contentLength))
            } else {
                try {
                    failureCallback?.invoke(task,"请求失败：response.code=${code}")
                } finally {
                    response.close()
                }
            }
        }, onFailure = { _, iOException ->
            failureCallback?.invoke(task,iOException.message.toString())
        })
        getBuilder().send(okHttpCallback,task)
    }


}
