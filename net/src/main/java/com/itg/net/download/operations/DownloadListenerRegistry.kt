package com.itg.net.download.operations

import android.util.Log
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.download.data.Task
import java.util.concurrent.CopyOnWriteArrayList

internal class DownloadListenerRegistry {
    private val globalListeners = CopyOnWriteArrayList<IProgressCallback>()
    private val taskListeners: MutableMap<String, MutableList<IProgressCallback>> = mutableMapOf()
    private val lock = Any()

    fun addGlobal(listener: IProgressCallback) {
        globalListeners.addIfAbsent(listener)
    }

    fun removeGlobal(listener: IProgressCallback) {
        globalListeners.remove(listener)
    }

    fun addTaskListener(task: Task, listener: IProgressCallback) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            val listeners = taskListeners.getOrPut(url) { mutableListOf() }
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeTaskListeners(task: Task) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            taskListeners.remove(url)
        }
    }

    fun removeTaskListener(task: Task, listener: IProgressCallback) {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            val listeners = taskListeners[url]
            listeners?.remove(listener)
            if (listeners.isNullOrEmpty()) {
                taskListeners.remove(url)
            }
        }
    }

    fun listenerCount(task: Task): Int {
        val url = task.url?.takeIf { it.isNotBlank() } ?: return 0
        return listenerCount(url)
    }

    fun listenerCount(url: String): Int {
        if (url.isBlank()) return 0
        return synchronized(lock) {
            taskListeners[url]?.size ?: 0
        }
    }

    fun listenersFor(task: Task): List<IProgressCallback> {
        return globalListeners.toList() + taskListeners(task)
    }

    fun debugPrint() {
        val taskListenerSize = synchronized(lock) { taskListeners.size }
        Log.i(DOWNLOAD_DEBUG_TAG, "download listener registry: taskUrls=$taskListenerSize, global=${globalListeners.size}")
    }

    private fun taskListeners(task: Task): List<IProgressCallback> {
        val url = task.url ?: return emptyList()
        return synchronized(lock) {
            taskListeners[url]?.toList().orEmpty()
        }
    }
}