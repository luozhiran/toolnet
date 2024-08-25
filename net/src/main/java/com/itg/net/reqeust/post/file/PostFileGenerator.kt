package com.itg.net.reqeust.post.file

import android.app.Activity
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.reqeust.post.content.PostContentGenerator
import okhttp3.Cookie
import java.io.File

/**
 * 上传一个文件
 */
abstract class PostFileGenerator : PostFileBuilder() {

    fun addFile(file: File?): ParamsBuilder {
         super.addFile1(file)
        return this
    }

    fun addFile(fileName: String?, file: File?): ParamsBuilder {
        super.addFile1(fileName, file)
        return this
    }

    fun addFile(fileName: String?, mediaType: String?, file: File?): ParamsBuilder {
        super.addFile1(fileName, mediaType, file)
        return this
    }

    override fun autoCancel(activity: Activity?): PostFileGenerator =this

    override fun addHeader(key: String?, value: String?): ParamsBuilder {
         super.addHeader(key, value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): ParamsBuilder {
         super.addHeader(map)
        return this
    }

    override fun url(url: String?): ParamsBuilder {
         super.url(url)
        return this
    }

    override fun addCookie(cookie: Cookie?): ParamsBuilder {
         super.addCookie(cookie)
        return this
    }

    override fun addCookie(cookie: List<Cookie?>?): ParamsBuilder {
         super.addCookie(cookie)
        return this
    }

    override fun addTag(tag: String?): ParamsBuilder {
         super.addTag(tag)
        return this
    }
    override fun path(path: String): ParamsBuilder {
        super.path(path)
        return this
    }

    override fun noUseGlobalParams(): ParamsBuilder {
        super.noUseGlobalParams()
        return this
    }

}