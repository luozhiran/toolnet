package com.itg.net.download

import com.itg.net.Net
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task
import com.itg.net.download.dispatcher.DownloadScheduler
import com.itg.net.download.operations.DownloadEvent
import com.itg.net.download.operations.DownloadEventDispatcher
import com.itg.net.download.operations.DownloadListenerRegistry

/**
 * 下载管理器（模块内部使用）
 *
 * 负责下载任务的创建、调度、进度监听和取消。
 * 外部统一通过 [com.itg.net.Net] 暴露的公开方法访问下载功能，不直接依赖本类。
 */
internal class Download {
    companion object {

        /**
         * 全局单例实例
         */
        @JvmStatic
        val instance: Download by lazy { Download() }
    }

    /**
     * 下载任务调度器，管理运行队列、等待队列和并发调度。
     */
    val scheduler: DownloadScheduler by lazy { DownloadScheduler() }

    /**
     * 下载监听注册表，只负责保存和移除监听器。
     */
    internal val listenerRegistry: DownloadListenerRegistry by lazy { DownloadListenerRegistry() }

    /**
     * 下载事件分发器，负责事件顺序、广播和 listener 回调分发。
     */
    internal val eventDispatcher: DownloadEventDispatcher by lazy {
        DownloadEventDispatcher(listenerRegistry)
    }

    internal fun publishDownloadEvent(event: DownloadEvent) {
        eventDispatcher.dispatch(event)
    }

    /**
     * 创建下载任务构建器
     *
     * @return [TaskBuilder] 实例，用于链式配置下载参数并启动任务
     */
    fun taskBuilder(): TaskBuilder {
        return TaskBuilder()
    }

    /**
     * 注册全局下载进度监听器，监听所有下载任务的连接、进度、失败事件
     *
     * @param progressBack 下载进度回调
     */
    fun setGlobalProgressListener(progressBack: IProgressCallback) {
        listenerRegistry.addGlobal(progressBack)
    }

    /**
     * 移除全局下载进度监听器
     *
     * @param progressBack 要移除的下载进度回调
     */
    fun removeGlobalProgressListener(progressBack: IProgressCallback) {
        listenerRegistry.removeGlobal(progressBack)
    }

    /**
     * 移除指定下载任务的所有进度监听器
     *
     * 启动下载请求时如果没有调用 bindActivity() 方法，且不取消下载任务后台继续保持下载时，
     * 必须手动调用此方法释放内部下载监听器，否则会导致内存泄露。
     * 调用 [cancel] 取消任务时也会同时释放监听器。
     *
     * @param task 下载任务
     */
    fun removeAllProgressListener(task: Task) {
        listenerRegistry.removeTaskListeners(task)
    }

    /**
     * 移除指定下载任务中某个特定的进度监听器
     *
     * @param task             下载任务
     * @param progressCallback 要移除的监听器
     */
    fun removeProgressListener(task: Task, progressCallback: IProgressCallback) {
        listenerRegistry.removeTaskListener(task, progressCallback)
    }

    /**
     * 判断指定 URL 是否正在下载或排队等待下载
     *
     * @param url 下载地址
     * @return true 表示正在下载或排队中
     */
    fun isQueued(url: String): Boolean {
        val queueState = scheduler.getQueueState()
        return queueState.containsWaitingUrl(url) || queueState.containsRunningUrl(url)
    }

    /**
     * 根据 URL 取消下载任务
     *
     * 优先取消正在执行的下载任务，其次从等待队列中移除。
     *
     * @param url 下载地址，为 null 则无操作
     */
    fun cancel(url: String?) {
        val queueState = scheduler.getQueueState()
        if (queueState.markRunningTaskCanceled(url) != null) {
            Net.instance.cancelFirstTag(url)
            return
        }
        if (queueState.containsWaitingUrl(url)) {
            queueState.cancelWaiting(url, ERROR_DOWNLOAD_CANCELED)
        }
    }

    /**
     * 根据 [Task] 取消下载任务
     *
     * 优先取消正在执行的下载任务，其次从等待队列中移除。
     *
     * @param task 下载任务，为 null 则无操作
     */
    fun cancel(task: Task?) {
        val queueState = scheduler.getQueueState()
        if (queueState.markRunningTaskCanceled(task)) {
            Net.instance.cancelFirstTag(task?.url)
            return
        }
        if (queueState.containsWaiting(task)) {
            queueState.cancelWaiting(task, ERROR_DOWNLOAD_CANCELED)
        }
    }
}