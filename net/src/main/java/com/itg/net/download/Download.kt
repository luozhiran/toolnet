package com.itg.net.download

import com.itg.net.Net
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.download.dispatcher.DispatchTool
import com.itg.net.download.operations.GlobalDownloadProgressCache
import com.itg.net.download.operations.HoldActivityCallbackMap

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
     * 下载任务调度工具，管理运行队列和等待队列
     */
    val dispatchTool: DispatchTool by lazy { DispatchTool() }

    /**
     * 全局下载进度缓存，回调所有注册的全局下载监听器
     */
    internal val globalDownloadProgressCache: GlobalDownloadProgressCache by lazy { GlobalDownloadProgressCache() }

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
        globalDownloadProgressCache.addItem(progressBack)
    }

    /**
     * 移除全局下载进度监听器
     *
     * @param progressBack 要移除的下载进度回调
     */
    fun removeGlobalProgressListener(progressBack: IProgressCallback) {
        globalDownloadProgressCache.removeItem(progressBack)
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
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    /**
     * 移除指定下载任务中某个特定的进度监听器
     *
     * @param task             下载任务
     * @param progressCallback 要移除的监听器
     */
    fun removeProgressListener(task: Task, progressCallback: IProgressCallback) {
        HoldActivityCallbackMap.removeProgressCallback(task, progressCallback)
    }

    /**
     * 判断指定 URL 是否正在下载或排队等待下载
     *
     * @param url 下载地址
     * @return true 表示正在下载或排队中
     */
    fun isQueued(url: String): Boolean {
        val taskState = dispatchTool.getTaskState()
        return taskState.exitWaitUrl(url) || taskState.exitRunningUrl(url)
    }

    /**
     * 根据 URL 取消下载任务
     *
     * 优先取消正在执行的下载任务，其次从等待队列中移除。
     *
     * @param url 下载地址，为 null 则无操作
     */
    fun cancel(url: String?) {
        val taskState = dispatchTool.getTaskState()
        if (taskState.markRunningTaskCanceled(url) != null) {
            Net.instance.cancelFirstTag(url)
            return
        }
        if (taskState.exitWaitUrl(url)) {
            taskState.deleteWaitTask(url)
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
        val taskState = dispatchTool.getTaskState()
        if (taskState.markRunningTaskCanceled(task)) {
            Net.instance.cancelFirstTag(task?.url)
            return
        }
        if (taskState.exitWaitTask(task)) {
            taskState.deleteWaitTask(task)
        }
    }

}