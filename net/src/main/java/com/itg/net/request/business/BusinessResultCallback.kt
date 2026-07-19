package com.itg.net.request.business

interface BusinessResultCallback {
    fun onSuccess(result: BusinessResult.Success)
    fun onBusinessError(error: BusinessResult.BusinessError)
    fun onHttpError(error: BusinessResult.HttpError)
    fun onResponseTooLarge(error: BusinessResult.ResponseTooLarge) = Unit
    fun onNetworkError(error: BusinessResult.NetworkError)
    fun onInterceptorError(error: BusinessResult.InterceptorError) = Unit
    fun onConsumed(result: BusinessResult.Consumed) = Unit
}
