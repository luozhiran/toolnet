package com.itg.net.monitor

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 默认的监控事件上报处理器（优化版）
 *
 * 实现 [IMonitorReportHandler] 接口，提供开箱即用的上报能力。
 * 业务方未注入自定义 Handler 时，框架自动使用此实现。
 *
 * ## 核心机制（Phase 2 优化后）
 * - **内存缓冲**：使用 [LinkedBlockingQueue] 缓存事件，默认容量 1000
 * - **批量上报**：攒够 batchSize 条或到达 flushIntervalMs 时间窗口，触发批量 HTTP POST
 * - **按需唤醒**：batch 为空时使用 take() 无限阻塞（零 CPU），非空时定期 poll 检查 flush 窗口
 * - **异步上报**：HTTP POST 使用 OkHttp 异步 enqueue()，不阻塞消费者线程
 * - **熔断器**：连续失败 N 次后冷却 M 秒，避免持续阻塞
 * - **简化降级**：队列满直接丢弃，不做 poll+offer 双重操作
 *
 * ## 线程模型
 * ```
 * MonitorInterceptor worker thread → onEvent(event) → LinkedBlockingQueue
 *                                                           │
 * Consumer thread (monitor-reporter) ← take()/poll() ← ───┘
 *                                     │
 *                                     ▼ (异步)
 *                             POST reportUrl (OkHttp enqueue)
 * ```
 *
 * @param reportUrl 监控数据接收地址（建议使用 HTTPS）
 * @param batchSize 单次批量上报的最大事件数，默认 20
 * @param flushIntervalMs 上报时间窗口（毫秒），默认 10 秒
 * @param maxQueueSize 内存队列最大容量，默认 1000
 */
class DefaultMonitorReportHandler(
    private val reportUrl: String,
    private val batchSize: Int = 20,
    private val flushIntervalMs: Long = 10_000L,
    private val maxQueueSize: Int = 1000
) : IMonitorReportHandler {

    companion object {
        private const val TAG = "DefaultMonitorReport"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val safeBatchSize = batchSize.coerceAtLeast(1)
    private val safeFlushIntervalMs = flushIntervalMs.coerceAtLeast(1L)
    private val safeMaxQueueSize = maxQueueSize.coerceAtLeast(1)

    /** 事件缓冲队列（有界阻塞队列） */
    private val queue: BlockingQueue<MonitorEvent> = LinkedBlockingQueue(safeMaxQueueSize)

    /** 是否正在运行 */
    private val running = AtomicBoolean(true)

    /** 强制刷新标志：flush() 时设为 true，消费者线程检测后立即 flush 当前 batch */
    @Volatile
    private var forceFlush = false

    // ==================== 熔断器 ====================
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntil = AtomicLong(0)

    /** 连续失败多少次后熔断 */
    private val maxConsecutiveFailures = 5

    /** 熔断冷却时间初始值（毫秒），后续指数退避翻倍 */
    private val initialCooldownMs = 30_000L

    /** 熔断冷却时间最大值（毫秒） */
    private val maxCooldownMs = 300_000L  // 5 分钟

    /** 当前冷却时间（指数退避时动态调整） */
    @Volatile
    private var currentCooldownMs: Long = initialCooldownMs

    /** 半开探测进行中：冷却到期后只放行一个请求探测，成功则关闭熔断，失败则重新熔断 */
    @Volatile
    private var probeInFlight = false

    /**
     * 熔断器检查，支持全开/半开/关闭三态：
     * - 全开：连续失败达阈值且冷却时间未到 → 丢弃所有请求
     * - 半开：冷却时间已到 → 放行一个探测请求：
     *         已有探测进行中 → 其余请求阻塞等待探测结果
     *         无探测 → 当前请求抢占探测机会，通过
     * - 关闭：失败计数未达阈值 → 正常上报
     */
    private fun isCircuitOpen(): Boolean {
        val failures = consecutiveFailures.get()
        if (failures < maxConsecutiveFailures) {
            return false  // 关闭
        }
        val now = System.currentTimeMillis()
        val openUntil = circuitOpenUntil.get()
        if (now < openUntil) {
            return true   // 全开，冷却中
        }
        // 冷却到期 → 半开状态
        if (probeInFlight) {
            return true   // 已有探测进行中，其余请求等待
        }
        // 无探测 → 尝试抢占探测机会，抢到则通过（返回 false），未抢到则阻塞（返回 true）
        return !tryStartProbe()
    }

    /** 尝试开始探测，返回 true 表示抢到探测机会 */
    private fun tryStartProbe(): Boolean {
        synchronized(this) {
            if (probeInFlight) return false
            probeInFlight = true
            return true
        }
    }

    /** 探测结束 */
    private fun endProbe() {
        probeInFlight = false
    }

    /** 上报成功：重置失败计数 + 冷却时间回归初始值 */
    private fun recordSuccess() {
        consecutiveFailures.set(0)
        currentCooldownMs = initialCooldownMs
    }

    /** 上报失败：递增失败计数，达到阈值时触发熔断并指数退避冷却时间 */
    private fun recordFailure(detail: String) {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= maxConsecutiveFailures) {
            // 指数退避：每次重新熔断冷却时间翻倍，上限 maxCooldownMs
            val newCooldown = minOf(currentCooldownMs * 2, maxCooldownMs)
            currentCooldownMs = newCooldown
            circuitOpenUntil.set(System.currentTimeMillis() + newCooldown)
            Log.w(TAG, "Circuit opened for ${newCooldown}ms " +
                "after $failures consecutive failures ($detail)")
        } else {
            Log.w(TAG, "Report failed ($failures/$maxConsecutiveFailures): $detail")
        }
    }

    /** 上报使用的 OkHttpClient（独立实例，避免与业务共用连接池） */
    private val reportClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 消费者线程：batch 为空时 take() 无限阻塞（零 CPU），非空时 poll 定期检查 flush 窗口 */
    private val consumerThread: Thread = Thread({
        val batch = mutableListOf<MonitorEvent>()
        var lastFlushTime = System.currentTimeMillis()

        fun flushBatchIfRequested() {
            if (!forceFlush) return
            forceFlush = false
            if (batch.isNotEmpty()) {
                doFlushAsync(batch.toList())
                batch.clear()
                lastFlushTime = System.currentTimeMillis()
            }
        }

        while (running.get()) {
            try {
                // flush() 会 interrupt 唤醒线程，先处理已取出的 batch，避免继续阻塞到时间窗口结束。
                flushBatchIfRequested()

                // batch 为空 → take() 无限阻塞，直到事件到达（零 CPU，不溢出）
                // batch 非空 → poll(flushIntervalMs) 等待 flush 窗口到期，到期自动唤醒检查
                // forceFlush 通过 interrupt 精准唤醒，避免高频轮询。
                val event = if (batch.isEmpty()) {
                    queue.take()
                } else {
                    queue.poll(safeFlushIntervalMs, TimeUnit.MILLISECONDS)
                }

                if (event != null) {
                    batch.add(event)
                }

                // 检测 forceFlush 标志：外部 flush() 要求立即排空当前 batch
                flushBatchIfRequested()

                val shouldFlush = batch.size >= safeBatchSize ||
                    (batch.isNotEmpty() &&
                     System.currentTimeMillis() - lastFlushTime >= safeFlushIntervalMs)

                if (shouldFlush) {
                    doFlushAsync(batch.toList())
                    batch.clear()
                    lastFlushTime = System.currentTimeMillis()
                }
            } catch (e: InterruptedException) {
                if (!running.get()) {
                    Thread.currentThread().interrupt()
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Consumer error", e)
            }
        }

        // 退出前最后一次刷新
        if (batch.isNotEmpty()) {
            doFlushAsync(batch.toList())
        }
    }, "monitor-reporter").apply {
        isDaemon = true
    }

    init {
        consumerThread.start()
    }

    // ==================== IMonitorReportHandler 实现 ====================

    /**
     * 内部使用 BlockingQueue + 后台消费者线程，onEvent() 仅做 lock-free 入队操作，立即返回。
     * 因此声明为异步，框架无需额外创建线程隔离。
     */
    override val isAsync: Boolean get() = true

    /**
     * 接收事件并入队
     *
     * - 队列未满：正常入队，立即返回
     * - 队列已满：静默丢弃（简化降级策略，不做 poll+offer）
     * - 已 shutdown：静默丢弃
     */
    override fun onEvent(event: MonitorEvent) {
        if (!running.get()) return
        if (!queue.offer(event)) {
            // 队列满，静默丢弃（可在后续版本增加丢弃计数用于监控自监控）
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Queue full, event dropped")
            }
        }
    }

    /**
     * 立即刷新队列中所有未上报的事件
     */
    override fun flush() {
        // 排空队列中的事件（事件还在 queue 中，消费者尚未 take）
        val pending = mutableListOf<MonitorEvent>()
        queue.drainTo(pending)
        if (pending.isNotEmpty()) {
            doFlushAsync(pending)
        }
        // 通知消费者线程立即 flush 当前 batch（事件已被 take 但尚未到 flush 窗口）
        // forceFlush 是 volatile，interrupt 只用于唤醒阻塞中的 take/poll。
        forceFlush = true
        consumerThread.interrupt()
    }

    /**
     * 关闭上报器，等待剩余事件上报完成后释放资源
     */
    override fun shutdown() {
        running.set(false)
        consumerThread.interrupt()
        try {
            consumerThread.join(5000)
        } catch (_: InterruptedException) {
            // ignore
        }
        // 排空队列 + 二次排空（捕获 drainTo 之后竞态到达的事件）
        val remaining = mutableListOf<MonitorEvent>()
        queue.drainTo(remaining)
        val lateArrivals = mutableListOf<MonitorEvent>()
        queue.drainTo(lateArrivals)
        if (lateArrivals.isNotEmpty()) {
            remaining.addAll(lateArrivals)
        }
        if (remaining.isNotEmpty()) {
            doFlushAsync(remaining)
        }
        // 释放 OkHttp 资源
        reportClient.dispatcher.executorService.shutdown()
        reportClient.connectionPool.evictAll()
    }

    // ==================== 内部实现 ====================

    /**
     * 异步批量 HTTP 上报 + 熔断保护
     */
    private fun doFlushAsync(events: List<MonitorEvent>) {
        if (events.isEmpty()) return

        // 熔断检查
        if (isCircuitOpen()) {
            Log.w(TAG, "Circuit open, dropping ${events.size} events")
            return
        }

        try {
            val jsonArray = JSONArray()
            for (event in events) {
                jsonArray.put(event.toJson())
            }

            val body = JSONObject().apply {
                put("events", jsonArray)
                put("platform", "android")
                put("timestamp", System.currentTimeMillis())
            }

            val request = Request.Builder()
                .url(reportUrl)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            // 异步上报，不阻塞消费者线程
            reportClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    recordFailure("IO error: ${e.message}")
                    endProbe()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        recordSuccess()
                    } else {
                        // HTTP 4xx/5xx 也计入失败，触发熔断保护
                        recordFailure("HTTP ${response.code}")
                    }
                    endProbe()
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Report exception: ${e.message}")
            recordFailure("exception: ${e.message}")
            endProbe()
        }
    }
}
