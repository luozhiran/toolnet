package com.itg.net.reqeust.post.file

import android.app.Activity
import com.itg.net.Net
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.tools.UrlTools
import okhttp3.CacheControl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

abstract class PostFileBuilder : ParamsBuilder() {
    private val files = mutableListOf<File?>()
    private val fileNames = mutableListOf<String?>()
    private val fileMediaTypes = mutableListOf<String?>()

    //断点续传时使用
    private var intervalOffset: Long = 0
    private val urlParams = StringBuilder()

    internal fun addFile1(file: File?): PostFileBuilder = addFile1("file", file)
    internal fun addFile1(fileName: String?, file: File?): PostFileBuilder =
        addFile1(fileName, "", file)

    internal fun addFile1(fileName: String?, mediaType: String?, file: File?): PostFileBuilder {
        files.add(file)
        fileNames.add(fileName)
        fileMediaTypes.add(mediaType)
        return this
    }

    protected open fun getRequestBody(): RequestBody? {
        return getRequestBody(0)
    }

    fun getRequestBody(index: Int): RequestBody {
        val file = files.getOrNull(index) ?: File("")
        val mediaType = if (fileMediaTypes.size > index) {
            fileMediaTypes[index]?.takeIf { it.isNotBlank() }?.toMediaTypeOrNull()
        } else {
            getFileType(file.name)
        }
        return file.asRequestBody(mediaType)
    }

    internal fun getFile(index: Int): File? {
        return files.getOrNull(index)
    }

    internal fun getFileName(index: Int): String? {
        return fileNames.getOrNull(index)
    }

    internal fun getFileRealName(index: Int): String? {
        return files.getOrNull(index)?.name
    }

    internal fun getCount(): Int {
        return files.size
    }

    fun addResumeFileOffset1(intervalOffset: Long) {
        this.intervalOffset = intervalOffset
    }

    protected fun getResumeFileOffset1(): Long {
        return this.intervalOffset
    }

    private fun getFileType(fileName: String): MediaType? {
        val lowerFileName = fileName.lowercase()
        return if (lowerFileName.endsWith(".png")) {
            "image/png".toMediaTypeOrNull()
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
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
        val urlParamsMap = UrlTools.cutOffStrToMap(urlParams.toString())
        val totalParamsMap = mutableMapOf<String, Any?>()
        if (!this.noGlobalParams) {
            totalParamsMap.putAll(Net.instance.ddNetConfig.globalParams)
        }
        urlParamsMap?.let { totalParamsMap.putAll(it) }
        return UrlTools.getSpliceUrl(totalParamsMap, this.url ?: "")
    }

    override fun autoCancel(activity: Activity?): PostFileBuilder =this

    override fun addCacheControl(cacheControl: CacheControl): PostFileBuilder {
        this.cacheControl = cacheControl
        return this
    }
}
