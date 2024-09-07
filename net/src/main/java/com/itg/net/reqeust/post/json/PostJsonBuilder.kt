package com.itg.net.reqeust.post.json

import android.app.Activity
import android.icu.number.IntegerWidth
import android.text.TextUtils
import com.itg.net.Net
import com.itg.net.reqeust.base.Builder
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.reqeust.get.GetBuilder
import com.itg.net.reqeust.post.form.PostFormBuilder
import com.itg.net.tools.JsonTools
import com.itg.net.tools.UrlTools
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


abstract class PostJsonBuilder : ParamsBuilder(), GetBuilder {

    private val urlParams = StringBuilder()
    private val params = StringBuilder()
    private var jsonObject = JSONObject()

    fun addJson(key: String?, value: Any?): PostJsonBuilder {
        if (!TextUtils.isEmpty(key)) {
            if (key != null) {
                try {
                    this.jsonObject.put(key, value)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return this
    }

    protected fun getRequestBody(): RequestBody {
        if (!this.noGlobalParams) {
            Net.instance.ddNetConfig.globalParams.forEach {
                if (!TextUtils.isEmpty(it.key)) {
                    jsonObject.put(it.key, it.value)
                }
            }
        }
        return jsonObject.toString().toRequestBody("application/json;charset=utf-8".toMediaType());
    }

    fun addAppendParams(key: String?, value: String?): PostJsonBuilder {
        UrlTools.appendUrlParamsToStr(urlParams, key, value)
        return this
    }

    internal fun getAppendParams(): StringBuilder {
        return urlParams
    }

    override fun addParam(key: String?, value: String?): PostJsonBuilder {
        if (!TextUtils.isEmpty(key)) {
            if (key != null) {
                this.jsonObject.put(key, value)
            }
        }
        return this
    }

    fun addParam(key: String?, value: Long?): PostJsonBuilder {
        if (!TextUtils.isEmpty(key)) {
            if (key != null) {
                this.jsonObject.put(key, value)
            }
        }
        return this
    }

    fun addParam(key: String?, value: Int?): PostJsonBuilder {
        if (!TextUtils.isEmpty(key)) {
            if (key != null) {
                this.jsonObject.put(key, value)
            }
        }
        return this
    }

    fun addParam(key: String?, value: Float?): PostJsonBuilder {
        if (!TextUtils.isEmpty(key)) {
            if (key != null) {
                this.jsonObject.put(key, value)
            }
        }
        return this
    }

    fun addParam(obj: JSONObject?): PostJsonBuilder {
        if (obj == null) return this
        this.jsonObject = JsonTools.deepMerge(obj, this.jsonObject)
        return this
    }

    fun addJsonStr(obj: String?): PostJsonBuilder {
        if (TextUtils.isEmpty(obj)) return this
        try {
            this.jsonObject = JsonTools.deepMerge(JSONObject(obj!!), this.jsonObject)
        } catch (e: Exception) {
            e.printStackTrace()
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

    internal fun getParams(): java.lang.StringBuilder {
        return params
    }

    internal fun getUrl(): String {

        return UrlTools.getSpliceUrl(null, this.url ?: "")
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
        return this
    }
    override fun addCacheControl(cacheControl: CacheControl): PostJsonBuilder {
        this.cacheControl = cacheControl
        return this
    }
}