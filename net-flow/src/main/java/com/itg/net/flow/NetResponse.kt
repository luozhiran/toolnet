package com.itg.net.flow

/**
 * 类型化网络响应包装
 *
 * 封装 HTTP 响应的状态码、原始响应体、反序列化后的 body 以及响应头，
 * 通过 [isSuccessful] 提供便捷的成功判断。
 *
 * @param T 反序列化后的业务数据类型
 * @property body 反序列化后的对象，不需要反序列化时与 [rawBody] 相同
 * @property rawBody 原始响应字符串，始终保留，方便调试和 fallback
 * @property code HTTP 状态码
 * @property headers 响应头键值对
 */
data class NetResponse<T>(
    val body: T?,
    val rawBody: String?,
    val code: Int,
    val headers: Map<String, String> = emptyMap()
) {

    /**
     * HTTP 状态码是否在 2xx 范围内
     */
    val isSuccessful: Boolean
        get() = code in 200..299

    companion object {

        /**
         * 从原始响应字符串和状态码构建 [NetResponse]（body = rawBody）
         */
        fun <T> from(rawBody: String?, code: Int, headers: Map<String, String> = emptyMap()): NetResponse<String> {
            return NetResponse(
                body = rawBody,
                rawBody = rawBody,
                code = code,
                headers = headers
            )
        }
    }
}
