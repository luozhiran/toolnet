package com.itg.net.reqeust.body

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.*
import java.io.File
import java.io.FileNotFoundException

class IntervalBody constructor(private val file: File, private val offset: Long = 0) :
    RequestBody() {
    private val CONTENT_TYPE: MediaType = "application/octet-stream".toMediaType()

    override fun contentType(): MediaType {
        return CONTENT_TYPE
    }

    override fun contentLength(): Long {
        return file.length() - offset
    }

    override fun writeTo(sink: BufferedSink) {
        var source: Source? = null
        try {
            source = file.source()
            val bufferedSource: BufferedSource = source.buffer()
            val buffer = ByteArray(4096)
            var len = 0
            var totleSize = 0
            while (bufferedSource.read(buffer).also { len = it } != -1) {
                if (totleSize.toLong() == offset) {
                    sink.write(buffer, 0, len)
                } else {
                    totleSize += len
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } finally {
            source?.close()
        }
    }

}