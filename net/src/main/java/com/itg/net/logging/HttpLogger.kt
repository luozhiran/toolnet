package com.itg.net.logging

import android.util.Log
import com.itg.net.download.data.DOWNLOAD_LOG_TAG
import com.itg.net.util.JsonTools
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.GzipSource
import okio.buffer
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
        Log.d(DOWNLOAD_LOG_TAG, buildLog(request, response, tookMs))
        return response
    }

    private fun buildLog(request: Request, response: Response, tookMs: Long): String {
        val requestBody = request.body
        val requestContentLength = requestBody?.safeContentLength() ?: -1L
        val requestContentType = requestBody?.contentType()
        val requestBodyPreview = requestBody?.let {
            readRequestBodyPreview(it, requestContentType, requestContentLength, request.header("Content-Encoding"))
        } ?: BodyPreview.Empty

        val responseBody = response.body
        val responseContentLength = responseBody.safeContentLength()
        val responseContentType = responseBody?.contentType()
        val responseBodyPreview = responseBody?.let {
            readResponseBodyPreview(responseContentType, responseContentLength, response)
        } ?: BodyPreview.Empty

        return buildString {
            appendLine()
            appendLine("+---------------- HTTP ${statusLabel(response.code)} ----------------")
            appendLine("| Request")
            appendLine("|   ${request.method} ${request.url}")
            appendLine("|   Type     : ${requestContentType ?: "-"}")
            appendLine("|   Size     : ${formatBytes(requestContentLength)}")
            appendLine("|   Headers")
            appendHeaders(request.headers, "|     ")
            appendLine("|   Body")
            appendBodyPreview(requestBodyPreview, "|     ")
            appendLine("| Response")
            appendLine("|   Status   : ${response.code} ${response.message}")
            appendLine("|   Duration : ${tookMs}ms")
            appendLine("|   Type     : ${responseContentType ?: "-"}")
            appendLine("|   Size     : ${formatBytes(responseContentLength)}")
            appendLine("|   Headers")
            appendHeaders(response.headers, "|     ")
            appendLine("|   Body")
            appendBodyPreview(responseBodyPreview, "|     ")
            append("+---------------------------------------------")
        }
    }

    private fun StringBuilder.appendHeaders(headers: okhttp3.Headers, linePrefix: String) {
        if (headers.size == 0) {
            appendLine("${linePrefix}<empty>")
            return
        }
        headers.forEach { header ->
            appendLine("$linePrefix${header.first}: ${header.second}")
        }
    }

    private fun StringBuilder.appendBodyPreview(preview: BodyPreview, linePrefix: String) {
        when (preview) {
            BodyPreview.Empty -> appendLine("${linePrefix}<empty>")
            is BodyPreview.Skipped -> appendLine("${linePrefix}<skipped: ${preview.reason}>")
            is BodyPreview.Text -> preview.text
                .lineSequence()
                .take(MAX_BODY_LINES)
                .forEach { line -> appendLine("$linePrefix$line") }
        }
    }

    private fun readRequestBodyPreview(
        body: RequestBody,
        contentType: MediaType?,
        contentLength: Long,
        contentEncoding: String?
    ): BodyPreview {
        if (body.isDuplex()) {
            return BodyPreview.Skipped("duplex request body")
        }
        if (body.isOneShot()) {
            return BodyPreview.Skipped("one-shot request body")
        }
        if (!isTextContent(contentType)) {
            return BodyPreview.Skipped("binary content")
        }
        if (isStreamingContent(contentType)) {
            return BodyPreview.Skipped("streaming content")
        }
        if (contentLength < 0) {
            return BodyPreview.Skipped("unknown length content")
        }
        if (contentLength > MAX_BODY_BYTES) {
            return BodyPreview.Skipped("${formatBytes(contentLength)} exceeds preview limit ${formatBytes(MAX_BODY_BYTES)}")
        }
        if (isUnsupportedEncoding(contentEncoding)) {
            return BodyPreview.Skipped("${contentEncoding.orEmpty()} encoded content")
        }

        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = contentType?.charset(UTF8) ?: UTF8
            val text = readBufferText(buffer, contentEncoding, charset)
            BodyPreview.Text(formatTextBody(text))
        } catch (e: Exception) {
            BodyPreview.Skipped("preview failed: ${e.message.orEmpty()}")
        }
    }

    private fun readResponseBodyPreview(
        contentType: MediaType?,
        contentLength: Long,
        response: Response
    ): BodyPreview {
        if (!isTextContent(contentType)) {
            return BodyPreview.Skipped("binary content")
        }
        if (isStreamingContent(contentType)) {
            return BodyPreview.Skipped("streaming content")
        }
        if (contentLength > MAX_BODY_BYTES) {
            return BodyPreview.Skipped("${formatBytes(contentLength)} exceeds preview limit ${formatBytes(MAX_BODY_BYTES)}")
        }

        val contentEncoding = response.header("Content-Encoding")
        if (isUnsupportedEncoding(contentEncoding)) {
            return BodyPreview.Skipped("${contentEncoding.orEmpty()} encoded content")
        }

        return try {
            val source = response.body?.source() ?: return BodyPreview.Empty
            val charset = contentType?.charset(UTF8) ?: UTF8
            val text = readPreviewText(source, contentEncoding, charset)
            BodyPreview.Text(formatTextBody(text))
        } catch (e: Exception) {
            BodyPreview.Skipped("preview failed: ${e.message.orEmpty()}")
        }
    }

    private fun readPreviewText(
        source: BufferedSource,
        contentEncoding: String?,
        charset: Charset
    ): String {
        val peekedSource = source.peek()
        val decodedSource = if (isGzipEncoding(contentEncoding)) {
            GzipSource(peekedSource).buffer()
        } else {
            peekedSource
        }
        return decodedSource.use { readStringLimited(it, charset) }
    }

    private fun readBufferText(
        buffer: Buffer,
        contentEncoding: String?,
        charset: Charset
    ): String {
        val source = if (isGzipEncoding(contentEncoding)) {
            GzipSource(buffer).buffer()
        } else {
            buffer
        }
        return source.use { readStringLimited(it, charset) }
    }

    private fun readStringLimited(source: BufferedSource, charset: Charset): String {
        val buffer = Buffer()
        var total = 0L
        while (total < MAX_BODY_BYTES) {
            val read = source.read(buffer, MAX_BODY_BYTES - total)
            if (read == -1L) break
            total += read
        }
        return buffer.readString(charset)
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

    private fun isStreamingContent(contentType: MediaType?): Boolean {
        val subtype = contentType?.subtype?.lowercase().orEmpty()
        return subtype.contains("event-stream") ||
            subtype.contains("stream")
    }

    private fun isUnsupportedEncoding(contentEncoding: String?): Boolean {
        val encoding = contentEncoding?.trim()?.lowercase().orEmpty()
        return encoding.isNotEmpty() && encoding != "identity" && !isGzipEncoding(encoding)
    }

    private fun isGzipEncoding(contentEncoding: String?): Boolean {
        return contentEncoding?.trim()?.equals("gzip", ignoreCase = true) == true
    }

    private fun ResponseBody?.safeContentLength(): Long {
        return try {
            this?.contentLength() ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun RequestBody.safeContentLength(): Long {
        return try {
            contentLength()
        } catch (e: Exception) {
            -1L
        }
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
