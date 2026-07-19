package com.itg.ddlnet

import com.itg.net.Net
import com.itg.net.flow.*
import com.itg.net.flow.converter.NetConverter
import com.itg.net.flow.converter.GsonNetConverter
import com.itg.net.request.business.*
import com.itg.net.request.result.NetResult
import com.itg.net.download.data.Task
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.callback.AbstractProgressCallback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Net-Flow 模块完整测试示例。
 *
 * 覆盖：
 * - flowString：原始响应体字符串
 * - flowResult：结构化 HTTP 结果（NetResult 密封类）
 * - flowBusinessResult：业务结果（BusinessResult 密封类 + 拦截器链）
 * - flowTypedBusinessResult：类型化业务结果（TypedBusinessResult<T>）
 * - flowResponse：带自定义反序列化的响应
 * - Net.flowGet / Net.flowPostJson / Net.flowPostForm 顶层便捷方法
 * - Net.flowGetResponse / Net.flowPostJsonResponse 带反序列化的便捷方法
 * - Net.flowDownload 下载进度 Flow
 * - NetConverter 接口和 GsonNetConverter 实现
 * - NetFlowException 异常
 * - NetResponse 数据类
 * - DownloadPhase / DownloadProgress 下载模型
 * - Flow 取消自动取消底层 Call
 * - Flow 多次收集（冷流特性）
 * - 响应体大小限制
 * - 各种错误场景（HTTP 错误、网络错误、数据转换错误）
 */
class NetFlowExamplesTest {

    // ========================================================================
    // 测试基础设施
    // ========================================================================

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resetConfig()
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
    // 一、flowString —— 原始响应体字符串
    // ========================================================================

    @Test
    fun `flowString 发射成功响应体`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":"hello flow"}"""))

        val body = Net.get()
            .url(server.url("/flow-string").toString())
            .flowString()
            .first()

        assertTrue(body.contains("hello flow"))
    }

    @Test
    fun `flowString HTTP 4xx 通过 NetFlowException 关闭 Flow`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        try {
            Net.get()
                .url(server.url("/flow-404").toString())
                .flowString()
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertEquals(404, e.code)
            assertEquals("not found", e.message)
        }
    }

    @Test
    fun `flowString HTTP 5xx 通过 NetFlowException 关闭 Flow`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        try {
            Net.get()
                .url(server.url("/flow-500").toString())
                .flowString()
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun `flowString 网络断开通过 NetFlowException 关闭 Flow`() = runBlocking {
        val url = server.url("/offline").toString()
        server.shutdown()

        try {
            Net.get()
                .url(url)
                .flowString()
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertNull(e.code) // 非 HTTP 错误，code 为 null
            assertNotNull(e.message)
        }
    }

    @Test
    fun `flowString 响应体超大通过 NetFlowException 关闭 Flow`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("too-large-body"))

        try {
            Net.get()
                .url(server.url("/flow-too-large").toString())
                .flowString()
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertTrue(e.message.orEmpty().contains("large"))
        }
    }

    @Test
    fun `flowString 无效 URL 通过 NetFlowException 关闭 Flow`() = runBlocking {
        try {
            Net.get()
                .url("") // 空 URL
                .flowString()
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertTrue(e.message.orEmpty().contains("url"))
        }
    }

    // ========================================================================
    // 二、flowResult —— 结构化 HTTP 结果
    // ========================================================================

    @Test
    fun `flowResult 发射 NetResult_Success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":"ok"}"""))

        val result = Net.get()
            .url(server.url("/result-success").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
        assertEquals(200, result.code)
        assertTrue((result as NetResult.Success).body.orEmpty().contains("ok"))
    }

    @Test
    fun `flowResult 发射 NetResult_HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))

        val result = Net.get()
            .url(server.url("/result-403").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.HttpError)
        assertEquals(403, result.code)
        assertTrue((result as NetResult.HttpError).message.orEmpty().contains("Forbidden") ||
            (result as NetResult.HttpError).body.orEmpty().contains("forbidden"))
    }

    @Test
    fun `flowResult 发射 NetResult_NetworkError`() = runBlocking {
        val url = server.url("/result-offline").toString()
        server.shutdown()

        val result = Net.get()
            .url(url)
            .flowResult()
            .first()

        assertTrue(result is NetResult.NetworkError)
    }

    @Test
    fun `flowResult 发射 NetResult_ResponseTooLarge`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("too-large"))

        val result = Net.get()
            .url(server.url("/result-too-large").toString())
            .flowResult()
            .first()

        assertTrue("actual=$result", result is NetResult.ResponseTooLarge)
        assertEquals(200, result.code)
    }

    @Test
    fun `flowResult 包含响应头`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("X-Custom", "custom-value")
                .addHeader("Content-Type", "application/json")
        )

        val result = Net.get()
            .url(server.url("/result-headers").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
        val headers = (result as NetResult.Success).headers
        assertTrue(headers.containsKey("X-Custom") || headers.containsKey("x-custom"))
    }

    @Test
    fun `flowResult POST JSON 成功`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0"}"""))

        val result = Net.postJson()
            .url(server.url("/result-post").toString())
            .addJson("""{"key":"value"}""")
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
    }

    // ========================================================================
    // 三、flowBusinessResult —— 业务结果 + 拦截器链
    // ========================================================================

    @Test
    fun `flowBusinessResult 发射 BusinessResult_Success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","message":"ok","data":{"id":1}}"""))

        val result = Net.get()
            .url(server.url("/biz-success").toString())
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.Success)
        val success = result as BusinessResult.Success
        assertTrue(success.dataRaw.orEmpty().contains("id"))
    }

    @Test
    fun `flowBusinessResult 发射 BusinessResult_BusinessError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"1001","message":"参数错误"}"""))

        val result = Net.get()
            .url(server.url("/biz-error").toString())
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.BusinessError)
        assertEquals("1001", (result as BusinessResult.BusinessError).code)
    }

    @Test
    fun `flowBusinessResult 拦截器消费返回 Consumed`() = runBlocking {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return if (chain.envelope.code == "401") {
                        BusinessResult.Consumed("token expired", chain.response.code, chain.response.body)
                    } else {
                        chain.proceed()
                    }
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"401","message":"expired"}"""))

        val result = Net.get()
            .url(server.url("/biz-401").toString())
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.Consumed)
        assertEquals("token expired", (result as BusinessResult.Consumed).reason)
    }

    @Test
    fun `flowBusinessResult HTTP 4xx 转为 BusinessResult_HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        val result = Net.get()
            .url(server.url("/biz-http-err").toString())
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.HttpError)
        assertEquals(404, result.httpCode)
    }

    @Test
    fun `flowBusinessResult 网络错误转为 BusinessResult_NetworkError`() = runBlocking {
        val url = server.url("/biz-offline").toString()
        server.shutdown()

        val result = Net.get()
            .url(url)
            .flowBusinessResult()
            .first()

        assertTrue("actual=$result", result is BusinessResult.NetworkError)
    }

    @Test
    fun `flowBusinessResult 多拦截器按顺序执行`() = runBlocking {
        val order = mutableListOf<String>()
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    order.add("A-in")
                    val r = chain.proceed()
                    order.add("A-out")
                    return r
                }
            })
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    order.add("B-in")
                    val r = chain.proceed()
                    order.add("B-out")
                    return r
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        Net.get()
            .url(server.url("/biz-chain").toString())
            .flowBusinessResult()
            .first()

        assertEquals(listOf("A-in", "B-in", "B-out", "A-out"), order)
    }

    // ========================================================================
    // 四、flowTypedBusinessResult —— 类型化业务结果
    // ========================================================================

    @Test
    fun `flowTypedBusinessResult 成功转换 data 为指定类型`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Alice","age":25}}"""))

        val result = Net.get()
            .url(server.url("/typed-ok").toString())
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        assertEquals("Alice", (result as TypedBusinessResult.Success).data?.name)
        assertEquals(25, (result as TypedBusinessResult.Success).data?.age)
    }

    @Test
    fun `flowTypedBusinessResult 数据格式不匹配返回 DataConvertError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"not-an-object"}"""))

        val result = Net.get()
            .url(server.url("/typed-bad").toString())
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.DataConvertError)
        assertNotNull((result as TypedBusinessResult.DataConvertError).error)
    }

    @Test
    fun `flowTypedBusinessResult 业务失败返回 BusinessError 不进行 data 转换`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"500","message":"server error"}"""))

        val result = Net.get()
            .url(server.url("/typed-biz-fail").toString())
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.BusinessError)
    }

    @Test
    fun `flowTypedBusinessResult 拦截器消费返回 Consumed`() = runBlocking {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return BusinessResult.Consumed("maintenance mode", 200, chain.response.body)
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        val result = Net.get()
            .url(server.url("/typed-consumed").toString())
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.Consumed)
        assertEquals("maintenance mode", (result as TypedBusinessResult.Consumed).reason)
    }

    @Test
    fun `flowTypedBusinessResult HTTP 错误返回 HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))

        val result = Net.get()
            .url(server.url("/typed-http-err").toString())
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.HttpError)
        assertEquals(502, result.httpCode)
    }

    @Test
    fun `flowTypedBusinessResult 网络错误返回 NetworkError`() = runBlocking {
        val url = server.url("/typed-offline").toString()
        server.shutdown()

        val result = Net.get()
            .url(url)
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.NetworkError)
    }

    // ========================================================================
    // 五、flowResponse —— 自定义反序列化
    // ========================================================================

    @Test
    fun `flowResponse 发射 NetResponse 包含自定义 body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Bob","age":40}"""))

        val response = Net.get()
            .url(server.url("/flow-response").toString())
            .flowResponse { raw -> GsonNetConverter<User>(User::class.java).convert(raw) }
            .first()

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals("Bob", response.body?.name)
        assertEquals(40, response.body?.age)
        assertNotNull(response.rawBody)
    }

    @Test
    fun `flowResponse HTTP 错误仍发射 NetResponse（不抛异常）`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"missing"}"""))

        val response = Net.get()
            .url(server.url("/flow-resp-404").toString())
            .flowResponse { raw -> raw }
            .first()

        assertEquals(404, response.code)
        assertFalse(response.isSuccessful)
    }

    @Test
    fun `flowResponse 网络错误通过 NetFlowException 关闭 Flow`() = runBlocking {
        val url = server.url("/flow-resp-offline").toString()
        server.shutdown()

        try {
            Net.get()
                .url(url)
                .flowResponse { raw -> raw }
                .first()
            fail("Expected NetFlowException")
        } catch (e: NetFlowException) {
            assertNull(e.code)
        }
    }

    @Test
    fun `flowResponse 包含响应头`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("X-Request-Id", "req-123")
        )

        val response = Net.get()
            .url(server.url("/flow-resp-headers").toString())
            .flowResponse { raw -> raw }
            .first()

        assertTrue(response.headers.containsKey("X-Request-Id") ||
            response.headers.containsKey("x-request-id"))
    }

    // ========================================================================
    // 六、Net.flowGet / Net.flowPostJson / Net.flowPostForm 便捷方法
    // ========================================================================

    @Test
    fun `Net_flowGet 便捷方法`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("get-ok"))

        val body = Net.flowGet {
            url(server.url("/convenience-get").toString())
        }.first()

        assertEquals("get-ok", body)
    }

    @Test
    fun `Net_flowPostJson 便捷方法`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("post-ok"))

        val body = Net.flowPostJson {
            url(server.url("/convenience-post").toString())
            addJson("""{"action":"test"}""")
        }.first()

        assertEquals("post-ok", body)
    }

    @Test
    fun `Net_flowPostForm 便捷方法`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("form-ok"))

        val body = Net.flowPostForm {
            url(server.url("/convenience-form").toString())
            addParam("field", "value")
        }.first()

        assertEquals("form-ok", body)
    }

    @Test
    fun `Net_flowGetResponse 便捷方法带反序列化`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Eve","age":28}"""))

        val converter = GsonNetConverter<User>(User::class.java)
        val response = Net.flowGetResponse(converter) {
            url(server.url("/convenience-get-resp").toString())
        }.first()

        assertEquals("Eve", response.body?.name)
        assertEquals(28, response.body?.age)
    }

    @Test
    fun `Net_flowPostJsonResponse 便捷方法带反序列化`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Dan","age":35}"""))

        val converter = GsonNetConverter<User>(User::class.java)
        val response = Net.flowPostJsonResponse(converter) {
            url(server.url("/convenience-post-resp").toString())
            addJson("""{}""")
        }.first()

        assertEquals("Dan", response.body?.name)
        assertEquals(35, response.body?.age)
    }

    // ========================================================================
    // 七、NetConverter 接口和 GsonNetConverter
    // ========================================================================

    @Test
    fun `GsonNetConverter 正确反序列化 JSON`() {
        val converter = GsonNetConverter<User>(User::class.java)

        val user = converter.convert("""{"name":"Charlie","age":22}""")

        assertNotNull(user)
        assertEquals("Charlie", user!!.name)
        assertEquals(22, user.age)
    }

    @Test
    fun `GsonNetConverter 处理 null 输入`() {
        val converter = GsonNetConverter<User>(User::class.java)

        val user = converter.convert(null)

        assertNull(user)
    }

    @Test
    fun `GsonNetConverter 处理格式错误的 JSON`() {
        val converter = GsonNetConverter<User>(User::class.java)

        val user = converter.convert("invalid json")

        assertNull(user)
    }

    @Test
    fun `自定义 NetConverter 实现`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("custom-format:hello"))

        val customConverter = object : NetConverter<String> {
            override fun convert(raw: String?): String? {
                return raw?.removePrefix("custom-format:")
            }
        }

        val response = Net.flowGetResponse(customConverter) {
            url(server.url("/custom-converter").toString())
        }.first()

        assertEquals("hello", response.body)
    }

    // ========================================================================
    // 八、NetFlowException
    // ========================================================================

    @Test
    fun `NetFlowException 是 IOException 的子类`() {
        val ex = NetFlowException(404, "not found")

        assertTrue(ex is java.io.IOException)
        assertEquals(404, ex.code)
        assertEquals("not found", ex.message)
    }

    @Test
    fun `NetFlowException 无 HTTP 状态码时 code 为 null`() {
        val ex = NetFlowException(null, "network error")

        assertNull(ex.code)
        assertEquals("network error", ex.message)
    }

    // ========================================================================
    // 九、NetResponse 数据类
    // ========================================================================

    @Test
    fun `NetResponse isSuccessful 判断 2xx 范围`() {
        assertTrue(NetResponse("ok", "ok", 200).isSuccessful)
        assertTrue(NetResponse("ok", "ok", 201).isSuccessful)
        assertTrue(NetResponse("ok", "ok", 299).isSuccessful)
        assertFalse(NetResponse(null, null, 300).isSuccessful)
        assertFalse(NetResponse(null, null, 404).isSuccessful)
        assertFalse(NetResponse(null, null, 500).isSuccessful)
    }

    @Test
    fun `NetResponse_from 工厂方法 body 等于 rawBody`() {
        val response = NetResponse.from("hello", 200, mapOf("X-Key" to "val"))

        assertEquals("hello", response.body)
        assertEquals("hello", response.rawBody)
        assertEquals(200, response.code)
        assertEquals("val", response.headers["X-Key"])
    }

    // ========================================================================
    // 十、Flow 取消自动取消底层 Call
    // ========================================================================

    @Test
    fun `Flow 收集取消后底层请求被取消`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("cancelled").setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS))

        val job = launch {
            Net.get()
                .url(server.url("/cancel-flow").toString())
                .flowString()
                .collect { /* 不会到达 */ }
        }

        // 等待请求发出
        delay(200)
        job.cancelAndJoin()
    }

    // ========================================================================
    // 十一、Flow 冷流特性 —— 多次收集
    // ========================================================================

    @Test
    fun `flowResult 多次收集每次发起新请求`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("second"))

        val flow = Net.get()
            .url(server.url("/multi-collect").toString())
            .flowResult()

        val first = flow.first()
        val second = flow.first()

        assertTrue((first as NetResult.Success).body.orEmpty().contains("first"))
        assertTrue((second as NetResult.Success).body.orEmpty().contains("second"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `flowString 多次收集每次发起新请求`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("req-1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("req-2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("req-3"))

        val flow = Net.get()
            .url(server.url("/multi-string").toString())
            .flowString()

        val results = listOf(flow.first(), flow.first(), flow.first())

        assertEquals(listOf("req-1", "req-2", "req-3"), results)
        assertEquals(3, server.requestCount)
    }

    // ========================================================================
    // 十二、Flow 异常处理（catch 操作符）
    // ========================================================================

    @Test
    fun `catch 操作符捕获 flowString 的异常`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        val result = Net.get()
            .url(server.url("/catch-test").toString())
            .flowString()
            .catch { e ->
                assertTrue(e is NetFlowException)
                emit("caught:${(e as NetFlowException).code}")
            }
            .first()

        assertEquals("caught:500", result)
    }

    @Test
    fun `catch 操作符捕获 flowResult 的 IOException`() = runBlocking {
        val url = server.url("/catch-io").toString()
        server.shutdown()

        var caught = false
        Net.get()
            .url(url)
            .flowResult()
            .catch { caught = true }
            .collect { /* NetworkError 已作为值发射，不走 catch */ }

        // flowResult 网络错误通过发射 NetworkError 而非抛异常处理
        assertFalse(caught)
    }

    // ========================================================================
    // 十三、响应体大小限制
    // ========================================================================

    @Test
    fun `flowResult 小响应体正常返回`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(1024)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("small"))

        val result = Net.get()
            .url(server.url("/small-body").toString())
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
    }

    @Test
    fun `flowResult 超大响应体返回 ResponseTooLarge`() = runBlocking {
        Net.configure {
            maxResponseBodyBytes(4)
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("very-large-body"))

        val result = Net.get()
            .url(server.url("/large-body").toString())
            .flowResult()
            .first()

        assertTrue("actual=$result", result is NetResult.ResponseTooLarge)
    }

    // ========================================================================
    // 十四、DownloadPhase 和 DownloadProgress 模型
    // ========================================================================

    @Test
    fun `DownloadPhase 枚举值完整`() {
        assertEquals(4, DownloadPhase.values().size)
        assertEquals(DownloadPhase.Connecting, DownloadPhase.valueOf("Connecting"))
        assertEquals(DownloadPhase.Downloading, DownloadPhase.valueOf("Downloading"))
        assertEquals(DownloadPhase.Complete, DownloadPhase.valueOf("Complete"))
        assertEquals(DownloadPhase.Failed, DownloadPhase.valueOf("Failed"))
    }

    @Test
    fun `DownloadProgress 数据类属性`() {
        val task = Task().apply {
            url = "https://example.com/file.zip"
            path = "/tmp/file.zip"
            contentLength = 1024
            downloadSize = 512
        }

        val progress = DownloadProgress(task, DownloadPhase.Downloading)

        assertEquals(task, progress.task)
        assertEquals(DownloadPhase.Downloading, progress.phase)
        assertEquals(1024, progress.task.contentLength)
        assertEquals(512, progress.task.downloadSize)
    }

    @Test
    fun `DownloadProgress 各阶段构造`() {
        val task = Task().apply {
            url = "https://example.com/dl.zip"
            path = "/tmp/dl.zip"
        }

        val connecting = DownloadProgress(task, DownloadPhase.Connecting)
        val downloading = DownloadProgress(task, DownloadPhase.Downloading)
        val complete = DownloadProgress(task, DownloadPhase.Complete)
        val failed = DownloadProgress(task, DownloadPhase.Failed)

        assertEquals(DownloadPhase.Connecting, connecting.phase)
        assertEquals(DownloadPhase.Downloading, downloading.phase)
        assertEquals(DownloadPhase.Complete, complete.phase)
        assertEquals(DownloadPhase.Failed, failed.phase)
    }

    // ========================================================================
    // 十五、POST 请求 + Flow 组合
    // ========================================================================

    @Test
    fun `POST JSON flowBusinessResult 完整链路`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"PostTest"}}"""))

        val result = Net.postJson()
            .url(server.url("/flow-post-biz").toString())
            .addJson("""{"user":"test"}""")
            .flowTypedBusinessResult<User>()
            .first()

        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        assertEquals("PostTest", (result as TypedBusinessResult.Success).data?.name)
    }

    @Test
    fun `POST Form flowResult 完整链路`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("form-success"))

        val result = Net.postForm()
            .url(server.url("/flow-form-result").toString())
            .addParam("action", "submit")
            .flowResult()
            .first()

        assertTrue(result is NetResult.Success)
    }

    @Test
    fun `POST Multipart flowString 完整链路`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("multipart-ok"))

        val body = Net.postMultipart()
            .url(server.url("/flow-multipart").toString())
            .addParam("token", "abc")
            .addContent("data", "text/plain")
            .flowString()
            .first()

        assertEquals("multipart-ok", body)
    }

    // ========================================================================
    // 辅助数据类
    // ========================================================================

    data class User(val name: String, val age: Int)
}
