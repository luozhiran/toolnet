package com.itg.net.request.post.file

import android.app.Activity
import com.itg.net.Net
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.util.UrlTools
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

    internal fun addFile1(fileName: String?, file: File?): PostFileBuilder {
        return addFile1(fileName, "", file)
    }

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
        val mediaType = fileMediaTypes.getOrNull(index)
            ?.takeIf { it.isNotBlank() }
            ?.toMediaTypeOrNull()
            ?: getFileType(file.name)
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
        return intervalOffset
    }

    fun addAppendParams(key: String?, value: String?): PostFileBuilder {
        UrlTools.appendUrlParamsToStr(urlParams, key, value)
        return this
    }

    protected fun getAppendParams(): StringBuilder {
        return urlParams
    }

    internal fun getUrl(): String {
        val urlParamsMap = UrlTools.cutOffStrToMap(urlParams.toString())
        val totalParamsMap = mutableMapOf<String, Any?>()
        if (!noGlobalParams) {
            totalParamsMap.putAll(Net.instance.ddNetConfig.globalParams)
        }
        urlParamsMap?.let { totalParamsMap.putAll(it) }
        return UrlTools.getSpliceUrl(totalParamsMap, url ?: "")
    }

    override fun autoCancel(activity: Activity?): PostFileBuilder = this

    override fun addCacheControl(cacheControl: CacheControl): PostFileBuilder {
        this.cacheControl = cacheControl
        return this
    }

    private fun getFileType(fileName: String): MediaType? {
        val lowerFileName = fileName.lowercase()
        return when {
            lowerFileName.endsWith(".png") -> "image/png".toMediaTypeOrNull()
            lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") -> {
                "image/jpeg".toMediaTypeOrNull()
            }
            else -> "application/octet-stream".toMediaTypeOrNull()
        }
    }
}
