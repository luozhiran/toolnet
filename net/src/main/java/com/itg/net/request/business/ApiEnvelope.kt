package com.itg.net.request.business

/**
 * 从 HTTP 成功响应体中解析出的业务协议包装。
 *
 * 这个类型描述的是 App 和服务端约定的业务协议，不是 HTTP 协议。
 * 例如 HTTP 状态码是 200 时，如果业务码表示登录失效或无权限，
 * [success] 仍然应该是 false。
 */
data class ApiEnvelope(
    /**
     * 从响应体中解析出的业务状态码。
     *
     * 默认解析器会读取 `code`、`status` 等字段。
     */
    val code: String?,

    /**
     * 从响应体中解析出的业务提示信息。
     *
     * 默认解析器会读取 `message`、`msg`、`error` 等字段。
     */
    val message: String?,

    /**
     * 业务数据字段的原始字符串。
     *
     * 默认解析器会读取 `data`、`result` 等字段。
     * 业务成功后，调用方可以继续把这个值反序列化成自己的数据模型。
     */
    val dataRaw: String?,

    /**
     * 完整的原始响应体。
     *
     * 适合用于日志输出、问题排查和兜底解析。
     */
    val rawBody: String?,

    /**
     * 业务协议层是否认为这次响应成功。
     *
     * 这个值独立于 HTTP 状态码。HTTP 可能是 200，
     * 但业务层仍然可能因为登录失效、权限不足等原因失败。
     */
    val success: Boolean
)
