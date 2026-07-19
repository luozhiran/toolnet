package com.itg.net.download

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.operations.DownloadEventPublisher
import com.itg.net.monitor.MonitorMarker
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool
import java.io.File

/**
 * 下载任务构建器
 *
 * 通过链式调用的方式配置下载参数（保存路径、URL、重试次数、是否覆盖、是否断点续传、
 * Activity 生命周期绑定、进度监听器等），最终调用 [start] 发起下载。
 *
 * ## 使用示例
 * ```
 * Net.instance.newDownload()
 *     .savePath("/sdcard/file.zip")
 *     .url("https://example.com/file.zip")
 *     .retryCount(3)
 *     .overwrite(true)
 *     .supportCheckpoint()
 *     .bindActivity(activity)
 *     .listener(callback)
 *     .start()
 * ```
 */
class TaskBuilder {

    /**
     * 将 [Lifecycle] 和 [LifecycleEventObserver] 封装为单一对象，
     * 保证 [Volatile] 读写原子性，避免部分可见问题。
     */
    private class ActivityLifecycleBinding(
        val lifecycle: Lifecycle,
        val observer: LifecycleEventObserver
    )

    /**
     * 待构建的下载任务实例，使用 lazy 延迟初始化
     */
    private val task by lazy { Task() }

    /**
     * 内部进度回调，负责将下载状态转换为统一的下载事件。
     * 并在下载完成或最终失败时自动移除 Activity 生命周期观察者
     */
    private val progressCallback by lazy {
        object : AbstractProgressCallback() {
            override fun onConnecting(task: Task) {
                DownloadEventPublisher.connecting(task)
            }

            override fun onProgress(task: Task, complete: Boolean) {
                if (complete) {
                    DownloadEventPublisher.complete(task)
                    DownloadEventPublisher.finished(task)
                    externalProgressCallback = null
                    removeActivityLifecycleObserver()
                } else {
                    DownloadEventPublisher.progress(task)
                }
            }

            override fun onFail(error: String?, task: Task) {
                // 下载失败但仍有重试次数时跳过，等待重试
                if (Download.instance.dispatchTool.getTaskState()
                        .isTryAgainDownload(error)
                ) {
                    return
                }
                DownloadEventPublisher.failed(task, error)
                DownloadEventPublisher.finished(task)
                externalProgressCallback = null
                removeActivityLifecycleObserver()
            }

        }
    }

    /**
     * 持有 Activity 引用的外部回调监听器，由 [listener] 方法设置。
     * 在 Activity 销毁时会自动移除以防止内存泄露。
     */
    private var holdActivityRef: IProgressCallback? = null

    private var externalProgressCallback: IProgressCallback? = null

    /**
     * 绑定到 Activity 的生命周期和观察者的包装对象。
     * 下载完成或 Activity 销毁时通过此引用主动移除观察者。
     */
    @Volatile
    private var activityBinding: ActivityLifecycleBinding? = null

    /**
     * 标记绑定的 Activity 是否已销毁。
     * 为 true 时 [start] 将直接返回，不再发起下载。
     */
    @Volatile
    private var lifecycleDestroyed = false

    /**
     * 设置下载文件的保存路径
     *
     * @param path 文件保存的绝对路径
     * @return 返回自身，支持链式调用
     */
    fun savePath(path: String): TaskBuilder {
        task.path = path
        return this
    }

    /**
     * 设置下载请求的 URL
     *
     * @param url 下载地址
     * @return 返回自身，支持链式调用
     */
    fun url(url: String): TaskBuilder {
        task.url = url
        return this
    }

    /**
     * 设置下载失败后的最大重试次数
     *
     * @param count 重试次数，最小值为 1
     * @return 返回自身，支持链式调用
     */
    fun retryCount(count: Int): TaskBuilder {
        task.tryAgainCount = count.coerceAtLeast(1)
        return this
    }

    /**
     * 设置当目标文件已存在时是否覆盖
     *
     * @param overwrite true 则覆盖已存在的文件，false 则通过 [IProgressCallback.onFail] 返回 [ERROR_TARGET_FILE_EXISTS] 错误
     * @return 返回自身，支持链式调用
     */
    fun overwrite(overwrite: Boolean): TaskBuilder {
        task.overwrite = overwrite
        return this
    }

    /**
     * 绑定 [FragmentActivity] 生命周期
     *
     * 绑定后下载任务与 Activity 生命周期一致：当 Activity 销毁时，自动取消下载任务、
     * 释放回调监听器、移除生命周期观察者，防止内存泄露。
     *
     * @param activity 要绑定的 FragmentActivity
     * @return 返回自身，支持链式调用
     */
    fun bindActivity(activity: FragmentActivity): TaskBuilder {
        bindLife(activity.lifecycle)
        return this
    }

    /**
     * 绑定 [Lifecycle] 生命周期
     *
     * 在主线程注册 [LifecycleEventObserver]，监听 ON_DESTROY 事件。
     * 如果生命周期已处于 DESTROYED 状态，则标记 [lifecycleDestroyed] 并取消任务。
     */
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
                        Download.instance.listenerRegistry.debugPrint()
                        Download.instance.listenerRegistry.removeTaskListener(
                            task,
                            it
                        )
                        PrintLog.logSubd("释放下完成")
                        Download.instance.listenerRegistry.debugPrint()
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
     *
     * 开启后下载任务将支持从已下载的位置继续下载，
     * 首次请求会通过 Range 头获取服务器已接收的字节偏移量。
     *
     * @return 返回自身，支持链式调用
     */
    fun supportCheckpoint(): TaskBuilder {
        this.task.append = true
        return this
    }

    /**
     * 移除绑定到 Activity 生命周期上的观察者
     *
     * 在下载任务终结时（成功 / 最终失败 / 取消）主动调用，避免观察者只能在 Activity
     * 销毁时才被移除。该方法可在任意线程调用，内部会派发到主线程执行实际的 removeObserver 操作。
     */
    private fun removeActivityLifecycleObserver() {
        val binding = activityBinding ?: return
        activityBinding = null
        ThreadTool.runOnUIThread {
            binding.lifecycle.removeObserver(binding.observer)
        }
    }

    /**
     * 设置下载进度监听器
     *
     * 监听器持有当前 Activity 的引用，会被下载监听注册表管理；
     * Activity 销毁或下载完成时会自动移除，防止内存泄露。
     *
     * @param progressBack 下载进度回调
     * @return 返回自身，支持链式调用
     */
    fun listener(progressBack: IProgressCallback): TaskBuilder {
        holdActivityRef = progressBack
        return this
    }

    /**
     * 设置内部进度回调（供 net-flow 等扩展模块使用）
     *
     * 允许在 [start] 之前注入自定义的 [IProgressCallback]，扩展模块可借此
     * 将下载事件桥接到 Flow / Channel 等响应式模型中。
     *
     * 注意：此方法为扩展模块预留，普通业务代码应使用 [listener] 方法。
     *
     * @param callback 要设置的进度回调
     * @return 返回自身，支持链式调用
     */
    fun addDownloadListener(callback: IProgressCallback): TaskBuilder {
        externalProgressCallback = callback
        return this
    }

    /**
     * 跳过全局参数
     *
     * 设置后下载请求的 URL 不会附加 [NetConfig.globalParams] 中配置的全局参数，
     * 适用于下载第三方域名的文件时避免泄露内部参数。
     *
     * @return 返回自身，支持链式调用
     */
    fun noUseGlobalParams(): TaskBuilder {
        this.task.noGlobalParams = true
        return this
    }

    /**
     * 强制对本下载任务开启监控上报，优先级最高
     *
     * 无视全局 [com.itg.net.monitor.MonitorConfig.enabled] 配置。
     *
     * @return 返回自身，支持链式调用
     */
    fun monitor(): TaskBuilder {
        this.task.monitorFlag = MonitorMarker.MONITOR
        return this
    }

    /**
     * 强制跳过本下载任务的监控上报，优先级最高
     *
     * 无视全局 [com.itg.net.monitor.MonitorConfig.enabled] 配置。
     * 适用于心跳/轮询等高频下载任务。
     *
     * @return 返回自身，支持链式调用
     */
    fun skipMonitor(): TaskBuilder {
        this.task.monitorFlag = MonitorMarker.SKIP
        return this
    }

    /**
     * 设置下载监控业务附加字段，原样透传到 MonitorEvent.extra 上报后端
     *
     * @param extra 业务自定义字符串（如 "scene=preload;version=2.3.1"），传 null 清除
     * @return 返回自身，支持链式调用
     */
    fun monitorExtra(extra: String?): TaskBuilder {
        this.task.monitorExtra = extra
        return this
    }

    /**
     * 启动下载任务
     *
     * 执行前会进行一系列校验，任一条件不满足则通过回调通知失败并返回：
     * 1. Activity 是否已销毁
     * 2. 任务参数是否有效（URL、保存路径是否配置等）
     * 3. 目标文件是否已存在且未开启覆盖
     * 4. 同一 URL 是否正在下载中
     * 5. 同一 URL 是否已在等待队列中
     *
     * 校验通过后，根据是否开启断点续传分别走 [DispatchTool.appendDownload]
     * 或 [DispatchTool.download] 发起下载。
     *
     * @return 配置完成并已发起或已排队的 [Task] 实例
     */
    fun start(): Task {
        val taskState = Download.instance.dispatchTool.getTaskState()
        // 检查绑定的 Activity 是否已销毁
        if (lifecycleDestroyed) {
            task.cancelUrl = task.url
            return task
        }
        // 校验任务是否为无效任务（URL 或保存路径未配置等）
        if (taskState.isInvalidTask(task)) {
            holdActivityRef?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            externalProgressCallback?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            holdActivityRef?.onFinish(task)
            externalProgressCallback?.onFinish(task)
            externalProgressCallback = null
            removeActivityLifecycleObserver()
            return task
        }
        // 目标文件已存在且未开启覆盖
        if (!task.overwrite && File(task.path.orEmpty()).exists()) {
            holdActivityRef?.onFail(ERROR_TARGET_FILE_EXISTS, task)
            externalProgressCallback?.onFail(ERROR_TARGET_FILE_EXISTS, task)
            holdActivityRef?.onFinish(task)
            externalProgressCallback?.onFinish(task)
            externalProgressCallback = null
            removeActivityLifecycleObserver()
            return task
        }

        // 校验请求地址是否正在下载中
        // 校验请求地址是否已经在等待队列中
        holdActivityRef?.apply { Download.instance.listenerRegistry.addTaskListener(task, this) }
        externalProgressCallback?.apply { Download.instance.listenerRegistry.addTaskListener(task, this) }
        task.progressCallback = progressCallback
        // 根据是否开启断点续传选择下载方式
        val accepted = if (taskState.isBreakpointContinuation(task)) {
            Download.instance.dispatchTool.appendDownload(task)
        } else {
            Download.instance.dispatchTool.download(task)
        }
        if (!accepted) {
            holdActivityRef?.let { Download.instance.listenerRegistry.removeTaskListener(task, it) }
            externalProgressCallback?.let { Download.instance.listenerRegistry.removeTaskListener(task, it) }
            holdActivityRef?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            externalProgressCallback?.onFail(ERROR_INVALID_DOWNLOAD_TASK, task)
            holdActivityRef?.onFinish(task)
            externalProgressCallback?.onFinish(task)
            externalProgressCallback = null
            removeActivityLifecycleObserver()
        }
        return task
    }

}
