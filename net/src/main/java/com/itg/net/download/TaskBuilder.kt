package com.itg.net.download

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.operations.DownloadEndNotify
import com.itg.net.download.operations.HoldActivityCallbackMap
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool
import java.io.File

class TaskBuilder {
    // 将lifecycle和observer封装为单一对象，保证volatile读写原子性，避免部分可见问题
    private class ActivityLifecycleBinding(
        val lifecycle: Lifecycle,
        val observer: LifecycleEventObserver
    )

    private val task by lazy { Task() }
    private val progressCallback by lazy {
        object : IProgressCallback {
            override fun onConnecting(task: Task) {
                DownloadEndNotify.connectNotify(task)
            }

            override fun onProgress(task: Task, complete: Boolean) {
                if (complete) {
                    DownloadEndNotify.completeNotify(task)
                    removeActivityLifecycleObserver()
                } else {
                    DownloadEndNotify.progressNotify(task)
                }
            }

            override fun onFail(error: String?, task: Task) {
                if (Download.instance.dispatchTool.getTaskState()
                        .isTryAgainDownload(error)
                ) {
                    return
                }
                DownloadEndNotify.failNotify(task, error)
                removeActivityLifecycleObserver()
            }

        }
    }

    // 持有activity引用的回调
    private var holdActivityRef: IProgressCallback? = null

    // 保存绑定到Activity的生命周期和观察者，用于下载完成时主动移除
    @Volatile
    private var activityBinding: ActivityLifecycleBinding? = null

    @Volatile
    private var lifecycleDestroyed = false


    fun savePath(path: String): TaskBuilder {
        task.path = path
        return this
    }

    fun url(url: String): TaskBuilder {
        task.url = url
        return this
    }


    /**
     * 当下载请求失败后，可以重新发起请求的次数
     */
    fun retryCount(count: Int): TaskBuilder {
        task.tryAgainCount = count.coerceAtLeast(1)
        return this
    }

    fun overwrite(overwrite: Boolean): TaskBuilder {
        task.overwrite = overwrite
        return this
    }

    /**
     * 绑定生命周期后，发起下载请求和Activity生命周期一致，
     * 当activity销毁，请求取消
     */
    fun bindActivity(activity: FragmentActivity): TaskBuilder {
        bindLife(activity.lifecycle)
        return this
    }


    private fun bindLife(lifecycle: Lifecycle): TaskBuilder {
        ThreadTool.runOnUIThread {
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                lifecycleDestroyed = true
                task.cancelUrl = task.url
                PrintLog.logd("Activity已经销毁，无法绑定Activity")
                return@runOnUIThread
            }
            PrintLog.logd("绑定Activity")
            val observer = LifecycleEventObserver { source, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    PrintLog.logd("销毁Activity 开始释放资源")
                    lifecycleDestroyed = true
                    holdActivityRef?.let {
                        PrintLog.logSubd("释放下载时注册的回调监听(监听内持有Activity引用)")
                        HoldActivityCallbackMap.debugPrint()
                        HoldActivityCallbackMap.removeProgressCallback(
                            task,
                            it
                        )
                        PrintLog.logSubd("释放下完成")
                        HoldActivityCallbackMap.debugPrint()
                    }
                    holdActivityRef = null
                    PrintLog.logSubd("自动取消下载任务 ${task.url}")
                    Download.instance.cancel(task)
                    removeActivityLifecycleObserver()
                    PrintLog.logSubd("销毁Activity 资源释放完成")
                }
            }
            activityBinding = ActivityLifecycleBinding(lifecycle, observer)
            lifecycle.addObserver(observer)
        }
        return this

    }

    /**
     * 开启断点续传功能
     */
    fun supportCheckpoint(): TaskBuilder {
        this.task.append = true
        return this
    }

    /**
     * 移除绑定到Activity生命周期上的观察者。
     * 在下载任务终结时（成功/最终失败/取消）主动调用，避免观察者只能在Activity销毁时才被移除。
     * 该方法可在任意线程调用，内部会派发到主线程执行实际的removeObserver操作。
     */
    private fun removeActivityLifecycleObserver() {
        val binding = activityBinding ?: return
        activityBinding = null
        ThreadTool.runOnUIThread {
            binding.lifecycle.removeObserver(binding.observer)
        }
    }

    /**
     * 设置下载监听器
     */
    fun listener(progressBack: IProgressCallback): TaskBuilder{
        holdActivityRef = progressBack
        return this
    }


    fun start(): Task {
        val taskState = Download.instance.dispatchTool.getTaskState()
        if (lifecycleDestroyed) {
            task.cancelUrl = task.url
            return task
        }
        // 校验任务是否为无效任务
        if (taskState.isInvalidTask(task)) {
            holdActivityRef?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            removeActivityLifecycleObserver()
            return task
        }
        if (!task.overwrite && File(task.path.orEmpty()).exists()) {
            holdActivityRef?.onFail(ERROR_TARGET_FILE_EXISTS, task)
            removeActivityLifecycleObserver()
            return task
        }


        // 校验请求地址是否正在下载
        if (taskState.exitRunningUrl(task.url)) {
            removeActivityLifecycleObserver()
            return task
        }
        // 校验请求地址是否已经在任务队列
        if (taskState.exitWaitUrl(task.url)) {
            removeActivityLifecycleObserver()
            return task
        }
        holdActivityRef?.apply { HoldActivityCallbackMap.setProgressCallback(task, this) }
        task.progressCallback = progressCallback
        // 下载任务是否启动断点续传
        if (taskState.isBreakpointContinuation(task)) {
            Download.instance.dispatchTool.appendDownload(task)
            return task
        }
        Download.instance.dispatchTool.download(task)
        return task
    }


}
