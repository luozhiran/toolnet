package com.itg.net.download

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.monitor.MonitorMarker
import java.io.File

/**
 * 下载任务构建器
 *
 * 通过链式调用的方式配置下载参数（保存路径、URL、重试次数、是否覆盖、是否断点续传、
 * Activity 生命周期绑定、进度监听器等），最终调用 [start] 发起下载。
 */
class TaskBuilder {

    /**
     * 待构建的下载任务实例，使用 lazy 延迟初始化
     */
    private val task by lazy { Task() }

    /**
     * 业务侧下载监听器，由 [listener] 方法设置。
     */
    private var activityBoundProgressCallback: IProgressCallback? = null

    /**
     * 扩展侧下载监听器，由 [addExtensionDownloadListener] 方法设置。
     */
    private var extensionProgressCallback: IProgressCallback? = null

    /**
     * 绑定到下载任务的生命周期。
     *
     * 具体 observer 注册、取消和释放由 [DownloadTaskSession] 统一管理。
     */
    private var lifecycle: Lifecycle? = null

    /**
     * 设置下载文件的保存路径
     */
    fun savePath(path: String): TaskBuilder {
        task.path = path
        return this
    }

    /**
     * 设置下载请求的 URL
     */
    fun url(url: String): TaskBuilder {
        task.url = url
        return this
    }

    /**
     * 设置下载失败后的最大重试次数
     */
    fun retryCount(count: Int): TaskBuilder {
        task.tryAgainCount = count.coerceAtLeast(1)
        return this
    }

    /**
     * 设置当目标文件已存在时是否覆盖
     */
    fun overwrite(overwrite: Boolean): TaskBuilder {
        task.overwrite = overwrite
        return this
    }

    /**
     * 绑定 [FragmentActivity] 生命周期
     */
    fun bindActivity(activity: FragmentActivity): TaskBuilder {
        bindLife(activity.lifecycle)
        return this
    }

    /**
     * 绑定 [Lifecycle] 生命周期。
     *
     * 具体 observer 注册、ON_DESTROY 取消和资源释放由 [DownloadTaskSession] 管理。
     */
    private fun bindLife(lifecycle: Lifecycle): TaskBuilder {
        this.lifecycle = lifecycle
        return this
    }

    /**
     * 开启断点续传功能
     */
    fun supportCheckpoint(): TaskBuilder {
        task.append = true
        return this
    }

    /**
     * 设置下载进度监听器
     */
    fun listener(progressBack: IProgressCallback): TaskBuilder {
        activityBoundProgressCallback = progressBack
        return this
    }

    /**
     * 设置扩展模块进度回调（供 net-flow 等扩展模块使用）。
     *
     * 注意：此方法不绑定 Activity 生命周期。普通业务代码应使用 [listener]，
     * 并在需要跟随页面销毁时配合 [bindActivity]。
     */
    fun addExtensionDownloadListener(callback: IProgressCallback): TaskBuilder {
        extensionProgressCallback = callback
        return this
    }

    /**
     * 设置内部进度回调（供 net-flow 等扩展模块使用）。
     *
     * @deprecated 普通业务下载请使用 [listener]。扩展模块请改用
     * [addExtensionDownloadListener]，避免误把持有 Activity/Fragment/View 的回调
     * 挂到非生命周期监听位。
     */
    @Deprecated(
        message = "addDownloadListener 是扩展模块入口，普通业务下载请使用 listener(callback)。扩展模块请改用 addExtensionDownloadListener(callback)。",
        replaceWith = ReplaceWith("addExtensionDownloadListener(callback)")
    )
    fun addDownloadListener(callback: IProgressCallback): TaskBuilder {
        return addExtensionDownloadListener(callback)
    }

    /**
     * 跳过全局参数
     */
    fun noUseGlobalParams(): TaskBuilder {
        task.noGlobalParams = true
        return this
    }

    /**
     * 强制对本下载任务开启监控上报，优先级最高
     */
    fun monitor(): TaskBuilder {
        task.monitorFlag = MonitorMarker.MONITOR
        return this
    }

    /**
     * 强制跳过本下载任务的监控上报，优先级最高
     */
    fun skipMonitor(): TaskBuilder {
        task.monitorFlag = MonitorMarker.SKIP
        return this
    }

    /**
     * 设置下载监控业务附加字段，原样透传到 MonitorEvent.extra 上报后端
     */
    fun monitorExtra(extra: String?): TaskBuilder {
        task.monitorExtra = extra
        return this
    }

    /**
     * 启动下载任务
     */
    fun start(): Task {
        val queueState = Download.instance.scheduler.getQueueState()
        val session = DownloadTaskSession(
            task = task,
            lifecycle = lifecycle,
            activityBoundProgressCallback = activityBoundProgressCallback,
            extensionProgressCallback = extensionProgressCallback
        )
        activityBoundProgressCallback = null
        extensionProgressCallback = null
        lifecycle = null

        if (!session.prepare() || session.isLifecycleDestroyed()) {
            task.cancelUrl = task.url
            session.finishBeforeStart(ERROR_DOWNLOAD_CANCELED)
            return task
        }
        if (queueState.isInvalidTask(task)) {
            session.finishBeforeStart(ERROR_INVALID_DOWNLOAD_TASK)
            return task
        }
        if (!task.overwrite && File(task.path.orEmpty()).exists()) {
            session.finishBeforeStart(ERROR_TARGET_FILE_EXISTS)
            return task
        }

        session.startListening()
        val accepted = if (queueState.isBreakpointContinuation(task)) {
            Download.instance.scheduler.appendDownload(session)
        } else {
            Download.instance.scheduler.download(session)
        }
        if (!accepted) {
            session.finishBeforeStart(ERROR_INVALID_DOWNLOAD_TASK)
        }
        return task
    }
}