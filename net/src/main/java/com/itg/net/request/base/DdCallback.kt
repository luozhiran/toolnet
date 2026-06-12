package com.itg.net.request.base

interface DdCallback {
    fun onFailure(er: String?)

    fun onResponse(result: String?, code: Int)
}
