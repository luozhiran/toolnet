package com.itg.net.request.business

import com.itg.net.request.result.NetResult

internal class BusinessResultChain(
    private val interceptors: List<BusinessResultInterceptor>,
    override val response: NetResult.Success,
    override val envelope: ApiEnvelope,
    private val index: Int = 0
) : BusinessResultInterceptor.Chain {

    override fun proceed(): BusinessResult {
        if (index >= interceptors.size) {
            return if (envelope.success) {
                BusinessResult.Success(
                    envelope = envelope,
                    httpCode = response.code,
                    headers = response.headers
                )
            } else {
                BusinessResult.BusinessError(
                    envelope = envelope,
                    httpCode = response.code,
                    headers = response.headers
                )
            }
        }

        return interceptors[index].intercept(
            BusinessResultChain(
                interceptors = interceptors,
                response = response,
                envelope = envelope,
                index = index + 1
            )
        )
    }
}
