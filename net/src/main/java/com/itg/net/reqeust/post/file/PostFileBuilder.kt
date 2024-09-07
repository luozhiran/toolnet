package com.itg.net.reqeust.post.file

import android.app.Activity
import com.itg.net.Net
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.reqeust.post.content.PostContentBuilder
import com.itg.net.tools.UrlTools
import okhttp3.CacheControl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

abstract class PostFileBuilder : ParamsBuilder() {
    private var files: MutableList<File?>? = null
    private var fileNames: MutableList<String?>? = null
    private var fileMediaTypes: MutableList<String?>? = null

    //断点续传时使用
    private var intervalOffset: Long = 0
    private val urlParams = StringBuilder()

    init {
        files = mutableListOf()
        fileNames = mutableListOf()
        fileMediaTypes = mutableListOf()
    }

    internal fun addFile1(file: File?): PostFileBuilder = addFile1("file", file)
    internal fun addFile1(fileName: String?, file: File?): PostFileBuilder =
        addFile1(fileName, "", file)

    internal fun addFile1(fileName: String?, mediaType: String?, file: File?): PostFileBuilder {
        files?.add(file)
        fileNames?.add(fileName)
        fileMediaTypes?.add(mediaType)
        return this
    }

    protected open fun getRequestBody(): RequestBody? {
        return getRequestBody(0)
    }

    fun getRequestBody(index: Int): RequestBody {
        val file = files?.get(index) ?: File("")
        val mediaStr = if (fileMediaTypes?.size ?: 0 > index) {
            fileMediaTypes?.get(index)?.toMediaTypeOrNull()
        } else {
            getFileType(file.name)
        }
        return file.asRequestBody(mediaStr)
    }

    internal fun getFile(index: Int): File? {
        return files?.get(index)
    }

    internal fun getFileName(index: Int): String? {
        return fileNames?.get(index)
    }

    internal fun getFileRealName(index: Int): String? {
        return files?.get(index)?.name
    }

    internal fun getCount(): Int {
        return files?.size ?: 0
    }

    fun addResumeFileOffset1(intervalOffset: Long) {
        this.intervalOffset = intervalOffset;
    }

    protected fun getResumeFileOffset1(): Long {
        return this.intervalOffset ?: 0
    }

    private fun getFileType(fileName: String): MediaType? {
        return if (fileName.endsWith(".png")) {
            "image/png".toMediaTypeOrNull()
        } else if (fileName.endsWith(".jpg")) {
            "image/jpeg".toMediaTypeOrNull()
        } else {
            "application/octet-stream".toMediaTypeOrNull()
        }
    }

    fun addAppendParams(key: String?, value: String?): PostFileBuilder {
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

    override fun autoCancel(activity: Activity?): PostFileBuilder =this

    override fun addCacheControl(cacheControl: CacheControl): PostFileBuilder {
        this.cacheControl = cacheControl
        return this
    }
}