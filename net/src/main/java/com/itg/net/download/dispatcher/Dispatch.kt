package com.itg.net.download.dispatcher

import com.itg.net.download.data.Task

interface Dispatch {
    fun download(task: Task)
    fun appendDownload(task: Task)
}
