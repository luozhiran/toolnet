package com.itg.net.request.post.multipart

import android.app.Activity
import android.os.Handler
import com.itg.net.Net
import com.itg.net.request.base.DdCallback
import com.itg.net.request.base.PostBuilder
import com.itg.net.request.get.GetBuilder
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.post.content.PostContentBuilder
import com.itg.net.request.post.file.PostFileBuilder
import com.itg.net.request.post.form.PostFormBuilder
import com.itg.net.request.post.json.PostJsonBuilder
import com.itg.net.util.UrlTools
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
        postFile.addFile1(fileName, mediaType, file)
       return this
    }

    override fun addContent(content: String?, mediaType: String?): PostMulBuilderImpl {
        postContent.addRealContent(content, mediaType)
        return this
    }

    override fun addContent(content: String?, contentFlag: String?, mediaType: String?): PostMulBuilderImpl {
        postContent.addRealContent(content, contentFlag, mediaType)
        return this
    }

    override fun addParam(map: MutableMap<String, String?>?): PostMulBuilderImpl {
        postForm.addParam(map)
        return this
    }

    override fun addParam(key: String?, value: String?): PostMulBuilderImpl {
        postForm.addParam(key, value)
        return this
    }

    override fun addJson(key: String?, value: Any?): PostMulBuilderImpl {
        postJson.addJson(key, value)
        return this
    }

    internal fun getRequestBody(): RequestBody? {
        return getMultipartBody()
    }

    private fun getMultipartBody(): MultipartBody {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        var hasValue = appendFormFields(builder)
        hasValue = appendJsonPart(builder) || hasValue
        hasValue = appendContentParts(builder) || hasValue
        hasValue = appendFileParts(builder) || hasValue

        if (!hasValue) {
            builder.addFormDataPart("body", "not appropriate body")
        }
        return builder.build()
    }

    private fun appendFormFields(builder: MultipartBody.Builder): Boolean {
        var hasValue = false
        val formParams = mutableMapOf<String, Any?>()
        if (!noGlobalParams) {
            formParams.putAll(Net.instance.ddNetConfig.globalParams)
        }
        UrlTools.cutOffStrToMap(postForm.getParams().toString())?.let {
            formParams.putAll(it)
        }
        formParams.forEach { entry ->
            if (entry.key.isNotBlank()) {
                builder.addFormDataPart(entry.key, entry.value?.toString().orEmpty())
                hasValue = true
            }
        }
        return hasValue
    }

    private fun appendJsonPart(builder: MultipartBody.Builder): Boolean {
        if (!postJson.hasJsonBody()) return false
        builder.addFormDataPart("json", null, postJson.getMultipartJsonRequestBody())
        return true
    }

    private fun appendContentParts(builder: MultipartBody.Builder): Boolean {
        var hasValue = false
        for (index in 0 until postContent.getCount()) {
            val body = postContent.getRequestBody(index) ?: continue
            builder.addFormDataPart(postContent.getContentName(index), null, body)
            hasValue = true
        }
        return hasValue
    }

    private fun appendFileParts(builder: MultipartBody.Builder): Boolean {
        var hasValue = false
        for (index in 0 until postFile.getCount()) {
            val partName = postFile.getFileName(index)?.takeIf { it.isNotBlank() } ?: "file"
            val file = postFile.getFile(index) ?: continue
            val body = postFile.getRequestBody(index) ?: continue
            builder.addFormDataPart(partName, file.name, body)
            hasValue = true
        }
        return hasValue
    }

    fun addAppendParams(key: String?, value: String?): PostMulBuilderImpl {
        UrlTools.appendUrlParamsToStr(urlParams, key, value)
        return this
    }

    internal fun getAppendParams(): StringBuilder {
        return urlParams
    }

    internal fun getUrl(): String {
        val urlParamsMap = UrlTools.cutOffStrToMap(urlParams.toString())
        return UrlTools.getSpliceUrl(urlParamsMap, this.url ?: "")
    }

    override fun addCacheControl(cacheControl: CacheControl): PostMulBuilderImpl {
        this.cacheControl = cacheControl
        return this
    }

}
