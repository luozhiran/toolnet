package com.itg.net.download.operations

import com.itg.net.download.data.Task
import com.itg.net.Net
import org.junit.After
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
        }

        assertTrue(taskState.addRunningTask(task))
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
        }

        assertTrue(taskState.addRunningTask(task))
        assertFalse(taskState.runningQueueCanAcceptTask())

        Net.configure {
            maxConcurrentDownloads(2)
        }

        assertTrue(taskState.runningQueueCanAcceptTask())
    }
}
