package com.itg.net.request.result

import java.io.IOException

sealed class NetResult {
    abstract val code: Int?
    abstract val body: String?

    data class Success(
        override val body: String?,
        override val code: Int,
        val headers: Map<String, String> = emptyMap()
    ) : NetResult()

    data class HttpError(
        override val body: String?,
        override val code: Int,
        val message: String?,
        val headers: Map<String, String> = emptyMap()
    ) : NetResult()

    data class NetworkError(
        val error: IOException,
        override val body: String? = null
    ) : NetResult() {
        override val code: Int? = null
        val message: String?
            get() = error.message
    }
}
