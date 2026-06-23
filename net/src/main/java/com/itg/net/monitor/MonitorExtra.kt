package com.itg.net.monitor

/**
 * 用户自定义业务字段的 OkHttp Typed Tag
 *
 * 通过 [okhttp3.Request.Builder.tag] 的 Class-key 机制传递到 [MonitorInterceptor]，
 * 透传到 [MonitorEvent.extra] 上报给后端。不占用通用的 request.tag(Any)。
 *
 * 与 [MonitorMarker] 和 [com.itg.net.encrypt.EncryptMarker] 保持相同的设计模式。
 *
 * @property value 业务自定义字符串，由用户自行定义格式（如 "orderId=123"、JSON 片段）
 */
class MonitorExtra(val value: String)
