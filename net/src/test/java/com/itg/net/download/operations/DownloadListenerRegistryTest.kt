package com.itg.net.download.operations

import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadListenerRegistryTest {

    @Test
    fun dispatchesEventsToGlobalAndTaskListeners() {
        val registry = DownloadListenerRegistry()
        val task = task("https://example.com/a.zip")
        val globalEvents = mutableListOf<String>()
        val taskEvents = mutableListOf<String>()
        val globalListener = recordingListener(globalEvents)
        val taskListener = recordingListener(taskEvents)

        registry.addGlobal(globalListener)
        registry.addTaskListener(task, taskListener)

        registry.dispatch(DownloadEvent.Connecting(task))
        registry.dispatch(DownloadEvent.Progress(task, complete = false))
        registry.dispatch(DownloadEvent.Progress(task, complete = true))
        registry.dispatch(DownloadEvent.Finished(task))

        assertEquals(listOf("connecting", "progress:false", "progress:true", "finish"), globalEvents)
        assertEquals(globalEvents, taskEvents)
    }

    @Test
    fun failedEventWithPartialContentDispatchesProgressBeforeFailure() {
        val registry = DownloadListenerRegistry()
        val task = task("https://example.com/a.zip").apply {
            contentLength = 100
            downloadSize = 50
        }
        val events = mutableListOf<String>()
        val listener = recordingListener(events)

        registry.addTaskListener(task, listener)
        registry.dispatch(DownloadEvent.Failed(task, "network error"))

        assertEquals(listOf("progress:false", "fail:network error"), events)
    }

    @Test
    fun canceledFailureDoesNotDispatchProgressSnapshot() {
        val registry = DownloadListenerRegistry()
        val task = task("https://example.com/a.zip").apply {
            contentLength = 100
            downloadSize = 50
        }
        val events = mutableListOf<String>()
        val listener = recordingListener(events)

        registry.addTaskListener(task, listener)
        registry.dispatch(DownloadEvent.Failed(task, ERROR_DOWNLOAD_CANCELED))

        assertEquals(listOf("fail:$ERROR_DOWNLOAD_CANCELED"), events)
    }

    @Test
    fun removesTaskListenersByTaskOrSpecificListener() {
        val registry = DownloadListenerRegistry()
        val task = task("https://example.com/a.zip")
        val first = recordingListener(mutableListOf())
        val second = recordingListener(mutableListOf())

        registry.addTaskListener(task, first)
        registry.addTaskListener(task, second)
        assertEquals(2, registry.listenerCount(task))

        registry.removeTaskListener(task, first)
        assertEquals(1, registry.listenerCount(task))

        registry.removeTaskListeners(task)
        assertEquals(0, registry.listenerCount(task))
    }

    @Test
    fun finishedEventRemovesTaskListenersAfterDispatch() {
        val registry = DownloadListenerRegistry()
        val task = task("https://example.com/a.zip")
        val events = mutableListOf<String>()
        val listener = recordingListener(events)

        registry.addTaskListener(task, listener)
        registry.dispatch(DownloadEvent.Finished(task))

        assertEquals(listOf("finish"), events)
        assertEquals(0, registry.listenerCount(task))
    }

    private fun task(url: String): Task {
        return Task().apply {
            this.url = url
            path = "build/tmp/a.zip"
        }
    }

    private fun recordingListener(events: MutableList<String>): AbstractProgressCallback {
        return object : AbstractProgressCallback() {
            override fun onConnecting(task: Task) {
                events.add("connecting")
            }

            override fun onProgress(task: Task, complete: Boolean) {
                events.add("progress:$complete")
            }

            override fun onFail(error: String?, task: Task) {
                events.add("fail:$error")
            }

            override fun onFinish(task: Task) {
                events.add("finish")
            }
        }
    }
}
