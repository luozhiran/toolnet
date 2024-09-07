package com.itg.net.reqeust.post.multipart

import android.app.Activity
import android.os.Handler
import com.itg.net.Net
import com.itg.net.reqeust.base.DdCallback
import com.itg.net.reqeust.base.PostBuilder
import com.itg.net.reqeust.get.GetBuilder
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.reqeust.post.content.PostContentBuilder
import com.itg.net.reqeust.post.file.PostFileBuilder
import com.itg.net.reqeust.post.form.PostFormBuilder
import com.itg.net.reqeust.post.json.PostJsonBuilder
import com.itg.net.tools.UrlTools
import okhttp3.*
import java.io.File

/**
 * 对post的特有请求参数做处理，ParamsBuilder是共有参数
 * @property files MutableList<File?>?
 * @property fileNames MutableList<String?>?
 * @property fileMediaTypes MutableList<String?>?
 * @property contents MutableList<String?>?
 * @property contentMediaTypes MutableList<String?>?
 * @property contentNames MutableList<String?>?
 * @property intervalOffset Long
 * @property intervalFile File?
 * @property json String?
 */
abstract class PostMulBuilderImpl : ParamsBuilder(), PostBuilder, GetBuilder {

    private val urlParams = StringBuilder()
    private val postFile: PostFileBuilder by lazy {
        object : PostFileBuilder() {
            override fun autoCancel(activity: Activity?): PostFileBuilder = this

            override fun send(callback: DdCallback?) {}

            override fun send(handler: Handler?, what: Int, errorWhat: Int) {}
        }
    }
    private val postJson: PostJsonBuilder by lazy {
        object : PostJsonBuilder() {
            override fun autoCancel(activity: Activity?): PostJsonBuilder = this

            override fun send(callback: DdCallback?) {}

            override fun send(handler: Handler?, what: Int, errorWhat: Int) {}
        }
    }
    private val postContent: PostContentBuilder by lazy {
        object : PostContentBuilder() {
            override fun autoCancel(activity: Activity?): PostContentBuilder = this
            override fun send(callback: DdCallback?) {}
            override fun send(handler: Handler?, what: Int, errorWhat: Int) {}
        }
    }
    private val postForm: PostFormBuilder by lazy { object : PostFormBuilder(){
        override fun autoCancel(activity: Activity?): PostFormBuilder = this
        override fun send(callback: DdCallback?) {}
        override fun send(handler: Handler?, what: Int, errorWhat: Int) {}
    } }

    override fun addFile(file: File?): PostMulBuilderImpl {
        postFile.addFile1("file", file)
        return this
    }

    override fun addFile(fileName: String?, file: File?): PostMulBuilderImpl {
        postFile.addFile1(fileName, "", file)
        return this
    }

    override fun addFile(fileName: String?, mediaType: String?, file: File?): PostMulBuilderImpl {
        postFile.addFile1(fileName,mediaType,file)
       return this
    }

    override fun addContent(content: String?, mediaType: String?): PostMulBuilderImpl {
        postContent.addRealContent(content, "", mediaType)
        return this;
    }

    override fun addContent(content: String?, contentFlag: String?, mediaType: String?): PostMulBuilderImpl {
        postContent.addRealContent(content,contentFlag,mediaType)
        return this
    }

    override fun addParam(map: MutableMap<String, String?>?): PostMulBuilderImpl {
        postForm.addParam(map)
        return this
    }

    override fun addParam(key: String?, value: String?): PostMulBuilderImpl {
        postForm.addParam(key,value)
        return this
    }

    override fun addJson(key:String?,value:Any?): PostMulBuilderImpl {
        postJson.addJson(key,value)
        return this
    }

    protected fun getRequestBody(): RequestBody? {
      return  getMultipartBody()
    }

    private fun getMultipartBody(): MultipartBody {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        var hasValue = false
        postForm.getRequestBody()?.let {
            hasValue = true
            builder.addPart(it)
        }
        for (index in 0 until postContent.getCount()) {
            val body = postContent.getRequestBody(index)
            if (body!=null) {
                hasValue = true
                builder.addFormDataPart(postContent.getContentName(index),null,body)
            }
        }

        for (index in 0 until postFile.getCount()) {
            postFile.getFileName(index)?.let {
                hasValue = true
                val body = postFile.getRequestBody(index)
                builder.addFormDataPart(it,postFile.getFileRealName(index),body)
            }
        }

        if (!hasValue) {
            builder.addFormDataPart("body", "not appropriate body")
        }
        return builder.build()
    }

    fun addAppendParams(key: String?, value: String?): PostMulBuilderImpl {
        UrlTools.appendUrlParamsToStr(urlParams,key,value)
        return this
    }

    internal fun getAppendParams():StringBuilder{
        return urlParams
    }

    internal fun getUrl(): String {
        val urlParamsMap = UrlTools.cutOffStrToMap(urlParams.toString())
        val totalParamsMap = mutableMapOf<String,Any?>()
        if (!this.noGlobalParams) {
            totalParamsMap.putAll(Net.instance.ddNetConfig.globalParams)
            urlParamsMap?.let {
                totalParamsMap.putAll(it)
            }
        }
        return UrlTools.getSpliceUrl(totalParamsMap,this.url?:"")
    }

    override fun addCacheControl(cacheControl: CacheControl): PostMulBuilderImpl {
        this.cacheControl = cacheControl
        return this
    }

}