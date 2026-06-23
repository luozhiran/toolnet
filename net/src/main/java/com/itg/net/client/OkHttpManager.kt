package com.itg.net.client

import com.itg.net.config.NetConfig
import com.itg.net.encrypt.EncryptInterceptor
import com.itg.net.logging.HttpLogger
import com.itg.net.monitor.DefaultMonitorReportHandler
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorInterceptor
import com.itg.net.monitor.NetworkTypeCache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class OkHttpManager(ddNetConfig: NetConfig) {
    var okHttpClient: OkHttpClient = ddNetConfig.getOkHttpClient() ?: run {
        createDefaultClient(ddNetConfig)
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

    internal fun shutdownMonitor() {
        monitorReportHandler?.shutdown()
        monitorReportHandler = null
        if (networkTypeCacheLazy.isInitialized()) {
            networkTypeCacheLazy.value?.release()
        }
    }

    private fun createDefaultClient(ddNetConfig: NetConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(15, TimeUnit.SECONDS)
        builder.readTimeout(20, TimeUnit.SECONDS)
        builder.writeTimeout(35, TimeUnit.SECONDS)
        ddNetConfig.getInterceptors().forEach { builder.addInterceptor(it) }
        ddNetConfig.getCache()?.let {
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
            if (monitorConfig.enabled || monitorConfig.reportHandler != null || !monitorConfig.reportUrl.isNullOrBlank()) {
                val handler = monitorConfig.reportHandler
                    ?: DefaultMonitorReportHandler(
                        reportUrl = monitorConfig.reportUrl ?: "",
                        batchSize = monitorConfig.batchSize,
                        flushIntervalMs = monitorConfig.flushIntervalMs,
                        maxQueueSize = monitorConfig.maxQueueSize
                    )
                monitorReportHandler = handler   // 存储引用，供 Net.flushMonitor() / shutdownMonitor()
                builder.addInterceptor(MonitorInterceptor(
                    config = monitorConfig,
                    reportHandler = handler,
                    application = ddNetConfig.application,
                    networkTypeCache = networkTypeCache
                ))
            }
        }
        if (ddNetConfig.useHttpLog()) {
            val logInterceptor = HttpLoggingInterceptor(HttpLogger())
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            builder.addNetworkInterceptor(logInterceptor)
        }

        return builder.build()
    }
}
