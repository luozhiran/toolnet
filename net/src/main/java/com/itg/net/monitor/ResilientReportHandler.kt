package com.itg.net.monitor

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque

/**
 * 带本地文件兜底的监控上报处理器。
 *
 * 正常路径委托给 [delegate] 上报，同时把最近的 [maxLocalEvents] 条事件写入本地文件。
 * 本地文件采用固定行数保留策略，避免历史文件异常变大时一次性读入全部内容。
 */
class ResilientReportHandler(
    private val delegate: IMonitorReportHandler,
    private val localFile: File,
    private val maxLocalEvents: Int = 200
) : IMonitorReportHandler {

    companion object {
        private const val TAG = "ResilientMonitorReport"
    }

    private val safeMaxLocalEvents: Int = maxLocalEvents.coerceAtLeast(1)
    private val trimThreshold: Int = (safeMaxLocalEvents * 2).coerceAtLeast(safeMaxLocalEvents + 1)

    private var localEventCount = 0

    /**
     * 本地兜底写入包含磁盘 I/O，需要由 MonitorInterceptor 放到独立线程执行。
     */
    override val isAsync: Boolean get() = false

    @Volatile
    private var writer: RandomAccessFile? = null

    init {
        try {
            localFile.parentFile?.mkdirs()
            if (localFile.exists()) {
                localEventCount = countLines(localFile, trimThreshold)
                if (localEventCount > trimThreshold) {
                    truncateHead()
                    localEventCount = safeMaxLocalEvents
                }
            }
            writer = RandomAccessFile(localFile, "rw")
            writer?.seek(localFile.length())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize local backup file", e)
        }
    }

    override fun onEvent(event: MonitorEvent) {
        delegate.onEvent(event)
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
                } catch (_: Exception) {
                    // Ignore close errors.
                }
                writer = null
            }
        }
    }

    private fun appendToLocalFile(event: MonitorEvent) {
        try {
            val w = writer ?: return
            synchronized(w) {
                w.seek(localFile.length())
                w.write((event.toJson().toString() + "\n").toByteArray(Charsets.UTF_8))
                localEventCount++
                if (localEventCount > trimThreshold) {
                    truncateHead()
                    localEventCount = safeMaxLocalEvents
                    w.seek(localFile.length())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write local backup", e)
        }
    }

    private fun truncateHead() {
        try {
            val keep = readLastLines(localFile, safeMaxLocalEvents - 1)
            if (keep.isEmpty()) {
                localFile.writeText("", Charsets.UTF_8)
                return
            }
            localFile.writeText(keep.joinToString("\n") + "\n", Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to truncate local backup head", e)
        }
    }

    private fun countLines(file: File, maxLines: Int): Int {
        var count = 0
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isNotEmpty()) {
                    count++
                }
                if (count > maxLines) return count
            }
        }
        return count
    }

    private fun readLastLines(file: File, maxLines: Int): List<String> {
        if (maxLines <= 0) return emptyList()
        val buffer = ArrayDeque<String>(maxLines)
        file.bufferedReader(Charsets.UTF_8).use { reader: BufferedReader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (buffer.size == maxLines) {
                    buffer.removeFirst()
                }
                buffer.addLast(line)
            }
        }
        return buffer.toList()
    }
}
