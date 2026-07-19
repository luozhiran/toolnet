package com.itg.net.download.operations

import com.itg.net.download.Download
import com.itg.net.download.data.Task

internal object DownloadEventPublisher {

    fun connecting(task: Task?) {
        if (task == null) return
        publish(DownloadEvent.Connecting(task))
    }

    fun progress(task: Task?) {
        if (task == null) return
        publish(DownloadEvent.Progress(task, complete = false))
    }

    fun complete(task: Task) {
        publish(DownloadEvent.Progress(task, complete = true))
    }

    fun failed(task: Task, error: String?) {
        publish(DownloadEvent.Failed(task, error))
        Download.instance.dispatchTool.getTaskState().debugPrint()
        Download.instance.listenerRegistry.debugPrint()
    }

    fun finished(task: Task) {
        publish(DownloadEvent.Finished(task))
    }

    private fun publish(event: DownloadEvent) {
        Download.instance.publishDownloadEvent(event)
    }
}
