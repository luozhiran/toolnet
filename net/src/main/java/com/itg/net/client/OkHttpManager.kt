package com.itg.net.client

import android.util.Log
import com.itg.net.config.NetConfig
import com.itg.net.encrypt.EncryptInterceptor
import com.itg.net.logging.HttpLogger
import com.itg.net.monitor.DefaultMonitorReportHandler
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorInterceptor
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.NetworkTypeCache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class OkHttpManager(ddNetConfig: NetConfig) {
    companion object {
        private const val TAG = "OkHttpManager"
    }

    /** 网络类型缓存，供 MonitorInterceptor 和其他组件复用 */
    private val networkTypeCacheLazy = lazy {
        ddNetConfig.application?.let { NetworkTypeCache(it) }
    }
    private val networkTypeCache: NetworkTypeCache?
        get() = networkTypeCacheLazy.value

    /** 监控上报处理器引用，供 [com.itg.net.Net.flushMonitor] / [com.itg.net.Net.shutdownMonitor] 调用 */
    @Volatile
    internal var monitorReportHandler: IMonitorReportHandler? = null

    @Volatile
    private var monitorInterceptor: MonitorInterceptor? = null

    private val monitorEventExecutorLazy = lazy<ExecutorService> {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "monitor-download-event").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 2
            }
        }
    }

    var okHttpClient: OkHttpClient = createClient(ddNetConfig)

    internal fun dispatchMonitorEvent(event: MonitorEvent) {
        val handler = monitorReportHandler ?: return
        val dispatcher = Runnable {
            try {
                handler.onEvent(event)
            } catch (e: Exception) {
                Log.w(TAG, "Monitor event dispatch failed", e)
            }
        }
        if (handler.isAsync) {
            dispatcher.run()
            return
        }
        try {
            monitorEventExecutorLazy.value.execute(dispatcher)
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Monitor event dispatch rejected", e)
        }
    }

    internal fun shutdownMonitor() {
        monitorInterceptor?.shutdown()
        monitorInterceptor = null
        monitorReportHandler?.shutdown()
        monitorReportHandler = null
        if (monitorEventExecutorLazy.isInitialized()) {
            monitorEventExecutorLazy.value.shutdown()
        }
        if (networkTypeCacheLazy.isInitialized()) {
            networkTypeCacheLazy.value?.release()
        }
    }

    internal fun cancelAllRequests() {
        okHttpClient.dispatcher.cancelAll()
    }

    internal fun releaseIdleResources() {
        okHttpClient.connectionPool.evictAll()
    }

    private fun createClient(ddNetConfig: NetConfig): OkHttpClient {
        val customClient = ddNetConfig.client()
        val builder = if (customClient != null) {
            customClient.newBuilder()
                .dispatcher(Dispatcher())
                .connectionPool(ConnectionPool())
        } else {
            OkHttpClient.Builder().apply {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
                writeTimeout(35, TimeUnit.SECONDS)
            }
        }
        ddNetConfig.interceptors().forEach { builder.addInterceptor(it) }
        ddNetConfig.cache()?.let {
            builder.cache(it)
        }
        // 字段加解密拦截器（在日志拦截器之前注册，日志中显示密文）
        ddNetConfig.encryptConfig?.let { encryptConfig ->
            if (encryptConfig.hasValidConfig()) {
                builder.addInterceptor(EncryptInterceptor(encryptConfig))
            }
        }
        // 网络监控拦截器（在加密拦截器之后注册，监控加密后的请求元信息）
        ddNetConfig.monitorConfig?.let { monitorConfig ->
            val handler = monitorConfig.reportHandler
                ?: monitorConfig.reportUrl?.takeIf { it.isNotBlank() }?.let { reportUrl ->
                    DefaultMonitorReportHandler(
                        reportUrl = reportUrl,
                        batchSize = monitorConfig.batchSize,
                        flushIntervalMs = monitorConfig.flushIntervalMs,
                        maxQueueSize = monitorConfig.maxQueueSize
                    )
                }
            if (handler != null) {
                monitorReportHandler = handler   // 存储引用，供 Net.flushMonitor() / shutdownMonitor()
                val interceptor = MonitorInterceptor(
                    config = monitorConfig,
                    reportHandler = handler,
                    networkTypeCache = networkTypeCache
                )
                monitorInterceptor = interceptor
                builder.addInterceptor(interceptor)
            } else if (monitorConfig.enabled) {
                Log.w(TAG, "Monitor enabled but reportUrl/reportHandler is empty; monitor disabled")
            } else {
                // No report target configured.
            }
        }
        if (ddNetConfig.isHttpLogEnabled) {
            builder.addNetworkInterceptor(HttpLogger())
        }

        return builder.build()
    }
}
