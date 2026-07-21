package com.itg.net.download

import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskSessionTest {

    @Test
    fun completeFinishesAndReleasesTaskListeners() {
        val task = task("https://example.com/complete.zip")
        val events = mutableListOf<String>()
        val listener = recordingListener(events)
        val session = DownloadTaskSession(task, null, listener, null)

        assertTrue(session.prepare())
        session.startListening()
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(task))
        assertSame(session, task.progressCallback)

        session.onProgress(task, complete = true)

        assertEquals(listOf("progress:true", "finish"), events)
        assertEquals(0, Download.instance.listenerRegistry.listenerCount(task))
        assertNull(task.progressCallback)
    }

    @Test
    fun retryingFailureKeepsTaskListenersForNextAttempt() {
        val task = task("https://example.com/retry.zip")
        val events = mutableListOf<String>()
        val listener = recordingListener(events)
        val session = DownloadTaskSession(task, null, listener, null)

        session.startListening()

        session.onFail(ERROR_DOWNLOAD_RETRYING, task)

        assertEquals(emptyList<String>(), events)
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(task))
        assertSame(session, task.progressCallback)

        session.onProgress(task, complete = true)
    }


    @Test
    fun finishBeforeStartNotifiesListenersWithoutRegistryRegistration() {
        val task = task("https://example.com/invalid.zip")
        val events = mutableListOf<String>()
        val listener = recordingListener(events)
        val session = DownloadTaskSession(task, null, listener, null)

        session.finishBeforeStart("invalid task")

        assertEquals(listOf("fail:invalid task", "finish"), events)
        assertEquals(0, Download.instance.listenerRegistry.listenerCount(task))
        assertNull(task.progressCallback)
    }

    private fun task(url: String): Task {
        return Task().apply {
            this.url = url
            path = "build/tmp/download-session.zip"
        }
    }

    private fun recordingListener(events: MutableList<String>): AbstractProgressCallback {
        return object : AbstractProgressCallback() {
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