package com.itg.net.monitor

import android.app.Application
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 网络请求监控拦截器
 *
 * ## 工作机制
 * 1. 判断是否需要对本次请求进行监控（开关优先级链）
 * 2. 执行实际请求并记录时间戳
 * 3. 快速判断是否需要上报（前置过滤，避免无效事件构建）
 * 4. 仅在需要上报时才构建 [MonitorEvent] 并投递给 [IMonitorReportHandler]
 *
 * ## 开关优先级
 * 1. 请求级标记 MonitorMarker.MONITOR → 强制开启
 * 2. 请求级标记 MonitorMarker.SKIP → 强制跳过
 * 3. 全局配置 MonitorConfig.enabled → 兜底
 *
 * ## 性能保障（Phase 1 优化）
 * - 事件构建前置过滤：成功请求 + FAILURE_ONLY 模式直接跳过，减少 ~95% 监控开销
 * - 网络状态缓存：使用 [NetworkTypeCache] 避免每次请求查询系统服务
 * - 非加密级请求 ID：使用 AtomicLong 替代 UUID.randomUUID()，消除 SecureRandom 熵池阻塞风险
 * - 智能线程隔离：根据 [IMonitorReportHandler.isAsync] 决定是否创建专用线程；
 *   Handler 已异步时内联调用，消除不必要的线程切换开销
 */
class MonitorInterceptor(
    private val config: MonitorConfig,
    private val reportHandler: IMonitorReportHandler,
    private val application: Application?,
    private val networkTypeCache: NetworkTypeCache? = null
) : Interceptor {

    companion object {
        private const val TAG = "MonitorInterceptor"
    }

    // ==================== 非加密级请求 ID 生成 ====================
    // 使用 AtomicLong + 时间戳 + 设备标识，消除 UUID.randomUUID() 的 SecureRandom 熵池阻塞风险

    private val idCounter = AtomicLong(0)
    private val deviceId: String by lazy {
        try {
            application?.let {
                val androidId = android.provider.Settings.Secure.getString(
                    it.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                if (!androidId.isNullOrBlank()) androidId.take(8) else "unknown"
            } ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun nextRequestId(): String =
        "${deviceId}_${System.currentTimeMillis()}_${idCounter.incrementAndGet()}"

    /** 后台线程池，仅当 Handler 不是异步时（isAsync=false）才创建。
     * Handler 已异步时（如 DefaultMonitorReportHandler / Firebase），框架内联调用，省去线程开销 */
    private val executor: ExecutorService? by lazy {
        if (!reportHandler.isAsync) {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "monitor-event").apply {
                    isDaemon = true
                    // 使用 NORM_PRIORITY - 2（优先级 3），保证基本调度但不会抢占核心业务线程
                    priority = Thread.NORM_PRIORITY - 2
                }
            }
        } else null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // === 第1步：开关判断 ===
        if (!shouldMonitor(request)) {
            return chain.proceed(request)  // 快速路径：跳过监控
        }

        // === 第2步：执行实际请求（记录时间戳） ===
        val requestStartMs = System.currentTimeMillis()
        var response: Response? = null
        var ioException: IOException? = null

        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            ioException = e
        }
        val requestEndMs = System.currentTimeMillis()
        val totalCostMs = requestEndMs - requestStartMs

        // === 第3步：前置过滤 —— 先判断是否需要上报，再决定是否构建事件 ===
        val httpCode = response?.code ?: -1
        val isSuccess = ioException == null && httpCode in 200..299
        val isCancelled = ioException != null &&
            ioException.message?.contains("Canceled", ignoreCase = true) == true

        // 快速判断：利用 isSuccess + totalCostMs 避免构建完整 MonitorEvent
        val needsReport = config.reportMode.shouldReport(isSuccess, totalCostMs, config.slowRequestThresholdMs)
            || isCancelled  // 取消也算失败，始终上报

        if (!needsReport) {
            // 不满足上报条件，跳过事件构建 —— 成功请求 + FAILURE_ONLY 模式的主要优化路径
            if (ioException != null) throw ioException
            return response!!
        }

        // === 第4步：投递事件（根据 Handler 能力选择异步/内联） ===
        val finalResponse = response
        val finalException = ioException
        val requestId = nextRequestId()

        val dispatcher: () -> Unit = {
            dispatchEvent(request, requestId, requestStartMs, requestEndMs,
                finalResponse, finalException, isSuccess, httpCode, isCancelled)
        }

        if (executor != null) {
            // Handler 未声明为异步（如同步文件 I/O），使用独立线程隔离
            executor!!.submit(dispatcher)
        } else {
            // Handler 已声明为异步（如 DefaultMonitorReportHandler / Firebase SDK），
            // 内联调用避免不必要的线程切换开销
            dispatcher()
        }

        // === 第5步：返回结果（异常重新抛出，与 OkHttp 约定一致） ===
        if (finalException != null) throw finalException
        return finalResponse!!
    }

    // ==================== 开关判断 ====================

    /**
     * 判断是否需要对本次请求进行监控
     *
     * 优先级链：
     * 1. 请求级 MONITOR → true
     * 2. 请求级 SKIP    → false
     * 3. 全局 enabled    → 兜底
     */
    private fun shouldMonitor(request: Request): Boolean {
        val marker = request.tag(MonitorMarker::class.java)
        if (marker?.value == MonitorMarker.MONITOR) return true
        if (marker?.value == MonitorMarker.SKIP) return false
        return config.enabled
    }

    // ==================== 事件构建 ====================

    private fun buildEvent(
        request: Request,
        requestId: String,
        requestStartMs: Long,
        requestEndMs: Long,
        response: Response?,
        exception: IOException?,
        isSuccess: Boolean,
        httpCode: Int,
        isCancelled: Boolean
    ): MonitorEvent {
        // 提取 response body 局部变量，避免重复调用 Response.body()
        val respBody = response?.body

        // URL 脱敏处理
        val rawUrl = request.url.toString()
        val sanitizedUrl = try {
            config.urlSanitizer(rawUrl)
        } catch (_: Exception) {
            rawUrl
        }

        // 提取 tag（与 SendTool 中 tag 设置逻辑一致，tag 为 String 类型）
        val tag = request.tag(String::class.java)
            ?: request.tag(Any::class.java)?.toString()

        return MonitorEvent(
            requestId = requestId,
            url = sanitizedUrl,
            method = request.method,
            tag = tag,

            requestStartMs = requestStartMs,
            requestEndMs = requestEndMs,
            totalCostMs = requestEndMs - requestStartMs,

            httpCode = httpCode,
            responseBodySize = respBody?.contentLength() ?: -1,
            contentType = respBody?.contentType()?.toString(),

            isSuccess = isSuccess,
            errorType = classifyError(exception, httpCode, isCancelled),
            errorMessage = exception?.message,
            exceptionClass = exception?.javaClass?.simpleName,

            networkType = networkTypeCache?.getNetworkType(),
            carrierName = null
        )
    }

    // ==================== 事件投递 ====================

    /**
     * 构建事件并投递给 Handler（公共方法，内联和异步路径共用）
     *
     * 异常静默吞掉：监控自身错误不影响业务请求/响应流程。
     */
    private fun dispatchEvent(
        request: Request,
        requestId: String,
        requestStartMs: Long,
        requestEndMs: Long,
        response: Response?,
        exception: IOException?,
        isSuccess: Boolean,
        httpCode: Int,
        isCancelled: Boolean
    ) {
        try {
            val event = buildEvent(
                request = request,
                requestId = requestId,
                requestStartMs = requestStartMs,
                requestEndMs = requestEndMs,
                response = response,
                exception = exception,
                isSuccess = isSuccess,
                httpCode = httpCode,
                isCancelled = isCancelled
            )
            reportHandler.onEvent(event)
        } catch (e: Exception) {
            // 监控自身异常静默吞掉，不影响业务
            Log.w(TAG, "Monitor event dispatch failed", e)
        }
    }

    // ==================== 错误分类 ====================

    /**
     * 将 IOException 和 HTTP 状态码分类为 ErrorType
     *
     * 注：SocketTimeoutException 统一归类为 TIMEOUT，
     *     拦截器层无法精确区分连接/读取超时（需 EventListener 配合）。
     *     ConnectException 可进一步通过消息区分 timeout vs refused。
     */
    private fun classifyError(
        e: IOException?,
        httpCode: Int,
        isCancelled: Boolean
    ): MonitorEvent.ErrorType {
        if (isCancelled) return MonitorEvent.ErrorType.CANCELLED

        if (e != null) {
            return when (e) {
                is java.net.UnknownHostException -> MonitorEvent.ErrorType.DNS_ERROR
                is java.net.ConnectException -> {
                    if (e.message?.contains("refused", ignoreCase = true) == true)
                        MonitorEvent.ErrorType.CONNECT_REFUSED
                    else
                        MonitorEvent.ErrorType.CONNECT_TIMEOUT
                }
                is javax.net.ssl.SSLException -> MonitorEvent.ErrorType.SSL_ERROR
                is java.net.SocketTimeoutException -> MonitorEvent.ErrorType.TIMEOUT
                else -> MonitorEvent.ErrorType.UNKNOWN
            }
        }

        return when {
            httpCode in 200..299 -> MonitorEvent.ErrorType.NONE
            httpCode in 400..499 -> MonitorEvent.ErrorType.HTTP_CLIENT_ERROR
            httpCode >= 500 -> MonitorEvent.ErrorType.HTTP_SERVER_ERROR
            else -> MonitorEvent.ErrorType.UNKNOWN
        }
    }
}
