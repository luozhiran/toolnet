package com.itg.net.request.business

import com.google.gson.reflect.TypeToken
import com.itg.net.Net
import com.itg.net.request.base.ParamsBuilder
import java.lang.reflect.Type

@Suppress("UNCHECKED_CAST")
fun <T> BusinessResult.toTypedBusinessResult(type: Type): TypedBusinessResult<T> {
    return when (this) {
        is BusinessResult.Success -> {
            runCatching {
                Net.instance.ddNetConfig.businessConverter.convert(dataRaw, type) as? T
            }.fold(
                onSuccess = { data ->
                    TypedBusinessResult.Success(
                        data = data,
                        envelope = envelope,
                        httpCode = httpCode,
                        headers = headers
                    )
                },
                onFailure = { error ->
                    TypedBusinessResult.DataConvertError(
                        error = error,
                        envelope = envelope,
                        httpCode = httpCode,
                        headers = headers
                    )
                }
            )
        }
        is BusinessResult.BusinessError -> TypedBusinessResult.BusinessError(
            envelope = envelope,
            httpCode = httpCode,
            headers = headers
        )
        is BusinessResult.HttpError -> TypedBusinessResult.HttpError(this)
        is BusinessResult.NetworkError -> TypedBusinessResult.NetworkError(this)
        is BusinessResult.Consumed -> TypedBusinessResult.Consumed(this)
    }
}

fun <T> ParamsBuilder.sendTypedBusinessResult(
    type: Type,
    callback: TypedBusinessResultCallback<T>?
) {
    sendBusinessResult(object : BusinessResultCallback {
        override fun onSuccess(result: BusinessResult.Success) {
            dispatchTypedResult(result.toTypedBusinessResult(type), callback)
        }

        override fun onBusinessError(error: BusinessResult.BusinessError) {
            dispatchTypedResult(error.toTypedBusinessResult(type), callback)
        }

        override fun onHttpError(error: BusinessResult.HttpError) {
            dispatchTypedResult(error.toTypedBusinessResult(type), callback)
        }

        override fun onNetworkError(error: BusinessResult.NetworkError) {
            dispatchTypedResult(error.toTypedBusinessResult(type), callback)
        }

        override fun onConsumed(result: BusinessResult.Consumed) {
            dispatchTypedResult(result.toTypedBusinessResult(type), callback)
        }
    })
}

inline fun <reified T> ParamsBuilder.sendTypedBusinessResult(
    callback: TypedBusinessResultCallback<T>?
) {
    sendTypedBusinessResult(object : TypeToken<T>() {}.type, callback)
}

inline fun <reified T> ParamsBuilder.sendBusinessResult(
    callback: TypedBusinessResultCallback<T>?
) {
    sendTypedBusinessResult(callback)
}

private fun <T> dispatchTypedResult(
    result: TypedBusinessResult<T>,
    callback: TypedBusinessResultCallback<T>?
) {
    when (result) {
        is TypedBusinessResult.Success -> callback?.onSuccess(result)
        is TypedBusinessResult.DataConvertError -> callback?.onDataConvertError(result)
        is TypedBusinessResult.BusinessError -> callback?.onBusinessError(result)
        is TypedBusinessResult.HttpError -> callback?.onHttpError(result)
        is TypedBusinessResult.NetworkError -> callback?.onNetworkError(result)
        is TypedBusinessResult.Consumed -> callback?.onConsumed(result)
    }
}
