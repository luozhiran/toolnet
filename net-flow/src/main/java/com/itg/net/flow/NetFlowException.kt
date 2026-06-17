package com.itg.net.flow

import java.io.IOException

/**
 * 网络请求 Flow 异常
 *
 * 携带 HTTP 状态码信息，用于在 [kotlinx.coroutines.flow.Flow] 中
 * 通过异常通道传递网络错误。
 *
 * @property code HTTP 状态码，非 HTTP 错误（如网络断开）为 null
 * @property message 错误描述信息
 */
class NetFlowException(
    val code: Int?,
    override val message: String?
) : IOException(message)
