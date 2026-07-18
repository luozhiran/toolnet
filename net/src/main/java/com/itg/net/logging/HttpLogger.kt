package com.itg.net.logging

import android.util.Log
import com.itg.net.download.data.DOWNLOAD_LOG_TAG
import com.itg.net.util.JsonTools
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class HttpLogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(DOWNLOAD_LOG_TAG, buildErrorLog(request.method, request.url.toString(), e), e)
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
        Log.d(DOWNLOAD_LOG_TAG, buildLog(response, tookMs))
        return response
    }

    private fun buildLog(response: Response, tookMs: Long): String {
        val request = response.request
        val responseBody = response.body
        val contentLength = responseBody?.contentLength() ?: -1L
        val contentType = responseBody?.contentType()
        val bodyPreview = responseBody?.let {
            readBodyPreview(contentType, contentLength, response)
        } ?: BodyPreview.Empty

        return buildString {
            appendLine()
            appendLine("+---------------- HTTP ${statusLabel(response.code)} ----------------")
            appendLine("| ${request.method} ${request.url}")
            appendLine("| Status   : ${response.code} ${response.message}")
            appendLine("| Duration : ${tookMs}ms")
            appendLine("| Type     : ${contentType ?: "-"}")
            appendLine("| Size     : ${formatBytes(contentLength)}")
            appendLine("| Headers")
            response.headers.forEach { header ->
                appendLine("|   ${header.first}: ${header.second}")
            }
            appendLine("| Body")
            appendBodyPreview(bodyPreview)
            append("+---------------------------------------------")
        }
    }

    private fun StringBuilder.appendBodyPreview(preview: BodyPreview) {
        when (preview) {
            BodyPreview.Empty -> appendLine("|   <empty>")
            is BodyPreview.Skipped -> appendLine("|   <skipped: ${preview.reason}>")
            is BodyPreview.Text -> preview.text
                .lineSequence()
                .take(MAX_BODY_LINES)
                .forEach { line -> appendLine("|   $line") }
        }
    }

    private fun readBodyPreview(
        contentType: MediaType?,
        contentLength: Long,
        response: Response
    ): BodyPreview {
        if (!isTextContent(contentType)) {
            return BodyPreview.Skipped("binary content")
        }
        if (isStreamingContent(contentType, contentLength)) {
            return BodyPreview.Skipped("streaming or unknown length content")
        }
        if (contentLength > MAX_BODY_BYTES) {
            return BodyPreview.Skipped("${formatBytes(contentLength)} exceeds preview limit ${formatBytes(MAX_BODY_BYTES)}")
        }

        return try {
            val source = response.body?.source() ?: return BodyPreview.Empty
            source.request(contentLength.coerceAtMost(MAX_BODY_BYTES))
            val buffer = source.buffer.clone()
            val charset = contentType?.charset(UTF8) ?: UTF8
            val text = buffer.readString(charset)
            BodyPreview.Text(formatTextBody(text))
        } catch (e: Exception) {
            BodyPreview.Skipped("preview failed: ${e.message.orEmpty()}")
        }
    }

    private fun formatTextBody(text: String): String {
        val value = text.take(MAX_BODY_CHARS)
        val decoded = JsonTools.decodeUnicode(value)
        return if (decoded.looksLikeJson()) {
            JsonTools.formatJson(decoded)
        } else {
            decoded
        }
    }

    private fun buildErrorLog(method: String, url: String, error: Exception): String {
        return buildString {
            appendLine()
            appendLine("+---------------- HTTP ERROR ----------------")
            appendLine("| $method $url")
            appendLine("| ${error::class.java.simpleName}: ${error.message.orEmpty()}")
            append("+---------------------------------------------")
        }
    }

    private fun isTextContent(contentType: MediaType?): Boolean {
        val type = contentType?.type?.lowercase().orEmpty()
        val subtype = contentType?.subtype?.lowercase().orEmpty()
        return type == "text" ||
            subtype.contains("json") ||
            subtype.contains("xml") ||
            subtype.contains("html") ||
            subtype.contains("form")
    }

    private fun isStreamingContent(contentType: MediaType?, contentLength: Long): Boolean {
        val subtype = contentType?.subtype?.lowercase().orEmpty()
        return contentLength < 0 ||
            subtype.contains("event-stream") ||
            subtype.contains("stream")
    }

    private fun statusLabel(code: Int): String {
        return when (code) {
            in 200..299 -> "OK"
            in 300..399 -> "REDIRECT"
            in 400..499 -> "CLIENT ERROR"
            in 500..599 -> "SERVER ERROR"
            else -> "RESPONSE"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "unknown"
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1fKB", kb)
        return String.format("%.2fMB", kb / 1024.0)
    }

    private fun String.looksLikeJson(): Boolean {
        val trimmed = trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private sealed class BodyPreview {
        object Empty : BodyPreview()
        data class Text(val text: String) : BodyPreview()
        data class Skipped(val reason: String) : BodyPreview()
    }

    private companion object {
        private val UTF8: Charset = Charset.forName("UTF-8")
        private const val MAX_BODY_BYTES = 64L * 1024L
        private const val MAX_BODY_CHARS = 16 * 1024
        private const val MAX_BODY_LINES = 120
    }
}
