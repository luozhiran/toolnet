package com.itg.net.download.callback

import com.itg.net.download.data.Task

interface IProgressCallback {
    /**
     * 与服务器建立连接过程中
     *
     * @param task
     */
    fun onConnecting(task: Task)

    fun onProgress(task: Task, complete:Boolean)

    fun onFail(error: String?, task: Task)

    /**
     * 下载任务终结回调，无论成功还是失败都会触发
     *
     * 触发时机：下载成功（[onProgress] complete=true）或最终失败/取消（[onFail]）后调用。
     * 注意：重试中的 [onFail] 不会触发本方法。
     *
     * @param task 下载任务
     */
    fun onFinish(task: Task)
}
