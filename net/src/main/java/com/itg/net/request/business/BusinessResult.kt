package com.itg.net.request.business

import com.itg.net.request.result.NetResult

sealed class BusinessResult {
    abstract val httpCode: Int?
    abstract val rawBody: String?

    data class Success(
        val envelope: ApiEnvelope,
        override val httpCode: Int,
        val headers: Map<String, String> = emptyMap()
    ) : BusinessResult() {
        override val rawBody: String?
            get() = envelope.rawBody

        val dataRaw: String?
            get() = envelope.dataRaw
    }

    data class BusinessError(
        val envelope: ApiEnvelope,
        override val httpCode: Int,
        val headers: Map<String, String> = emptyMap()
    ) : BusinessResult() {
        override val rawBody: String?
            get() = envelope.rawBody

        val code: String?
            get() = envelope.code

        val message: String?
            get() = envelope.message
    }

    data class HttpError(
        val error: NetResult.HttpError
    ) : BusinessResult() {
        override val httpCode: Int
            get() = error.code

        override val rawBody: String?
            get() = error.body
    }

    data class NetworkError(
        val error: NetResult.NetworkError
    ) : BusinessResult() {
        override val httpCode: Int? = null

        override val rawBody: String?
            get() = error.body
    }

    data class InterceptorError(
        val error: Throwable,
        val index: Int,
        override val httpCode: Int?,
        override val rawBody: String?
    ) : BusinessResult()

    data class Consumed(
        val reason: String? = null,
        override val httpCode: Int? = null,
        override val rawBody: String? = null
    ) : BusinessResult()
}
