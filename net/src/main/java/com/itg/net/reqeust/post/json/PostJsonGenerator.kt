package com.itg.net.reqeust.post.json

import android.app.Activity
import com.itg.net.reqeust.post.form.PostFormBuilder
import com.itg.net.tools.UrlTools
import okhttp3.Cookie

abstract class PostJsonGenerator: PostJsonBuilder() {



    override fun autoCancel(activity: Activity?): PostJsonGenerator {
        return this
    }

    override fun addHeader(key: String?, value: String?): PostJsonGenerator {
        super.addHeader(key, value)
        return this
    }

    override fun addHeader(map: MutableMap<String, String?>?): PostJsonGenerator {
        super.addHeader(map)
        return this
    }

    override fun url(url: String?): PostJsonGenerator {
        super.url(url)
        return this
    }

    override fun addCookie(cookie: Cookie?): PostJsonGenerator {
        super.addCookie(cookie)
        return this
    }

    override fun addCookie(cookie: List<Cookie?>?): PostJsonGenerator {
        super.addCookie(cookie)
        return this
    }

    override fun addTag(tag: String?): PostJsonGenerator {
        super.addTag(tag)
        return this
    }

    override fun path(path: String): PostJsonGenerator {
        super.path(path)
        return this
    }
}