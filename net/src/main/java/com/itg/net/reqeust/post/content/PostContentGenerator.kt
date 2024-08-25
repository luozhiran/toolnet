package com.itg.net.reqeust.post.content

import android.app.Activity
import com.itg.net.reqeust.post.form.PostFormBuilder
import com.itg.net.reqeust.post.json.PostJsonBuilder
import okhttp3.Cookie

abstract class PostContentGenerator : PostContentBuilder() {

    fun addContent(content: String?, mediaType: String?): PostContentBuilder {
        addRealContent(content, mediaType)
        return this
    }

    fun addContent(content: String?, contentFlag: String?, mediaType: String?): PostContentGenerator {
        addRealContent(content, contentFlag, mediaType)
        return this
    }

    override fun autoCancel(activity: Activity?): PostContentGenerator {
        return this
    }

    override fun addHeader(key: String?, value: String?): PostContentGenerator {
        super.addHeader(key, value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): PostContentGenerator {
        super.addHeader(map)
        return this
    }

    override fun url(url: String?): PostContentGenerator {
        super.url(url)
        return this
    }

    override fun addCookie(cookie: Cookie?): PostContentGenerator {
        super.addCookie(cookie)
        return this
    }

    override fun addCookie(cookie: List<Cookie?>?): PostContentGenerator {
        super.addCookie(cookie)
        return this
    }

    override fun addTag(tag: String?): PostContentGenerator {
        super.addTag(tag)
        return this
    }

    override fun path(path: String): PostContentGenerator {
        super.path(path)
        return this
    }

    override fun noUseGlobalParams(): PostContentGenerator {
        super.noUseGlobalParams()
        return this
    }

}