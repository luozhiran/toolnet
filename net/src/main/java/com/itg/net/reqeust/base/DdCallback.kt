package com.itg.net.reqeust.base

interface DdCallback {
    fun onFailure(er: String?)

    fun onResponse(result: String?, code: Int)
}