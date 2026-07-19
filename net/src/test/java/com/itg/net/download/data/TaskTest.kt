package com.itg.net.download.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TaskTest {

    @Test
    fun consumeDownloadAttemptIsAtomicAndNeverNegative() {
        val task = Task().apply {
            tryAgainCount = 100
        }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(200)

        repeat(200) {
            executor.execute {
                start.await()
                task.consumeDownloadAttempt()
                done.countDown()
            }
        }

        start.countDown()

        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(0, task.tryAgainCount)
        assertFalse(task.canRetryDownload())
    }

    @Test
    fun canRetryDownloadReflectsRemainingAttempts() {
        val task = Task().apply {
            tryAgainCount = 2
        }

        assertTrue(task.canRetryDownload())
        assertEquals(1, task.consumeDownloadAttempt())
        assertTrue(task.canRetryDownload())
        assertEquals(0, task.consumeDownloadAttempt())
        assertFalse(task.canRetryDownload())
    }
}
