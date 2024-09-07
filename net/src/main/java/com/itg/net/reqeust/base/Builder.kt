package com.itg.net.reqeust.base

import android.app.Activity
import okhttp3.CacheControl
import okhttp3.Cookie

/**
 * get和post的公共参数
 */
interface Builder {
    fun addHeader(key: String?, value: String?): Builder
    fun addHeader(map:MutableMap<String,String?>?): Builder
    fun url(url: String?): Builder
    fun addCookie(cookie: Cookie?): Builder
    fun addCookie(cookie: List<Cookie?>?): Builder
    fun addTag(tag: String?): Builder
    fun autoCancel(activity: Activity?): Builder
    fun path(path:String):Builder
    fun noUseGlobalParams():Builder
    fun addCacheControl(cacheControl: CacheControl):Builder;
}