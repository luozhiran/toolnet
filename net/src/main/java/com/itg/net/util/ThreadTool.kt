package com.itg.net.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ThreadTool {
    private val executor by lazy { Executors.newFixedThreadPool(2) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun runOnExecutor(runnable: Runnable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runnable.run()
        } else {
            executor.execute(runnable)
        }
    }

    fun runOnUIThread(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    fun runOnUIThreadBlocking(
        timeoutMillis: Long = DEFAULT_UI_WAIT_TIMEOUT_MS,
        block: () -> Unit
    ): Boolean {
        return runOnUIThreadBlocking(Runnable { block() }, timeoutMillis)
    }

    fun runOnUIThreadBlocking(runnable: Runnable, timeoutMillis: Long = DEFAULT_UI_WAIT_TIMEOUT_MS): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
            return true
        }
        val latch = CountDownLatch(1)
        val shouldRun = AtomicBoolean(true)
        val postedRunnable = Runnable {
            try {
                if (shouldRun.compareAndSet(true, false)) {
                    runnable.run()
                }
            } finally {
                latch.countDown()
            }
        }
        mainHandler.post(postedRunnable)
        return try {
            val completed = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed && shouldRun.compareAndSet(true, false)) {
                mainHandler.removeCallbacks(postedRunnable)
            }
            completed
        } catch (_: InterruptedException) {
            if (shouldRun.compareAndSet(true, false)) {
                mainHandler.removeCallbacks(postedRunnable)
            }
            Thread.currentThread().interrupt()
            false
        }
    }

    fun postDelayed(runnable: Runnable?, delayMillis: Long) {
        runnable ?: return
        mainHandler.postDelayed(runnable, delayMillis)
    }

    fun removeCallback(runnable: Runnable?) {
        runnable ?: return
        mainHandler.removeCallbacks(runnable)
    }

    private const val DEFAULT_UI_WAIT_TIMEOUT_MS = 3000L
}
