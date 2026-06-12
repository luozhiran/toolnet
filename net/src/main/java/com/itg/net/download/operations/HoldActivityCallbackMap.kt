package com.itg.net.download.operations

import android.util.Log
import com.itg.net.download.data.DEBUG_TAG
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.tools.TaskTools

object HoldActivityCallbackMap {

    private val progressCallbackMap: MutableMap<String, MutableList<IProgressCallback>> by lazy { mutableMapOf() }
    private val lock = Any()

    fun loopConnecting(task: Task) {
        callbacks(task).forEach {
            try {
                it.onConnecting(task)
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Progress callback onConnecting failed.", e)
            }
        }
    }

    fun loop(task: Task) {
        callbacks(task).forEach {
            try {
                it.onProgress(task, TaskTools.getDownloadProgress(task) == 100)
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Progress callback onProgress failed.", e)
            }
        }
    }

    fun loopFail(msg: String, task: Task) {
        callbacks(task).forEach {
            try {
                it.onFail(msg, task)
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Progress callback onFail failed.", e)
            }
        }
    }


    fun setProgressCallback(task: Task, progressCallback: IProgressCallback) {
        if (task.url.isNullOrBlank()) return
        synchronized(lock) {
            var callbackList = progressCallbackMap[task.url]
            if (callbackList == null) {
                callbackList = mutableListOf()
                progressCallbackMap[task.url!!] = callbackList
            }
            if (!callbackList.contains(progressCallback)) {
                callbackList.add(progressCallback)
            }
        }
    }


    fun removeProgressCallback(task: Task) {
        if (task.url.isNullOrBlank()) return
        synchronized(lock) {
            progressCallbackMap.remove(task.url)
        }
    }

    /**
     * 删除指定的回调对象
     * @param task Task
     * @param iProgressCallback IProgressCallback
     */
    fun removeProgressCallback(task: Task, iProgressCallback:IProgressCallback) {
        if (task.url.isNullOrBlank()) return
        synchronized(lock) {
            val callbackList = progressCallbackMap[task.url]
            callbackList?.remove(iProgressCallback)
            if (callbackList.isNullOrEmpty()) {
                progressCallbackMap.remove(task.url)
            }
        }
    }

    /**
     * 获取url对于监听器数量
     */
    fun getUrlProgressCallbackNum(url:String):Int{
        if (url.isBlank()) return 0
        return synchronized(lock) {
            progressCallbackMap[url]?.size ?: 0
        }
    }

    /**
     * 获取task对于监听器数量
     */
    fun getUrlProgressCallbackNum(task: Task):Int{
        if (task.url.isNullOrBlank()) return 0
        return getUrlProgressCallbackNum(task.url!!)
    }

    fun debugPrint(){
        val size = synchronized(lock) {
            progressCallbackMap.size
        }
        Log.i(DEBUG_TAG,"监听器数量：${size}")
    }

    private fun callbacks(task: Task): List<IProgressCallback> {
        val url = task.url ?: return emptyList()
        return synchronized(lock) {
            progressCallbackMap[url]?.toList().orEmpty()
        }
    }
}
