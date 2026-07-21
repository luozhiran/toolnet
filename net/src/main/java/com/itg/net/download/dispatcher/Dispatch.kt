package com.itg.net.download.dispatcher

import com.itg.net.download.DownloadTaskSession

internal interface Dispatch {
    fun download(session: DownloadTaskSession): Boolean
    fun appendDownload(session: DownloadTaskSession): Boolean
}