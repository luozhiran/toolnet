package com.itg.net.request.post.content

import android.app.Activity
import com.itg.net.Net
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.post.json.PostJsonBuilder
import com.itg.net.util.UrlTools
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

abstract class PostContentBuilder : ParamsBuilder() {
    private val contents = mutableListOf<String?>()
    private val contentMediaTypes = mutableListOf<String?>()
    private val contentNames = mutableListOf<String?>()
    private val urlParams = StringBuilder()

   internal fun addRealContent(content: String?, mediaType: String?): PostContentBuilder =
       addRealContent(content, null, mediaType)

   internal fun addRealContent(content: String?, contentFlag: String?, mediaType: String?): PostContentBuilder {
        contents.add(content)
        contentNames.add(contentFlag)
        contentMediaTypes.add(mediaType)
        return this
    }

    protected fun getRequestBody(): RequestBody? {
        if (contents.isEmpty()) return null
        val mediaType = contentMediaTypes.getOrNull(0)?.toMediaTypeOrNull()
        return contents.getOrNull(0)?.toRequestBody(mediaType)
    }

    fun getCount(): Int {
        return contents.size
    }

    fun getContentName(index: Int): String {
        val configuredName = contentNames.getOrNull(index)?.takeIf { it.isNotBlank() }
        return configuredName ?: if (index == 0) "body" else "body$index"
    }

    fun getRequestBody(index: Int): RequestBody? {
        val mediaType = contentMediaTypes.getOrNull(index)?.toMediaTypeOrNull()
        return contents.getOrNull(index)?.toRequestBody(mediaType)
    }

    fun addAppendParams(key: String?, value: String?): PostContentBuilder {
        UrlTools.appendUrlParamsToStr(urlParams,key,value)
        return this
    }

   protected fun getAppendParams(): StringBuilder {
        return urlParams
    }


    internal fun getUrl(): String {
//        val urlParamsMap = UrlTools.cutOffStrToMap(urlParams.toString())
//        val totalParamsMap = mutableMapOf<String,Any?>()
//        if (!this.noGlobalParams) {
//            totalParamsMap.putAll(Net.instance.ddNetConfig.globalParams)
//            urlParamsMap?.let {
//                totalParamsMap.putAll(it)
//            }
//        }
        return UrlTools.getSpliceUrl(null,this.url?:"")
    }

    override fun autoCancel(activity: Activity?): PostContentBuilder = this

    override fun addCacheControl(cacheControl: CacheControl): PostContentBuilder {
        this.cacheControl = cacheControl
        return this
    }
}
