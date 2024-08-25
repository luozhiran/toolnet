package com.itg.net

import com.itg.net.download.operations.GlobalDownloadProgressCache
import com.itg.net.download.data.Task
import com.itg.net.download.DispatchTool
import com.itg.net.download.TaskBuilder
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.download.operations.HoldActivityCallbackMap

class Download {
    companion object {
        @JvmStatic
        val instance: Download by lazy { Download() }
    }

    val dispatchTool: DispatchTool by lazy { DispatchTool() }
    internal val globalDownloadProgressCache: GlobalDownloadProgressCache by lazy { GlobalDownloadProgressCache() }

    fun taskBuilder(): TaskBuilder {
        return TaskBuilder()
    }

    /**
     * 监听所有下载任务的回调
     * @param progressBack IProgressCallback
     */
    fun setGlobalProgressListener(progressBack: IProgressCallback) {
        globalDownloadProgressCache.addItem(progressBack)
    }

    /**
     * 和setGlobalProgressListener配套使用
     * @param progressBack IProgressCallback
     */
    fun remoteGlobalProgressListener(progressBack: IProgressCallback) {
        globalDownloadProgressCache.removeItem(progressBack)
    }


    /**
     * 启动下载请求时，没有调用autoCancel()方法且不取消下载任务后台继续保持下载时， 必须手动取消内部下载监听器，否则会导致内存泄露
     * 或者调用DdNet.instance.download.cancel(task),取消任务同时会释放下载器
     * @param task Task?
     */
    fun removeAllProgressListener(task: Task){
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    /**
     * 移动指定Task对于监听器列表中指定的监听器
     */
    fun removeProgressListener(task: Task, iProgressCallback:IProgressCallback){
        HoldActivityCallbackMap.removeProgressCallback(task,iProgressCallback)
    }

    fun isQueue(url: String): Boolean {
        val taskState = dispatchTool.getTaskState()
        return taskState.exitWaitUrl(url) || taskState.exitRunningUrl(url)
    }

    fun cancel(url: String?) {
        val taskState = dispatchTool.getTaskState()
        if (taskState.exitRunningUrl(url)) {
            taskState.deleteRunningTask(url)
            Net.instance.cancelFirstTag(url)
        }else if (taskState.exitWaitUrl(url)) {
            taskState.deleteWaitTask(url)
        }
    }

    fun cancel(task: Task?) {
        val taskState = dispatchTool.getTaskState()
        if (taskState.exitRunningTask(task)) {
            taskState.deleteRunningTask(task)
            Net.instance.cancelFirstTag(task?.url)
        } else if (taskState.exitWaitTask(task)) {
            taskState.deleteWaitTask(task)
        }
    }

}