package com.itg.net.request.post.json

import android.app.Activity
import com.itg.net.Net
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.get.GetBuilder
import com.itg.net.util.JsonTools
import com.itg.net.util.UrlTools
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


abstract class PostJsonBuilder : ParamsBuilder(), GetBuilder {

    private val urlParams = StringBuilder()
    private val params = StringBuilder()
    private var jsonObject = JSONObject()
    private var hasExplicitJsonBody = false

    fun addJson(key: String?, value: Any?): PostJsonBuilder {
        putParam(key, value)
        return this
    }

    protected fun getRequestBody(): RequestBody {
        val requestJson = JSONObject(jsonObject.toString())
        if (!this.noGlobalParams) {
            Net.instance.ddNetConfig.forEachGlobalParam {
                if (it.key.isNotBlank() && !requestJson.has(it.key)) {
                    requestJson.put(it.key, it.value)
                }
            }
        }
        return requestJson.toString().toRequestBody("application/json;charset=utf-8".toMediaType())
    }

    fun addAppendParams(key: String?, value: String?): PostJsonBuilder {
        UrlTools.appendUrlParamsToStr(urlParams, key, value)
        return this
    }

    internal fun getAppendParams(): StringBuilder {
        return urlParams
    }

    override fun addParam(key: String?, value: String?): PostJsonBuilder {
        putParam(key, value)
        return this
    }

    fun addParam(key: String?, value: Long?): PostJsonBuilder {
        putParam(key, value)
        return this
    }

    fun addParam(key: String?, value: Int?): PostJsonBuilder {
        putParam(key, value)
        return this
    }

    fun addParam(key: String?, value: Float?): PostJsonBuilder {
        putParam(key, value)
        return this
    }

    fun addParam(obj: JSONObject?): PostJsonBuilder {
        if (obj == null) return this
        this.jsonObject = JsonTools.deepMerge(obj, this.jsonObject)
        hasExplicitJsonBody = true
        return this
    }

    fun addJsonStr(obj: String?): PostJsonBuilder {
        val json = obj?.takeIf { it.isNotBlank() } ?: return this
        try {
            this.jsonObject = JsonTools.deepMerge(JSONObject(json), this.jsonObject)
            hasExplicitJsonBody = true
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON body", e)
        }
        return this
    }


    override fun addParam(map: MutableMap<String, String?>?): PostJsonBuilder {
        if (map.isNullOrEmpty()) return this
        map.forEach { entry ->
            addParam(entry.key, entry.value)
        }
        return this
    }

    internal fun getParams(): StringBuilder {
        return params
    }

    internal fun hasJsonBody(): Boolean {
        return hasExplicitJsonBody
    }

    internal fun getMultipartJsonRequestBody(): RequestBody {
        return jsonObject.toString().toRequestBody("application/json;charset=utf-8".toMediaTypeOrNull())
    }

    internal fun getUrl(): String {
        return UrlTools.getSpliceUrl(UrlTools.cutOffStrToMap(urlParams.toString()), this.url ?: "")
    }

    override fun addHeader(key: String?, value: String?): PostJsonBuilder {
        super.addHeader(key, value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): PostJsonBuilder {
        super.addHeader(map)
        return this
    }

    override fun url(url: String?): PostJsonBuilder {
        super.url(url)
        return this
    }

    override fun addCookie(cookie: Cookie?): PostJsonBuilder {
        super.addCookie(cookie)
        return this
    }

    override fun noUseGlobalParams(): PostJsonBuilder {
        super.noUseGlobalParams()
        return this
    }

    override fun addCookie(cookie: List<Cookie?>?): PostJsonBuilder {
        super.addCookie(cookie)
        return this
    }

    override fun addTag(tag: String?): PostJsonBuilder {
        super.addTag(tag)
        return this
    }

    override fun path(path: String): PostJsonBuilder {
        super.path(path)
        return this
    }

    override fun autoCancel(activity: Activity?): PostJsonBuilder {
        super.autoCancel(activity)
        return this
    }
    override fun addCacheControl(cacheControl: CacheControl): PostJsonBuilder {
        this.cacheControl = cacheControl
        return this
    }

    private fun putParam(key: String?, value: Any?) {
        if (!key.isNullOrBlank()) {
            jsonObject.put(key, value)
            hasExplicitJsonBody = true
        }
    }
}
