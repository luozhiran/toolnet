package com.itg.net.monitor

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 默认监控事件上报处理器。
 *
 * 线程模型：
 * - 业务线程调用 [onEvent]，只做无锁入队，队列满时丢弃事件。
 * - 后台消费者线程按数量或时间窗口批量上报。
 * - [flush] 会唤醒消费者线程，并同步排空已经在队列中的事件。
 * - 连续失败后进入熔断；冷却到期后只允许一个半开探测请求通过。
 */
class DefaultMonitorReportHandler(
    private val reportUrl: String,
    private val batchSize: Int = 20,
    private val flushIntervalMs: Long = 10_000L,
    private val maxQueueSize: Int = 1000
) : IMonitorReportHandler {

    companion object {
        private const val TAG = "DefaultMonitorReport"
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val INITIAL_COOLDOWN_MS = 30_000L
        private const val MAX_COOLDOWN_MS = 300_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val safeBatchSize = batchSize.coerceAtLeast(1)
    private val safeFlushIntervalMs = flushIntervalMs.coerceAtLeast(1L)
    private val safeMaxQueueSize = maxQueueSize.coerceAtLeast(1)

    private val queue: BlockingQueue<MonitorEvent> = LinkedBlockingQueue(safeMaxQueueSize)
    private val running = AtomicBoolean(true)
    private val flushLock = Any()
    private val reportInFlight = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntil = AtomicLong(0)
    private val probeInFlight = AtomicBoolean(false)
    private val currentCooldownMs = AtomicLong(INITIAL_COOLDOWN_MS)

    private val flushExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "monitor-flush").apply {
            isDaemon = true
        }
    }

    private val reportClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Volatile
    private var forceFlush = false

    private val consumerThread: Thread = Thread({
        val batch = mutableListOf<MonitorEvent>()
        var lastFlushTime = System.currentTimeMillis()

        fun flushBatchIfRequested() {
            if (!forceFlush) return
            forceFlush = false
            if (batch.isNotEmpty()) {
                doFlushAsync(batch.toList(), awaitCompletion = true)
                batch.clear()
                lastFlushTime = System.currentTimeMillis()
            }
        }

        while (running.get()) {
            try {
                flushBatchIfRequested()

                val event = if (batch.isEmpty()) {
                    queue.take()
                } else {
                    queue.poll(safeFlushIntervalMs, TimeUnit.MILLISECONDS)
                }

                if (event != null) {
                    batch.add(event)
                }

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

        if (batch.isNotEmpty()) {
            doFlushAsync(batch.toList(), awaitCompletion = true)
        }
    }, "monitor-reporter").apply {
        isDaemon = true
    }

    override val isAsync: Boolean get() = true

    init {
        consumerThread.start()
    }

    override fun onEvent(event: MonitorEvent) {
        if (!running.get()) return
        if (!queue.offer(event) && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Queue full, event dropped")
        }
    }

    override fun flush() {
        synchronized(flushLock) {
            if (!running.get()) return
            val pending = mutableListOf<MonitorEvent>()
            queue.drainTo(pending)
            if (pending.isNotEmpty()) {
                runFlushWorker(pending)
            }
            forceFlush = true
        }
        consumerThread.interrupt()
    }

    override fun shutdown() {
        synchronized(flushLock) {
            running.set(false)
        }
        consumerThread.interrupt()
        try {
            consumerThread.join(5000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        shutdownFlushExecutor()

        val remaining = mutableListOf<MonitorEvent>()
        queue.drainTo(remaining)
        if (remaining.isNotEmpty()) {
            doFlushAsync(remaining, awaitCompletion = true)
        }

        reportClient.dispatcher.executorService.shutdown()
        try {
            reportClient.dispatcher.executorService.awaitTermination(5000, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        reportClient.connectionPool.evictAll()
    }

    private fun isCircuitOpen(): Boolean {
        if (consecutiveFailures.get() < MAX_CONSECUTIVE_FAILURES) {
            return false
        }
        if (System.currentTimeMillis() < circuitOpenUntil.get()) {
            return true
        }
        return !probeInFlight.compareAndSet(false, true)
    }

    private fun recordSuccess() {
        consecutiveFailures.set(0)
        circuitOpenUntil.set(0)
        currentCooldownMs.set(INITIAL_COOLDOWN_MS)
    }

    private fun recordFailure(detail: String) {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            val newCooldown = nextCooldown()
            circuitOpenUntil.set(System.currentTimeMillis() + newCooldown)
            Log.w(
                TAG,
                "Circuit opened for ${newCooldown}ms after $failures consecutive failures ($detail)"
            )
        } else {
            Log.w(TAG, "Report failed ($failures/$MAX_CONSECUTIVE_FAILURES): $detail")
        }
    }

    private fun doFlushAsync(
        events: List<MonitorEvent>,
        awaitCompletion: Boolean = false
    ) {
        if (events.isEmpty()) return
        if (isCircuitOpen()) {
            Log.w(TAG, "Circuit open, dropping ${events.size} events")
            return
        }
        if (!acquireReportSlot(awaitCompletion)) {
            probeInFlight.set(false)
            Log.w(TAG, "Report in flight, dropping ${events.size} events")
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

            val latch = if (awaitCompletion) CountDownLatch(1) else null
            reportClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    try {
                        recordFailure("IO error: ${e.message}")
                    } finally {
                        finishReport(latch)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            recordSuccess()
                        } else {
                            recordFailure("HTTP ${response.code}")
                        }
                    } finally {
                        response.close()
                        finishReport(latch)
                    }
                }
            })

            if (awaitCompletion) {
                try {
                    latch?.await(5000, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Report exception: ${e.message}")
            recordFailure("exception: ${e.message}")
            finishReport(null)
        }
    }

    private fun finishReport(latch: CountDownLatch?) {
        probeInFlight.set(false)
        reportInFlight.set(false)
        latch?.countDown()
    }

    private fun acquireReportSlot(awaitCompletion: Boolean): Boolean {
        if (reportInFlight.compareAndSet(false, true)) {
            return true
        }
        if (!awaitCompletion) {
            return false
        }
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            if (reportInFlight.compareAndSet(false, true)) {
                return true
            }
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun nextCooldown(): Long {
        while (true) {
            val current = currentCooldownMs.get()
            val next = minOf(current * 2, MAX_COOLDOWN_MS)
            if (currentCooldownMs.compareAndSet(current, next)) {
                return next
            }
        }
    }

    private fun runFlushWorker(events: List<MonitorEvent>) {
        if (!running.get()) return
        try {
            flushExecutor.execute {
                doFlushAsync(events, awaitCompletion = true)
            }
        } catch (_: RejectedExecutionException) {
            Log.w(TAG, "Flush executor is shut down, dropping ${events.size} events")
        }
    }

    private fun shutdownFlushExecutor() {
        flushExecutor.shutdown()
        try {
            flushExecutor.awaitTermination(5000, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

}
