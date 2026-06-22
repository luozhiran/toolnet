package com.itg.net.monitor

/**
 * 监控事件上报处理器接口
 *
 * 定义监控事件从产生到最终落地的完整生命周期契约。
 * net 模块只依赖此接口，不关心具体实现。
 *
 * ## 实现者需要注意
 * - [onEvent] 会在拦截器的工作线程中调用，实现方应尽快返回，
 *   耗时操作（网络 IO、文件写入等）在实现内部异步化
 * - [flush] 在以下时机被调用：
 *   1. App 进入后台时
 *   2. 进程即将终止时
 *   3. 定时触发（由实现方自行决定）
 * - [shutdown] 在 OkHttpClient 被释放时调用，确保数据不丢失
 *
 * ## 自定义实现示例
 * ```kotlin
 * // 示例1：接入 Firebase Crashlytics
 * class FirebaseReportHandler : IMonitorReportHandler {
 *     override fun onEvent(event: MonitorEvent) {
 *         if (!event.isSuccess) {
 *             FirebaseCrashlytics.getInstance().log(event.toJson().toString())
 *         }
 *     }
 *     override fun flush() {}  // Firebase 实时上报，无需 flush
 *     override fun shutdown() {}
 * }
 *
 * // 示例2：同时写入本地文件 + HTTP 上报
 * class DualReportHandler(
 *     private val httpReporter: DefaultMonitorReportHandler,
 *     private val logFile: File
 * ) : IMonitorReportHandler {
 *     override fun onEvent(event: MonitorEvent) {
 *         httpReporter.onEvent(event)          // HTTP 上报
 *         logFile.appendText(event.toJson().toString() + "\n")  // 本地备份
 *     }
 *     override fun flush() { httpReporter.flush() }
 *     override fun shutdown() { httpReporter.shutdown() }
 * }
 * ```
 */
interface IMonitorReportHandler {

    /**
     * 声明此 Handler 的 [onEvent] 是否为异步实现
     *
     * - **true**：onEvent() 内部已将事件异步化（如入队、投递到其他线程/SDK），
     *   框架可以在拦截器线程中直接调用，无需额外创建线程隔离
     * - **false**（默认）：onEvent() 可能包含同步耗时逻辑（如文件 I/O），
     *   框架将使用独立线程调用以避免阻塞 OkHttp 拦截器链
     *
     * ## 实现指南
     * - 内部使用队列 + 后台线程的模式（如 [DefaultMonitorReportHandler]）→ 设为 true
     * - 委托给三方 SDK 且 SDK 内部异步处理（如 Firebase）→ 设为 true
     * - 包含同步文件写入、同步网络请求 → 保持默认 false
     * - 不确定时保持默认 false（保守策略，框架自动做线程隔离）
     */
    val isAsync: Boolean get() = false

    /**
     * 接收单条监控事件
     *
     * 在拦截器工作线程中被调用。实现方应：
     * - 尽快返回，避免阻塞拦截器链
     * - 内部自行处理异步化（入队、写磁盘等）
     *
     * @param event 监控事件，包含请求元信息、错误类型、时序数据等
     */
    fun onEvent(event: MonitorEvent)

    /**
     * 主动刷新缓冲区
     *
     * 适用于批量上报场景（如攒够 N 条再发 HTTP），
     * 在 App 进入后台或进程终止前调用，避免数据丢失。
     * 对于实时上报的实现（如直接写文件），可为空实现。
     */
    fun flush()

    /**
     * 关闭处理器，释放资源
     *
     * 在 OkHttpClient 被回收时调用。实现方应在此方法中：
     * - 等待缓冲区中的数据全部处理完成
     * - 关闭线程池/文件句柄/网络连接等资源
     * - 确保不丢失未上报的数据
     */
    fun shutdown()
}
