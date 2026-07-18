package com.itg.net.flow

import com.itg.net.Net
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor
import com.itg.net.request.business.DefaultApiEnvelopeParser
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.result.NetResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestFlowResultTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resetBusinessConfig()
    }

    @After
    fun tearDown() {
        server.shutdown()
        resetBusinessConfig()
    }

    @Test
    fun flowResultEmitsSuccessForHttp2xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Ada"}}"""))

        val result = Net.get()
            .url(server.url("/ok").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
        assertEquals(200, result.code)
        assertTrue(result.body.orEmpty().contains("Ada"))
    }

    @Test
    fun flowResultEmitsHttpErrorForHttp4xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"missing"}"""))

        val result = Net.get()
            .url(server.url("/missing").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.HttpError)
        assertEquals(404, result.code)
        assertTrue(result.body.orEmpty().contains("missing"))
    }

    @Test
    fun flowResultEmitsNetworkErrorForIOException() = runBlocking {
        val url = server.url("/offline").toString()
        server.shutdown()

        val result = Net.get()
            .url(url)
            .flowResult()
            .first()

        assertTrue(result is NetResult.NetworkError)
    }

    @Test
    fun flowBusinessResultEmitsConsumedWhenInterceptorConsumes() = runBlocking {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return if (chain.envelope.code == "401") {
                        BusinessResult.Consumed(
                            reason = "login expired",
                            httpCode = chain.response.code,
                            rawBody = chain.response.body
                        )
                    } else {
                        chain.proceed()
                    }
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"401","message":"expired"}"""))

        val result = Net.get()
            .url(server.url("/business").toString())
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.Consumed)
        assertEquals("login expired", (result as BusinessResult.Consumed).reason)
    }

    @Test
    fun flowTypedBusinessResultConvertsDataObject() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Ada"}}"""))

        val result = Net.get()
            .url(server.url("/typed").toString())
            .flowTypedBusinessResult<UserDto>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        assertEquals("Ada", (result as TypedBusinessResult.Success).data?.name)
    }

    @Test
    fun flowTypedBusinessResultEmitsConvertErrorWhenDataShapeMismatches() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"not-object"}"""))

        val result = Net.get()
            .url(server.url("/typed-error").toString())
            .flowTypedBusinessResult<UserDto>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.DataConvertError)
    }

    private fun resetBusinessConfig() {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
        }
    }

    data class UserDto(val name: String)
}
