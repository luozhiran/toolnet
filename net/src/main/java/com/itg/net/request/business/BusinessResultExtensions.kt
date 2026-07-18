package com.itg.net.request.business

import com.itg.net.Net
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.NetResultCallback
import com.itg.net.request.result.sendResult

fun NetResult.toBusinessResult(): BusinessResult {
    return when (this) {
        is NetResult.Success -> {
            val config = Net.instance.ddNetConfig
            val envelope = config.businessResultParser.parse(body)
            BusinessResultChain(
                interceptors = config.getBusinessResultInterceptors(),
                response = this,
                envelope = envelope
            ).proceed()
        }
        is NetResult.HttpError -> BusinessResult.HttpError(this)
        is NetResult.NetworkError -> BusinessResult.NetworkError(this)
    }
}

fun ParamsBuilder.sendBusinessResult(callback: BusinessResultCallback?) {
    sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            when (val businessResult = result.toBusinessResult()) {
                is BusinessResult.Success -> callback?.onSuccess(businessResult)
                is BusinessResult.BusinessError -> callback?.onBusinessError(businessResult)
                is BusinessResult.HttpError -> callback?.onHttpError(businessResult)
                is BusinessResult.NetworkError -> callback?.onNetworkError(businessResult)
                is BusinessResult.Consumed -> callback?.onConsumed(businessResult)
            }
        }

        override fun onHttpError(error: NetResult.HttpError) {
            callback?.onHttpError(BusinessResult.HttpError(error))
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            callback?.onNetworkError(BusinessResult.NetworkError(error))
        }
    })
}
