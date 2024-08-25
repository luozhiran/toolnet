package com.itg.net.tools

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object ThreadTool {
    private val mExecutors by lazy { Executors.newCachedThreadPool() }
    private val mHandler by lazy { Handler(Looper.getMainLooper()) }

    fun runOnExecutor(runnable: Runnable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runnable.run()
        } else {
            mExecutors.execute(runnable)
        }
    }

    fun runOnUIThread(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mHandler.post(runnable)
        }
    }

    fun postDelayed(runnable: Runnable?, delayMillis: Long) {
        mHandler.postDelayed(runnable!!, delayMillis)
    }

    fun removeCallback(runnable: Runnable?) {
        mHandler.removeCallbacks(runnable!!)
    }
}