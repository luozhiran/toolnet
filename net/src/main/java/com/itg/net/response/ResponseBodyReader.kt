package com.itg.net.response

import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset

sealed class BodyReadResult {
    data class Text(val value: String?) : BodyReadResult()
    data class TooLarge(
        val contentLength: Long,
        val maxBytes: Long
    ) : BodyReadResult() {
        val message: String = "response body is too large: ${formatBytes(contentLength)} > ${formatBytes(maxBytes)}"
        fun asIOException(): IOException = IOException(message)
    }
}

object ResponseBodyReader {
    fun readText(
        body: ResponseBody?,
        maxBytes: Long,
        charsetFallback: Charset = UTF8
    ): BodyReadResult {
        body ?: return BodyReadResult.Text(null)
        val effectiveMaxBytes = if (maxBytes > 0L) maxBytes else DEFAULT_MAX_BYTES

        val declaredLength = body.contentLength()
        if (declaredLength > effectiveMaxBytes) {
            return BodyReadResult.TooLarge(declaredLength, effectiveMaxBytes)
        }

        val buffer = Buffer()
        val source = body.source()
        var total = 0L
        while (true) {
            val readLimit = minOf(DEFAULT_SEGMENT_SIZE, effectiveMaxBytes + 1L - total)
            if (readLimit <= 0L) {
                return BodyReadResult.TooLarge(total + 1L, effectiveMaxBytes)
            }
            val read = source.read(buffer, readLimit)
            if (read == -1L) {
                break
            }
            total += read
            if (total > effectiveMaxBytes) {
                return BodyReadResult.TooLarge(total, effectiveMaxBytes)
            }
        }

        return BodyReadResult.Text(buffer.readString(body.charset(charsetFallback)))
    }

    private fun ResponseBody.charset(fallback: Charset): Charset {
        return contentType()?.charset(fallback) ?: fallback
    }

    private const val DEFAULT_SEGMENT_SIZE = 8192L
    private const val DEFAULT_MAX_BYTES = 2L * 1024L * 1024L
    private val UTF8: Charset = Charset.forName("UTF-8")
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    return String.format("%.2fMB", kb / 1024.0)
}
