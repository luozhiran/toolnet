package com.itg.net.download.interfaces

import com.itg.net.download.data.Task

interface IProgressCallback {
    /**
     * 与服务器建立连接过程中
     *
     * @param task
     */
    fun onConnecting(task: Task)

    fun onProgress(task: Task, complete:Boolean)

    fun onFail(error: String?, task: Task)
}