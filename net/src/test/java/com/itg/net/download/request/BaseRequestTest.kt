package com.itg.net.download.request

import com.itg.net.download.data.DOWNLOAD_SUCCESS_MESSAGE
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class BaseRequestTest {

    @Test
    fun unknownContentLengthCompletesAndBackfillsContentLength() {
        val file = File("build/tmp/download-${UUID.randomUUID()}.txt")
        val task = Task().apply {
            url = "https://example.com/file.txt"
            path = file.absolutePath
            overwrite = true
        }
        val request = TestRequest(task)
        val events = mutableListOf<String>()
        request.setSuccessCallback { _, message -> events.add("success:$message") }
        request.setFailCallback { _, message -> events.add("fail:$message") }

        request.handle(testResponse(UnknownLengthBody("hello")))

        assertEquals(listOf("success:$DOWNLOAD_SUCCESS_MESSAGE"), events)
        assertEquals(5L, task.downloadSize)
        assertEquals(5L, task.contentLength)
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun blankPathFailsBeforeWritingNullTempFile() {
        val nullTempFile = File("null.tmp")
        nullTempFile.delete()
        val task = Task().apply {
            url = "https://example.com/file.txt"
            path = null
        }
        val request = TestRequest(task)
        val events = mutableListOf<String>()
        request.setFailCallback { _, message -> events.add(message) }

        request.handle(testResponse(UnknownLengthBody("hello")))

        assertEquals(listOf(ERROR_INVALID_DOWNLOAD_TASK), events)
        assertFalse(nullTempFile.exists())
    }

    @Test
    fun targetPathEndingWithTmpIsPreserved() {
        val file = File("build/tmp/download-${UUID.randomUUID()}.tmp")
        val task = Task().apply {
            url = "https://example.com/file.txt"
            path = file.absolutePath
            overwrite = true
        }
        val request = TestRequest(task)
        val events = mutableListOf<String>()
        request.setSuccessCallback { _, message -> events.add("success:$message") }

        request.handle(testResponse(UnknownLengthBody("hello")))

        assertEquals(listOf("success:$DOWNLOAD_SUCCESS_MESSAGE"), events)
        assertTrue(file.exists())
        assertEquals("hello", file.readText())
        file.delete()
    }

    private class TestRequest(task: Task) : BaseRequest(task, TaskState()) {
        fun handle(response: Response) {
            handleResponse(response)
        }

        override fun start() = Unit
    }

    private class UnknownLengthBody(
        private val content: String
    ) : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = Buffer().writeUtf8(content)
    }

    private fun testResponse(body: ResponseBody): Response {
        val request = Request.Builder()
            .url("https://example.com/file.txt")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }
}
