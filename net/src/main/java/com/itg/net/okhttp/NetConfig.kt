package com.itg.net.okhttp

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.text.TextUtils
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class NetConfig {
    internal var url: String? = null
    internal var maxDownloadNum = 3
    internal var pkgName: String? = null
    val globalParams = HashMap<String, Any?>()
    private var handler: Handler? = null
    var application: Application? = null
    private var mDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private var logPath: String? = null
    private val interceptors: MutableList<Interceptor> = ArrayList()
    private var okHttpClient: OkHttpClient? = null
    private var useHeepLogger = false

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
        if (globalParams.containsKey(key)) {
            globalParams.remove(key)
        }
        globalParams[key] = value
        return this
    }

    fun addInterceptor(interceptor: Interceptor): NetConfig {
        interceptors.add(interceptor)
        return this
    }

    fun getInterceptors(): List<Interceptor> {
        return interceptors
    }

    val uiHandler: Handler
        get() {
            if (handler == null) {
                handler = Handler(application!!.mainLooper)
            }
            return handler!!
        }

    fun today(): String {
        return mDateFormat.format(Date())
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
        get() = if (TextUtils.isEmpty(logPath)) {
            var file = Environment.getExternalStorageDirectory()
            file = File(file, "/itg/$logPath/httpLog.txt")
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                if (file.length() > 1024 * 1024 * 5) {
                    file.delete()
                }
            }
            file.absolutePath
        } else {
            val file = File(logPath)
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                if (file.length() > 1024 * 1024 * 5) {
                    file.delete()
                }
            }
            file.absolutePath
        }


    val debugLog: String
        get() {
            var file = Environment.getExternalStorageDirectory()
            file = File(file, "/itg/$logPath/debug.txt")
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                if (file.length() > 1024 * 1024 * 5) {
                    file.delete()
                }
            }
            return file.absolutePath
        }

    fun useHttpLog(use: Boolean): NetConfig {
        this.useHeepLogger = use
        if (use) {
            Logger.addLogAdapter(AndroidLogAdapter())
        }
        return this
    }

    fun useHttpLog(): Boolean {
        return this.useHeepLogger
    }

    fun maxDownloadNum(max:Int): NetConfig {
        maxDownloadNum = max
        return this
    }
}