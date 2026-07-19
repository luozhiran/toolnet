package com.itg.net.request.result

interface NetResultCallback {
    fun onSuccess(result: NetResult.Success)
    fun onHttpError(error: NetResult.HttpError)
    fun onResponseTooLarge(error: NetResult.ResponseTooLarge) = Unit
    fun onNetworkError(error: NetResult.NetworkError)
}
