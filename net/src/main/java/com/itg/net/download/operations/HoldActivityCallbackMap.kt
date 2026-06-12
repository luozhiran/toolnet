package com.itg.net.download.operations

import android.util.Log
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.download.data.Task
import com.itg.net.util.TaskTools

object HoldActivityCallbackMap {

    private val progressCallbackMap: MutableMap<String, MutableList<IProgressCallback>> by lazy { mutableMapOf() }
    private val lock = Any()

    fun loopConnecting(task: Task) {
        callbacks(task).forEach {
            try {
                it.onConnecting(task)
            } catch (e: Exception) {
                Log.w(DOWNLOAD_DEBUG_TAG, "下载连接回调执行异常", e)
            }
        }
    }

    fun loop(task: Task) {
        callbacks(task).forEach {
            try {
                it.onProgress(task, TaskTools.getDownloadProgress(task) == 100)
            } catch (e: Exception) {
                Log.w(DOWNLOAD_DEBUG_TAG, "下载进度回调执行异常", e)
            }
        }
    }

    fun loopFail(msg: String, task: Task) {
        callbacks(task).forEach {
            try {
                it.onFail(msg, task)
            } catch (e: Exception) {
                Log.w(DOWNLOAD_DEBUG_TAG, "下载失败回调执行异常", e)
            }
        }
    }

    fun setProgressCallback(task: Task, progressCallback: IProgressCallback) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            val callbackList = progressCallbackMap.getOrPut(url) { mutableListOf() }
            if (!callbackList.contains(progressCallback)) {
                callbackList.add(progressCallback)
            }
        }
    }

    fun removeProgressCallback(task: Task) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            progressCallbackMap.remove(url)
        }
    }

    /**
     * 删除指定的回调对象
     * @param task Task
     * @param iProgressCallback IProgressCallback
     */
    fun removeProgressCallback(task: Task, progressCallback: IProgressCallback) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            val callbackList = progressCallbackMap[url]
            callbackList?.remove(progressCallback)
            if (callbackList.isNullOrEmpty()) {
                progressCallbackMap.remove(url)
            }
        }
    }

    /**
     * 获取url对于监听器数量
     */
    fun getUrlProgressCallbackNum(url: String): Int {
        if (url.isBlank()) return 0
        return synchronized(lock) {
            progressCallbackMap[url]?.size ?: 0
        }
    }

    /**
     * 获取task对于监听器数量
     */
    fun getUrlProgressCallbackNum(task: Task): Int {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return 0
        return getUrlProgressCallbackNum(url)
    }

    fun debugPrint() {
        val size = synchronized(lock) {
            progressCallbackMap.size
        }
        Log.i(DOWNLOAD_DEBUG_TAG, "下载监听器缓存数量=$size")
    }

    private fun callbacks(task: Task): List<IProgressCallback> {
        val url = task.url ?: return emptyList()
        return synchronized(lock) {
            progressCallbackMap[url]?.toList().orEmpty()
        }
    }
}
