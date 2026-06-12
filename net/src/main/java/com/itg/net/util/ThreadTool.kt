package com.itg.net.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object ThreadTool {
    private val executor by lazy { Executors.newCachedThreadPool() }
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

    fun postDelayed(runnable: Runnable?, delayMillis: Long) {
        runnable ?: return
        mainHandler.postDelayed(runnable, delayMillis)
    }

    fun removeCallback(runnable: Runnable?) {
        runnable ?: return
        mainHandler.removeCallbacks(runnable)
    }
}
