# Net 网络请求库使用教程

Net 是一款基于 OkHttp 封装的 Android 网络请求库，包含三个模块：

| 模块 | 说明 | 依赖方式 |
|---|---|---|
| `net` | 核心库，Builder 模式请求 + 文件下载 | `implementation project(':net')` |
| `net-flow` | Kotlin Flow 扩展，协程/流式调用 | `implementation project(':net-flow')` |
| `net-retrofit` | Retrofit 声明式 API 集成 | `implementation project(':net-retrofit')` |

---

## 目录

**net 核心库**

1. [快速开始 - 初始化配置](#1-快速开始---初始化配置)
2. [GET 请求](#2-get-请求)
3. [POST JSON 请求](#3-post-json-请求)
4. [POST Form 表单请求](#4-post-form-表单请求)
5. [POST 文件上传](#5-post-文件上传)
6. [POST Multipart 请求](#6-post-multipart-请求)
7. [POST Content 自定义请求](#7-post-content-自定义请求)
8. [POST 断点续传上传](#8-post-断点续传上传)
9. [通用 Builder 功能](#9-通用-builder-功能)
10. [取消请求](#10-取消请求)
11. [文件下载](#11-文件下载)
12. [全局配置详解](#12-全局配置详解)
13. [工具类](#13-工具类)

**net-flow 模块**

14. [Flow 化普通请求](#14-flow-化普通请求)
15. [Flow + 反序列化](#15-flow--反序列化)
16. [NetResponse 类型详解](#16-netresponse-类型详解)
17. [下载进度 Flow](#17-下载进度-flow)
18. [线程模型与 UI 安全](#18-线程模型与-ui-安全)

**net-retrofit 模块**

19. [定义 Service 接口](#19-定义-service-接口)
20. [构建 NetRetrofit 实例](#20-构建-netretrofit-实例)
21. [suspend 函数请求](#21-suspend-函数请求)
22. [Flow 流式返回](#22-flow-流式返回)
23. [自定义 Converter](#23-自定义-converter)
24. [API 速查表](#24-api-速查表)

---

# net 核心库

## 1. 快速开始 - 初始化配置

在 `Application.onCreate()` 中进行全局配置：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.instance.configure {
            // 【必选】传入 Application 实例
            app(this@MyApp)

            // 【可选】全局 Base URL，后续可用 path() 拼接相对路径
            url("https://api.example.com")

            // 【可选】全局参数，所有请求自动附带
            setGlobalParams("platform", "android")
            setGlobalParams("version", "1.0.0")

            // 【可选】最大并行下载数，默认 3，最小 1
            maxDownloadNum(5)

            // 【可选】HTTP 日志（请求/响应体写入文件）
            useHttpLog(true)

            // 【可选】自定义日志文件目录
            log("/sdcard/myapp/logs")

            // 【可选】自定义 OkHttpClient
            okHttpClient(myCustomOkHttpClient)

            // 【可选】HTTP 缓存
            useCacheControl(Cache(cacheDir, 10 * 1024 * 1024))

            // 【可选】添加拦截器
            addInterceptor(authInterceptor)
            addInterceptor(loggingInterceptor)
        }
    }
}
```

> **入口说明**：所有功能统一通过 `Net.instance` 单例访问。`configure` 采用 Kotlin DSL 风格，闭包内 `this` 为 `NetConfig`。

---

## 2. GET 请求

### 2.1 基础 GET

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/info")
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
            // 网络异常、超时等，er 为错误信息
        }
        override fun onResponse(result: String?, code: Int) {
            // result 为响应体字符串，code 为 HTTP 状态码
        }
    })
```

### 2.2 带 Query 参数

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/list")
    .addParam("page", "1")                          // 单个键值对
    .addParam("size", "20")
    .addParam(mapOf("status" to "active"))          // Map 批量添加
    .send(callback)
```

### 2.3 path 拼接

```kotlin
// 全局 url = "https://api.example.com"
// 实际请求：https://api.example.com/api/v2/user/profile
Net.instance.get()
    .path("api/v2/user/profile")
    .send(callback)
```

### 2.4 使用 Handler 接收回调

```kotlin
val handler = Handler(Looper.getMainLooper()) { msg ->
    when (msg.what) {
        1 -> Log.d("TAG", "成功: ${msg.obj}")   // what=成功
        2 -> Log.e("TAG", "失败: ${msg.obj}")   // errorWhat=失败
    }
    true
}
Net.instance.get()
    .url("https://api.example.com/data")
    .send(handler, what = 1, errorWhat = 2)
```

---

## 3. POST JSON 请求

### 3.1 基础 JSON POST

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/user/login")
    .addParam("username", "admin")     // 键值对放入 JSON body
    .addParam("password", "123456")
    .send(callback)
```

### 3.2 支持多种参数类型

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addParam("name", "hello")         // String
    .addParam("age", 25)              // Int
    .addParam("score", 98.5f)          // Float
    .addParam("count", 100L)           // Long
    .send(callback)
```

| `addParam` 重载 | 类型 |
|---|---|
| `addParam(key, value: String?)` | String |
| `addParam(key, value: Int?)` | Int |
| `addParam(key, value: Long?)` | Long |
| `addParam(key, value: Float?)` | Float |
| `addParam(map: MutableMap)` | 批量 Map |

### 3.3 JSONObject 作为请求体

```kotlin
val jsonObj = JSONObject().apply {
    put("orderId", "2024001")
    put("amount", 99.99)
}

Net.instance.postJson()
    .url("https://api.example.com/order/create")
    .addParam(jsonObj)                 // 深度合并到现有 JSON
    .send(callback)
```

### 3.4 JSON 字符串

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addJsonStr("""{"type":"push","target":"all"}""")
    .send(callback)
```

### 3.5 addJson 插入嵌套值

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addJson("nested", JSONObject().apply { put("key", "value") })
    .send(callback)
```

### 3.6 URL 追加参数 + JSON Body

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/order/query")
    .addAppendParams("page", "1")      // 拼接到 URL Query
    .addAppendParams("limit", "10")
    .addParam("status", "pending")     // 放入 JSON body
    .send(callback)
```

---

## 4. POST Form 表单请求

使用 `application/x-www-form-urlencoded` 编码：

```kotlin
Net.instance.postForm()
    .url("https://api.example.com/user/register")
    .addParam("username", "newUser")
    .addParam("email", "user@test.com")
    .addParam("password", "123456")
    .addParam(mapOf("gender" to "male", "city" to "Beijing"))
    .send(callback)
```

---

## 5. POST 文件上传

```kotlin
val file = File("/sdcard/photo.jpg")

Net.instance.postFile()
    .url("https://api.example.com/upload")
    .addFile(file)                     // 默认表单字段名 "file"
    .send(callback)
```

Content-Type 自动推断：

| 文件扩展名 | Content-Type |
|---|---|
| `.png` | `image/png` |
| `.jpg` / `.jpeg` | `image/jpeg` |
| 其他 | `application/octet-stream` |

---

## 6. POST Multipart 请求

一次请求可混合文件、文本、JSON 和表单参数：

```kotlin
val file = File("/sdcard/report.pdf")

Net.instance.postMultipart()
    .url("https://api.example.com/report/submit")
    // 文件
    .addFile("report", "application/pdf", file)
    // 纯文本内容
    .addContent("这是一段自定义文本", "text/plain")
    .addContent("<xml>...</xml>", "xml_body", "application/xml")
    // JSON
    .addJson("meta", JSONObject().apply { put("author", "张三") })
    // 表单参数
    .addParam("title", "2024年报告")
    .addParam("department", "技术部")
    // URL Query 参数
    .addAppendParams("token", "abc123")
    .send(callback)
```

| `postMultipart()` 方法 | 说明 |
|---|---|
| `addFile(name, mediaType, file)` | 文件 Part |
| `addFile(name, file)` | 文件 Part（自动推断 mediaType） |
| `addFile(file)` | 文件 Part（默认名称 "file"） |
| `addContent(content, mediaType)` | 文本 Part（默认名称 "body"） |
| `addContent(content, flag, mediaType)` | 文本 Part（自定义名称） |
| `addJson(key, value)` | JSON 键值对 |
| `addParam(key, value)` | 表单参数 |
| `addAppendParams(key, value)` | URL Query 参数 |

---

## 7. POST Content 自定义请求

适用于自定义 Content-Type 场景（XML、纯文本等）：

```kotlin
Net.instance.postContent()
    .url("https://api.example.com/soap")
    .addContent("<soap:Envelope>...</soap:Envelope>", "application/xml")
    .addHeader("SOAPAction", "urn:example")
    .send(callback)
```

---

## 8. POST 断点续传上传

```kotlin
val file = File("/sdcard/large_video.mp4")

Net.instance.builder(ModeType.PostResume) as PostResumeFile
    .url("https://api.example.com/upload/resume")
    .addFile(file)
    .addResumeFileOffset(1024 * 1024 * 5)   // 从 5MB 偏移开始上传
    .send(callback)
```

> 需通过 `builder(ModeType.PostResume)` 创建，使用 `addResumeFileOffset` 设置已上传的字节偏移量。

---

## 9. 通用 Builder 功能

以下功能适用于**全部请求类型**（Get / PostJson / PostForm / PostFile / PostMul / PostContent / PostResume）。

### 9.1 请求 Header

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .addHeader("Authorization", "Bearer xxxx-token-xxxx")
    .addHeader("X-Request-Id", UUID.randomUUID().toString())
    .addHeader(mapOf("Accept" to "application/json"))
    .send(callback)
```

### 9.2 Cookie

```kotlin
val cookie = Cookie.Builder()
    .name("sessionId")
    .value("abc123def456")
    .domain("api.example.com")
    .build()

Net.instance.get()
    .url("https://api.example.com/data")
    .addCookie(cookie)                        // 单个
    .addCookie(listOf(cookie1, cookie2))      // 多个
    .send(callback)
```

### 9.3 请求 Tag（用于取消）

```kotlin
Net.instance.get()
    .url("https://api.example.com/long-polling")
    .tag("longPollingTask")
    .send(callback)

// 在其他地方取消
Net.instance.cancel("longPollingTask")
```

### 9.4 缓存策略

```kotlin
import com.itg.net.util.CacheControlFactory

// 预设缓存策略
Net.instance.get()
    .url("https://api.example.com/config")
    .addCacheControl(CacheControlFactory.FORCE_NETWORK)  // 强制网络
    .addCacheControl(CacheControlFactory.FORCE_CACHE)    // 强制缓存
    .addCacheControl(CacheControlFactory.CHECK_CACHE)    // 先检查缓存
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
| `getCacheControlForMILLISECONDS(n)` | 缓存 n 毫秒 |
| `getCacheControl(n, unit)` | 自定义时间和单位 |

> 使用缓存需要先在 `configure {}` 中通过 `useCacheControl(Cache)` 开启。

### 9.5 跳过全局参数

```kotlin
Net.instance.postJson()
    .url("https://third-party.example.com/api")
    .noUseGlobalParams()                // 不附带全局参数
    .addParam("data", "value")
    .send(callback)
```

### 9.6 Activity 生命周期绑定

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .autoCancel(this)                   // Activity 销毁时自动取消请求
    .send(callback)
```

| 方法 | 说明 |
|---|---|
| `url(url)` | 设置请求 URL |
| `path(path)` | 拼接相对路径（与全局 Base URL 组合） |
| `addHeader(key, value)` | 添加单个 Header |
| `addHeader(map)` | 批量添加 Header |
| `addCookie(cookie)` | 添加单个 Cookie |
| `addCookie(list)` | 批量添加 Cookie |
| `tag(tag)` | 设置请求标识 |
| `autoCancel(activity)` | 绑定 Activity 生命周期 |
| `noUseGlobalParams()` | 跳过全局参数 |
| `addCacheControl(cacheControl)` | 设置缓存策略 |

---

## 10. 取消请求

```kotlin
// 取消所有排队中和执行中的请求
Net.instance.cancelAll()

// 取消所有匹配 tag 的请求
Net.instance.cancel("myTag")
Net.instance.cancelTag("myTag")          // 等价

// 只取消第一个匹配 tag 的请求（返回 true 表示找到并取消）
val found = Net.instance.cancelFirstTag("uploadTask")

// 按 URL 取消下载
Net.instance.cancelDownload("https://example.com/file.zip")

// 按 Task 取消下载
Net.instance.cancelDownload(task)
```

---

## 11. 文件下载

### 11.1 基础下载

```kotlin
val task = Net.instance.newDownload()
    .savePath("${filesDir}/video.mp4")
    .url("https://example.com/video.mp4")
    .listener(object : IProgressCallback {
        override fun onConnecting(task: Task) {
            // 正在与服务器建立连接
        }

        override fun onProgress(task: Task, complete: Boolean) {
            val percent = task.downloadSize * 100 / maxOf(task.contentLength, 1L)
            Log.d("TAG", "进度: $percent%")
            if (complete) {
                Log.d("TAG", "下载完成！路径: ${task.path}")
            }
        }

        override fun onFail(error: String?, task: Task) {
            Log.e("TAG", "下载失败: $error")
        }

        override fun onFinish(task: Task) {
            // 无论成功失败都会触发（重试中的 onFail 不会触发）
            Log.d("TAG", "下载任务结束: ${task.url}")
        }
    })
    .start()
```

### 11.2 Task 属性

| 属性 | 类型 | 说明 |
|---|---|---|
| `url` | `String?` | 下载地址 |
| `path` | `String?` | 保存路径 |
| `downloadSize` | `Long` | 已下载字节数 |
| `contentLength` | `Long` | 文件总字节数 |
| `append` | `Boolean` | 是否断点续传 |
| `overwrite` | `Boolean` | 是否覆盖已存在文件 |
| `tryAgainCount` | `Int` | 最大重试次数 |
| `cancelUrl` | `String?` | 被取消时的 URL |
| `uniqueId` | `String` | 任务唯一标识 |

### 11.3 AbstractProgressCallback

Java 友好适配器，只需覆写关心的回调：

```kotlin
builder.listener(object : AbstractProgressCallback() {
    override fun onProgress(task: Task, complete: Boolean) {
        // 只处理进度，其他回调空实现
    }
})
```

### 11.4 TaskTools 进度计算

```kotlin
val percent = TaskTools.getDownloadProgress(task)  // 返回 0..100
```

### 11.5 覆盖已存在文件

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .overwrite(true)                    // 目标文件存在时覆盖
    .listener(callback)
    .start()
```

> `overwrite(false)`（默认）时，目标文件已存在会通过 `onFail` 回调 `"目标文件已存在，未开启覆盖"`。

### 11.6 断点续传下载

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/large_file.zip")
    .url("https://example.com/large_file.zip")
    .supportCheckpoint()                // 开启断点续传
    .listener(callback)
    .start()
```

> 依赖服务器支持 Range 请求。

### 11.7 重试次数

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .retryCount(3)                      // 失败后最多重试 3 次，默认 1
    .listener(callback)
    .start()
```

### 11.8 绑定 Activity 生命周期

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(url)
    .bindActivity(this)                 // Activity 销毁时自动取消+释放监听器
    .listener(callback)
    .start()
```

> 未调用 `bindActivity()` 且未取消的下载任务，后台会继续执行。此时必须手动调用 `removeDownloadListeners(task)` 释放监听器，否则回调中持有的 Activity 引用会导致内存泄露。

### 11.9 全局下载进度监听

```kotlin
val globalListener = object : IProgressCallback {
    override fun onConnecting(task: Task) { /* ... */ }
    override fun onProgress(task: Task, complete: Boolean) { /* ... */ }
    override fun onFail(error: String?, task: Task) { /* ... */ }
    override fun onFinish(task: Task) { /* ... */ }
}

// 注册
Net.instance.addGlobalDownloadListener(globalListener)
// 移除
Net.instance.removeGlobalDownloadListener(globalListener)
```

### 11.10 手动释放监听器

```kotlin
// 移除某个任务的所有监听器
Net.instance.removeDownloadListeners(task)

// 移除某个任务的特定监听器
Net.instance.removeDownloadListener(task, specificCallback)
```

### 11.11 查询下载状态

```kotlin
if (Net.instance.isDownloadQueued("https://example.com/file.zip")) {
    // 该 URL 正在下载或排队中
}
```

### 11.12 下载 TaskBuilder 完整方法

| 方法 | 说明 |
|---|---|
| `savePath(path)` | 文件保存路径 |
| `url(url)` | 下载地址 |
| `retryCount(count)` | 最大重试次数（≥1） |
| `overwrite(bool)` | 目标文件存在时是否覆盖 |
| `supportCheckpoint()` | 开启断点续传 |
| `bindActivity(activity)` | 绑定 FragmentActivity 生命周期 |
| `listener(callback)` | 下载进度监听器 |
| `noUseGlobalParams()` | 跳过全局参数 |
| `start(): Task` | 启动下载，返回 Task 实例 |

---

## 12. 全局配置详解

### 12.1 NetConfig 所有配置项

| 方法 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `app(application)` | 必选 | - | 设置 Application 实例 |
| `url(url)` | 可选 | null | 全局 Base URL |
| `setGlobalParams(key, value)` | 可选 | - | 添加全局参数 |
| `removeGlobalParam(key)` | 可选 | - | 移除单个全局参数 |
| `clearGlobalParams()` | 可选 | - | 清空所有全局参数 |
| `maxDownloadNum(max)` | 可选 | 3 | 最大并行下载数 |
| `useHttpLog(bool)` | 可选 | false | 是否开启 HTTP 日志 |
| `log(path)` | 可选 | null | 自定义日志文件路径 |
| `okHttpClient(client)` | 可选 | null | 自定义 OkHttpClient |
| `addInterceptor(interceptor)` | 可选 | - | 添加 OkHttp 拦截器 |
| `getInterceptors()` | 只读 | - | 获取所有已注册拦截器 |
| `useCacheControl(cache)` | 可选 | null | 设置 OkHttp 缓存 |

### 12.2 完整配置示例

```kotlin
Net.instance.configure {
    app(application)
    url("https://api.example.com")
    setGlobalParams("platform", "android")
    setGlobalParams("version", BuildConfig.VERSION_NAME)
    maxDownloadNum(5)
    useHttpLog(BuildConfig.DEBUG)
    addInterceptor(authInterceptor)
    useCacheControl(Cache(File(cacheDir, "http_cache"), 50 * 1024 * 1024))
}
```

---

## 13. 工具类

### 13.1 StrTools

| 方法 | 说明 |
|---|---|
| `getCookieString(cookie: List<Cookie?>?): String?` | Cookie 列表转请求头字符串 |
| `getMd5(input: String): String?` | 计算 MD5 |
| `extractUrlFileName(url: String?, defaultName: String?): String?` | 从 URL 提取文件名并 URL 解码 |

### 13.2 TaskTools

| 方法 | 说明 |
|---|---|
| `getDownloadProgress(task: Task): Int` | 计算下载进度百分比（0..100） |

### 13.3 CacheControlFactory

| 预设 | 说明 |
|---|---|
| `FORCE_NETWORK` | 强制网络 |
| `FORCE_CACHE` | 强制缓存 |
| `CHECK_CACHE` | 验证缓存有效性 |
| `getCacheControlForSecond(n)` | 缓存 n 秒 |
| `getCacheControl(n, unit)` | 自定义时间单位 |

### 13.4 CacheFactory

| 方法 | 说明 |
|---|---|
| `getCache(context): Cache` | 默认 10MB 缓存 |
| `getCache(context, maxSize): Cache` | 自定义大小缓存 |

### 13.5 PrintLog

调试日志工具，仅在 `BuildConfig.DEBUG` 为 true 时输出：
- `PrintLog.logr(message)` — 普通请求日志
- `PrintLog.logd(message)` — 下载相关日志
- `PrintLog.logSubd(message)` — 下载子步骤日志

### 13.6 JsonTools

| 方法 | 说明 |
|---|---|
| `formatJson(jsonStr): String` | 格式化 JSON 字符串 |
| `decodeUnicode(str): String` | Unicode 转中文 |
| `deepMerge(source, target): JSONObject` | 深度合并两个 JSONObject |

---

# net-flow 模块

## 14. Flow 化普通请求

### 14.1 flowString() —— 响应体字符串 Flow

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .catch { e -> Log.e("TAG", "请求失败: ${e.message}") }
        .collect { body -> updateUI(body) }
}
```

`.flowString()` 定义在 `ParamsBuilder` 上，所有请求类型均可使用：

```kotlin
// GET
Net.instance.get().url("...").flowString().collect { ... }

// POST JSON
Net.instance.postJson().url("...").addParam("k","v").flowString().collect { ... }

// POST Form
Net.instance.postForm().url("...").addParam("k","v").flowString().collect { ... }

// POST Multipart
Net.instance.postMultipart().url("...").addFile(file).flowString().collect { ... }
```

### 14.2 与原有 DdCallback 对比

```kotlin
// 原有回调（后台线程）
Net.instance.get().url("...").send(object : DdCallback {
    override fun onFailure(er: String?) { /* ⚠️ 后台线程 */ }
    override fun onResponse(result: String?, code: Int) { /* ⚠️ 后台线程 */ }
})

// Flow（默认主线程）
lifecycleScope.launch {
    Net.instance.get().url("...").flowString().collect { body ->
        textView.text = body  // ✅ 主线程，可直接操作 UI
    }
}
```

### 14.3 retry 重试

```kotlin
Net.instance.get()
    .url("https://api.example.com/unstable")
    .flowString()
    .retry(3) { e ->
        Log.w("TAG", "重试中...", e)
        delay(1000)
        true
    }
    .collect { body -> updateUI(body) }
```

---

## 15. Flow + 反序列化

### 15.1 flowResponse(lambda)

```kotlin
data class User(val id: Int, val name: String)

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/1")
        .flowResponse { raw -> Gson().fromJson(raw, User::class.java) }
        .collect { response ->
            if (response.isSuccessful) {
                updateUI(response.body)   // response.body 类型为 User?
            }
        }
}
```

### 15.2 GsonNetConverter

```kotlin
import com.itg.net.flow.converter.GsonNetConverter

Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(GsonNetConverter<User>(type = User::class.java))
    .collect { response -> /* response.body: User? */ }
```

### 15.3 自定义 Gson 配置

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .create()

Net.instance.get()
    .url("https://api.example.com/orders")
    .flowResponse(GsonNetConverter<OrderList>(gson = gson, type = OrderList::class.java))
    .collect { /* ... */ }
```

### 15.4 自定义 Converter

```kotlin
class MoshiNetConverter<T>(private val moshi: Moshi, private val type: Type) : NetConverter<T> {
    override fun convert(raw: String?): T? =
        raw?.let { moshi.adapter<T>(type).fromJson(it) }
}

Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(MoshiNetConverter<User>(moshi, User::class.java))
    .collect { /* ... */ }
```

---

## 16. NetResponse 类型详解

```kotlin
data class NetResponse<T>(
    val body: T?,                       // 反序列化后的对象
    val rawBody: String?,               // 原始响应字符串
    val code: Int,                      // HTTP 状态码
    val headers: Map<String, String>,    // 响应头
    val isSuccessful: Boolean           // code ∈ 200..299
)
```

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/login")
    .addParam("username", "admin")
    .flowResponse { raw -> Gson().fromJson(raw, LoginResult::class.java) }
    .collect { response ->
        when {
            response.isSuccessful -> navigateToHome(response.body)
            response.code == 401 -> showLoginError()
            response.code in 500..599 -> {
                Log.e("TAG", "服务器错误: ${response.rawBody}")
                showError("服务器繁忙")
            }
        }
    }
```

---

## 17. 下载进度 Flow

### 17.1 TaskBuilder.flow()

```kotlin
import com.itg.net.flow.DownloadPhase

lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .flow()                                              // → Flow<DownloadProgress>
        .catch { e -> Log.e("TAG", "下载失败", e) }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> showConnecting()
                DownloadPhase.Downloading -> {
                    val pct = TaskTools.getDownloadProgress(progress.task)
                    progressBar.progress = pct
                }
                DownloadPhase.Complete -> onDownloadComplete()
                DownloadPhase.Failed -> onDownloadFailed()
            }
        }
}
```

### 17.2 取消下载

```kotlin
val job = lifecycleScope.launch {
    Net.instance.newDownload().savePath(path).url(url).flow().collect { ... }
}
// 点击取消 → 协程取消 → Flow 关闭 → 下载任务自动取消
btnCancel.setOnClickListener { job.cancel() }
```

### 17.3 DownloadProgress 结构

```kotlin
data class DownloadProgress(
    val task: Task,           // 下载任务（url, path, downloadSize, contentLength 等）
    val phase: DownloadPhase  // 当前阶段
)

enum class DownloadPhase {
    Connecting,    // 连接中（发射 1 次）
    Downloading,   // 下载中（持续多次发射）
    Complete,      // 下载完成（发射后 Flow 关闭）
    Failed         // 下载失败（Flow 以 NetFlowException 关闭）
}
```

---

## 18. 线程模型与 UI 安全

| 调用方式 | 网络执行线程 | 结果所在线程 | 能否直接操作 UI |
|---|---|---|---|
| `send(DdCallback)` | OkHttp 线程池（后台） | **后台线程** | ❌ 需 `runOnUiThread` |
| `send(Handler, what, errorWhat)` | OkHttp 线程池 | **Handler 对应 Looper** | 取决于 Handler |
| `send(OkHttp Callback)` | OkHttp 线程池 | **后台线程** | ❌ |
| `flowString().collect {}` | OkHttp 线程池 | **launch 的 Dispatchers** | ✅ 默认主线程 |
| `flowResponse().collect {}` | OkHttp 线程池 | **launch 的 Dispatchers** | ✅ 默认主线程 |
| `TaskBuilder.flow().collect {}` | DdNet 下载线程池 | **launch 的 Dispatchers** | ✅ 默认主线程 |

> **核心原理**：`callbackFlow` 内部使用 `Channel` 作为桥梁，后台线程通过 `trySend()` 写入 Channel（线程安全），数据最终在 `collect {}` 所在的协程调度器上消费。`lifecycleScope.launch` 默认使用 `Dispatchers.Main`，所以可以直接更新 UI。

**NetFlowException** —— 网络异常信息：

```kotlin
class NetFlowException(
    val code: Int?,          // HTTP 状态码（非 HTTP 错误为 null）
    override val message: String?
) : IOException(message)
```

### 快速参考：Flow API

| API | 所在文件 | 返回类型 |
|---|---|---|
| `ParamsBuilder.flowString()` | RequestFlow.kt | `Flow<String>` |
| `ParamsBuilder.flowResponse(converter)` | RequestFlow.kt | `Flow<NetResponse<T>>` |
| `TaskBuilder.flow()` | DownloadFlow.kt | `Flow<DownloadProgress>` |
| `Net.flowGet { }` | FlowExtensions.kt | `Flow<String>` |
| `Net.flowPostJson { }` | FlowExtensions.kt | `Flow<String>` |
| `Net.flowPostForm { }` | FlowExtensions.kt | `Flow<String>` |
| `Net.flowDownload { }` | FlowExtensions.kt | `Flow<DownloadProgress>` |
| `Net.flowGetResponse(converter) { }` | FlowExtensions.kt | `Flow<NetResponse<T>>` |
| `Net.flowPostJsonResponse(converter) { }` | FlowExtensions.kt | `Flow<NetResponse<T>>` |

---

# net-retrofit 模块

## 19. 定义 Service 接口

使用标准 Retrofit 注解：

```kotlin
import retrofit2.http.*
import com.itg.net.flow.NetResponse

interface UserService {

    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): User

    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("user/list")
    suspend fun listUsers(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): List<User>

    @FormUrlEncoded
    @POST("user/update")
    suspend fun updateProfile(
        @Field("name") name: String,
        @Field("email") email: String
    ): User

    @Multipart
    @POST("upload/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part
    ): UploadResult

    // NetResponse 包装，不抛 HttpException
    @POST("user/login")
    suspend fun loginSafe(@Body request: LoginRequest): NetResponse<LoginResponse>

    // Flow 流式返回
    @GET("events")
    fun eventStream(): Flow<Event>
}
```

---

## 20. 构建 NetRetrofit 实例

### 20.1 便捷入口（推荐）

```kotlin
val userService = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

### 20.2 多 Service 共享实例

```kotlin
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val userService = retrofit.create<UserService>()
val orderService = retrofit.create<OrderService>()
```

### 20.3 单例管理

```kotlin
object ApiServices {
    val retrofit = Net.instance.retrofit
        .baseUrl("https://api.example.com/")
        .build()

    val user: UserService by lazy { retrofit.create() }
    val order: OrderService by lazy { retrofit.create() }
}
```

### 20.4 默认行为

| 配置项 | 默认值 |
|---|---|
| OkHttpClient | `Net.instance.okhttpManager.okHttpClient`（继承全部拦截器/超时/缓存） |
| Converter.Factory | `GsonConverterFactory.create()` |
| CallAdapter.Factory | `NetFlowCallAdapterFactory()`（支持 `Flow<T>` / `Flow<NetResponse<T>>`） |

### 20.5 自定义 OkHttpClient

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .client(customClient)
    .build()
    .create<UserService>()
```

---

## 21. suspend 函数请求

### 21.1 直接返回反序列化对象

```kotlin
lifecycleScope.launch {
    try {
        val user = userService.getUser("123")    // 返回 User 对象
        updateUI(user)                           // 主线程，安全
    } catch (e: HttpException) {
        showError("HTTP ${e.code()}: ${e.message()}")   // 非 2xx 抛此异常
    } catch (e: IOException) {
        showError("网络连接异常: ${e.message}")
    }
}
```

### 21.2 返回 Retrofit Response

```kotlin
@GET("user/{id}")
suspend fun getUser(@Path("id") id: String): Response<User>

// 使用
val response = userService.getUser("123")
when {
    response.isSuccessful -> updateUI(response.body())
    response.code() == 404 -> showNotFound()
    else -> showError("HTTP ${response.code()}")
}
```

### 21.3 返回 NetResponse（不抛 HttpException）

```kotlin
@POST("user/login")
suspend fun login(@Body request: LoginRequest): NetResponse<LoginResponse>

// 非 2xx 不会抛异常，统一通过 response.code 判断
val response = userService.login(LoginRequest("admin", "123456"))
if (response.isSuccessful) {
    navigateToHome(response.body)
} else {
    showError("登录失败: ${response.rawBody}")
}
```

---

## 22. Flow 流式返回

### 22.1 Flow<T>

```kotlin
@GET("events")
fun eventStream(): Flow<Event>

// 使用
lifecycleScope.launch {
    eventService.eventStream()
        .catch { e -> Log.e("TAG", "事件流中断", e) }
        .collect { event -> processEvent(event) }
}
```

### 22.2 Flow<NetResponse<T>>

```kotlin
@GET("data/stream")
fun dataStream(): Flow<NetResponse<DataChunk>>

lifecycleScope.launch {
    dataService.dataStream()
        .collect { response ->
            if (response.isSuccessful) processData(response.body)
        }
}
```

### 22.3 返回类型对比

| Service 返回类型 | 非 2xx 行为 | 需要 CallAdapter |
|---|---|---|
| `T` (suspend) | 抛 `HttpException` | Retrofit 内置 |
| `Response<T>` (suspend) | 正常返回，body() 为 null | Retrofit 内置 |
| `NetResponse<T>` (suspend) | 正常返回，rawBody 含错误信息 | Retrofit 内置 |
| `Flow<T>` | 以 `NetFlowException` 关闭 | `NetFlowCallAdapterFactory` |
| `Flow<NetResponse<T>>` | 正常发送，.code 体现错误 | `NetFlowCallAdapterFactory` |
| `Call<T>` | Retrofit 原生 | 否 |

---

## 23. 自定义 Converter

### 23.1 Moshi

```kotlin
Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
```

### 23.2 自定义 Gson

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .create()

Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()
```

### 23.3 Scalars（String 返回值）

当接口返回纯文本而非 JSON 时需要：

```kotlin
Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(ScalarsConverterFactory.create())   // 先匹配 String/Int
    .addConverterFactory(GsonConverterFactory.create())      // 再匹配 JSON→对象
    .build()
```

> 已内置在 `net-retrofit` 模块依赖中（`converter-scalars:2.9.0`）。

---

## 24. API 速查表

### 24.1 Net 入口

| API | 说明 |
|---|---|
| `Net.instance` | 全局单例 |
| `Net.instance.configure { }` | DSL 初始化配置 |
| `Net.instance.ddNetConfig` | 全局配置实例 |
| `Net.instance.okhttpManager` | OkHttp 客户端管理器 |

### 24.2 请求类型

| 方法 | 返回类型 | 说明 |
|---|---|---|
| `get()` | `Get` | GET 请求 |
| `postJson()` | `PostJson` | POST application/json |
| `postForm()` | `PostForm` | POST application/x-www-form-urlencoded |
| `postFile()` | `PostFile` | 文件上传 |
| `postMultipart()` | `PostMul` | multipart/form-data |
| `postContent()` | `PostContent` | 自定义 Content-Type |
| `builder(ModeType.PostResume)` | `PostResumeFile` | 断点续传上传 |

### 24.3 下载

| 方法 | 说明 |
|---|---|
| `newDownload()` | 创建下载 TaskBuilder |
| `addGlobalDownloadListener(cb)` | 注册全局下载监听 |
| `removeGlobalDownloadListener(cb)` | 移除全局下载监听 |
| `removeDownloadListeners(task)` | 移除任务的所有监听器 |
| `removeDownloadListener(task, cb)` | 移除任务的特定监听器 |
| `isDownloadQueued(url)` | 查询 URL 是否在下载/排队 |
| `cancelDownload(url)` | 按 URL 取消下载 |
| `cancelDownload(task)` | 按 Task 取消下载 |

### 24.4 取消

| 方法 | 说明 |
|---|---|
| `cancel(tag)` | 取消所有匹配 tag 的请求 |
| `cancelTag(tag)` | 同上 |
| `cancelFirstTag(tag)` | 取消第一个匹配 tag 的请求 |
| `cancelAll()` | 取消所有请求 |

### 24.5 NetRetrofit.Builder

| 方法 | 说明 |
|---|---|
| `baseUrl(url)` | 设置 Base URL（须以 `/` 结尾） |
| `client(client)` | 自定义 OkHttpClient |
| `addConverterFactory(f)` | 添加 Converter |
| `addCallAdapterFactory(f)` | 添加 CallAdapter |
| `build(): NetRetrofit` | 构建 |
| `create<T>(): T` | 创建 Service 代理 |

### 24.6 入口扩展

| 入口 | 说明 |
|---|---|
| `Net.instance.retrofit` | 获取 `NetRetrofit.Builder` |

### 24.7 完整模块关系

```
app
└── implementation project(':net-retrofit')
    ├── api project(':net-flow')
    │   ├── api project(':net')
    │   │   ├── OkHttp 4.9.2
    │   │   └── AndroidX appcompat / core-ktx
    │   └── kotlinx-coroutines 1.7.3 + Gson
    └── Retrofit 2.9.0 + converter-gson + converter-scalars
```
