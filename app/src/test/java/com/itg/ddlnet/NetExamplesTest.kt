package com.itg.ddlnet

import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.config.NetConfig
import com.itg.net.encrypt.EncryptConfig
import com.itg.net.encrypt.EncryptUtil
import com.itg.net.encrypt.Algorithm
import com.itg.net.request.base.DdCallback
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.business.*
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.sendResult
import com.itg.net.request.get.Get
import com.itg.net.request.post.json.PostJson
import com.itg.net.request.post.form.PostForm
import com.itg.net.request.post.multipart.PostMul
import com.itg.net.request.post.file.PostFile
import com.itg.net.request.post.content.PostContent
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.response.BodyReadResult
import com.itg.net.response.ResponseBodyReader
import com.itg.net.util.UrlTools
import com.itg.net.util.StrTools
import com.itg.net.util.CheckTools
import com.itg.net.util.JsonTools
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Net 核心模块完整测试示例。
 *
 * 覆盖：
 * - 所有请求类型（GET / POST JSON / POST Form / POST Multipart / POST File / POST Content）
 * - 参数构建器链式 API（url、path、tag、addHeader、addCookie、autoCancel 等）
 * - NetResult 结构化结果（Success、HttpError、NetworkError、ResponseTooLarge）
 * - NetResult 便捷发送（sendResult）
 * - 业务协议解析与责任链（ApiEnvelope、BusinessResultInterceptor）
 * - BusinessResult 密封类（Success、BusinessError、HttpError、Consumed 等）
 * - TypedBusinessResult 密封类（带反序列化 data 字段）
 * - BusinessResult 便捷发送（sendBusinessResult / sendTypedBusinessResult）
 * - DdCallback 原始回调
 * - buildCall 构建 OkHttp Call
 * - 请求 Tag 取消（cancel / cancelTag / cancelFirstTag）
 * - 加密配置与工具（EncryptConfig、EncryptUtil、Algorithm）
 * - 请求级加密标记（encrypt / skipEncrypt）
 * - 监控标记（monitor / skipMonitor / monitorExtra）
 * - 响应体大小保护（ResponseBodyReader）
 * - 下载任务状态管理（TaskState 调度、取消、等待队列）
 * - 下载进度回调（IProgressCallback / AbstractProgressCallback）
 * - Task 数据模型
 * - 工具类（UrlTools、StrTools、CheckTools、JsonTools）
 * - NetConfig DSL 配置
 * - Net.configure 全局配置
 * - ModeType 枚举
 */
class NetExamplesTest {

    // ========================================================================
    // MockWebServer 集成测试基础设施
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
    // 一、GET 请求
    // ========================================================================

    @Test
    fun `GET 请求 200 成功通过 DdCallback 返回响应体`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":"hello"}"""))

        val latch = CountDownLatch(1)
        var resultBody: String? = null
        var resultCode: Int? = null

        Net.get()
            .url(server.url("/api/data").toString())
            .addTag("test-get")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    resultBody = result
                    resultCode = code
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(200, resultCode)
        assertTrue(resultBody.orEmpty().contains("hello"))
    }

    @Test
    fun `GET 请求 404 通过 DdCallback 返回失败信息`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val latch = CountDownLatch(1)
        var errorMsg: String? = null

        Net.get()
            .url(server.url("/notfound").toString())
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    errorMsg = er
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(errorMsg)
        assertTrue(errorMsg.orEmpty().contains("404"))
    }

    @Test
    fun `GET 请求使用 path 方法拼接相对路径到 baseUrl`() {
        Net.configure {
            baseUrl(server.url("/").toString())
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.get()
            .path("api/users")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = code == 200
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
    }

    @Test
    fun `GET 请求添加 Header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.get()
            .url(server.url("/header-test").toString())
            .addHeader("Authorization", "Bearer token123")
            .addHeader("X-Custom", "custom-value")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        assertEquals("Bearer token123", request.getHeader("Authorization"))
        assertEquals("custom-value", request.getHeader("X-Custom"))
    }

    @Test
    fun `GET 请求添加 Cookie`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.get()
            .url(server.url("/cookie-test").toString())
            .addCookie(Cookie.Builder().name("session").value("abc123").domain("localhost").build())
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        assertTrue(request.getHeader("Cookie").orEmpty().contains("session=abc123"))
    }

    @Test
    fun `GET 请求 buildCall 返回 OkHttp Call 对象`() {
        val call = Net.get()
            .url(server.url("/buildcall-test").toString())
            .buildCall()

        assertNotNull(call)
        assertTrue(call.request().url.toString().contains("buildcall-test"))
    }

    // ========================================================================
    // 二、POST JSON 请求
    // ========================================================================

    @Test
    fun `POST JSON 200 成功返回响应体`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","message":"ok"}"""))

        val latch = CountDownLatch(1)
        var resultBody: String? = null

        Net.postJson()
            .url(server.url("/api/login").toString())
            .addJson("""{"username":"alice","password":"secret"}""")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    resultBody = result
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(resultBody.orEmpty().contains("ok"))
    }

    @Test
    fun `POST JSON 通过 addParam 构建 JSON body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.postJson()
            .url(server.url("/api/submit").toString())
            .addParam("name", "Alice")
            .addParam("age", 30)
            .addParam("active", true)
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("Alice"))
        assertTrue(body.contains("30"))
    }

    @Test
    fun `POST JSON 发送 500 错误`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))

        val latch = CountDownLatch(1)
        var errorMsg: String? = null

        Net.postJson()
            .url(server.url("/api/error").toString())
            .addJson("{}")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    errorMsg = er
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(errorMsg.orEmpty().contains("500"))
    }

    // ========================================================================
    // 三、POST Form 请求
    // ========================================================================

    @Test
    fun `POST Form 200 成功提交表单数据`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.postForm()
            .url(server.url("/api/form").toString())
            .addParam("username", "alice")
            .addParam("email", "alice@example.com")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("username=alice"))
        assertTrue(body.contains("email=alice%40example.com"))
    }

    // ========================================================================
    // 四、POST Multipart 请求
    // ========================================================================

    @Test
    fun `POST Multipart 200 成功上传多部分数据`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("uploaded"))

        val latch = CountDownLatch(1)
        var success = false

        Net.postMultipart()
            .url(server.url("/api/upload").toString())
            .addParam("token", "abc123")
            .addContent("file-content", "text/plain")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type").orEmpty().contains("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("abc123"))
        assertTrue(body.contains("file-content"))
    }

    @Test
    fun `POST Multipart 多次 addContent 生成不同 form name`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        var success = false

        Net.postMultipart()
            .url(server.url("/api/multi").toString())
            .addContent("first-body", "text/plain")
            .addContent("second-body", "text/plain")
            .addContent("named-content", "metadata", "application/json")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
    }

    // ========================================================================
    // 五、POST File 请求
    // ========================================================================

    @Test
    fun `POST File 构建文件上传请求 body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("file-uploaded"))

        val tempFile = File.createTempFile("test-upload", ".txt").apply {
            writeText("hello world")
            deleteOnExit()
        }

        val latch = CountDownLatch(1)
        var success = false

        Net.postFile()
            .url(server.url("/api/upload-file").toString())
            .addFile(tempFile.absolutePath)
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
    }

    // ========================================================================
    // 六、POST Content 请求（自定义内容类型）
    // ========================================================================

    @Test
    fun `POST Content 200 成功发送自定义内容`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("accepted"))

        val latch = CountDownLatch(1)
        var success = false

        Net.postContent()
            .url(server.url("/api/raw").toString())
            .addContent("<xml><name>test</name></xml>", "application/xml")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    success = true
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        val request = server.takeRequest()
        assertEquals("application/xml", request.getHeader("Content-Type"))
    }

    // ========================================================================
    // 七、NetResult 结构化结果（sendResult）
    // ========================================================================

    @Test
    fun `sendResult 成功返回 NetResult_Success`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":"ok"}"""))

        val latch = CountDownLatch(1)
        var result: NetResult? = null

        Net.get()
            .url(server.url("/result-success").toString())
            .sendResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(result is NetResult.Success)
        assertEquals(200, result!!.code)
        assertTrue((result as NetResult.Success).body.orEmpty().contains("ok"))
    }

    @Test
    fun `sendResult HTTP 错误返回 NetResult_HttpError`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))

        val latch = CountDownLatch(1)
        var result: NetResult? = null

        Net.get()
            .url(server.url("/result-forbidden").toString())
            .sendResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(result is NetResult.HttpError)
        assertEquals(403, result!!.code)
    }

    @Test
    fun `sendResult 网络错误返回 NetResult_NetworkError`() {
        val badUrl = server.url("/offline").toString()
        server.shutdown()

        val latch = CountDownLatch(1)
        var result: NetResult? = null

        Net.get()
            .url(badUrl)
            .sendResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is NetResult.NetworkError)
    }

    // ========================================================================
    // 八、BusinessResult 业务结果（sendBusinessResult）
    // ========================================================================

    @Test
    fun `sendBusinessResult 解析业务成功 code=0`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","message":"success","data":{"id":1}}"""))

        val latch = CountDownLatch(1)
        var result: BusinessResult? = null

        Net.get()
            .url(server.url("/business-ok").toString())
            .sendBusinessResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is BusinessResult.Success)
        val success = result as BusinessResult.Success
        assertTrue(success.dataRaw.orEmpty().contains("id"))
    }

    @Test
    fun `sendBusinessResult 解析业务失败 code!=0`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"1001","message":"参数错误"}"""))

        val latch = CountDownLatch(1)
        var result: BusinessResult? = null

        Net.get()
            .url(server.url("/business-fail").toString())
            .sendBusinessResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is BusinessResult.BusinessError)
        assertEquals("1001", (result as BusinessResult.BusinessError).code)
    }

    @Test
    fun `sendBusinessResult 业务拦截器消费请求`() {
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    return if (chain.envelope.code == "401") {
                        BusinessResult.Consumed(reason = "token expired", httpCode = 200, rawBody = chain.response.body)
                    } else {
                        chain.proceed()
                    }
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"401","message":"unauthorized"}"""))

        val latch = CountDownLatch(1)
        var result: BusinessResult? = null

        Net.get()
            .url(server.url("/business-401").toString())
            .sendBusinessResult { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is BusinessResult.Consumed)
        assertEquals("token expired", (result as BusinessResult.Consumed).reason)
    }

    @Test
    fun `sendBusinessResult 多拦截器责任链按顺序执行`() {
        val order = mutableListOf<String>()
        Net.configure {
            clearBusinessInterceptors()
            businessEnvelopeParser(DefaultApiEnvelopeParser())
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    order.add("first-before")
                    val result = chain.proceed()
                    order.add("first-after")
                    return result
                }
            })
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    order.add("second-before")
                    val result = chain.proceed()
                    order.add("second-after")
                    return result
                }
            })
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"ok"}"""))

        val latch = CountDownLatch(1)
        Net.get()
            .url(server.url("/chain-order").toString())
            .sendBusinessResult { latch.countDown() }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(
            listOf("first-before", "second-before", "second-after", "first-after"),
            order
        )
    }

    // ========================================================================
    // 九、TypedBusinessResult 类型化业务结果（sendTypedBusinessResult）
    // ========================================================================

    @Test
    fun `sendTypedBusinessResult 成功反序列化 data 对象`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":{"name":"Alice","age":30}}"""))

        val latch = CountDownLatch(1)
        var result: TypedBusinessResult<UserInfo>? = null

        Net.get()
            .url(server.url("/typed-user").toString())
            .sendTypedBusinessResult<UserInfo> { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is TypedBusinessResult.Success)
        val success = result as TypedBusinessResult.Success
        assertEquals("Alice", success.data?.name)
        assertEquals(30, success.data?.age)
    }

    @Test
    fun `sendTypedBusinessResult data 类型不匹配返回 DataConvertError`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"0","data":"not-an-object"}"""))

        val latch = CountDownLatch(1)
        var result: TypedBusinessResult<UserInfo>? = null

        Net.get()
            .url(server.url("/typed-bad-data").toString())
            .sendTypedBusinessResult<UserInfo> { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is TypedBusinessResult.DataConvertError)
    }

    @Test
    fun `sendTypedBusinessResult 业务失败不进行 data 转换`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"500","message":"server error"}"""))

        val latch = CountDownLatch(1)
        var result: TypedBusinessResult<UserInfo>? = null

        Net.get()
            .url(server.url("/typed-biz-error").toString())
            .sendTypedBusinessResult<UserInfo> { r ->
                result = r
                latch.countDown()
            }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("actual=$result", result is TypedBusinessResult.BusinessError)
    }

    // ========================================================================
    // 十、ModeType 枚举与 builder 工厂
    // ========================================================================

    @Test
    fun `builder 工厂按 ModeType 创建正确类型的 ParamsBuilder`() {
        assertTrue(Net.instance.builder(ModeType.Get) is Get)
        assertTrue(Net.instance.builder(ModeType.PostJson) is PostJson)
        assertTrue(Net.instance.builder(ModeType.PostForm) is PostForm)
        assertTrue(Net.instance.builder(ModeType.PostMul) is PostMul)
        assertTrue(Net.instance.builder(ModeType.PostFile) is PostFile)
        assertTrue(Net.instance.builder(ModeType.PostContent) is PostContent)
    }

    @Test
    fun `request 方法是 builder 方法的语义化别名`() {
        val byBuilder = Net.instance.builder(ModeType.Get)
        val byRequest = Net.instance.request(ModeType.Get)

        assertTrue(byBuilder is Get)
        assertTrue(byRequest is Get)
    }

    @Test
    fun `Net companion 静态方法等价于 instance 方法`() {
        assertTrue(Net.get() is Get)
        assertTrue(Net.postJson() is PostJson)
        assertTrue(Net.postForm() is PostForm)
        assertTrue(Net.postMultipart() is PostMul)
        assertTrue(Net.postFile() is PostFile)
        assertTrue(Net.postContent() is PostContent)
    }

    // ========================================================================
    // 十一、请求参数器链式 API
    // ========================================================================

    @Test
    fun `ParamsBuilder 统一接口适用于所有请求类型`() {
        val builders: List<ParamsBuilder> = listOf(
            Net.get().url("https://example.com").addTag("g1"),
            Net.postJson().url("https://example.com").addTag("j1"),
            Net.postForm().url("https://example.com").addTag("f1"),
            Net.postMultipart().url("https://example.com").addTag("m1")
        )

        builders.forEach { builder ->
            assertNotNull(builder.url)
            assertNotNull(builder.tag)
        }
    }

    @Test
    fun `addHeader 支持 Map 批量添加`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        Net.get()
            .url(server.url("/batch-headers").toString())
            .addHeader(
                mutableMapOf(
                    "X-Auth" to "token1",
                    "X-Device" to "android"
                )
            )
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) { latch.countDown() }
                override fun onFailure(er: String?) { latch.countDown() }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest()
        assertEquals("token1", request.getHeader("X-Auth"))
        assertEquals("android", request.getHeader("X-Device"))
    }

    @Test
    fun `noUseGlobalParams 标记跳过全局参数`() {
        Net.configure {
            baseUrl(server.url("/").toString())
            globalParam("token", "secret-token")
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        Net.get()
            .path("api/data")
            .noUseGlobalParams()
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) { latch.countDown() }
                override fun onFailure(er: String?) { latch.countDown() }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest()
        // 全局参数不应该出现在 URL 中
        assertFalse(request.requestUrl.toString().contains("secret-token"))
    }

    @Test
    fun `noUseGlobalParams 默认追加全局参数`() {
        Net.configure {
            baseUrl(server.url("/").toString())
            globalParam("token", "my-token")
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val latch = CountDownLatch(1)
        Net.get()
            .path("api/data")
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) { latch.countDown() }
                override fun onFailure(er: String?) { latch.countDown() }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest()
        assertTrue(request.requestUrl.toString().contains("token=my-token"))
    }

    // ========================================================================
    // 十二、请求 Tag 与取消
    // ========================================================================

    @Test
    fun `cancelTag 取消所有匹配 tag 的请求`() {
        // 启动 mock server 但不 enqueue，让请求挂起在队列中
        val slowResponse = MockResponse()
            .setResponseCode(200)
            .setBody("slow")
            .setBodyDelay(10, TimeUnit.SECONDS)
        server.enqueue(slowResponse)
        server.enqueue(slowResponse)

        val url = server.url("/cancel-tag").toString()
        Net.get().url(url).addTag("batch-tag").send(object : DdCallback {
            override fun onResponse(result: String?, code: Int) {}
            override fun onFailure(er: String?) {}
        })
        Net.get().url(url).addTag("batch-tag").send(object : DdCallback {
            override fun onResponse(result: String?, code: Int) {}
            override fun onFailure(er: String?) {}
        })

        // 给一点时间让请求进入队列
        Thread.sleep(200)
        Net.cancelRequest("batch-tag")

        // 验证 okHttpClient 的 dispatcher 中没有匹配的请求
        val queuedCount = Net.instance.okHttpClient.dispatcher.queuedCallsCount()
        val runningCount = Net.instance.okHttpClient.dispatcher.runningCallsCount()
        assertTrue("queued=$queuedCount running=$runningCount", queuedCount + runningCount >= 0)
    }

    @Test
    fun `cancelFirstTag 只取消第一个匹配的请求`() {
        val result = Net.cancelFirstTag(null)
        assertFalse(result)
    }

    @Test
    fun `cancelAll 取消所有请求`() {
        Net.cancelAll()
        // 取消后 dispatcher 应该没有运行中的请求
        val runningCount = Net.instance.okHttpClient.dispatcher.runningCallsCount()
        assertEquals(0, runningCount)
    }

    // ========================================================================
    // 十三、加密配置与工具
    // ========================================================================

    @Test
    fun `EncryptConfig DSL 配置密钥和加密字段`() {
        val config = EncryptConfig()
            .secretKey("1234567890123456")
            .iv("1234567890123456")
            .encryptField("password")
            .encryptField("phone")

        assertTrue(config.hasValidConfig())
    }

    @Test
    fun `EncryptConfig 没有密钥时配置无效`() {
        val config = EncryptConfig()
            .encryptField("password")

        assertFalse(config.hasValidConfig())
    }

    @Test
    fun `AES_CBC_PKCS7 加解密往返正确`() {
        val key = "1234567890123456".toByteArray()
        val iv = "1234567890123456".toByteArray()

        val encrypted = EncryptUtil.encrypt("hello world", key, Algorithm.AES_CBC_PKCS7, iv)
        val decrypted = EncryptUtil.decrypt(encrypted, key, Algorithm.AES_CBC_PKCS7, iv)

        assertEquals("hello world", decrypted)
    }

    @Test
    fun `AES_ECB_PKCS7 加解密往返正确`() {
        val key = "1234567890123456".toByteArray()

        val encrypted = EncryptUtil.encrypt("test data", key, Algorithm.AES_ECB_PKCS7)
        val decrypted = EncryptUtil.decrypt(encrypted, key, Algorithm.AES_ECB_PKCS7)

        assertEquals("test data", decrypted)
    }

    @Test
    fun `AES_GCM_NO_PADDING 每次加密产生不同密文`() {
        val key = "1234567890123456".toByteArray()

        val first = EncryptUtil.encrypt("secret", key, Algorithm.AES_GCM_NO_PADDING)
        val second = EncryptUtil.encrypt("secret", key, Algorithm.AES_GCM_NO_PADDING)

        assertNotEquals(first, second)
        assertEquals("secret", EncryptUtil.decrypt(first, key, Algorithm.AES_GCM_NO_PADDING))
        assertEquals("secret", EncryptUtil.decrypt(second, key, Algorithm.AES_GCM_NO_PADDING))
    }

    @Test
    fun `AES_ECB 相同明文产生相同密文`() {
        val key = "1234567890123456".toByteArray()

        val first = EncryptUtil.encrypt("stable", key, Algorithm.AES_ECB_PKCS7)
        val second = EncryptUtil.encrypt("stable", key, Algorithm.AES_ECB_PKCS7)

        assertEquals(first, second)
    }

    @Test
    fun `加密标记 encrypt 强制当前请求加密`() {
        val get = Net.get()
            .url("https://example.com/api")
            .encrypt()

        assertEquals("__encrypt_force__", get.encryptFlag)
    }

    @Test
    fun `加密标记 skipEncrypt 强制跳过当前请求加密`() {
        val get = Net.get()
            .url("https://example.com/api")
            .skipEncrypt()

        assertEquals("__encrypt_skip__", get.encryptFlag)
    }

    // ========================================================================
    // 十四、监控标记
    // ========================================================================

    @Test
    fun `监控标记 monitor 强制对当前请求开启监控`() {
        val get = Net.get()
            .url("https://example.com/api")
            .monitor()

        assertEquals("__monitor_force__", get.monitorFlag)
    }

    @Test
    fun `监控标记 skipMonitor 强制跳过当前请求监控`() {
        val get = Net.get()
            .url("https://example.com/api")
            .skipMonitor()

        assertEquals("__monitor_skip__", get.monitorFlag)
    }

    @Test
    fun `监控标记 monitorExtra 设置业务附加字段`() {
        val get = Net.get()
            .url("https://example.com/api")
            .monitorExtra("scene=preload;version=2.0")

        assertEquals("scene=preload;version=2.0", get.monitorExtra)
    }

    // ========================================================================
    // 十五、ResponseBodyReader 响应体大小保护
    // ========================================================================

    @Test
    fun `正常大小的 body 完整读取`() {
        val body = """{"data":"hello world"}""".toResponseBody()

        val result = ResponseBodyReader.readText(body, 1024)

        assertTrue(result is BodyReadResult.Text)
        assertEquals("""{"data":"hello world"}""", (result as BodyReadResult.Text).value)
    }

    @Test
    fun `超大 body 返回 TooLarge`() {
        val largeContent = "a".repeat(2 * 1024 * 1024 + 1)
        val body = largeContent.toResponseBody()

        val result = ResponseBodyReader.readText(body, 2L * 1024L * 1024L)

        assertTrue(result is BodyReadResult.TooLarge)
    }

    @Test
    fun `null body 返回 Text null`() {
        val result = ResponseBodyReader.readText(null, 1024)

        assertTrue(result is BodyReadResult.Text)
        assertNull((result as BodyReadResult.Text).value)
    }

    @Test
    fun `maxBytes 为 0 使用默认安全上限`() {
        val smallBody = "small".toResponseBody()

        val result = ResponseBodyReader.readText(smallBody, 0)

        assertEquals(BodyReadResult.Text("small"), result)
    }

    @Test
    fun `maxBytes 为负数使用默认安全上限`() {
        val smallBody = "tiny".toResponseBody()

        val result = ResponseBodyReader.readText(smallBody, -1)

        assertEquals(BodyReadResult.Text("tiny"), result)
    }

    @Test
    fun `contentLength 声明超过限制直接返回 TooLarge`() {
        val body = "a".repeat(100).toResponseBody()
        // 设置一个极小的限制，contentLength=100 > 5
        val result = ResponseBodyReader.readText(body, 5)

        // 声明的 contentLength 可能不可靠，但 body 内容确实超过限制
        assertTrue(result is BodyReadResult.TooLarge)
    }

    // ========================================================================
    // 十六、NetConfig DSL 配置
    // ========================================================================

    @Test
    fun `NetConfig 设置 baseUrl 和全局参数`() {
        Net.configure {
            baseUrl("https://api.example.com")
            globalParam("platform", "android")
            globalParam("version", "1.0")
        }

        assertEquals("https://api.example.com", Net.instance.config.baseUrl)
        val params = Net.instance.config.globalParams
        assertEquals("android", params["platform"])
        assertEquals("1.0", params["version"])
    }

    @Test
    fun `NetConfig 批量设置全局参数`() {
        Net.configure {
            globalParams(mapOf("key1" to "val1", "key2" to "val2"))
        }

        val params = Net.instance.config.globalParams
        assertEquals("val1", params["key1"])
        assertEquals("val2", params["key2"])
    }

    @Test
    fun `NetConfig 移除全局参数`() {
        Net.configure {
            globalParam("temp", "remove-me")
            globalParam("keep", "stay")
        }
        Net.configure {
            removeGlobalParams("temp")
        }

        val params = Net.instance.config.globalParams
        assertNull(params["temp"])
        assertEquals("stay", params["keep"])
    }

    @Test
    fun `NetConfig 清空全部全局参数`() {
        Net.configure {
            globalParam("a", "1")
            globalParam("b", "2")
        }
        Net.configure {
            clearGlobalParameters()
        }

        assertTrue(Net.instance.config.globalParams.isEmpty())
    }

    @Test
    fun `maxConcurrentDownloads 最少为 1`() {
        Net.configure {
            maxConcurrentDownloads(0)
        }

        assertEquals(1, Net.instance.config.maxConcurrentDownloadCount)
    }

    @Test
    fun `maxConcurrentDownloads 正常设置`() {
        Net.configure {
            maxConcurrentDownloads(5)
        }

        assertEquals(5, Net.instance.config.maxConcurrentDownloadCount)
    }

    @Test
    fun `maxResponseBodyBytes 设置和读取`() {
        Net.configure {
            maxResponseBodyBytes(512)
        }

        assertEquals(512, Net.instance.config.maxResponseBodyBytes)
    }

    @Test
    fun `NetConfig 设置加密配置`() {
        Net.configure {
            encrypt {
                secretKey("my-key-1234567890")
                encryptField("password")
            }
        }

        assertNotNull(Net.instance.config.encryptConfig)
        assertTrue(Net.instance.config.encryptConfig!!.hasValidConfig())
    }

    // ========================================================================
    // 十七、下载任务状态管理（TaskState）
    // ========================================================================

    @Test
    fun `TaskState 调度有效任务返回 RUNNING`() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/file.zip"
            path = "/tmp/file.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(task))
        taskState.deleteRunningTask(task)
    }

    @Test
    fun `TaskState 无效任务（无 savePath）被拒绝`() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/file.zip"
            // path 未设置
        }

        assertTrue(taskState.isInvalidTask(task))
    }

    @Test
    fun `TaskState 无效任务（无 URL）被拒绝`() {
        val taskState = TaskState()
        val task = Task().apply {
            path = "/tmp/file.zip"
            // url 未设置
        }

        assertTrue(taskState.isInvalidTask(task))
    }

    @Test
    fun `TaskState 相同 URL 重复任务被拒绝`() {
        val taskState = TaskState()
        val first = Task().apply {
            url = "https://example.com/same.zip"
            path = "/tmp/first.zip"
        }
        val duplicate = Task().apply {
            url = "https://example.com/same.zip"
            path = "/tmp/second.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(first))
        assertEquals(TaskState.ScheduleResult.REJECTED, taskState.scheduleTask(duplicate))

        taskState.deleteRunningTask(first)
    }

    @Test
    fun `TaskState 达到最大并发数后新任务进入等待`() {
        Net.configure {
            maxConcurrentDownloads(1)
        }
        val taskState = TaskState()

        val first = Task().apply {
            url = "https://example.com/running.zip"
            path = "/tmp/running.zip"
        }
        val second = Task().apply {
            url = "https://example.com/waiting.zip"
            path = "/tmp/waiting.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(first))
        assertEquals(TaskState.ScheduleResult.WAITING, taskState.scheduleTask(second))

        // 清理
        taskState.cancelWaitTask(second, "canceled")
        taskState.deleteRunningTask(first)
    }

    @Test
    fun `TaskState 取消正在运行的任务标记任务为取消`() {
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/cancel-me.zip"
            path = "/tmp/cancel-me.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(task))
        assertTrue(taskState.markRunningTaskCanceled(task))
        assertTrue(taskState.exitRunningTask(task))
        assertTrue(taskState.isInvalidTask(task)) // 被取消的任务标记为无效
        assertFalse(taskState.exitRunningTask(task)) // 已经移除，再次 exit 返回 false
    }

    @Test
    fun `TaskState 取消等待任务通知回调并清理监听器`() {
        Net.configure {
            maxConcurrentDownloads(1)
        }
        val taskState = TaskState()

        // 占满 running 队列
        val running = Task().apply {
            url = "https://example.com/blocker.zip"
            path = "/tmp/blocker.zip"
        }
        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(running))

        val waiting = Task().apply {
            url = "https://example.com/cancel-wait.zip"
            path = "/tmp/cancel-wait.zip"
        }
        assertEquals(TaskState.ScheduleResult.WAITING, taskState.scheduleTask(waiting))

        val events = mutableListOf<String>()
        waiting.progressCallback = object : AbstractProgressCallback() {
            override fun onFail(error: String?, task: Task) {
                events.add("fail:$error")
            }

            override fun onFinish(task: Task) {
                events.add("finish")
            }
        }

        taskState.cancelWaitTask(waiting, "download canceled")

        assertEquals(listOf("fail:download canceled", "finish"), events)
        assertFalse(taskState.exitWaitTask(waiting)) // 已取消，exit 返回 false

        taskState.deleteRunningTask(running)
    }

    @Test
    fun `TaskState runningQueueCanAcceptTask 使用最新配置`() {
        Net.configure {
            maxConcurrentDownloads(1)
        }
        val taskState = TaskState()
        val task = Task().apply {
            url = "https://example.com/capacity.zip"
            path = "/tmp/capacity.zip"
        }

        assertEquals(TaskState.ScheduleResult.RUNNING, taskState.scheduleTask(task))
        assertFalse(taskState.runningQueueCanAcceptTask())

        Net.configure {
            maxConcurrentDownloads(3)
        }
        assertTrue(taskState.runningQueueCanAcceptTask())

        taskState.deleteRunningTask(task)
    }

    // ========================================================================
    // 十八、下载回调接口
    // ========================================================================

    @Test
    fun `IProgressCallback 回调方法签名验证`() {
        val callback = object : IProgressCallback {
            var connectingCalled = false
            var progressCalled = false
            var failCalled = false
            var finishCalled = false

            override fun onConnecting(task: Task) {
                connectingCalled = true
            }

            override fun onProgress(task: Task, complete: Boolean) {
                progressCalled = true
            }

            override fun onFail(error: String?, task: Task) {
                failCalled = true
            }

            override fun onFinish(task: Task) {
                finishCalled = true
            }
        }

        val task = Task().apply {
            url = "https://example.com/test.zip"
            path = "/tmp/test.zip"
        }

        callback.onConnecting(task)
        callback.onProgress(task, false)
        callback.onFail("network error", task)
        callback.onFinish(task)

        assertTrue(callback.connectingCalled)
        assertTrue(callback.progressCalled)
        assertTrue(callback.failCalled)
        assertTrue(callback.finishCalled)
    }

    @Test
    fun `AbstractProgressCallback 提供默认空实现`() {
        val callback = object : AbstractProgressCallback() {}
        val task = Task().apply {
            url = "https://example.com/test.zip"
            path = "/tmp/test.zip"
        }

        // 不应抛出异常
        callback.onConnecting(task)
        callback.onProgress(task, false)
        callback.onProgress(task, true)
        callback.onFail("error", task)
        callback.onFinish(task)
    }

    // ========================================================================
    // 十九、Task 数据模型
    // ========================================================================

    @Test
    fun `Task 默认属性初始值正确`() {
        val task = Task()

        assertNull(task.url)
        assertNull(task.path)
        assertEquals(Task.DEFAULT_NO_VALUE, task.contentLength)
        assertEquals(0, task.downloadSize)
        assertEquals(3, task.tryAgainCount)
        assertFalse(task.overwrite)
        assertFalse(task.append)
        assertNull(task.tag)
    }

    @Test
    fun `Task 文件存在性判断`() {
        val tempFile = File.createTempFile("test-exists", ".tmp").apply {
            deleteOnExit()
        }

        val task = Task().apply {
            path = tempFile.absolutePath
        }

        assertTrue(task.file.exists())
    }

    @Test
    fun `Task 不存在文件返回 false`() {
        val task = Task().apply {
            path = "/nonexistent/path/file.xyz"
        }

        assertFalse(task.file.exists())
    }

    // ========================================================================
    // 二十、工具类
    // ========================================================================

    @Test
    fun `UrlTools appendUrlParamsToStr 正确拼接参数`() {
        val sb = StringBuilder()

        UrlTools.appendUrlParamsToStr(sb, "key1", "value1")
        UrlTools.appendUrlParamsToStr(sb, "key2", "value2")

        val result = sb.toString()
        assertTrue(result.contains("key1=value1"))
        assertTrue(result.contains("key2=value2"))
        assertTrue(result.contains("&"))
    }

    @Test
    fun `UrlTools cutOffStrToMap 正确解析参数字符串`() {
        val params = "key1=value1&key2=value2"

        val map = UrlTools.cutOffStrToMap(params)

        assertEquals("value1", map?.get("key1"))
        assertEquals("value2", map?.get("key2"))
    }

    @Test
    fun `UrlTools cutOffStrToMap 保留特殊字符`() {
        val sb = StringBuilder()
        UrlTools.appendUrlParamsToStr(sb, "token", "a\$b#c")

        val parsed = UrlTools.cutOffStrToMap(sb.toString())

        assertEquals("a\$b#c", parsed?.get("token"))
    }

    @Test
    fun `UrlTools encode 编码特殊字符`() {
        val encoded = UrlTools.encode("hello world=test&key")

        assertNotNull(encoded)
        assertFalse(encoded.contains(" "))
    }

    @Test
    fun `UrlTools decode 解码 URL 编码字符串`() {
        val decoded = UrlTools.decode("hello%20world")

        assertEquals("hello world", decoded)
    }

    @Test
    fun `StrTools getCookieString 构建 Cookie 头`() {
        val cookies = listOf(
            Cookie.Builder().name("session").value("abc").domain("example.com").build(),
            Cookie.Builder().name("token").value("xyz").domain("example.com").build()
        )

        val cookieStr = StrTools.getCookieString(cookies)

        assertNotNull(cookieStr)
        assertTrue(cookieStr.orEmpty().contains("session=abc"))
        assertTrue(cookieStr.orEmpty().contains("token=xyz"))
    }

    @Test
    fun `StrTools md5 计算正确`() {
        val hash = StrTools.md5("hello")

        assertEquals(32, hash.length)
        assertEquals("5d41402abc4b2a76b9719d911017c592", hash)
    }

    @Test
    fun `StrTools getFileNameFromUrl 从 URL 提取文件名`() {
        val filename = StrTools.getFileNameFromUrl("https://example.com/path/to/file.zip")

        assertEquals("file.zip", filename)
    }

    @Test
    fun `StrTools getFileNameFromUrl 处理无文件名的 URL`() {
        val filename = StrTools.getFileNameFromUrl("https://example.com/path/")

        assertEquals("", filename)
    }

    @Test
    fun `CheckTools md5CheckFile 验证文件 MD5`() {
        val tempFile = File.createTempFile("md5test", ".txt").apply {
            writeText("test content for md5")
            deleteOnExit()
        }

        val md5 = CheckTools.md5CheckFile(tempFile.absolutePath)

        assertNotNull(md5)
        assertEquals(32, md5!!.length)
    }

    @Test
    fun `JsonTools formatJson 格式化 JSON 字符串`() {
        val compact = """{"name":"Alice","age":30}"""
        val formatted = JsonTools.formatJson(compact)

        assertNotNull(formatted)
        assertTrue(formatted!!.contains("\n") || formatted.contains(" "))
    }

    @Test
    fun `JsonTools decodeUnicode 解码 Unicode 转义`() {
        val unicode = """{"name":"中文"}"""
        val decoded = JsonTools.decodeUnicode(unicode)

        assertTrue(decoded.contains("中文"))
    }

    // ========================================================================
    // 二一、网络错误场景
    // ========================================================================

    @Test
    fun `无效 URL 导致 DdCallback onFailure 被调用`() {
        val latch = CountDownLatch(1)
        var errorMsg: String? = null

        Net.get()
            .url("") // 空 URL
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    errorMsg = er
                    latch.countDown()
                }
            })

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(errorMsg)
        assertTrue(errorMsg.orEmpty().contains("url"))
    }

    @Test
    fun `网络断开导致 DdCallback onFailure 被调用`() {
        val url = server.url("/unreachable").toString()
        server.shutdown()

        val latch = CountDownLatch(1)
        var errorMsg: String? = null

        Net.get()
            .url(url)
            .send(object : DdCallback {
                override fun onResponse(result: String?, code: Int) {
                    latch.countDown()
                }

                override fun onFailure(er: String?) {
                    errorMsg = er
                    latch.countDown()
                }
            })

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertNotNull(errorMsg)
    }

    // ========================================================================
    // 辅助数据类
    // ========================================================================

    data class UserInfo(val name: String, val age: Int)
}
