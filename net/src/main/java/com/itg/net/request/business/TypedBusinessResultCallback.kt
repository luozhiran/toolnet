package com.itg.net.request.business

interface TypedBusinessResultCallback<T> {
    fun onSuccess(result: TypedBusinessResult.Success<T>)
    fun onDataConvertError(error: TypedBusinessResult.DataConvertError)
    fun onBusinessError(error: TypedBusinessResult.BusinessError)
    fun onHttpError(error: TypedBusinessResult.HttpError)
    fun onNetworkError(error: TypedBusinessResult.NetworkError)
    fun onConsumed(result: TypedBusinessResult.Consumed) = Unit
}
