package com.itg.ddlnet

import com.itg.net.Net
import com.itg.net.flow.NetFlowException
import com.itg.net.flow.NetResponse
import com.itg.net.request.business.*
import com.itg.net.request.result.NetResult
import com.itg.net.retrofit.NetFlowCallAdapterFactory
import com.itg.net.retrofit.NetRetrofit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.lang.reflect.Type

/**
 * Net-Retrofit 模块完整测试示例。
 *
 * 覆盖：
 * - Flow<T> 返回类型（原始 body 反序列化）
 * - Flow<NetResponse<T>> 返回类型（带响应包装）
 * - Flow<NetResult> 返回类型（HTTP 层结构化结果）
 * - Flow<BusinessResult> 返回类型（业务层结构化结果 + 拦截器链）
 * - Flow<TypedBusinessResult<T>> 返回类型（类型化业务结果）
 * - Retrofit 接口定义（GET / POST / Query / Path / Body / Header 等注解）
 * - NetRetrofit.Builder（自定义 client、converter、callAdapter）
 * - Net.retrofit 扩展属性
 * - NetRetrofit.create<T>() 泛型创建
 * - NetFlowCallAdapterFactory 行为
 * - Flow 冷流特性（多次收集）
 * - 各种错误场景（HTTP 错误、网络错误、业务错误、超大数据）
 * - 自定义 CallAdapter.Factory 和 Converter.Factory 共存
 */
class NetRetrofitExamplesTest {

    // ========================================================================
    // 测试基础设施
    // ========================================================================

    private lateinit var server: MockWebServer
    private lateinit var api: TestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resetConfig()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .addCallAdapterFactory(NetFlowCallAdapterFactory())
            .build()
            .create(TestApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
        resetConfig()
    }

    private fun resetConfig() {
        Net.configure {
            clearGlobalParameters()
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessConverter(GsonBusinessDataConverter())
            maxResponseBodyBytes(0) // 不限制
            enableHttpLog(false)
        }
    }

    // ========================================================================
    // 一、Flow<T> —— 原始 body 反序列化
    // ========================================================================

    @Test
    fun `Flow User 成功反序列化 body`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"Alice","age":30}""")
                .addHeader("Content-Type", "application/json")
        )

        val user = api.getUser().first()

        assertEquals("Alice", user.name)
        assertEquals(30, user.age)
    }

    @Test
    fun `Flow User HTTP 错误通过 NetFlowException 关闭 Flow`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        try {
            api.getUser().first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertEquals(404, e.code)
        }
    }

    @Test
    fun `Flow User 网络错误通过 NetFlowException 关闭 Flow`() = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        // 重新创建一个指向已关闭 server 的 api
        val offlineApi = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .addCallAdapterFactory(NetFlowCallAdapterFactory())
            .build()
            .create(TestApi::class.java)

        try {
            offlineApi.getUser().first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertNull(e.code)
        }
    }

    @Test
    fun `Flow List String 反序列化集合类型`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""["a","b","c"]""")
                .addHeader("Content-Type", "application/json")
        )

        val items = api.getItems().first()

        assertEquals(listOf("a", "b", "c"), items)
    }

    // ========================================================================
    // 二、Flow<NetResponse<T>> —— 类型化响应包装
    // ========================================================================

    @Test
    fun `Flow NetResponse User 成功包含反序列化 body`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"Bob","age":25}""")
                .addHeader("Content-Type", "application/json")
        )

        val response = api.getUserResponse().first()

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals("Bob", response.body?.name)
        assertEquals(25, response.body?.age)
        assertTrue(response.rawBody.orEmpty().contains("Bob"))
    }

    @Test
    fun `Flow NetResponse User HTTP 错误包含 rawBody 不含 body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))

        val response = api.getUserResponse().first()

        assertFalse(response.isSuccessful)
        assertEquals(400, response.code)
        assertNull(response.body)
        assertTrue(response.rawBody.orEmpty().contains("bad request"))
    }

    @Test
    fun `Flow NetResponse User 包含响应头`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"HeaderTest","age":1}""")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Trace-Id", "trace-abc-123")
        )

        val response = api.getUserResponse().first()

        assertTrue(response.headers.containsKey("X-Trace-Id") ||
            response.headers.containsKey("x-trace-id"))
    }

    // ========================================================================
    // 三、Flow<NetResult> —— HTTP 层结构化结果
    // ========================================================================

    @Test
    fun `Flow NetResult 成功发射 Success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":"ok"}"""))

        val result = api.netResult().first()

        assertTrue(result is NetResult.Success)
        assertEquals(200, result.code)
        assertTrue((result as NetResult.Success).body.orEmpty().contains("ok"))
    }

    @Test
    fun `Flow NetResult HTTP 4xx 发射 HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val result = api.netResult().first()

        assertTrue(result is NetResult.HttpError)
        assertEquals(403, result.code)
    }

    @Test
    fun `Flow NetResult HTTP 5xx 发射 HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"server error"}"""))

        val result = api.netResult().first()

        assertTrue(result is NetResult.HttpError)
        assertEquals(500, result.code)
        assertTrue((result as NetResult.HttpError).body.orEmpty().contains("server error"))
    }

    @Test
    fun `Flow NetResult 网络错误发射 NetworkError`() = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        val offlineApi = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .addCallAdapterFactory(NetFlowCallAdapterFactory())
            .build()
            .create(TestApi::class.java)

        val result = offlineApi.netResult().first()

        assertTrue("actual=$result", result is NetResult.NetworkError)
    }

    @Test
    fun `Flow NetResult 超大响应体发射 ResponseTooLarge`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("too-large-success"))

        val result = api.netResult().first()

        assertTrue("actual=$result", result is NetResult.ResponseTooLarge)
        assertEquals(200, result.code)
    }

    @Test
    fun `Flow NetResult 超大错误响应体保持 HttpError`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(500).setBody("too-large-error"))

        val result = api.netResult().first()

        assertTrue("actual=$result", result is NetResult.HttpError)
        assertEquals(500, (result as NetResult.HttpError).code)
    }

    // ========================================================================
    // 四、Flow<BusinessResult> —— 业务层结构化结果
    // ========================================================================

    @Test
    fun `Flow BusinessResult 发射 Success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","message":"ok","data":{"id":1}}"""))

        val result = api.businessResult().first()

        assertTrue("actual=$result", result is BusinessResult.Success)
        val success = result as BusinessResult.Success
        assertTrue(success.dataRaw.orEmpty().contains("id"))
    }

    @Test
    fun `Flow BusinessResult 发射 BusinessError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"2001","message":"余额不足"}"""))

        val result = api.businessResult().first()

        assertTrue("actual=$result", result is BusinessResult.BusinessError)
        assertEquals("2001", (result as BusinessResult.BusinessError).code)
    }

    @Test
    fun `Flow BusinessResult 拦截器消费返回 Consumed`() = runBlocking {
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
    fun `Flow BusinessResult HTTP 4xx 转为 HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = api.businessResult().first()

        assertTrue("actual=$result", result is BusinessResult.HttpError)
        assertEquals(404, result.httpCode)
    }

    @Test
    fun `Flow BusinessResult 网络错误转为 NetworkError`() = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        val offlineApi = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .addCallAdapterFactory(NetFlowCallAdapterFactory())
            .build()
            .create(TestApi::class.java)

        val result = offlineApi.businessResult().first()

        assertTrue("actual=$result", result is BusinessResult.NetworkError)
    }

    // ========================================================================
    // 五、Flow<TypedBusinessResult<T>> —— 类型化业务结果
    // ========================================================================

    @Test
    fun `Flow TypedBusinessResult User 成功反序列化 data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"TypedUser","age":20}}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        val success = result as TypedBusinessResult.Success
        assertEquals("TypedUser", success.data?.name)
        assertEquals(20, success.data?.age)
    }

    @Test
    fun `Flow TypedBusinessResult User data 不匹配返回 DataConvertError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"string-not-object"}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.DataConvertError)
    }

    @Test
    fun `Flow TypedBusinessResult User 业务失败返回 BusinessError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"500","message":"failed"}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.BusinessError)
    }

    @Test
    fun `Flow TypedBusinessResult User HTTP 错误返回 HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.HttpError)
        assertEquals(503, result.httpCode)
    }

    @Test
    fun `Flow TypedBusinessResult User 拦截器消费返回 Consumed`() = runBlocking {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return BusinessResult.Consumed("maintenance", 200, chain.response.body)
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        val result = api.typedBusinessResult().first()

        assertTrue("actual=$result", result is TypedBusinessResult.Consumed)
        assertEquals("maintenance", (result as TypedBusinessResult.Consumed).reason)
    }

    // ========================================================================
    // 六、NetRetrofit.Builder DSL
    // ========================================================================

    @Test
    fun `NetRetrofit Builder 使用共享的 OkHttpClient`() {
        val netRetrofit = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .build()

        assertNotNull(netRetrofit.retrofit)
    }

    @Test
    fun `NetRetrofit Builder 自定义 OkHttpClient`() {
        val customClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Custom-Client", "yes")
                    .build()
                chain.proceed(request)
            }
            .build()

        val netRetrofit = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .client(customClient)
            .build()

        assertNotNull(netRetrofit.retrofit)
    }

    @Test
    fun `NetRetrofit Builder 自定义 ConverterFactory`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("plain text response")
        )

        val service = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .build()
            .retrofit
            .create(PlainTextApi::class.java)

        val body = service.getText()

        assertEquals("plain text response", body)
    }

    @Test
    fun `NetRetrofit Builder 自定义 CallAdapterFactory 与 NetFlowCallAdapterFactory 共存`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        val service = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .addCallAdapterFactory(NoopCallAdapterFactory())
            .build()
            .retrofit
            .create(TestApi::class.java)

        val result = service.netResult().first()

        // NetFlowCallAdapterFactory 仍然生效
        assertTrue(result is NetResult.Success)
    }

    @Test
    fun `NetRetrofit Builder 未设置 baseUrl 抛出异常`() {
        try {
            NetRetrofit.builder().build()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("baseUrl"))
        }
    }

    // ========================================================================
    // 七、Net.retrofit 扩展属性
    // ========================================================================

    @Test
    fun `Net_retrofit 返回 NetRetrofit_Builder`() {
        val builder = Net.retrofit

        assertNotNull(builder)
        assertTrue(builder is NetRetrofit.Builder)
    }

    @Test
    fun `Net_retrofit 链式调用完整示例`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"ExtensionTest","age":99}""")
                .addHeader("Content-Type", "application/json")
        )

        val userService = Net.retrofit
            .baseUrl(server.url("/").toString())
            .build()
            .retrofit
            .create(UserOnlyApi::class.java)

        val user = userService.getUser().first()

        assertEquals("ExtensionTest", user.name)
        assertEquals(99, user.age)
    }

    // ========================================================================
    // 八、NetRetrofit.create<T>() 泛型创建
    // ========================================================================

    @Test
    fun `NetRetrofit_create 泛型方法创建 API 实例`() {
        val netRetrofit = NetRetrofit.builder()
            .baseUrl(server.url("/").toString())
            .build()

        val api: TestApi = netRetrofit.create()

        assertNotNull(api)
    }

    // ========================================================================
    // 九、NetFlowCallAdapterFactory
    // ========================================================================

    @Test
    fun `NetFlowCallAdapterFactory 非 Flow 返回类型返回 null`() {
        val factory = NetFlowCallAdapterFactory()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .build()

        // Call 返回类型不应被 NetFlowCallAdapterFactory 处理
        val adapter = factory.get(
            Call::class.java,
            emptyArray(),
            retrofit
        )

        assertNull(adapter)
    }

    // ========================================================================
    // 十、Retrofit 请求注解组合
    // ========================================================================

    @Test
    fun `GET 请求带 Query 参数`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"query-ok"}"""))

        val result = api.getWithQuery("test-value").first()

        assertTrue(result is NetResult.Success)
        val request = server.takeRequest()
        assertTrue(request.requestUrl.toString().contains("q=test-value"))
    }

    @Test
    fun `GET 请求带 Path 参数`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"path-ok"}"""))

        val result = api.getWithPath("user-123").first()

        assertTrue(result is NetResult.Success)
        val request = server.takeRequest()
        assertTrue(request.requestUrl.toString().contains("user-123"))
    }

    @Test
    fun `POST 请求带 Body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Created","age":1}"""))

        val user = api.createUser(CreateUserRequest("NewUser", 18)).first()

        assertEquals("Created", user.name)
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("NewUser"))
    }

    @Test
    fun `POST 请求带 Header 注解`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"header-ok"}"""))

        val result = api.postWithHeader("Bearer mytoken").first()

        assertTrue(result is NetResult.Success)
        val request = server.takeRequest()
        assertEquals("Bearer mytoken", request.getHeader("Authorization"))
    }

    // ========================================================================
    // 十一、Flow 冷流特性 —— 多次收集
    // ========================================================================

    @Test
    fun `Retrofit Flow 多次收集每次发起新请求`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"first"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"second"}"""))

        val flow = api.netResult()

        val first = flow.first()
        val second = flow.first()

        assertTrue((first as NetResult.Success).body.orEmpty().contains("first"))
        assertTrue((second as NetResult.Success).body.orEmpty().contains("second"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Retrofit Flow TypedBusinessResult 多次收集`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"First","age":1}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Second","age":2}}"""))

        val flow = api.typedBusinessResult()

        val first = flow.first()
        val second = flow.first()

        assertEquals("First", (first as TypedBusinessResult.Success).data?.name)
        assertEquals("Second", (second as TypedBusinessResult.Success).data?.name)
        assertEquals(2, server.requestCount)
    }

    // ========================================================================
    // 十二、NetFlowException on Flow<T> 模式
    // ========================================================================

    @Test
    fun `Flow T HTTP 错误响应体超大也通过 NetFlowException 关闭`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(500).setBody("big-error-body"))

        try {
            api.getUser().first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertEquals(500, e.code)
        }
    }

    // ========================================================================
    // 十三、Retrofit Call 原始 API 仍然可用
    // ========================================================================

    @Test
    fun `Retrofit Call 原始同步执行仍然可用`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"CallTest","age":50}""")
                .addHeader("Content-Type", "application/json")
        )

        val response: Response<User> = api.getUserCall().execute()

        assertTrue(response.isSuccessful)
        assertEquals("CallTest", response.body()?.name)
        assertEquals(50, response.body()?.age)
    }

    // ========================================================================
    // 测试接口定义
    // ========================================================================

    interface TestApi {
        // Flow<T> 原始 body
        @GET("user")
        fun getUser(): Flow<User>

        // Flow<NetResponse<T>> 带响应包装
        @GET("user")
        fun getUserResponse(): Flow<NetResponse<User>>

        // Flow<NetResult> HTTP 层结构化结果
        @GET("net-result")
        fun netResult(): Flow<NetResult>

        // Flow<BusinessResult> 业务层结构化结果
        @GET("business")
        fun businessResult(): Flow<BusinessResult>

        // Flow<TypedBusinessResult<T>> 类型化业务结果
        @GET("typed")
        fun typedBusinessResult(): Flow<TypedBusinessResult<User>>

        // Call<User> 原始 Retrofit Call
        @GET("user")
        fun getUserCall(): Call<User>

        // GET 带 Query 参数
        @GET("search")
        fun getWithQuery(@Query("q") query: String): Flow<NetResult>

        // GET 带 Path 参数
        @GET("users/{id}")
        fun getWithPath(@Path("id") userId: String): Flow<NetResult>

        // POST 带 Body
        @POST("users")
        fun createUser(@Body request: CreateUserRequest): Flow<User>

        // POST 带 Header
        @POST("auth")
        fun postWithHeader(@Header("Authorization") auth: String): Flow<NetResult>

        // Flow<List<String>> 集合类型
        @GET("items")
        fun getItems(): Flow<List<String>>
    }

    interface UserOnlyApi {
        @GET("user")
        fun getUser(): Flow<User>
    }

    interface PlainTextApi {
        @GET("text")
        fun getText(): String
    }

    // ========================================================================
    // 辅助数据类和自定义工厂
    // ========================================================================

    data class User(val name: String, val age: Int)
    data class CreateUserRequest(val name: String, val age: Int)

    private class NoopCallAdapterFactory : CallAdapter.Factory() {
        override fun get(
            returnType: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit
        ): CallAdapter<*, *>? = null
    }
}
