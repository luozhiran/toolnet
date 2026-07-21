package com.itg.net.download.operations

import com.itg.net.Net
import com.itg.net.download.Download
import com.itg.net.download.DownloadTaskSession
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueStateTest {

    @After
    fun tearDown() {
        Net.configure {
            maxConcurrentDownloads(3)
        }
    }

    @Test
    fun cancelRunningTaskOnlyMarksTaskUntilRequestFinishes() {
        val queueState = DownloadQueueState()
        val task = task("https://example.com/file.zip")
        val session = session(task)

        assertEquals(DownloadQueueState.ScheduleResult.RUNNING, queueState.enqueue(session))
        assertTrue(queueState.markRunningTaskCanceled(task))

        assertTrue(queueState.containsRunning(task))
        assertTrue(queueState.isInvalidTask(task))

        queueState.removeRunning(task)
        assertFalse(queueState.containsRunning(task))
    }

    @Test
    fun runningQueueUsesLatestConfiguredMaxDownloadCount() {
        val queueState = DownloadQueueState()
        Net.configure {
            maxConcurrentDownloads(1)
        }
        val session = session(task("https://example.com/file.zip"))

        assertEquals(DownloadQueueState.ScheduleResult.RUNNING, queueState.enqueue(session))
        assertFalse(queueState.runningQueueCanAcceptTask())

        Net.configure {
            maxConcurrentDownloads(2)
        }

        assertTrue(queueState.runningQueueCanAcceptTask())
    }

    @Test
    fun invalidTaskRequiresSavePath() {
        val queueState = DownloadQueueState()
        val task = Task().apply {
            url = "https://example.com/file.zip"
        }

        assertTrue(queueState.isInvalidTask(task))
    }

    @Test
    fun rejectedDuplicateTaskDoesNotClearExistingUrlListeners() {
        val queueState = DownloadQueueState()
        val firstTask = task("https://example.com/file.zip")
        val duplicateTask = Task().apply {
            url = firstTask.url
            path = "build/tmp/duplicate.zip"
        }
        val listener = object : AbstractProgressCallback() {}

        Download.instance.listenerRegistry.addTaskListener(firstTask, listener)
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(firstTask))

        assertEquals(DownloadQueueState.ScheduleResult.RUNNING, queueState.enqueue(session(firstTask)))
        assertEquals(DownloadQueueState.ScheduleResult.REJECTED, queueState.enqueue(session(duplicateTask)))
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(firstTask))

        queueState.removeRunning(firstTask)
        Download.instance.listenerRegistry.removeTaskListeners(firstTask)
    }

    @Test
    fun removeRunningOnlyRemovesRunningState() {
        val queueState = DownloadQueueState()
        val task = task("https://example.com/running-listener.zip")
        val listener = object : AbstractProgressCallback() {}
        val session = session(task)

        assertEquals(DownloadQueueState.ScheduleResult.RUNNING, queueState.enqueue(session))
        Download.instance.listenerRegistry.addTaskListener(task, listener)

        queueState.removeRunning(session)

        assertFalse(queueState.containsRunning(session))
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(task))

        Download.instance.listenerRegistry.removeTaskListeners(task)
    }

    @Test
    fun cancelWaitingTaskNotifiesFinishAndClearsListeners() {
        val queueState = DownloadQueueState()
        val task = task("https://example.com/waiting.zip")
        val events = mutableListOf<String>()
        val taskCallback = object : AbstractProgressCallback() {
            override fun onFail(error: String?, task: Task) {
                events.add("fail:$error")
            }

            override fun onFinish(task: Task) {
                events.add("finish")
            }
        }
        val waitingSession = DownloadTaskSession(task, null, taskCallback, null)
        waitingSession.startListening()

        Net.configure {
            maxConcurrentDownloads(1)
        }
        assertEquals(
            DownloadQueueState.ScheduleResult.RUNNING,
            queueState.enqueue(session(task("https://example.com/running.zip")))
        )
        assertEquals(DownloadQueueState.ScheduleResult.WAITING, queueState.enqueue(waitingSession))
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(task))

        queueState.cancelWaiting(task, ERROR_DOWNLOAD_CANCELED)

        assertEquals(listOf("fail:$ERROR_DOWNLOAD_CANCELED", "finish"), events)
        assertEquals(task.url, task.cancelUrl)
        assertEquals(0, Download.instance.listenerRegistry.listenerCount(task))
        assertFalse(queueState.containsWaiting(task))
    }

    private fun task(url: String): Task {
        return Task().apply {
            this.url = url
            path = "build/tmp/file.zip"
        }
    }

    private fun session(task: Task): DownloadTaskSession {
        return DownloadTaskSession(task, null, null, null)
    }
}