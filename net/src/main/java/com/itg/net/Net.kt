package com.itg.net

import com.itg.net.config.NetConfig
import com.itg.net.client.OkHttpManager
import com.itg.net.download.Download
import com.itg.net.request.create
import com.itg.net.request.get.Get
import com.itg.net.request.post.multipart.PostMul
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.post.content.PostContent
import com.itg.net.request.post.file.PostFile
import com.itg.net.request.post.form.PostForm
import com.itg.net.request.post.json.PostJson
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.util.PrintLog

/**
 * JSON 请求的 Content-Type
 */
const val MEDIA_JSON = "application/json; charset=utf-8"

/**
 * 二进制流请求的 Content-Type
 */
const val MEDIA_OCTET_STREAM = "application/octet-stream"

/**
 * 默认广播 Action，用于 App 安装广播
 */
const val BROAD_ACTION = "com.yqtec.install.broadcast"

/**
 * 网络请求类型枚举
 *
 * @property PostFile  文件上传
 * @property PostForm  表单提交
 * @property PostJson  JSON 请求体
 * @property PostMul   multipart/form-data 上传
 * @property Get       GET 请求
 * @property PostResume 断点续传上传
 * @property PostContent 自定义内容请求体
 */
enum class ModeType { PostFile, PostForm, PostJson, PostMul, Get, PostResume, PostContent }

/**
 * 网络库统一入口，全局单例
 *
 * 提供网络请求的配置、发送、取消以及下载任务管理等功能。
 *
 * ## 使用示例
 * ```
 * // 初始化配置
 * Net.instance.configure {
 *     app(application)
 *     url("https://api.example.com")
 *     setGlobalParams("token", "xxx")
 * }
 *
 * // GET 请求
 * Net.instance.get()
 *     .url("https://api.example.com/data")
 *     .tag("myTag")
 *     .send(callback)
 *
 * // POST JSON 请求
 * Net.instance.postJson()
 *     .url("https://api.example.com/submit")
 *     .json(jsonString)
 *     .send(callback)
 *
 * // 取消请求
 * Net.instance.cancel("myTag")
 *
 * // 下载文件
 * Net.instance.newDownload()
 *     .savePath("/sdcard/file.zip")
 *     .url("https://example.com/file.zip")
 *     .listener(progressCallback)
 *     .start()
 *
 * // 注册全局下载监听
 * Net.instance.addGlobalDownloadListener(progressCallback)
 *
 * // 取消下载
 * Net.instance.cancelDownload("https://example.com/file.zip")
 * ```
 */
class Net {

    companion object {

        /**
         * 全局单例实例
         */
        @JvmStatic
        val instance: Net by lazy { Net() }
    }

    /**
     * 网络配置实例，在 [configure] 中通过 DSL 方式设置各项参数
     */
    val ddNetConfig: NetConfig by lazy { NetConfig() }

    /**
     * OkHttp 客户端管理器，基于 [ddNetConfig] 构建 OkHttpClient 实例
     */
    val okhttpManager: OkHttpManager by lazy { OkHttpManager(ddNetConfig) }

    /**
     * 下载管理器单例，用于创建和管理下载任务（模块内部使用）
     */
    private val download: Download by lazy { Download.instance }

    /**
     * DSL 方式配置网络库参数
     *
     * @param block 配置闭包，接收 [NetConfig] 作为接收者
     * @return 返回自身，支持链式调用
     */
    fun configure(block: NetConfig.() -> Unit): Net {
        ddNetConfig.block()
        return this
    }

    /**
     * 根据请求类型创建对应的参数构建器
     *
     * @param type 请求类型，详见 [ModeType]
     * @return 对应类型的 [ParamsBuilder]，用于链式设置参数并最终发起请求
     */
    fun builder(type: ModeType): ParamsBuilder {
        PrintLog.logr("创建 ${type.name} 类型")
        return create(type)
    }

    /**
     * 创建 GET 请求构建器
     * @return [Get] 实例，支持链式设置 URL、参数、Header 等并发送
     */
    fun get() = builder(ModeType.Get) as Get

    /**
     * 创建 multipart/form-data 上传请求构建器
     * @return [PostMul] 实例，支持添加文件、文本等 Part
     */
    fun postMultipart() = builder(ModeType.PostMul) as PostMul

    /**
     * 创建文件上传请求构建器
     * @return [PostFile] 实例，支持上传单个文件
     */
    fun postFile() = builder(ModeType.PostFile) as PostFile

    /**
     * 创建表单提交请求构建器（application/x-www-form-urlencoded）
     * @return [PostForm] 实例
     */
    fun postForm() = builder(ModeType.PostForm) as PostForm

    /**
     * 创建 JSON 请求构建器（application/json）
     * @return [PostJson] 实例，支持传入 JSON 字符串或 Map
     */
    fun postJson() = builder(ModeType.PostJson) as PostJson

    /**
     * 创建自定义内容请求构建器，适用于特殊 Content-Type 场景
     * @return [PostContent] 实例
     */
    fun postContent() = builder(ModeType.PostContent) as PostContent

    /**
     * 创建新的下载任务构建器
     * @return [TaskBuilder] 实例，用于配置并启动下载任务
     */
    fun newDownload() = download.taskBuilder()

    // ==================== 下载相关 API ====================

    /**
     * 注册全局下载进度监听器，监听所有下载任务的连接、进度、失败事件
     *
     * @param listener 下载进度回调
     */
    fun addGlobalDownloadListener(listener: IProgressCallback) {
        download.setGlobalProgressListener(listener)
    }

    /**
     * 移除全局下载进度监听器
     *
     * @param listener 下载进度回调
     */
    fun removeGlobalDownloadListener(listener: IProgressCallback) {
        download.removeGlobalProgressListener(listener)
    }

    /**
     * 移除指定下载任务的所有进度监听器
     *
     * 启动下载请求时如果未调用 autoCancel() 方法，且不取消下载任务后台继续保持下载时，
     * 必须手动调用此方法释放内部下载监听器，否则会导致内存泄露。
     * 取消任务时（[cancelDownload]）会同时释放监听器。
     *
     * @param task 下载任务
     */
    fun removeDownloadListeners(task: Task) {
        download.removeAllProgressListener(task)
    }

    /**
     * 移除指定下载任务中某个特定的进度监听器
     *
     * @param task     下载任务
     * @param listener 要移除的监听器
     */
    fun removeDownloadListener(task: Task, listener: IProgressCallback) {
        download.removeProgressListener(task, listener)
    }

    /**
     * 判断指定 URL 是否正在下载或排队等待下载
     *
     * @param url 下载地址
     * @return true 表示正在下载或排队中
     */
    fun isDownloadQueued(url: String): Boolean {
        return download.isQueued(url)
    }

    /**
     * 根据 URL 取消下载任务
     *
     * 优先取消正在执行的下载，其次从等待队列中移除。
     *
     * @param url 下载地址，为 null 则无操作
     */
    fun cancelDownload(url: String?) {
        download.cancel(url)
    }

    /**
     * 根据 [Task] 取消下载任务
     *
     * 优先取消正在执行的下载，其次从等待队列中移除。
     *
     * @param task 下载任务，为 null 则无操作
     */
    fun cancelDownload(task: Task?) {
        download.cancel(task)
    }

    // ==================== 普通请求取消 API ====================

    /**
     * 取消所有正在排队和正在执行的 OkHttp 请求
     */
    fun cancelAll() {
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            it.cancel()
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            it.cancel()
        }
    }

    /**
     * 取消所有匹配指定 tag 的请求（包括排队中和执行中的）
     *
     * @param tag 请求的标识 tag，传 null 则无操作
     */
    fun cancelTag(tag: Any?) {
        if (tag == null) return
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
            }
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
            }
        }
    }

    /**
     * [cancelTag] 的别名，取消所有匹配指定 tag 的请求
     *
     * @param tag 请求的标识 tag
     */
    fun cancel(tag: Any?) = cancelTag(tag)

    // ==================== 网络监控生命周期 ====================

    /**
     * 刷新监控上报缓冲区
     *
     * 立即将当前内存队列中所有未上报的事件批量 POST 到监控服务器。
     * 适用于批量上报模式（如 [DefaultMonitorReportHandler]），
     * 对实时上报的实现（如 Firebase）为空操作。
     *
     * ## 调用时机
     * - App 进入后台时（`onStop` / `ProcessLifecycleOwner`）
     * - 即将执行可能触发进程终止的操作时
     *
     * ## 使用示例
     * ```kotlin
     * // Application 中注册生命周期监听
     * ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
     *     override fun onStop(owner: LifecycleOwner) {
     *         Net.instance.flushMonitor()
     *     }
     * })
     * ```
     */
    fun flushMonitor() {
        okhttpManager.monitorReportHandler?.flush()
    }

    /**
     * 关闭监控上报，释放所有资源
     *
     * 实现方应在此方法中：
     * - 等待缓冲区中的数据全部处理完成
     * - 关闭线程池/文件句柄/网络连接等资源
     * - 确保不丢失未上报的数据
     *
     * ## 调用时机
     * - OkHttpClient 重建前（如切换环境 BaseURL）
     * - 进程终止前
     * - 手动释放监控资源时
     */
    fun shutdownMonitor() {
        okhttpManager.monitorReportHandler?.shutdown()
        okhttpManager.monitorReportHandler = null
    }

    /**
     * 取消第一个匹配指定 tag 的请求（优先匹配排队中的请求）
     *
     * 与 [cancelTag] 不同的是，该方法只取消第一个匹配到的请求，找到后立即返回。
     *
     * @param tag 请求的标识 tag，传 null 则返回 false
     * @return true 表示找到并取消了一个请求，false 表示未找到匹配的请求
     */
    fun cancelFirstTag(tag: Any?): Boolean {
        if (tag == null) return false
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
                return true
            }
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
                return true
            }
        }
        return false
    }

}
