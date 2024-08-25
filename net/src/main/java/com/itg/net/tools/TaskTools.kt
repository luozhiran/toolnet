package com.itg.net.tools

import com.itg.net.download.data.Task

object TaskTools {

    @JvmStatic
    fun getDownloadProgress(task: Task):Int{
        return (100 * task.downloadSize.toDouble() / task.contentLength.toDouble()).toInt()
    }
}