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

    /** 事件缓冲队列（有界阻塞队列） */
    private val queue: BlockingQueue<MonitorEvent> = LinkedBlockingQueue(maxQueueSize)

    /** 是否正在运行 */
    private val running = AtomicBoolean(true)

    // ==================== 熔断器 ====================
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntil = AtomicLong(0)

    /** 连续失败多少次后熔断 */
    private val maxConsecutiveFailures = 5

    /** 熔断冷却时间（毫秒） */
    private val circuitCooldownMs = 30_000L

    private fun isCircuitOpen(): Boolean {
        return consecutiveFailures.get() >= maxConsecutiveFailures &&
            System.currentTimeMillis() < circuitOpenUntil.get()
    }

    /** 上报使用的 OkHttpClient（独立实例，避免与业务共用连接池） */
    private val reportClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 消费者线程：使用动态超时的 poll()，batch 为空时无限阻塞（按需唤醒） */
    private val consumerThread: Thread = Thread({
        val batch = mutableListOf<MonitorEvent>()
        var lastFlushTime = System.currentTimeMillis()

        while (running.get()) {
            try {
                // 动态超时：batch 为空时使用 Long.MAX_VALUE（等价 take()），非空时定期检查 flush 窗口
                val timeout = if (batch.isEmpty()) Long.MAX_VALUE
                else minOf(flushIntervalMs, 1000L)

                val event = queue.poll(timeout, TimeUnit.MILLISECONDS)
                if (event != null) {
                    batch.add(event)
                }

                val shouldFlush = batch.size >= batchSize ||
                    (batch.isNotEmpty() &&
                     System.currentTimeMillis() - lastFlushTime >= flushIntervalMs)

                if (shouldFlush) {
                    doFlushAsync(batch.toList())
                    batch.clear()
                    lastFlushTime = System.currentTimeMillis()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
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
        val pending = mutableListOf<MonitorEvent>()
        queue.drainTo(pending)
        if (pending.isNotEmpty()) {
            doFlushAsync(pending)
        }
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
        // 最后一次机会：排空队列
        val remaining = mutableListOf<MonitorEvent>()
        queue.drainTo(remaining)
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
                    val failures = consecutiveFailures.incrementAndGet()
                    if (failures >= maxConsecutiveFailures) {
                        circuitOpenUntil.set(System.currentTimeMillis() + circuitCooldownMs)
                        Log.w(TAG, "Circuit opened for ${circuitCooldownMs}ms after $failures consecutive failures")
                    } else {
                        Log.w(TAG, "Report failed (${failures}/$maxConsecutiveFailures): ${e.message}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    consecutiveFailures.set(0)  // 成功时重置熔断计数
                    if (response.isSuccessful) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Reported ${events.size} events successfully")
                        }
                    } else {
                        Log.w(TAG, "Report failed: HTTP ${response.code}")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Report exception: ${e.message}")
        }
    }
}
