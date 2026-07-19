package com.itg.net.download.operations

import com.itg.net.Net
import com.itg.net.download.Download
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.Task
import com.itg.net.util.PrintLog

class TaskState {
    enum class ScheduleResult {
        RUNNING,
        WAITING,
        REJECTED
    }

    private val lock = Any()
    private val waitingTasks: LinkedHashMap<String, Task> by lazy { LinkedHashMap() }
    private val runningTasks: LinkedHashMap<String, Task> by lazy { LinkedHashMap() }

    fun scheduleTask(task: Task): ScheduleResult {
        val url = task.url?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            if (url == null || waitingTasks.containsKey(url) || runningTasks.containsKey(url)) {
                return ScheduleResult.REJECTED
            }
            return if (runningTasks.size < maxDownloadSize()) {
                runningTasks[url] = task
                ScheduleResult.RUNNING
            } else {
                waitingTasks[url] = task
                ScheduleResult.WAITING
            }
        }
    }

    fun pollNextTaskToRun(): Task? {
        synchronized(lock) {
            if (!runningQueueCanAcceptTaskLocked() || waitingTasks.isEmpty()) return null
            val entry = waitingTasks.entries.iterator().next()
            val url = entry.key
            val task = entry.value
            waitingTasks.remove(url)
            runningTasks[url] = task
            return task
        }
    }

    fun cancelWaitTask(task: Task?, error: String?) {
        if (task == null) return
        val removed = synchronized(lock) {
            val url = task.url?.takeIf { waitingTasks[it] === task }
            if (url == null) null else waitingTasks.remove(url)
        }
        removed?.let { finishCanceledWaitTask(it, error) }
    }

    fun cancelWaitTask(url: String?, error: String?) {
        val removed = synchronized(lock) {
            removeTaskByUrl(waitingTasks, url)
        }
        removed?.let { finishCanceledWaitTask(it, error) }
    }

    fun deleteRunningTask(task: Task?) {
        if (task == null) return
        synchronized(lock) {
            val url = task.url?.takeIf { runningTasks[it] === task }
            if (url != null) {
                runningTasks.remove(url)
            }
        }
        Download.instance.listenerRegistry.removeTaskListeners(task)
    }

    fun deleteRunningTask(url: String?) {
        val removed = synchronized(lock) {
            removeTaskByUrl(runningTasks, url)
        }
        removed?.let {
            Download.instance.listenerRegistry.removeTaskListeners(it)
        }
    }

    fun markRunningTaskCanceled(url: String?): Task? {
        if (url.isNullOrBlank()) return null
        return synchronized(lock) {
            runningTasks[url]?.apply {
                cancelUrl = url
            }
        }
    }

    fun markRunningTaskCanceled(task: Task?): Boolean {
        if (task == null) return false
        return synchronized(lock) {
            val running = task.url?.let { runningTasks[it] === task } == true
            if (running) {
                task.cancelUrl = task.url
            }
            running
        }
    }

    fun exitRunningTask(task: Task?): Boolean {
        return task != null && synchronized(lock) {
            task.url?.let { runningTasks[it] === task } == true
        }
    }

    fun exitWaitTask(task: Task?): Boolean {
        return task != null && synchronized(lock) {
            task.url?.let { waitingTasks[it] === task } == true
        }
    }

    fun exitRunningUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && synchronized(lock) {
            runningTasks.containsKey(url)
        }
    }

    fun exitWaitUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && synchronized(lock) {
            waitingTasks.containsKey(url)
        }
    }

    fun isInvalidTask(task: Task?): Boolean {
        return task == null || task.url.isNullOrBlank() || task.path.isNullOrBlank() || task.url == task.cancelUrl
    }

    fun isBreakpointContinuation(task: Task): Boolean {
        return task.append
    }

    fun runningQueueCanAcceptTask(): Boolean {
        return synchronized(lock) {
            runningQueueCanAcceptTaskLocked()
        }
    }

    fun isCheckMd5(task: Task): Boolean {
        return task.md5.orEmpty().isNotBlank()
    }

    fun isTryAgainDownload(tag: String?): Boolean {
        return tag == ERROR_DOWNLOAD_RETRYING
    }

    fun debugPrint() {
        if (!PrintLog.open) return
        val (waitingSize, runningSize) = synchronized(lock) {
            waitingTasks.size to runningTasks.size
        }
        PrintLog.logd { "download queue: waiting=$waitingSize, running=$runningSize" }
    }

    private fun removeTaskByUrl(
        tasks: MutableMap<String, Task>,
        url: String?
    ): Task? {
        val targetUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return tasks.remove(targetUrl)
    }

    private fun runningQueueCanAcceptTaskLocked(): Boolean {
        return runningTasks.size < maxDownloadSize()
    }

    private fun maxDownloadSize(): Int {
        return Net.instance.ddNetConfig.maxDownloadNum.coerceAtLeast(1)
    }

    private fun finishCanceledWaitTask(task: Task, error: String?) {
        task.cancelUrl = task.url
        val callback = task.progressCallback
        callback?.onFail(error, task)
        callback?.onFinish(task)
        task.progressCallback = null
        Download.instance.listenerRegistry.removeTaskListeners(task)
    }
}
