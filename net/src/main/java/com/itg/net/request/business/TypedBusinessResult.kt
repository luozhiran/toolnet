package com.itg.net.request.business

/**
 * 类型化业务结果。
 *
 * 与 [BusinessResult] 的区别是：[Success.data] 已经由业务数据转换器
 * 从 [ApiEnvelope.dataRaw] 转换成调用方声明的数据类型。
 */
sealed class TypedBusinessResult<out T> {
    abstract val httpCode: Int?
    abstract val rawBody: String?

    data class Success<T>(
        val data: T?,
        val envelope: ApiEnvelope,
        override val httpCode: Int,
        val headers: Map<String, String> = emptyMap()
    ) : TypedBusinessResult<T>() {
        override val rawBody: String?
            get() = envelope.rawBody

        val dataRaw: String?
            get() = envelope.dataRaw
    }

    data class DataConvertError(
        val error: Throwable,
        val envelope: ApiEnvelope,
        override val httpCode: Int,
        val headers: Map<String, String> = emptyMap()
    ) : TypedBusinessResult<Nothing>() {
        override val rawBody: String?
            get() = envelope.rawBody

        val dataRaw: String?
            get() = envelope.dataRaw
    }

    data class BusinessError(
        val envelope: ApiEnvelope,
        override val httpCode: Int,
        val headers: Map<String, String> = emptyMap()
    ) : TypedBusinessResult<Nothing>() {
        override val rawBody: String?
            get() = envelope.rawBody

        val code: String?
            get() = envelope.code

        val message: String?
            get() = envelope.message
    }

    data class HttpError(
        val error: BusinessResult.HttpError
    ) : TypedBusinessResult<Nothing>() {
        override val httpCode: Int
            get() = error.httpCode

        override val rawBody: String?
            get() = error.rawBody
    }

    data class NetworkError(
        val error: BusinessResult.NetworkError
    ) : TypedBusinessResult<Nothing>() {
        override val httpCode: Int? = null

        override val rawBody: String?
            get() = error.rawBody
    }

    data class ResponseTooLarge(
        val error: BusinessResult.ResponseTooLarge
    ) : TypedBusinessResult<Nothing>() {
        override val httpCode: Int?
            get() = error.httpCode

        override val rawBody: String?
            get() = error.rawBody
    }

    data class InterceptorError(
        val error: BusinessResult.InterceptorError
    ) : TypedBusinessResult<Nothing>() {
        override val httpCode: Int?
            get() = error.httpCode

        override val rawBody: String?
            get() = error.rawBody
    }

    data class Consumed(
        val result: BusinessResult.Consumed
    ) : TypedBusinessResult<Nothing>() {
        override val httpCode: Int?
            get() = result.httpCode

        override val rawBody: String?
            get() = result.rawBody

        val reason: String?
            get() = result.reason
    }
}
