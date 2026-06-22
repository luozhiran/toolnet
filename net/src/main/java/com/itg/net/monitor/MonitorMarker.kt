package com.itg.net.monitor

/**
 * OkHttp 请求级监控控制的 Typed Tag
 *
 * 通过 [okhttp3.Request.Builder.tag] 的 Class-key 机制设置到 Request 上，
 * 供 [MonitorInterceptor] 读取，不占用通用的 request.tag(Any)。
 *
 * 与 [com.itg.net.encrypt.EncryptMarker] 保持相同的设计模式。
 *
 * @property value "MONITOR"=强制监控, "SKIP"=强制跳过, null=使用全局配置
 */
class MonitorMarker(val value: String?) {

    companion object {
        /** 强制监控（无视全局 enabled=false） */
        const val MONITOR = "__monitor_force__"

        /** 强制跳过（无视全局 enabled=true） */
        const val SKIP = "__monitor_skip__"

        /** 从 ParamsBuilder.monitorFlag 创建标记 */
        fun fromFlag(flag: String?): MonitorMarker? {
            return if (flag != null) MonitorMarker(flag) else null
        }
    }
}
