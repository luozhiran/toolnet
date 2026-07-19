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

    private val waitingTasks: MutableList<Task> by lazy { mutableListOf() }
    private val waitingTaskUrls: MutableSet<String> by lazy { mutableSetOf() }
    private val runningTasks: MutableList<Task> by lazy { mutableListOf() }
    private val runningTaskUrls: MutableSet<String> by lazy { mutableSetOf() }

    @Synchronized
    fun scheduleTask(task: Task): ScheduleResult {
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null || waitingTaskUrls.contains(url) || runningTaskUrls.contains(url)) {
            return ScheduleResult.REJECTED
        }
        return if (runningTasks.size < maxDownloadSize()) {
            runningTasks.add(task)
            runningTaskUrls.add(url)
            ScheduleResult.RUNNING
        } else {
            waitingTasks.add(task)
            waitingTaskUrls.add(url)
            ScheduleResult.WAITING
        }
    }

    @Synchronized
    fun pollNextTaskToRun(): Task? {
        if (!runningQueueCanAcceptTask() || waitingTasks.isEmpty()) return null
        val task = waitingTasks.removeAt(0)
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null) {
            return null
        }
        waitingTaskUrls.remove(url)
        runningTasks.add(task)
        runningTaskUrls.add(url)
        return task
    }

    fun cancelWaitTask(task: Task?, error: String?) {
        if (task == null) return
        val removed = synchronized(this) {
            if (waitingTasks.remove(task)) {
                task.url?.let { waitingTaskUrls.remove(it) }
                task
            } else {
                null
            }
        }
        removed?.let { finishCanceledWaitTask(it, error) }
    }

    fun cancelWaitTask(url: String?, error: String?) {
        val removed = synchronized(this) {
            removeTaskByUrl(waitingTasks, waitingTaskUrls, url)
        }
        removed?.let { finishCanceledWaitTask(it, error) }
    }

    @Synchronized
    fun deleteRunningTask(task: Task?) {
        if (task == null) return
        if (runningTasks.remove(task)) {
            task.url?.let { runningTaskUrls.remove(it) }
        }
        Download.instance.listenerRegistry.removeTaskListeners(task)
    }

    @Synchronized
    fun deleteRunningTask(url: String?) {
        removeTaskByUrl(runningTasks, runningTaskUrls, url)?.let {
            Download.instance.listenerRegistry.removeTaskListeners(it)
        }
    }

    @Synchronized
    fun markRunningTaskCanceled(url: String?): Task? {
        if (url.isNullOrBlank()) return null
        return runningTasks.firstOrNull { it.url == url }?.apply {
            cancelUrl = url
        }
    }

    @Synchronized
    fun markRunningTaskCanceled(task: Task?): Boolean {
        if (task == null || !runningTasks.contains(task)) return false
        task.cancelUrl = task.url
        return true
    }

    @Synchronized
    fun exitRunningTask(task: Task?): Boolean {
        return task != null && runningTasks.contains(task)
    }

    @Synchronized
    fun exitWaitTask(task: Task?): Boolean {
        return task != null && waitingTasks.contains(task)
    }

    @Synchronized
    fun exitRunningUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && runningTaskUrls.contains(url)
    }

    @Synchronized
    fun exitWaitUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && waitingTaskUrls.contains(url)
    }

    fun isInvalidTask(task: Task?): Boolean {
        return task == null || task.url.isNullOrBlank() || task.path.isNullOrBlank() || task.url == task.cancelUrl
    }

    fun isBreakpointContinuation(task: Task): Boolean {
        return task.append
    }

    @Synchronized
    fun runningQueueCanAcceptTask(): Boolean {
        return runningTasks.size < maxDownloadSize()
    }

    fun isCheckMd5(task: Task): Boolean {
        return task.md5.orEmpty().isNotBlank()
    }

    fun isTryAgainDownload(tag: String?): Boolean {
        return tag == ERROR_DOWNLOAD_RETRYING
    }

    @Synchronized
    fun debugPrint() {
        PrintLog.logd("download queue: waiting=${waitingTasks.size}, running=${runningTasks.size}")
    }

    @Synchronized
    private fun removeTaskByUrl(
        tasks: MutableList<Task>,
        taskUrls: MutableSet<String>,
        url: String?
    ): Task? {
        val targetUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val position = tasks.indexOfFirst { it.url == targetUrl }
        if (position < 0) return null
        taskUrls.remove(targetUrl)
        return tasks.removeAt(position)
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
