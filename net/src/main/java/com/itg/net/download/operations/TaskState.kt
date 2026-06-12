package com.itg.net.download.operations

import android.util.Log
import com.itg.net.Net
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.Task

class TaskState {

    private val maxDownloadSize = Net.instance.ddNetConfig.maxDownloadNum.coerceAtLeast(1)

    // 队列下载任务
    private val waitingTasks: MutableList<Task> by lazy { mutableListOf() }
    private val waitingTaskUrls: MutableSet<String> by lazy { mutableSetOf() }

    // 正在执行任务
    private val runningTasks: MutableList<Task> by lazy { mutableListOf() }
    private val runningTaskUrls: MutableSet<String> by lazy { mutableSetOf() }


    @Synchronized
    fun addWaitTask(task: Task): Boolean {
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null || waitingTaskUrls.contains(url)) {
            // 添加下载任务失败时，需要删除创建任务时生成的全局变量
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (waitingTasks.add(task)) {
            waitingTaskUrls.add(url)
            return true
        }
        // 添加下载任务失败时，需要删除创建任务时生成的全局变量
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

    @Synchronized
    fun addRunningTask(task: Task?): Boolean {
        if (task == null) return false
        val url = task.url?.takeIf { it.isNotBlank() }
        if (url == null || runningTaskUrls.contains(url)) {
            // 添加下载任务失败时，需要删除创建任务时生成的全局变量
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (runningTasks.add(task)) {
            runningTaskUrls.add(url)
            return true
        }
        // 添加下载任务失败时，需要删除创建任务时生成的全局变量
        HoldActivityCallbackMap.removeProgressCallback(task)
        return false
    }

    @Synchronized
    fun deleteRunningTask(task: Task?) {
        if (task == null) return
        if (runningTasks.remove(task)) {
            task.url?.let { runningTaskUrls.remove(it) }
        }
        //下载成功后，删除存储在单例集合中的持有Activity引用的回调对象
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
    fun markRunningTaskCanceled(task: Task?) {
        if (task == null) return
        task.cancelUrl = task.url
    }

    @Synchronized
    fun exitRunningTask(task: Task?): Boolean {
        if (task == null) return false
        return runningTasks.contains(task)
    }

    @Synchronized
    fun exitWaitTask(task: Task?): Boolean {
        if (task == null) return false
        return waitingTasks.contains(task)
    }

    @Synchronized
    fun exitRunningUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runningTaskUrls.contains(url)
    }

    @Synchronized
    fun exitWaitUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return waitingTaskUrls.contains(url)
    }

    /**
     * 校验是否是无效下载任务
     * @param task DTask?
     * @return Boolean
     */
    fun isInvalidTask(task: Task?): Boolean {
        if (task == null) return true
        if (task.url.isNullOrBlank()) return true
        if (task.url == task.cancelUrl) return true
        return false
    }

    /**
     * 断点续传
     * @param task Task
     * @return Boolean
     */
    fun isBreakpointContinuation(task: Task): Boolean {
        return task.append
    }


    /**
     * 下载队列是否可以接收新的下载任务
     * @return Boolean
     */
    @Synchronized
    fun runningQueueCanAcceptTask(): Boolean {
        return runningTasks.size < maxDownloadSize
    }

    /**
     * 按顺序从等待队列中取出下载任务
     * @param task Task
     */
    @Synchronized
    fun getTaskFromWaitQueue(task: Task?): Task? {
        return if (task == null) {
            findFirstTaskFromWaitQueue()
        } else if (runningQueueCanAcceptTask()) {
            task
        }else {
            addWaitTask(task)
            findFirstTaskFromWaitQueue()
        }
    }

    /**
     * 是否需要检测md5
     * @param task Task
     * @return Boolean
     */
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
        Log.i(DOWNLOAD_DEBUG_TAG, "下载队列：等待任务=${waitingTasks.size}，运行任务=${runningTasks.size}")
    }
}
