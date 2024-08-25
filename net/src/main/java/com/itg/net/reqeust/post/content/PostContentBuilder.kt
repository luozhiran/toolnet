package com.itg.net.reqeust.post.content

import android.app.Activity
import com.itg.net.Net
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.tools.UrlTools
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

abstract class PostContentBuilder : ParamsBuilder() {
    private var contents: MutableList<String?>? = null
    private var contentMediaTypes: MutableList<String?>? = null
    private var contentNames: MutableList<String?>? = null
    private val urlParams = StringBuilder()
    init {
        contents = mutableListOf()
        contentMediaTypes = mutableListOf()
        contentNames = mutableListOf()
    }
   internal fun addRealContent(content: String?, mediaType: String?): PostContentBuilder =
       addRealContent(content, "", mediaType)

   internal fun addRealContent(content: String?, contentFlag: String?, mediaType: String?): PostContentBuilder {
        this.contents?.add(content)
        this.contentNames?.add(contentFlag)
        this.contentMediaTypes?.add(mediaType)
        return this
    }

    protected fun getRequestBody(): RequestBody? {
        if (contentMediaTypes.isNullOrEmpty()) return null
        if (contentMediaTypes.isNullOrEmpty()) return null
        val mt = contentMediaTypes?.get(0)?.toMediaTypeOrNull()
        return contents?.get(0)?.toRequestBody(mt)
    }

    fun getCount():Int {
        return this.contents?.size?:0
    }

    fun getContentName(index:Int):String{
        return contentNames?.get(index)?:""
    }

    fun getRequestBody(index:Int): RequestBody?{
        val mt = contentMediaTypes?.get(index)?.toMediaTypeOrNull()
        return contents?.get(index)?.toRequestBody(mt)
    }

    fun addAppendParams(key: String?, value: String?): PostContentBuilder {
        UrlTools.appendUrlParamsToStr(urlParams,key,value)
        return this
    }

   protected fun getAppendParams():StringBuilder{
        return urlParams;
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
}