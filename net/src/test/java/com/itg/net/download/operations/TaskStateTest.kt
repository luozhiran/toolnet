package com.itg.net.download.operations

import com.itg.net.download.data.Task
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateTest {

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
}
