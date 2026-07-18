package com.itg.ddlnet

import android.app.Application
import android.util.Log
import com.google.gson.GsonBuilder
import com.itg.net.Net
import com.itg.net.config.NetConfig
import com.itg.net.encrypt.Algorithm
import com.itg.net.encrypt.EncryptMode
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.ReportMode
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor
import com.itg.net.request.business.DefaultApiEnvelopeParser
import com.itg.net.request.business.GsonBusinessDataConverter
import com.itg.net.util.CacheFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 网络配置示例中心。
 *
 * 这个类的目的不是把 demo 写死，而是给接入方一个可以按场景取舍的配置模板：
 * 1. 只做普通请求：保留 [configureBase]、[configureHttpDebug]。
 * 2. 需要公共参数：保留 [configureGlobalParams]。
 * 3. 需要缓存 GET 响应：保留 [configureCache]，请求侧再配合 `addCacheControl(...)`。
 * 4. 需要统一加解密字段：保留 [configureFieldEncryption]。
 * 5. 需要统一处理登录失效、业务错误：保留 [configureBusinessResult]。
 * 6. 需要网络质量、错误上报：保留 [configureMonitor]。
 * 7. 需要完全控制 OkHttp：参考 [buildCustomClientExample]，但要注意它会接管默认 OkHttpClient。
 *
 * 推荐做法：Application 只调用 [install]，所有网络配置集中放在这个类里，方便复制给其他项目。
 */
object NetworkConfigExample {

    private const val TAG_BUSINESS = "BusinessResult"
    private const val TAG_MONITOR = "Monitor"

    private const val BASE_URL = "http://47.76.59.147:8000/"
    private const val HTTP_LOG_DIR_NAME = "net-log"

    private const val SECURE_KEY = "0123456789abcdef0123456789abcdef"
    private const val SECURE_IV = "abcdef9876543210"
    private const val SECURE_PROFILE_PATH = "/api/secure-profile"

    private val secureFields = listOf("phone", "idCard", "token")

    /**
     * Application.onCreate() 中调用一次。
     *
     * 当前 demo 默认启用大多数能力，方便在示例 App 中观察效果。
     * 真实业务接入时可以按需删除不需要的配置段。
     */
    fun install(application: Application) {
        Net.configure {
            configureBase(application)
            configureGlobalParams()
            configureHttpDebug(application)
            configureDownload()
            configureInterceptors()
            configureCache(application)
            configureFieldEncryption()
            configureBusinessResult()
            configureMonitor()

            // 如需完全替换 OkHttpClient，再打开这一行。
            // 注意：一旦使用 client(...)，默认 OkHttpClient 构建流程不会再自动追加 cache/interceptor/encrypt/monitor/httpLog。
            // client(buildCustomClientExample(application))
        }
    }

    /**
     * 基础配置。
     *
     * 必选项只有 application(...)；baseUrl(...) 建议配置，单个请求也可以用 `.url(...)` 覆盖。
     */
    private fun NetConfig.configureBase(application: Application): NetConfig {
        return application(application)
            .baseUrl(BASE_URL)
    }

    /**
     * 全局参数。
     *
     * 适合放平台、版本、渠道、公共 token 等每个请求都要带的参数。
     * 单个请求不想带全局参数时，可以调用 `noUseGlobalParams()`。
     */
    private fun NetConfig.configureGlobalParams(): NetConfig {
        return globalParams(
            mapOf(
                "platform" to "android",
                "app" to "toolnet-demo",
                "ab" to "bai",
                "43" to "af"
            )
        )
    }

    /**
     * 调试日志。
     *
     * enableHttpLog() 会让库的 HttpLogger 输出请求/响应预览。
     * logPath(...) 指定日志目录，方便在真机排查问题。
     */
    private fun NetConfig.configureHttpDebug(application: Application): NetConfig {
        val logDir = File(application.filesDir, HTTP_LOG_DIR_NAME)
        return enableHttpLog()
            .logPath(logDir.absolutePath)
    }

    /**
     * 下载并发控制。
     *
     * 下载多文件时，maxConcurrentDownloads(...) 用来限制同时下载的任务数。
     */
    private fun NetConfig.configureDownload(): NetConfig {
        return maxConcurrentDownloads(1)
    }

    /**
     * OkHttp 普通拦截器。
     *
     * 适合添加固定 Header、请求追踪 ID、灰度标记等轻量逻辑。
     * 加解密、监控这种库内置能力优先使用对应 DSL，不建议写进普通 OkHttp 拦截器里。
     */
    private fun NetConfig.configureInterceptors(): NetConfig {
        return interceptor(demoHeaderInterceptor())
    }

    /**
     * HTTP 缓存。
     *
     * 这里只是配置 OkHttp Cache。是否真正走缓存，还要在单个请求上配置 CacheControl。
     */
    private fun NetConfig.configureCache(application: Application): NetConfig {
        return cache(CacheFactory.getCache(application, 50L * 1024L * 1024L))
    }

    /**
     * 字段加解密。
     *
     * OPT_IN 表示只处理显式匹配的路径，适合逐步接入，风险更低。
     * 当前只对 /api/secure-profile 的 phone/idCard/token 字段做双向加解密。
     *
     * 如果业务是“除少数接口外全部加密”，可以改成：
     * encryptMode(EncryptMode.OPT_OUT).skipPath(Regex("/api/public/.*"))
     */
    private fun NetConfig.configureFieldEncryption(): NetConfig {
        return encrypt {
            algorithm(Algorithm.AES_CBC_PKCS7)
                .secretKey(SECURE_KEY)
                .iv(SECURE_IV)
                .encryptMode(EncryptMode.OPT_IN)
                .encryptPath(Regex(SECURE_PROFILE_PATH), secureFields)
        }
    }

    /**
     * 业务结果处理。
     *
     * businessEnvelopeParser(...) 负责把后端响应解析成统一业务信封。
     * businessConverter(...) 负责把 dataRaw 转成目标实体。
     * businessInterceptor(...) 适合统一处理登录失效、权限不足、维护模式等全局业务状态。
     */
    private fun NetConfig.configureBusinessResult(): NetConfig {
        return businessEnvelopeParser(DefaultApiEnvelopeParser())
            .businessConverter(
                GsonBusinessDataConverter(
                    GsonBuilder()
                        .setDateFormat("yyyy-MM-dd HH:mm:ss")
                        .create()
                )
            )
            .businessInterceptor(loginExpiredInterceptor())
    }

    /**
     * 网络监控。
     *
     * 当前 demo 默认关闭监控，但保留上报模式和自定义 handler 示例。
     * 真实线上环境可以改成 enabled()，并配置 reportUrl(...) 或 reportHandler(...)。
     */
    private fun NetConfig.configureMonitor(): NetConfig {
        return monitor {
            disabled()
                .reportMode(ReportMode.FAILURE_ONLY)
                .sampleRate(1.0f)
                .slowRequestThresholdMs(1500L)
                .batchSize(20)
                .flushIntervalMs(10_000L)
                .maxQueueSize(1000)
                .reportHandler(logcatMonitorHandler())
        }
    }

    /**
     * 完全自定义 OkHttpClient 示例。
     *
     * 只有在需要证书固定、代理、DNS、特殊超时策略等场景才建议使用。
     * 如果调用 `client(buildCustomClientExample(...))`，请把需要的 cache/interceptor/log/encrypt/monitor
     * 自行挂到这个 client 上，否则库默认构建流程不会再补这些能力。
     */
    private fun buildCustomClientExample(application: Application): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .cache(CacheFactory.getCache(application))
            .addInterceptor(demoHeaderInterceptor())
            .build()
    }

    private fun demoHeaderInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("X-Demo-Client", "toolnet-android")
                .build()
            chain.proceed(request)
        }
    }

    private fun loginExpiredInterceptor(): BusinessResultInterceptor {
        return object : BusinessResultInterceptor {
            override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                val code = chain.envelope.code
                if (code == "TOKEN_EXPIRED" || code == "401001") {
                    Log.w(TAG_BUSINESS, "login expired, navigate to login here")
                    return BusinessResult.Consumed(
                        reason = "login expired",
                        httpCode = chain.response.code,
                        rawBody = chain.response.body
                    )
                }
                return chain.proceed()
            }
        }
    }

    private fun logcatMonitorHandler(): IMonitorReportHandler {
        return object : IMonitorReportHandler {
            override val isAsync: Boolean get() = true

            override fun onEvent(event: MonitorEvent) {
                Log.e(TAG_MONITOR, event.toJson().toString())
            }

            override fun flush() = Unit

            override fun shutdown() = Unit
        }
    }
}
