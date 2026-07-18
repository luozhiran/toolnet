package com.itg.net.monitor

import android.util.Log

/**
 * 网络监控全局配置
 *
 * 通过 [com.itg.net.config.NetConfig.monitor] DSL 方法设置，
 * 与 [com.itg.net.config.NetConfig.encrypt] 保持一致的使用风格。
 *
 * ## 使用示例
 * ```
 * Net.instance.configure {
 *     monitor {
 *         enabled(true)
 *         reportUrl("https://monitor.example.com/api/report")
 *         reportMode(ReportMode.FAILURE_ONLY)
 *         sampleRate(1.0f)  // 全量采样
 *     }
 * }
 * ```
 */
class MonitorConfig {
    companion object {
        private const val TAG = "MonitorConfig"
    }

    /** 全局开关：是否启用监控 */
    @Volatile var enabled: Boolean = false

    /** 监控数据上报地址（默认上报器使用） */
    @Volatile var reportUrl: String? = null
        set(value) {
            if (value != null && !value.startsWith("https://")) {
                Log.w(TAG, "reportUrl should use HTTPS for security, got: $value")
            }
            field = value
        }

    /** 上报模式 */
    @Volatile var reportMode: ReportMode = ReportMode.FAILURE_ONLY

    /** 采样率 0.0 ~ 1.0，1.0 表示全量 */
    @Volatile var sampleRate: Float = 1.0f

    /** 单次批量上报的最大事件数（默认上报器使用） */
    @Volatile var batchSize: Int = 20

    /** 上报时间窗口（毫秒），超过此时间即使未满 batchSize 也触发上报 */
    @Volatile var flushIntervalMs: Long = 10_000L

    /** 内存队列最大容量（默认上报器使用） */
    @Volatile var maxQueueSize: Int = 1000

    /**
     * 慢请求阈值（毫秒），超过此阈值的成功请求也会记录
     * 设为 0 表示不启用慢请求检测
     */
    @Volatile var slowRequestThresholdMs: Long = 0

    // ==================== 安全性扩展 ====================

    /**
     * URL 脱敏函数，在 MonitorEvent.toJson() 前调用
     *
     * 默认移除常见敏感 query 参数（token, sessionId, jsessionid, auth, key, secret, password）。
     * 业务方可替换为自定义实现。
     *
     * ## 使用示例
     * ```kotlin
     * monitor {
     *     urlSanitizer = { url -> MySecurityUtil.sanitizeUrl(url) }
     * }
     * ```
     */
    @Volatile var urlSanitizer: (String) -> String = { url ->
        url.replace(
            Regex(
                "([?&])(token|sessionId|jsessionid|auth|key|secret|password|access_token|api_key)=[^&]*",
                RegexOption.IGNORE_CASE
            ),
            "$1$2=***"
        )
    }

    // ==================== 自定义上报 ====================

    /**
     * 自定义上报处理器
     *
     * - 当此值为 **null** 时（默认），框架自动创建 [DefaultMonitorReportHandler]
     *   并使用 [reportUrl]、[batchSize]、[flushIntervalMs]、[maxQueueSize] 参数
     * - 当业务方注入自定义实现时，**完全接管**上报行为，
     *   上述四个参数不再生效（由自定义实现自行处理）
     *
     * ## 使用示例
     * ```kotlin
     * // 示例1：使用默认上报（无需设置 reportHandler）
     * monitor {
     *     enabled(true)
     *     reportUrl("https://monitor.example.com/api/report")
     * }
     *
     * // 示例2：注入自定义上报处理器
     * monitor {
     *     enabled(true)
     *     reportHandler(MyCustomReportHandler())
     * }
     * ```
     */
    @Volatile var reportHandler: IMonitorReportHandler? = null

    /**
     * 开启或关闭网络监控，默认开启。
     */
    fun enabled(enabled: Boolean = true): MonitorConfig {
        this.enabled = enabled
        return this
    }

    /**
     * 关闭网络监控。
     */
    fun disabled(): MonitorConfig {
        enabled = false
        return this
    }

    /**
     * 设置监控数据上报地址。
     */
    fun reportUrl(url: String?): MonitorConfig {
        reportUrl = url
        return this
    }

    /**
     * 设置上报模式。
     */
    fun reportMode(mode: ReportMode): MonitorConfig {
        reportMode = mode
        return this
    }

    /**
     * 设置采样率，范围会被限制在 0.0 到 1.0。
     */
    fun sampleRate(rate: Float): MonitorConfig {
        sampleRate = rate.coerceIn(0.0f, 1.0f)
        return this
    }

    /**
     * 设置单次批量上报的最大事件数。
     */
    fun batchSize(size: Int): MonitorConfig {
        batchSize = size.coerceAtLeast(1)
        return this
    }

    /**
     * 设置批量上报的时间窗口。
     */
    fun flushIntervalMs(intervalMs: Long): MonitorConfig {
        flushIntervalMs = intervalMs.coerceAtLeast(1L)
        return this
    }

    /**
     * 设置内存队列最大容量。
     */
    fun maxQueueSize(size: Int): MonitorConfig {
        maxQueueSize = size.coerceAtLeast(1)
        return this
    }

    /**
     * 设置慢请求阈值。传 0 表示不启用慢请求检测。
     */
    fun slowRequestThresholdMs(thresholdMs: Long): MonitorConfig {
        slowRequestThresholdMs = thresholdMs.coerceAtLeast(0L)
        return this
    }

    /**
     * 设置 URL 脱敏函数。
     */
    fun urlSanitizer(sanitizer: (String) -> String): MonitorConfig {
        urlSanitizer = sanitizer
        return this
    }

    /**
     * 设置自定义上报处理器。
     */
    fun reportHandler(handler: IMonitorReportHandler?): MonitorConfig {
        reportHandler = handler
        return this
    }
}

/**
 * 上报模式
 */
enum class ReportMode {
    /** 仅上报失败请求 */
    FAILURE_ONLY,
    /** 上报所有请求（成功 + 失败） */
    ALL,
    /** 仅上报成功但慢的请求（与 MonitorConfig.slowRequestThresholdMs 配合） */
    SLOW_ONLY;

    /**
     * 快速判断是否需要上报（不依赖 MonitorEvent 对象）
     *
     * @param isSuccess 请求是否成功
     * @param totalCostMs 请求总耗时
     * @param slowThresholdMs 慢请求阈值
     */
    fun shouldReport(isSuccess: Boolean, totalCostMs: Long, slowThresholdMs: Long): Boolean {
        return when (this) {
            FAILURE_ONLY -> !isSuccess
            ALL -> true
            SLOW_ONLY -> isSuccess && totalCostMs > slowThresholdMs
        }
    }
}
