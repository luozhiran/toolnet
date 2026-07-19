package com.itg.net.download.operations

import com.itg.net.download.data.Task
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.Download
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateTest {

    @After
    fun tearDown() {
        Net.configure {
            maxConcurrentDownloads(3)
        }
    }

    @Test
    fun cancelRunningTaskOnlyMarksTaskUntilRequestFinishes() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/file.zip"
            path = "build/tmp/file.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(task))
        assertTrue(taskState.markRunningTaskCanceled(task))

        assertTrue(taskState.exitRunningTask(task))
        assertTrue(taskState.isInvalidTask(task))

        taskState.deleteRunningTask(task)
        assertFalse(taskState.exitRunningTask(task))
    }

    @Test
    fun runningQueueUsesLatestConfiguredMaxDownloadCount() {
        val taskState = TaskState()
        Net.configure {
            maxConcurrentDownloads(1)
        }
        val task = Task().apply {
            url = "https://example.com/file.zip"
            path = "build/tmp/file.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(task))
        assertFalse(taskState.runningQueueCanAcceptTask())

        Net.configure {
            maxConcurrentDownloads(2)
        }

        assertTrue(taskState.runningQueueCanAcceptTask())
    }

    @Test
    fun invalidTaskRequiresSavePath() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/file.zip"
        }

        assertTrue(taskState.isInvalidTask(task))
    }

    @Test
    fun rejectedDuplicateTaskDoesNotClearExistingUrlListeners() {
        val taskState = TaskState()
        val firstTask = Task().apply {
            url = "https://example.com/file.zip"
            path = "build/tmp/file.zip"
        }
        val duplicateTask = Task().apply {
            url = firstTask.url
            path = "build/tmp/duplicate.zip"
        }
        val listener = object : AbstractProgressCallback() {}

        Download.instance.listenerRegistry.addTaskListener(firstTask, listener)
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(firstTask))

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(firstTask))
        assertEquals(TaskState.ScheduleResult.REJECTED, taskState.scheduleTask(duplicateTask))
        assertEquals(1, Download.instance.listenerRegistry.listenerCount(firstTask))

        taskState.deleteRunningTask(firstTask)
    }

    @Test
    fun cancelWaitingTaskNotifiesFinishAndClearsListeners() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/waiting.zip"
            path = "build/tmp/waiting.zip"
        }
        val events = mutableListOf<String>()
        val taskCallback = object : AbstractProgressCallback() {
            override fun onFail(error: String?, task: Task) {
                events.add("fail:$error")
            }

            override fun onFinish(task: Task) {
                events.add("finish")
            }
        }
        val registryListener = object : AbstractProgressCallback() {}
        task.progressCallback = taskCallback

        Net.configure {
            maxConcurrentDownloads(1)
        }
        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(Task().apply {
            url = "https://example.com/running.zip"
            path = "build/tmp/running.zip"
        }))
        assertEquals(TaskState.ScheduleResult.WAITING, taskState.scheduleTask(task))
        Download.instance.listenerRegistry.addTaskListener(task, registryListener)

        taskState.cancelWaitTask(task, ERROR_DOWNLOAD_CANCELED)

        assertEquals(listOf("fail:$ERROR_DOWNLOAD_CANCELED", "finish"), events)
        assertEquals(task.url, task.cancelUrl)
        assertEquals(0, Download.instance.listenerRegistry.listenerCount(task))
        assertFalse(taskState.exitWaitTask(task))
    }
}
