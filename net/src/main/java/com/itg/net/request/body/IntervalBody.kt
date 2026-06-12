package com.itg.net.request.body

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.*
import java.io.File

class IntervalBody constructor(private val file: File, private val offset: Long = 0) :
    RequestBody() {
    private val CONTENT_TYPE: MediaType = "application/octet-stream".toMediaType()

    override fun contentType(): MediaType {
        return CONTENT_TYPE
    }

    override fun contentLength(): Long {
        return (file.length() - offset.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    override fun writeTo(sink: BufferedSink) {
        file.source().buffer().use { source ->
            val skipBytes = offset.coerceIn(0L, file.length())
            if (skipBytes > 0L) {
                source.skip(skipBytes)
            }
            sink.writeAll(source)
        }
    }

}
