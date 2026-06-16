package com.itg.net.request.post.multipart

import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMulTest {

    @Test
    fun multipartBodyKeepsMultipleTextBodiesAndStructuredParts() {
        val postMul = PostMul()
            .addParam("token", "abc")
            .addContent("first-body", "text/plain")
            .addContent("second-body", "text/plain")
            .addContent("named-body", "metadata", "text/plain")

        val multipartBody = postMul.multipartBody()

        assertEquals(MultipartBody.FORM, multipartBody.type)
        assertTrue(multipartBody.containsPart("token", "abc"))
        assertTrue(multipartBody.containsPart("body", "first-body"))
        assertTrue(multipartBody.containsPart("body1", "second-body"))
        assertTrue(multipartBody.containsPart("metadata", "named-body"))
    }

    private fun PostMulGenerator.multipartBody(): MultipartBody {
        return getRequestBody() as MultipartBody
    }

    private fun MultipartBody.containsPart(name: String, bodyText: String): Boolean {
        return parts.any { part ->
            val disposition = part.headers?.get("Content-Disposition").orEmpty()
            disposition.contains("name=\"$name\"") && part.body.asString().contains(bodyText)
        }
    }

    private fun okhttp3.RequestBody.asString(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }
}
