package com.itg.net.request.base

import android.app.Activity
import android.text.TextUtils
import com.itg.net.Net
import com.itg.net.request.post.json.PostJsonBuilder
import com.itg.net.util.StrTools
import com.itg.net.util.UrlTools
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.Headers
import org.json.JSONObject

/**
 * 处理基础参数,post put delete get 所有共同需要的参数
 * @property url String?
 * @property headerSb StringBuilder
 * @property params StringBuilder
 * @property cookies String?
 * @property tag String?
 * @property json String?
 */
abstract class ParamsBuilder : Builder, SentBuilder {
    var url: String? = Net.instance.ddNetConfig.url
        get() {
            return if (TextUtils.isEmpty(this.path)) {
                field
            } else {
                if (field?.endsWith("/") == true) {
                    field + this.path
                } else {
                    field +'/'+this.path
                }
            }
        }
    private val headerStringBuilder = StringBuilder()
    var cookies: String? = null
    var tag: String? = null
    var path:String?=null
    var noGlobalParams = false
    var cacheControl: CacheControl? = null

    /**
     * 加密标记：null=使用全局配置，"__encrypt_force__"=强制加密，"__encrypt_skip__"=强制跳过
     *
     * 通过 OkHttp Typed Tag 传递到 [com.itg.net.encrypt.EncryptInterceptor]，
     * 不占用 [tag] 变量（tag 用于请求取消等功能）。
     */
    @Volatile
    var encryptFlag: String? = null

    /**
     * 监控标记：null=使用全局配置，"__monitor_force__"=强制监控，"__monitor_skip__"=强制跳过
     *
     * 通过 OkHttp Typed Tag 传递到 [com.itg.net.monitor.MonitorInterceptor]，
     * 不占用 [tag] 变量（tag 用于请求取消等功能）。
     */
    @Volatile
    var monitorFlag: String? = null

    override fun addHeader(key: String?, value: String?): ParamsBuilder {
        if (key.isNullOrBlank() || value.isNullOrBlank()) return this
        UrlTools.appendUrlParamsToStr(headerStringBuilder,key,value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): ParamsBuilder {
        if (map.isNullOrEmpty()) return this
        map.forEach { entry ->
            addHeader(entry.key, entry.value)
        }
        return this
    }

    override fun url(url: String?): ParamsBuilder {
        this.url = url
        return this
    }

    override fun addCookie(cookie: Cookie?): ParamsBuilder = addCookie(mutableListOf(cookie))

    override fun addCookie(cookie: List<Cookie?>?): ParamsBuilder {
        cookies = StrTools.getCookieString(cookie)
        return this
    }

    override fun addTag(tag: String?): ParamsBuilder {
        this.tag = tag
        return this
    }

    override fun path(path: String): ParamsBuilder {
        this.path = path
        return this
    }

    override fun noUseGlobalParams(): ParamsBuilder {
        this.noGlobalParams = true
        return this
    }

    protected fun getHeader(): Headers? {
        if (headerStringBuilder.isBlank() && cookies.isNullOrBlank()) return null
        val builder: Headers.Builder = Headers.Builder()
        val headerParams = UrlTools.cutOffStrToMap(headerStringBuilder.toString())
        headerParams?.forEach{entry ->
            builder.add(entry.key, entry.value.toString())
        }
        if (cookies.orEmpty().isNotBlank()) {
            cookies?.let { builder.add("Cookie", it) }
        }
        return builder.build()
    }

    private fun formToJson(formParams: StringBuilder): String {
        val headerParams = UrlTools.cutOffStrToMap(formParams.toString())
        val json = JSONObject()
        headerParams?.forEach { entry ->
            json.put(entry.key, entry.value)
        }
        return json.toString()
    }

    /**
     * 强制加密当前请求
     *
     * 优先级最高，覆盖全局 [com.itg.net.encrypt.EncryptConfig.skipPath]
     * 和 [com.itg.net.encrypt.EncryptMode.OPT_IN] 模式下的默认跳过行为。
     * 调用后当前请求的所有字段规则（encryptField / encryptPath / encryptPattern）都会生效。
     */
    /**
     * 强制加密当前请求，优先级最高
     */
    fun encrypt(): ParamsBuilder {
        this.encryptFlag = "__encrypt_force__"
        return this
    }

    /**
     * 强制跳过当前请求的加解密，优先级最高
     */
    fun skipEncrypt(): ParamsBuilder {
        this.encryptFlag = "__encrypt_skip__"
        return this
    }

    /**
     * 强制对本请求开启监控上报，优先级最高
     */
    fun monitor(): ParamsBuilder {
        this.monitorFlag = "__monitor_force__"
        return this
    }

    /**
     * 强制跳过本请求的监控上报，优先级最高
     */
    fun skipMonitor(): ParamsBuilder {
        this.monitorFlag = "__monitor_skip__"
        return this
    }

    override fun autoCancel(activity: Activity?): ParamsBuilder =this

}
