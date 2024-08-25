package com.itg.net.download.interfaces

import com.itg.net.download.data.Task

interface Dispatch {
    fun download(task: Task)
    fun appendDownload(task: Task)
}