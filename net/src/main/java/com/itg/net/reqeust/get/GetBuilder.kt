package com.itg.net.reqeust.get

import com.itg.net.reqeust.base.Builder

interface GetBuilder: Builder {
    fun addParam(map:MutableMap<String,String?>?): Builder
    fun addParam(key: String?, value: String?): Builder
}