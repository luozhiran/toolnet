package com.itg.net.request.result

interface NetResultCallback {
    fun onSuccess(result: NetResult.Success)
    fun onHttpError(error: NetResult.HttpError)
    fun onNetworkError(error: NetResult.NetworkError)
}
