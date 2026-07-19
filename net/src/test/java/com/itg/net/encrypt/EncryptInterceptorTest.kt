package com.itg.net.encrypt

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptInterceptorTest {

    @Test
    fun requestBodyIsPreservedWhenNoEncryptRuleMatches() {
        val config = EncryptConfig()
            .secretKey("1234567890123456")
            .iv("1234567890123456")
            .encryptField("password")
        val capturedBody = StringBuilder()
        val client = OkHttpClient.Builder()
            .addInterceptor(EncryptInterceptor(config))
            .addInterceptor(captureRequestBody(capturedBody))
            .build()
        val requestBody = """{"username":"alice"}"""
        val request = Request.Builder()
            .url("https://example.com/login")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().close()

        assertEquals(requestBody, capturedBody.toString())
    }

    @Test
    fun aesGcmUsesUniqueIvAndCanDecryptPayload() {
        val key = "1234567890123456".toByteArray()

        val first = EncryptUtil.encrypt("secret", key, Algorithm.AES_GCM_NO_PADDING)
        val second = EncryptUtil.encrypt("secret", key, Algorithm.AES_GCM_NO_PADDING)

        assertNotEquals(first, second)
        assertEquals("secret", EncryptUtil.decrypt(first, key, Algorithm.AES_GCM_NO_PADDING))
        assertEquals("secret", EncryptUtil.decrypt(second, key, Algorithm.AES_GCM_NO_PADDING))
    }

    private fun captureRequestBody(capturedBody: StringBuilder): Interceptor {
        return Interceptor { chain ->
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            capturedBody.append(buffer.readUtf8())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
