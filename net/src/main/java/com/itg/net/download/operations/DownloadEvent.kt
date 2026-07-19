package com.itg.net.download.operations

import com.itg.net.download.data.Task

internal sealed class DownloadEvent {
    abstract val task: Task

    data class Connecting(override val task: Task) : DownloadEvent()

    data class Progress(
        override val task: Task,
        val complete: Boolean
    ) : DownloadEvent()

    data class Failed(
        override val task: Task,
        val error: String?
    ) : DownloadEvent()

    data class Finished(override val task: Task) : DownloadEvent()
}
