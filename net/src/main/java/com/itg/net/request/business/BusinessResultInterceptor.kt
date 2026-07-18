package com.itg.net.request.business

import com.itg.net.request.result.NetResult

interface BusinessResultInterceptor {
    fun intercept(chain: Chain): BusinessResult

    interface Chain {
        val response: NetResult.Success
        val envelope: ApiEnvelope
        fun proceed(): BusinessResult
    }
}
