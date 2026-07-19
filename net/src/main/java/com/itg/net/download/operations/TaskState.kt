package com.itg.net.download.operations

import android.util.Log
import com.itg.net.Net
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
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
            HoldActivityCallbackMap.removeProgressCallback(task)
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
            HoldActivityCallbackMap.removeProgressCallback(task)
            return null
        }
        waitingTaskUrls.remove(url)
        runningTasks.add(task)
        runningTaskUrls.add(url)
        return task
    }

    @Synchronized
    fun addWaitTask(task: Task): Boolean {
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null || waitingTaskUrls.contains(url)) {
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (waitingTasks.add(task)) {
            waitingTaskUrls.add(url)
            return true
        }
        HoldActivityCallbackMap.removeProgressCallback(task)
        return false
    }

    @Synchronized
    fun deleteWaitTask(task: Task?) {
        if (task == null) return
        if (waitingTasks.remove(task)) {
            task.url?.let { waitingTaskUrls.remove(it) }
        }
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    @Synchronized
    fun deleteWaitTask(url: String?) {
        removeTaskByUrl(waitingTasks, waitingTaskUrls, url)?.let {
            HoldActivityCallbackMap.removeProgressCallback(it)
        }
    }

    @Synchronized
    fun addRunningTask(task: Task?): Boolean {
        if (task == null) return false
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null || runningTaskUrls.contains(url)) {
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (runningTasks.add(task)) {
            runningTaskUrls.add(url)
            return true
        }
        HoldActivityCallbackMap.removeProgressCallback(task)
        return false
    }

    @Synchronized
    fun deleteRunningTask(task: Task?) {
        if (task == null) return
        if (runningTasks.remove(task)) {
            task.url?.let { runningTaskUrls.remove(it) }
        }
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    @Synchronized
    fun deleteRunningTask(url: String?) {
        removeTaskByUrl(runningTasks, runningTaskUrls, url)?.let {
            HoldActivityCallbackMap.removeProgressCallback(it)
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
        return task == null || task.url.isNullOrBlank() || task.url == task.cancelUrl
    }

    fun isBreakpointContinuation(task: Task): Boolean {
        return task.append
    }

    @Synchronized
    fun runningQueueCanAcceptTask(): Boolean {
        return runningTasks.size < maxDownloadSize()
    }

    @Synchronized
    fun getTaskFromWaitQueue(task: Task?): Task? {
        return if (task == null) {
            findFirstTaskFromWaitQueue()
        } else if (runningQueueCanAcceptTask()) {
            task
        } else {
            addWaitTask(task)
            findFirstTaskFromWaitQueue()
        }
    }

    fun isCheckMd5(task: Task): Boolean {
        return task.md5.orEmpty().isNotBlank()
    }

    @Synchronized
    fun canNextTask(): Boolean {
        return runningQueueCanAcceptTask() && waitingTasks.isNotEmpty()
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

    @Synchronized
    private fun findFirstTaskFromWaitQueue(): Task? {
        if (waitingTasks.isEmpty()) return null
        val task = waitingTasks.removeAt(0)
        task.url?.let { waitingTaskUrls.remove(it) }
        return task
    }

    private fun maxDownloadSize(): Int {
        return Net.instance.ddNetConfig.maxDownloadNum.coerceAtLeast(1)
    }
}
