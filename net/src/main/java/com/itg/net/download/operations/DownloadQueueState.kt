package com.itg.net.download.operations

import com.itg.net.Net
import com.itg.net.download.DownloadTaskSession
import com.itg.net.download.data.ERROR_DOWNLOAD_RETRYING
import com.itg.net.download.data.Task
import com.itg.net.util.PrintLog

internal class DownloadQueueState {
    enum class ScheduleResult {
        RUNNING,
        WAITING,
        REJECTED
    }

    private val lock = Any()
    private val waitingSessions: LinkedHashMap<String, DownloadTaskSession> by lazy { LinkedHashMap() }
    private val runningSessions: LinkedHashMap<String, DownloadTaskSession> by lazy { LinkedHashMap() }

    fun enqueue(session: DownloadTaskSession): ScheduleResult {
        val url = session.task.url?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            if (url == null || waitingSessions.containsKey(url) || runningSessions.containsKey(url)) {
                return ScheduleResult.REJECTED
            }
            return if (runningSessions.size < maxDownloadSize()) {
                runningSessions[url] = session
                ScheduleResult.RUNNING
            } else {
                waitingSessions[url] = session
                ScheduleResult.WAITING
            }
        }
    }

    fun pollNextSessionToRun(): DownloadTaskSession? {
        synchronized(lock) {
            if (!runningQueueCanAcceptTaskLocked() || waitingSessions.isEmpty()) return null
            val entry = waitingSessions.entries.iterator().next()
            val url = entry.key
            val session = entry.value
            waitingSessions.remove(url)
            runningSessions[url] = session
            return session
        }
    }

    fun cancelWaiting(task: Task?, error: String?) {
        if (task == null) return
        val removed = synchronized(lock) {
            val url = task.url?.takeIf { waitingSessions[it]?.task === task }
            if (url == null) null else waitingSessions.remove(url)
        }
        removed?.let {
            it.task.cancelUrl = it.task.url
            it.cancel(error)
        }
    }

    fun cancelWaiting(url: String?, error: String?) {
        val removed = synchronized(lock) {
            removeSessionByUrl(waitingSessions, url)
        }
        removed?.let {
            it.task.cancelUrl = it.task.url
            it.cancel(error)
        }
    }

    fun removeRunning(session: DownloadTaskSession?) {
        if (session == null) return
        synchronized(lock) {
            val url = session.task.url?.takeIf { runningSessions[it] === session }
            if (url != null) {
                runningSessions.remove(url)
            }
        }
    }

    fun removeRunning(task: Task?) {
        if (task == null) return
        synchronized(lock) {
            val url = task.url?.takeIf { runningSessions[it]?.task === task }
            if (url != null) {
                runningSessions.remove(url)
            }
        }
    }

    fun removeRunning(url: String?) {
        synchronized(lock) {
            removeSessionByUrl(runningSessions, url)
        }
    }

    fun markRunningTaskCanceled(url: String?): DownloadTaskSession? {
        if (url.isNullOrBlank()) return null
        return synchronized(lock) {
            runningSessions[url]?.apply {
                task.cancelUrl = url
            }
        }
    }

    fun markRunningTaskCanceled(task: Task?): Boolean {
        if (task == null) return false
        return synchronized(lock) {
            val session = task.url?.let { runningSessions[it] }
            val running = session?.task === task
            if (running) {
                task.cancelUrl = task.url
            }
            running
        }
    }

    fun containsRunning(session: DownloadTaskSession?): Boolean {
        return session != null && synchronized(lock) {
            session.task.url?.let { runningSessions[it] === session } == true
        }
    }

    fun containsRunning(task: Task?): Boolean {
        return task != null && synchronized(lock) {
            task.url?.let { runningSessions[it]?.task === task } == true
        }
    }

    fun containsWaiting(task: Task?): Boolean {
        return task != null && synchronized(lock) {
            task.url?.let { waitingSessions[it]?.task === task } == true
        }
    }

    fun containsRunningUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && synchronized(lock) {
            runningSessions.containsKey(url)
        }
    }

    fun containsWaitingUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && synchronized(lock) {
            waitingSessions.containsKey(url)
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
            waitingSessions.size to runningSessions.size
        }
        PrintLog.logd { "download queue: waiting=$waitingSize, running=$runningSize" }
    }

    private fun removeSessionByUrl(
        sessions: MutableMap<String, DownloadTaskSession>,
        url: String?
    ): DownloadTaskSession? {
        val targetUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return sessions.remove(targetUrl)
    }

    private fun runningQueueCanAcceptTaskLocked(): Boolean {
        return runningSessions.size < maxDownloadSize()
    }

    private fun maxDownloadSize(): Int {
        return Net.instance.ddNetConfig.maxDownloadNum.coerceAtLeast(1)
    }
}
