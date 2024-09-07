package com.itg.net.reqeust.get

import com.itg.net.reqeust.base.Builder
import okhttp3.CacheControl

interface GetBuilder: Builder {
    fun addParam(map:MutableMap<String,String?>?): Builder
    fun addParam(key: String?, value: String?): Builder
}