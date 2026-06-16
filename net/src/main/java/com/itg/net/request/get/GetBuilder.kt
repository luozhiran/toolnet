package com.itg.net.request.get

import com.itg.net.request.base.Builder
import okhttp3.CacheControl

interface GetBuilder: Builder {
    fun addParam(map:MutableMap<String,String?>?): Builder
    fun addParam(key: String?, value: String?): Builder
}
