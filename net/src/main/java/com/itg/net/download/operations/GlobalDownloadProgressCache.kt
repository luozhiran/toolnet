package com.itg.net.download.operations

import android.util.Log
import com.itg.net.download.data.Task
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.util.TaskTools
import java.util.concurrent.CopyOnWriteArrayList

class GlobalDownloadProgressCache {
    private val progressCallbackList = CopyOnWriteArrayList<IProgressCallback>()

    fun addItem(progressCallback:IProgressCallback){
        progressCallbackList.addIfAbsent(progressCallback)
    }

    fun removeItem(progressCallback:IProgressCallback){
        progressCallbackList.remove(progressCallback)
    }


    fun execAllConnecting(task: Task){
        forEachCallback {
            it.onConnecting(task)
        }
    }

    fun execAllOnProgress(task: Task){
        val complete = TaskTools.getDownloadProgress(task) >= 100
        forEachCallback {
            it.onProgress(task, complete)
        }
    }

    fun execAllOnFail(msg:String, task: Task){
        forEachCallback {
            it.onFail(msg,task)
        }
    }

    private inline fun forEachCallback(action: (IProgressCallback) -> Unit) {
        for (callback in progressCallbackList) {
            try {
                action(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Global download progress callback failed.", e)
            }
        }
    }

    private companion object {
        private const val TAG = "GlobalProgressCache"
    }
}
