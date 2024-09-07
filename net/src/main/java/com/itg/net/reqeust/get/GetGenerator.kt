package com.itg.net.reqeust.get

import android.app.Activity
import com.itg.net.Net
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.reqeust.base.SentBuilder
import com.itg.net.reqeust.post.content.PostContentGenerator
import com.itg.net.tools.UrlTools
import okhttp3.CacheControl
import okhttp3.Cookie

abstract class GetGenerator: ParamsBuilder(), SentBuilder, GetBuilder {
    protected val params = StringBuilder()

    override fun addParam(key: String?, value: String?): GetGenerator {
        UrlTools.appendUrlParamsToStr(params,key,value)
        return this
    }

    override fun addParam(map: MutableMap<String, String?>?): GetGenerator {
        if (map.isNullOrEmpty()) return this
        map.forEach { entry ->
            addParam(entry.key, entry.value)
        }
        return this
    }

    override fun addHeader(key: String?, value: String?): GetGenerator {
        super.addHeader(key, value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): GetGenerator {
        super.addHeader(map)
        return this
    }

    override fun url(url: String?): GetGenerator {
         super.url(url)
        return this
    }

    override fun addCookie(cookie: Cookie?): GetGenerator {
         super.addCookie(cookie)
        return this
    }

    override fun addCookie(cookie: List<Cookie?>?): GetGenerator {
        super.addCookie(cookie)
        return this
    }

    override fun addTag(tag: String?): GetGenerator {
        super.addTag(tag)
        return this
    }

    internal fun getUrl(): String {
        val urlParamsMap = UrlTools.cutOffStrToMap(params.toString())
        val totalParamsMap = mutableMapOf<String,Any?>()
        if (!this.noGlobalParams) {
            totalParamsMap.putAll(Net.instance.ddNetConfig.globalParams)
            urlParamsMap?.let {
                totalParamsMap.putAll(it)
            }
        }
        return UrlTools.getSpliceUrl(totalParamsMap,this.url?:"")
    }


    override fun autoCancel(activity: Activity?): GetGenerator {
        return this
    }

    override fun path(path: String): GetGenerator {
        super.path(path)
        return this
    }

    override fun addCacheControl(cacheControl: CacheControl):GetGenerator {
        this.cacheControl = cacheControl;
        return this;
    }
}