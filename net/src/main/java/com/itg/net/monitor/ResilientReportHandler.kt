package com.itg.net.monitor

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * 带本地文件兜底的上报处理器
 *
 * 确保进程崩溃时最近的失败事件不丢失：
 * - 正常路径：委托给 [delegate] 处理（如 [DefaultMonitorReportHandler]）
 * - 兜底路径：每条事件同时追加到本地环形日志文件（最多保留 [maxLocalEvents] 条）
 *
 * ## 使用场景
 * 适合对数据完整性要求较高的生产环境。
 *
 * ## 使用示例
 * ```kotlin
 * Net.instance.configure {
 *     monitor {
 *         enabled(true)
 *         reportUrl("https://monitor.example.com/api/report")
 *         reportHandler(ResilientReportHandler(
 *             delegate = DefaultMonitorReportHandler(
 *                 reportUrl = "https://monitor.example.com/api/report"
 *             ),
 *             localFile = File(cacheDir, "network_monitor_backup.log")
 *         ))
 *     }
 * }
 * ```
 *
 * @param delegate 主上报处理器（如 HTTP 批量上报）
 * @param localFile 本地兜底日志文件
 * @param maxLocalEvents 本地文件最大保留事件数，超出后环形截断，默认 200
 */
class ResilientReportHandler(
    private val delegate: IMonitorReportHandler,
    private val localFile: File,
    private val maxLocalEvents: Int = 200
) : IMonitorReportHandler {

    companion object {
        private const val TAG = "ResilientMonitorReport"
    }

    /** 本地事件计数（用于环形截断） */
    private var localEventCount = 0

    /**
     * 本地兜底写入包含磁盘 I/O，需要由 MonitorInterceptor 放到独立线程执行。
     */
    override val isAsync: Boolean get() = false

    /** 本地文件写入器 */
    @Volatile
    private var writer: RandomAccessFile? = null

    init {
        try {
            localFile.parentFile?.mkdirs()
            // 读取已有事件数量
            if (localFile.exists()) {
                localEventCount = localFile.readLines().size
            }
            writer = RandomAccessFile(localFile, "rw")
            writer?.seek(localFile.length())  // 追加模式
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize local backup file", e)
        }
    }

    override fun onEvent(event: MonitorEvent) {
        // 主路径：委托给默认上报器
        delegate.onEvent(event)

        // 兜底路径：异步写入本地文件
        appendToLocalFile(event)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun shutdown() {
        delegate.shutdown()
        writer?.let { w ->
            synchronized(w) {
                try {
                    w.close()
                } catch (_: Exception) { }
                writer = null
            }
        }
    }

    private fun appendToLocalFile(event: MonitorEvent) {
        try {
            val w = writer ?: return
            synchronized(w) {
                val jsonLine = event.toJson().toString()

                // 环形截断：超过最大行数时截断文件头部
                if (localEventCount >= maxLocalEvents) {
                    truncateHead()
                    localEventCount--
                }

                w.seek(localFile.length())
                w.writeBytes(jsonLine)
                w.writeBytes("\n")
                localEventCount++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write local backup", e)
        }
    }

    /**
     * 截断文件头部（移除最旧的事件行）
     */
    private fun truncateHead() {
        try {
            val lines = localFile.readLines()
            if (lines.size <= 1) {
                localFile.writeText("")
                return
            }
            // 保留最近的 maxLocalEvents - 1 条
            val keep = lines.takeLast(maxLocalEvents - 1)
            localFile.writeText(keep.joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to truncate local backup head", e)
        }
    }
}
