# 2. 基本请求

如何使用 Net 库发送 GET、POST JSON、POST Form、文件上传、Multipart、自定义 Content-Type 和断点续传上传请求，以及掌握通用 Builder 功能。

## 适用条件

- 已完成初始化配置（见 [01-快速开始](./01-quick-start.md)）
- 知道目标接口的 URL、请求方式和参数

## 推荐做法

### GET 请求

```kotlin
// 基础 GET
Net.instance.get()
    .url("https://api.example.com/user/info")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { /* 网络异常、超时等 */ }
        override fun onResponse(result: String?, code: Int) { /* result 为响应体字符串 */ }
    })

// 带 Query 参数
Net.instance.get()
    .url("https://api.example.com/user/list")
    .addParam("page", "1")
    .addParam("size", "20")
    .addParam(mapOf("status" to "active"))
    .send(callback)

// path 拼接（配合全局 Base URL）
// 全局 url = "https://api.example.com"，实际请求：https://api.example.com/api/v2/user/profile
Net.instance.get()
    .path("api/v2/user/profile")
    .send(callback)
```

### 结构化处理 4xx/5xx 和网络异常

新代码推荐使用 `sendResult()`。它会把结果拆成三类：`onSuccess` 处理 HTTP 2xx，`onHttpError` 处理 4xx/5xx，`onNetworkError` 处理断网、超时、DNS 失败等 IOException。

```kotlin
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.NetResultCallback
import com.itg.net.request.result.sendResult

Net.instance.get()
    .url("https://api.example.com/user/info")
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            // 2xx
        }

        override fun onHttpError(error: NetResult.HttpError) {
            // 4xx/5xx，例如 401 登录过期、404 接口不存在、500 服务器错误
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            // 断网、超时、DNS 失败、连接失败
        }
    })
```

详细说明见 [09. HTTP 错误与网络异常处理](./09-error-handling.md)。

### POST JSON 请求

`Content-Type: application/json`

```kotlin
// 基础 JSON POST
Net.instance.postJson()
    .url("https://api.example.com/user/login")
    .addParam("username", "admin")
    .addParam("password", "123456")
    .send(callback)

// 多种参数类型（String/Int/Float/Long 自动写入 JSON）
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addParam("name", "hello")   // String
    .addParam("age", 25)         // Int
    .addParam("score", 98.5f)    // Float
    .addParam("count", 100L)     // Long
    .send(callback)

// JSONObject 作为请求体（深度合并）
val jsonObj = JSONObject().apply {
    put("orderId", "2024001")
    put("amount", 99.99)
}
Net.instance.postJson()
    .url("https://api.example.com/order/create")
    .addParam(jsonObj)
    .send(callback)

// JSON 字符串（自动解析并深度合并）
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addJsonStr("""{"type":"push","target":"all"}""")
    .send(callback)

// addJson 插入嵌套值
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addJson("nested", JSONObject().apply { put("key", "value") })
    .send(callback)

// URL 追加参数 + JSON Body（二者互不干扰）
Net.instance.postJson()
    .url("https://api.example.com/order/query")
    .addAppendParams("page", "1")    // 拼接到 URL Query
    .addAppendParams("limit", "10")
    .addParam("status", "pending")   // 放入 JSON body
    .send(callback)
```

### POST Form 表单请求

`Content-Type: application/x-www-form-urlencoded`

```kotlin
Net.instance.postForm()
    .url("https://api.example.com/user/register")
    .addParam("username", "newUser")
    .addParam("email", "user@test.com")
    .addParam("password", "123456")
    .addParam(mapOf("gender" to "male", "city" to "Beijing"))
    .send(callback)
```

### POST 文件上传

```kotlin
val file = File("/sdcard/photo.jpg")

Net.instance.postFile()
    .url("https://api.example.com/upload")
    .addFile(file)  // 默认表单字段名 "file"
    .send(callback)
```

Content-Type 根据文件扩展名自动推断：`.png` → `image/png`，`.jpg`/`.jpeg` → `image/jpeg`，其他 → `application/octet-stream`

### POST Multipart 请求

一次请求可混合文件、文本、JSON 和表单参数：

```kotlin
val file = File("/sdcard/report.pdf")

Net.instance.postMultipart()
    .url("https://api.example.com/report/submit")
    .addFile("report", "application/pdf", file)         // 文件 Part
    .addContent("这是一段自定义文本", "text/plain")       // 文本 Part（默认名称 "body"）
    .addContent("<xml>...</xml>", "xml_body", "application/xml")  // 自定义名称
    .addJson("meta", JSONObject().apply { put("author", "张三") })
    .addParam("title", "2024年报告")                     // 表单参数
    .addParam("department", "技术部")
    .addAppendParams("token", "abc123")                  // URL Query 参数
    .send(callback)
```

### POST Content 自定义请求

适用于自定义 Content-Type（XML、纯文本等）：

```kotlin
Net.instance.postContent()
    .url("https://api.example.com/soap")
    .addContent("<soap:Envelope>...</soap:Envelope>", "application/xml")
    .addHeader("SOAPAction", "urn:example")
    .send(callback)
```

### POST 断点续传上传

```kotlin
val file = File("/sdcard/large_video.mp4")

Net.instance.builder(ModeType.PostResume) as PostResumeFile
    .url("https://api.example.com/upload/resume")
    .addFile(file)
    .addResumeFileOffset(1024 * 1024 * 5)  // 从 5MB 偏移开始上传
    .send(callback)
```

> 需通过 `builder(ModeType.PostResume)` 创建，使用 `addResumeFileOffset` 设置已上传的字节偏移量。

## 通用 Builder 功能

以下功能适用于**所有请求类型**（Get / PostJson / PostForm / PostFile / PostMul / PostContent / PostResume）。

### 请求 Header

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .addHeader("Authorization", "Bearer xxxx-token-xxxx")
    .addHeader("X-Request-Id", UUID.randomUUID().toString())
    .addHeader(mapOf("Accept" to "application/json"))
    .send(callback)
```

### Cookie

```kotlin
val cookie = Cookie.Builder()
    .name("sessionId").value("abc123def456")
    .domain("api.example.com").build()

Net.instance.get()
    .url("https://api.example.com/data")
    .addCookie(cookie)                        // 单个
    .addCookie(listOf(cookie1, cookie2))      // 多个
    .send(callback)
```

### 请求 Tag

```kotlin
Net.instance.get()
    .url("https://api.example.com/long-polling")
    .tag("longPollingTask")
    .send(callback)

// 在其他地方取消
Net.instance.cancel("longPollingTask")
```

### 缓存策略

```kotlin
import com.itg.net.util.CacheControlFactory

Net.instance.get()
    .url("https://api.example.com/config")
    .addCacheControl(CacheControlFactory.FORCE_NETWORK)  // 强制网络
    .send(callback)

// 自定义缓存时间
Net.instance.get()
    .url("https://api.example.com/config")
    .addCacheControl(CacheControlFactory.getCacheControlForSecond(30))
    .send(callback)
```

| `CacheControlFactory` 预设 | 说明 |
|---|---|
| `FORCE_NETWORK` | 强制使用网络，不使用缓存 |
| `FORCE_CACHE` | 强制使用缓存，不使用网络 |
| `CHECK_CACHE` | maxAge=0，先验证缓存有效性 |
| `getCacheControlForSecond(n)` | 缓存 n 秒 |

> 使用缓存需要先在 `configure {}` 中通过 `useCacheControl(Cache)` 开启。

### 跳过全局参数

```kotlin
Net.instance.postJson()
    .url("https://third-party.example.com/api")
    .noUseGlobalParams()  // 不附带全局参数
    .addParam("data", "value")
    .send(callback)
```

### Activity 生命周期绑定

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .autoCancel(this)  // Activity 销毁时自动取消请求
    .send(callback)
```

## 请求类型速查

| 方法 | 返回类型 | Content-Type | 说明 |
|---|---|---|---|
| `get()` | `Get` | — | GET 请求 |
| `postJson()` | `PostJson` | `application/json` | POST JSON |
| `postForm()` | `PostForm` | `application/x-www-form-urlencoded` | POST 表单 |
| `postFile()` | `PostFile` | 自动推断 | 文件上传 |
| `postMultipart()` | `PostMul` | `multipart/form-data` | 混合内容 |
| `postContent()` | `PostContent` | 自定义 | 自定义 Content-Type |
| `builder(ModeType.PostResume)` | `PostResumeFile` | 自动推断 | 断点续传上传 |

## 通用 Builder 方法速查

| 方法 | 说明 |
|---|---|
| `url(url)` | 设置请求 URL |
| `path(path)` | 拼接相对路径（与全局 Base URL 组合） |
| `addHeader(key, value)` / `addHeader(map)` | 添加 Header |
| `addCookie(cookie)` / `addCookie(list)` | 添加 Cookie |
| `tag(tag)` | 设置请求标识 |
| `autoCancel(activity)` | 绑定 Activity 生命周期 |
| `noUseGlobalParams()` | 跳过全局参数 |
| `addCacheControl(cacheControl)` | 设置缓存策略 |

## 验证方式

- 通过 `PrintLog.logr()` 查看请求日志（需开启 `useHttpLog(true)`）
- 通过 OkHttp Logging Interceptor 查看原始请求/响应
- 在回调中打印 `code` 和 `result` 确认响应正确

[返回 README](../../README.md)
