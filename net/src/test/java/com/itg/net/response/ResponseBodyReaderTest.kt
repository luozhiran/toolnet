package com.itg.net.response

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseBodyReaderTest {

    @Test
    fun nonPositiveLimitFallsBackToSafeLimit() {
        val body = "ok".toResponseBody()

        val result = ResponseBodyReader.readText(body, 0L)

        assertEquals(BodyReadResult.Text("ok"), result)
    }

    @Test
    fun nonPositiveLimitStillRejectsLargeBody() {
        val body = "a".repeat(2 * 1024 * 1024 + 1).toResponseBody()

        val result = ResponseBodyReader.readText(body, -1L)

        assertTrue(result is BodyReadResult.TooLarge)
    }
}
