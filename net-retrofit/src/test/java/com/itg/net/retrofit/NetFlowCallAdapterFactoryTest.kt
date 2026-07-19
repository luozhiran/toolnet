package com.itg.net.retrofit

import com.itg.net.Net
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor
import com.itg.net.request.business.DefaultApiEnvelopeParser
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.result.NetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.CallAdapter
import retrofit2.Retrofit
import retrofit2.http.GET
import java.lang.reflect.Type

class NetFlowCallAdapterFactoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: Api

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addCallAdapterFactory(NetFlowCallAdapterFactory())
            .build()
            .create(Api::class.java)
        resetBusinessConfig()
    }

    @After
    fun tearDown() {
        server.shutdown()
        resetBusinessConfig()
    }

    @Test
    fun flowNetResultEmitsHttpErrorForHttp4xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"server error"}"""))

        val result = api.netResult().first()

        assertTrue(result is NetResult.HttpError)
        assertEquals(500, result.code)
        assertTrue(result.body.orEmpty().contains("server error"))
    }

    @Test
    fun flowBusinessResultUsesConfiguredInterceptorChain() = runBlocking {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return if (chain.envelope.code == "401") {
                        BusinessResult.Consumed("login expired", chain.response.code, chain.response.body)
                    } else {
                        chain.proceed()
                    }
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"401","message":"expired"}"""))

        val result = api.businessResult().first()

        assertTrue("actual=$result", result is BusinessResult.Consumed)
        assertEquals("login expired", (result as BusinessResult.Consumed).reason)
    }

    @Test
    fun flowTypedBusinessResultConvertsDataObject() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Ada"}}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        assertEquals("Ada", (result as TypedBusinessResult.Success).data?.name)
    }

    @Test
    fun flowTypedBusinessResultEmitsConvertErrorWhenDataShapeMismatches() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"not-object"}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.DataConvertError)
    }

    @Test
    fun flowCanBeCollectedMoreThanOnce() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"first"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"second"}"""))
        val flow = api.netResult()

        val first = flow.first()
        val second = flow.first()

        assertTrue(first is NetResult.Success)
        assertTrue((first as NetResult.Success).body.orEmpty().contains("first"))
        assertTrue(second is NetResult.Success)
        assertTrue((second as NetResult.Success).body.orEmpty().contains("second"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun netRetrofitKeepsDefaultFlowAdapterWhenCustomAdapterIsAdded() = runBlocking {
        val service = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .addCallAdapterFactory(NoopCallAdapterFactory())
            .build()
            .create<Api>()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        val result = service.netResult().first()

        assertTrue(result is NetResult.Success)
        assertTrue((result as NetResult.Success).body.orEmpty().contains("ok"))
    }

    private fun resetBusinessConfig() {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
        }
    }

    interface Api {
        @GET("net-result")
        fun netResult(): Flow<NetResult>

        @GET("business")
        fun businessResult(): Flow<BusinessResult>

        @GET("typed")
        fun typedBusinessResult(): Flow<TypedBusinessResult<UserDto>>
    }

    data class UserDto(val name: String)

    private class NoopCallAdapterFactory : CallAdapter.Factory() {
        override fun get(
            returnType: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit
        ): CallAdapter<*, *>? = null
    }
}
