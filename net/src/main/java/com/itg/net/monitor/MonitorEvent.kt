package com.itg.net.monitor

import org.json.JSONObject

/**
 * 单次网络请求的监控事件（精简版）
 *
 * 涵盖请求元信息、响应结果、异常类型和时序数据。
 * 序列化为 JSON 上报到监控服务器。
 *
 * 注：精确的 DNS/TCP/TLS 阶段耗时需配合 OkHttp EventListener 获取，
 *     拦截器层暂时只保留总耗时。详见进阶优化章节。
 */
data class MonitorEvent(
    // ===== 请求标识 =====
    val requestId: String,          // 唯一标识（非加密级随机 ID）
    val url: String,                // 经 urlSanitizer 脱敏后的 URL
    val method: String,             // GET / POST / PUT / DELETE
    val tag: String?,               // 请求 tag（业务标识）

    // ===== 时序数据（毫秒） =====
    val requestStartMs: Long,       // 请求开始时间（进入拦截器）
    val requestEndMs: Long,         // 请求结束时间（收到响应/异常）
    val totalCostMs: Long,          // 总耗时

    // ===== 响应信息 =====
    val httpCode: Int,              // HTTP 状态码（异常时为 -1）
    val responseBodySize: Long,     // 响应体大小（字节）
    val contentType: String?,       // Content-Type

    // ===== 错误信息 =====
    val isSuccess: Boolean,         // 是否成功（无异常且 HTTP 2xx）
    val errorType: ErrorType,       // 错误分类枚举
    val errorMessage: String?,      // 异常消息
    val exceptionClass: String?,    // 异常类名（如 SocketTimeoutException）

    // ===== 网络环境（取自缓存） =====
    val networkType: String?,       // WIFI / CELLULAR / ETHERNET / NONE
    val carrierName: String?,       // 运营商名称（预留，当前为 null）
    val eventStage: String = "HTTP",    // HTTP / DOWNLOAD

    // ===== 下载专用字段（Phase 3 新增） =====
    val downloadSize: Long = 0,         // 已下载字节数
    val contentLength: Long = 0,        // 文件总大小（Content-Length）
    val isAppend: Boolean = false,      // 是否断点续传
    val retryCount: Int = 0,            // 当前重试次数
    val downloadSpeed: Long = 0,        // 平均下载速度 (bytes/s)，totalCostMs>0 时计算
    val downloadError: ErrorType = ErrorType.NONE  // 下载阶段独立错误类型
) {
    enum class ErrorType {
        /** 成功 */
        NONE,
        /** DNS 解析失败（UnknownHostException） */
        DNS_ERROR,
        /** TCP 连接超时（ConnectException） */
        CONNECT_TIMEOUT,
        /** TCP 连接被拒绝 */
        CONNECT_REFUSED,
        /** TLS/SSL 握手失败（SSLException） */
        SSL_ERROR,
        /** 超时（SocketTimeoutException，拦截器层无法精确区分读/写超时） */
        TIMEOUT,
        /** 连接池耗尽 / 无路由 */
        NO_ROUTE,
        /** HTTP 4xx 客户端错误 */
        HTTP_CLIENT_ERROR,
        /** HTTP 5xx 服务端错误 */
        HTTP_SERVER_ERROR,
        /** 响应体解析失败（如 JSON 格式错误） */
        PARSE_ERROR,
        /** 请求被取消 */
        CANCELLED,
        /** 下载流读取中断（InputStream.read() 抛异常） */
        DOWNLOAD_STREAM_ERROR,
        /** 磁盘写入失败 */
        DISK_WRITE_ERROR,
        /** MD5 校验失败 */
        MD5_MISMATCH,
        /** 未知错误 */
        UNKNOWN
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("requestId", requestId)
        put("url", url)
        put("method", method)
        put("tag", tag ?: "")
        put("totalCostMs", totalCostMs)
        put("httpCode", httpCode)
        put("responseBodySize", responseBodySize)
        put("contentType", contentType ?: "")
        put("isSuccess", isSuccess)
        put("errorType", errorType.name)
        put("errorMessage", errorMessage ?: "")
        put("exceptionClass", exceptionClass ?: "")
        put("networkType", networkType ?: "")
        put("carrierName", carrierName ?: "")
        put("eventStage", eventStage)
        put("timestamp", requestStartMs)
        // 下载专用字段（普通请求为默认值 0/false/NONE）
        if (downloadSize > 0) put("downloadSize", downloadSize)
        if (contentLength > 0) put("contentLength", contentLength)
        if (isAppend) put("isAppend", true)
        if (retryCount > 0) put("retryCount", retryCount)
        if (downloadSpeed > 0) put("downloadSpeed", downloadSpeed)
        if (downloadError != ErrorType.NONE) put("downloadError", downloadError.name)
    }
}
