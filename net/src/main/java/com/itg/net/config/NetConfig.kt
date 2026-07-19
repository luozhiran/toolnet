package com.itg.net.config

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.encrypt.EncryptConfig
import com.itg.net.monitor.MonitorConfig
import com.itg.net.request.business.ApiEnvelopeParser
import com.itg.net.request.business.BusinessDataConverter
import com.itg.net.request.business.BusinessResultInterceptor
import com.itg.net.request.business.DefaultApiEnvelopeParser
import com.itg.net.request.business.GsonBusinessDataConverter
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Net 全局配置。
 *
 * 该类是 `Net.configure { }` 的 DSL 接收者，用于集中配置网络库运行时能力：
 * - 应用上下文、Base URL 和全局请求参数
 * - OkHttpClient、OkHttp 拦截器、HTTP 缓存和 HTTP 日志
 * - 最大并行下载数
 * - 字段加解密和网络监控
 * - 业务协议解析、业务责任链和业务数据转换
 *
 * 示例：
 * ```kotlin
 * Net.configure {
 *     application(app)
 *     baseUrl("https://api.example.com/")
 *     globalParam("token", token)
 *     maxConcurrentDownloads(2)
 *     enableHttpLog()
 * }
 * ```
 */
class NetConfig {

    @Volatile
    internal var url: String? = null
        private set

    @Volatile
    internal var maxDownloadNum = 3
        private set

    @Volatile
    internal var pkgName: String? = null
        private set

    private val globalParameterMap: MutableMap<String, Any?> = ConcurrentHashMap()
    private val interceptorList: MutableList<Interceptor> = CopyOnWriteArrayList()
    private val businessInterceptorList: MutableList<BusinessResultInterceptor> = CopyOnWriteArrayList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    @Volatile
    private var handler: Handler? = null

    @Volatile
    private var logPath: String? = null

    @Volatile
    private var customClient: OkHttpClient? = null

    @Volatile
    private var httpLoggerEnabled = false

    @Volatile
    private var okhttpCache: Cache? = null

    @Volatile
    private var responseBodyLimitBytes: Long = DEFAULT_RESPONSE_BODY_LIMIT_BYTES

    @Volatile
    var application: Application? = null
        private set

    /**
     * 字段加解密配置，通过 [encrypt] DSL 设置。
     */
    @Volatile
    var encryptConfig: EncryptConfig? = null
        private set

    /**
     * 网络监控配置，通过 [monitor] DSL 设置。
     */
    @Volatile
    var monitorConfig: MonitorConfig? = null
        private set

    /**
     * 业务协议解析器，用于把原始响应字符串解析成统一业务信封。
     */
    @Volatile
    var businessEnvelopeParser: ApiEnvelopeParser = DefaultApiEnvelopeParser()
        private set

    /**
     * 业务数据转换器，用于把业务信封里的 data 字段转换成目标类型。
     */
    @Volatile
    var businessConverter: BusinessDataConverter = GsonBusinessDataConverter()
        private set

    /**
     * 当前配置的 Base URL。
     */
    val baseUrl: String?
        get() = url

    /**
     * 当前应用包名。
     */
    val packageName: String?
        get() = pkgName

    /**
     * 当前最大并行下载数。
     */
    val maxConcurrentDownloadCount: Int
        get() = maxDownloadNum

    /**
     * HTTP 日志是否开启。
     */
    val isHttpLogEnabled: Boolean
        get() = httpLoggerEnabled

    val maxResponseBodyBytes: Long
        get() = responseBodyLimitBytes

    /**
     * 全局请求参数快照。
     */
    val globalParams: Map<String, Any?>
        get() = globalParameterMap.toMap()

    /**
     * 配置 Application。网络库会从中读取包名、主线程 Looper 和默认日志目录。
     */
    fun application(application: Application): NetConfig {
        this.application = application
        pkgName = application.packageName
        return this
    }

    /**
     * 配置全局 Base URL。单个请求未单独设置 URL 时会使用该地址。
     */
    fun baseUrl(url: String?): NetConfig {
        this.url = url
        return this
    }

    /**
     * 添加或覆盖单个全局请求参数。
     */
    fun globalParam(key: String, value: Any?): NetConfig {
        globalParameterMap[key] = value
        return this
    }

    /**
     * 批量添加或覆盖全局请求参数。
     */
    fun globalParams(params: Map<String, Any?>): NetConfig {
        globalParameterMap.putAll(params)
        return this
    }

    /**
     * 移除一个或多个全局请求参数。
     */
    fun removeGlobalParams(vararg keys: String): NetConfig {
        keys.forEach { globalParameterMap.remove(it) }
        return this
    }

    /**
     * 清空全部全局请求参数。
     */
    fun clearGlobalParameters(): NetConfig {
        globalParameterMap.clear()
        return this
    }

    /**
     * 添加 OkHttp 拦截器。
     */
    fun interceptor(interceptor: Interceptor): NetConfig {
        interceptorList.add(interceptor)
        return this
    }

    /**
     * 批量添加 OkHttp 拦截器。
     */
    fun interceptors(interceptors: Iterable<Interceptor>): NetConfig {
        interceptors.forEach { interceptor(it) }
        return this
    }

    /**
     * 当前 OkHttp 拦截器快照。
     */
    fun interceptors(): List<Interceptor> {
        return interceptorList.toList()
    }

    /**
     * 配置业务协议解析器。
     */
    fun businessEnvelopeParser(parser: ApiEnvelopeParser): NetConfig {
        businessEnvelopeParser = parser
        return this
    }

    /**
     * 配置业务 data 字段转换器。
     */
    fun businessConverter(converter: BusinessDataConverter): NetConfig {
        businessConverter = converter
        return this
    }

    /**
     * 添加业务结果责任链拦截器。
     */
    fun businessInterceptor(interceptor: BusinessResultInterceptor): NetConfig {
        businessInterceptorList.add(interceptor)
        return this
    }

    /**
     * 批量添加业务结果责任链拦截器。
     */
    fun businessInterceptors(interceptors: Iterable<BusinessResultInterceptor>): NetConfig {
        interceptors.forEach { businessInterceptor(it) }
        return this
    }

    /**
     * 清空全部业务结果责任链拦截器。
     */
    fun clearBusinessInterceptors(): NetConfig {
        businessInterceptorList.clear()
        return this
    }

    /**
     * 当前业务结果责任链拦截器快照。
     */
    fun businessInterceptors(): List<BusinessResultInterceptor> {
        return businessInterceptorList.toList()
    }

    val uiHandler: Handler
        get() {
            val current = handler
            if (current != null) {
                return current
            }
            return synchronized(this) {
                handler ?: Handler(application?.mainLooper ?: Looper.getMainLooper()).also {
                    handler = it
                }
            }
        }

    fun today(): String {
        return synchronized(dateFormat) {
            dateFormat.format(Date())
        }
    }

    /**
     * 配置日志目录或日志文件路径。
     *
     * 如果传入目录，debug 日志会写入该目录下的 `debug.txt`。
     * 如果不配置，默认写入应用外部私有目录下的 `itg` 子目录。
     */
    fun logPath(path: String?): NetConfig {
        logPath = path
        return this
    }

    /**
     * 配置自定义 OkHttpClient。配置后普通请求、上传、下载都会复用该客户端。
     */
    fun client(okHttpClient: OkHttpClient?): NetConfig {
        customClient = okHttpClient
        return this
    }

    /**
     * 当前自定义 OkHttpClient。
     */
    fun client(): OkHttpClient? {
        return customClient
    }

    val httpLog: String
        get() {
            val configuredLogPath = logPath
            val file = if (configuredLogPath.isNullOrBlank()) {
                defaultLogFile("httpLog.txt")
            } else {
                File(configuredLogPath)
            }
            return prepareLogFile(file)
        }

    val debugLog: String
        get() {
            val configuredLogPath = logPath
            val file = if (configuredLogPath.isNullOrBlank()) {
                defaultLogFile("debug.txt")
            } else {
                File(File(configuredLogPath), "debug.txt")
            }
            return prepareLogFile(file)
        }

    /**
     * 开启或关闭 HTTP 日志。默认开启。
     */
    fun enableHttpLog(enable: Boolean = true): NetConfig {
        httpLoggerEnabled = enable
        return this
    }

    /**
     * 关闭 HTTP 日志。
     */
    fun disableHttpLog(): NetConfig {
        httpLoggerEnabled = false
        return this
    }

    /**
     * 配置最大并行下载数，小于 1 时自动修正为 1。
     */
    fun maxConcurrentDownloads(max: Int): NetConfig {
        maxDownloadNum = max.coerceAtLeast(1)
        return this
    }

    /**
     * 配置 OkHttp HTTP 缓存。
     */
    fun cache(cache: Cache): NetConfig {
        okhttpCache = cache
        return this
    }

    /**
     * 当前 HTTP 缓存配置。
     */
    fun cache(): Cache? {
        return okhttpCache
    }

    /**
     * 配置普通 API 响应体最大读取字节数。
     *
     * 该限制只保护 `send`、`sendResult`、`net-flow`、`net-retrofit` 这类会把响应体读成
     * 字符串的接口；文件下载不受影响。传入小于等于 0 的值会回落到默认安全上限。
     */
    fun maxResponseBodyBytes(bytes: Long): NetConfig {
        responseBodyLimitBytes = if (bytes > 0L) bytes else DEFAULT_RESPONSE_BODY_LIMIT_BYTES
        return this
    }

    /**
     * DSL 方式配置字段加解密。
     *
     * 示例：
     * ```kotlin
     * Net.configure {
     *     encrypt {
     *         secretKey("my-secret-key")
     *         iv("1234567890abcdef")
     *         encryptField("password")
     *     }
     * }
     * ```
     */
    fun encrypt(block: EncryptConfig.() -> Unit): NetConfig {
        val config = encryptConfig ?: EncryptConfig().also { encryptConfig = it }
        config.block()
        return this
    }

    /**
     * DSL 方式配置网络监控。
     *
     * 示例：
     * ```kotlin
     * Net.configure {
     *     monitor {
     *         enabled(true)
     *         reportUrl("https://monitor.example.com/api/report")
     *     }
     * }
     * ```
     */
    fun monitor(block: MonitorConfig.() -> Unit): NetConfig {
        val config = monitorConfig ?: MonitorConfig().also { monitorConfig = it }
        config.block()
        return this
    }

    private fun defaultLogFile(fileName: String): File {
        val baseDir = application?.getExternalFilesDir(null) ?: application?.filesDir ?: File(".")
        return File(baseDir, "itg/$fileName")
    }

    private fun prepareLogFile(file: File): String {
        file.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                Log.w(DOWNLOAD_DEBUG_TAG, "创建日志文件失败: ${file.absolutePath}", e)
            }
        } else if (file.length() > MAX_LOG_FILE_SIZE) {
            file.delete()
        }
        return file.absolutePath
    }

    private companion object {
        private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 5
        private const val DEFAULT_RESPONSE_BODY_LIMIT_BYTES = 2L * 1024L * 1024L
    }
}
