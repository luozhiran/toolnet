package com.itg.net.config

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.encrypt.EncryptConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


class NetConfig {
    @Volatile
    internal var url: String? = null

    @Volatile
    internal var maxDownloadNum = 3

    @Volatile
    internal var pkgName: String? = null

    val globalParams: MutableMap<String, Any?> = ConcurrentHashMap()

    @Volatile
    private var handler: Handler? = null

    @Volatile
    var application: Application? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    @Volatile
    private var logPath: String? = null

    private val interceptors: MutableList<Interceptor> = CopyOnWriteArrayList()

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var httpLoggerEnabled = false

    @Volatile
    private var okhttpCache: Cache? = null

    /**
     * 字段加解密配置，通过 [encrypt] DSL 方法设置
     */
    @Volatile
    var encryptConfig: EncryptConfig? = null
        private set

    fun app(application: Application): NetConfig {
        this.application = application
        pkgName = application.packageName
        return this
    }

    fun url(url: String?): NetConfig {
        this.url = url
        return this
    }

    fun setGlobalParams(key: String, value: Any): NetConfig {
        globalParams[key] = value
        return this
    }

    fun removeGlobalParam(key: String): NetConfig {
        globalParams.remove(key)
        return this
    }

    fun clearGlobalParams(): NetConfig {
        globalParams.clear()
        return this
    }

    fun addInterceptor(interceptor: Interceptor): NetConfig {
        interceptors.add(interceptor)
        return this
    }

    fun getInterceptors(): List<Interceptor> {
        return interceptors.toList()
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

    fun log(path: String?): NetConfig {
        logPath = path
        return this
    }

    /**
     * 如果配置okhttpClient，则网络请求的所有发送端都会是改okHttpClient
     * @param okHttpClient OkHttpClient?
     * @return DdNetConfig
     */
    fun okHttpClient(okHttpClient: OkHttpClient?): NetConfig {
        this.okHttpClient = okHttpClient
        return this
    }

    fun getOkHttpClient() = okHttpClient

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

    fun useHttpLog(use: Boolean): NetConfig {
        this.httpLoggerEnabled = use
        return this
    }

    fun useHttpLog(): Boolean {
        return this.httpLoggerEnabled
    }

    fun maxDownloadNum(max: Int): NetConfig {
        maxDownloadNum = max.coerceAtLeast(1)
        return this
    }

    fun useCacheControl(cache: Cache): NetConfig {
        this.okhttpCache = cache
        return this
    }

    fun getCache(): Cache? {
        return this.okhttpCache
    }

    /**
     * DSL 方式配置字段加解密
     *
     * ## 使用示例
     * ```
     * Net.instance.configure {
     *     encrypt {
     *         algorithm(Algorithm.AES_CBC_PKCS7)
     *         secretKey("my-secret-key")
     *         iv("1234567890abcdef")
     *         encryptField("password")
     *         encryptField("phone")
     *     }
     * }
     * ```
     *
     * @param block 配置闭包，接收 [EncryptConfig] 作为接收者
     */
    fun encrypt(block: EncryptConfig.() -> Unit): NetConfig {
        val config = encryptConfig ?: EncryptConfig().also { encryptConfig = it }
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
        } else if (file.length() > 1024 * 1024 * 5) {
            file.delete()
        }
        return file.absolutePath
    }
}
