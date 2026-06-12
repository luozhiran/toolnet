package com.itg.net.util

import com.itg.net.download.data.Task

object TaskTools {

    @JvmStatic
    fun getDownloadProgress(task: Task):Int{
        if (task.contentLength <= 0L || task.downloadSize <= 0L) return 0
        return (100 * task.downloadSize.toDouble() / task.contentLength.toDouble())
            .toInt()
            .coerceIn(0, 100)
    }
}
