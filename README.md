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

**字段加密**

25. [字段加密](#25-字段加密)
   - 25.1 [快速开始](#251-快速开始)
   - 25.2 [加密模式](#252-加密模式)
   - 25.3 [单请求控制](#253-单请求控制)
   - 25.4 [跳过不需要加密的接口](#254-跳过不需要加密的接口)
   - 25.5 [按路径差异化加密](#255-按路径差异化加密)
   - 25.6 [AES/CBC/PKCS7](#256-aescbcpkcs7最常用)
   - 25.7 [AES/ECB/PKCS7](#257-aesecbpkcs7无-iv)
   - 25.8 [AES/GCM/NoPadding](#258-aesgcmnopadding推荐带认证)
   - 25.9 [RSA/ECB/PKCS1](#259-rsaecbpkcs1非对称加密)
   - 25.10 [独立使用 EncryptUtil](#2510-独立使用-encryptutil)
   - 25.11 [API 速查表](#2511-api-速查表)
   - 25.12 [密钥管理建议](#2512-密钥管理建议)

**网络监控**

26. [网络监控](#26-网络监控)
   - 26.1 [快速开始](#261-快速开始)
   - 26.2 [架构概览](#262-架构概览)
   - 26.3 [上报模式](#263-上报模式)
   - 26.4 [单请求控制](#264-单请求控制)
   - 26.5 [自定义上报处理器](#265-自定义上报处理器)
   - 26.6 [URL 脱敏](#266-url-脱敏)
   - 26.7 [崩溃安全上报](#267-崩溃安全上报-resilientreporthandler)
   - 26.8 [生命周期管理](#268-生命周期管理-flushmonitor--shutdownmonitor)
   - 26.9 [MonitorEvent 数据模型](#269-monitorevent-数据模型)
   - 26.10 [错误分类详解](#2610-错误分类详解)
   - 26.11 [拦截器链位置](#2611-拦截器链位置)
   - 26.12 [与 Flow / Retrofit 配合](#2612-与-flow--retrofit-配合)
   - 26.13 [性能设计](#2613-性能设计)
   - 26.14 [安全设计](#2614-安全设计)
   - 26.15 [API 速查表](#2615-api-速查表)

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
| `encrypt { }` | 可选 | - | 字段级加解密配置（见第 25 节） |
| `monitor { }` | 可选 | - | 网络监控上报配置（见第 26 节） |

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

---

## 25. 字段加密

`EncryptInterceptor` 在 OkHttp 层对 JSON/Form 请求体中的指定字段自动加密、响应体自动解密。支持两种全局模式 + 单请求精确控制，对 net / net-flow / net-retrofit 三个模块均透明。

### 25.1 快速开始

```kotlin
Net.instance.configure {
    app(this@MyApp)
    url(“https://api.example.com”)

    encrypt {
        algorithm(Algorithm.AES_CBC_PKCS7)       // 选择加密算法
        secretKey(“my-32-byte-secret-key!!”)      // 设置 AES 密钥
        iv(“1234567890abcdef”)                    // 设置 IV 向量
        encryptField(“password”)                  // 加密 password 字段
        encryptField(“phone”)                     // 加密 phone 字段
    }
}

// 业务代码完全无感
Net.instance.postJson()
    .url(“https://api.example.com/login”)
    .addParam(“password”, “123456”)
    .send(callback)
// 实际发送: {“password”:”a8f5e2d9b3c7f1...”}
```

### 25.2 加密模式

通过 `encryptMode` 切换全局策略：

```kotlin
encrypt {
    // ==== 方案一：OPT_OUT（默认）—— 全量加密，skipPath 排除 ====
    encryptMode(EncryptMode.OPT_OUT)               // 全量加密模式（默认值）
    encryptField(“password”)                        // 全局字段：所有接口加密
    skipPath(Regex(“/public/.*”))                   // 排除不需要加密的接口

    // ==== 方案二：OPT_IN —— 按需加密，仅 encryptPath 匹配的加密 ====
    encryptMode(EncryptMode.OPT_IN)                 // 按需加密模式
    encryptPath(Regex(“/user/login”), listOf(“password”))
                                                    // 仅 /user/login 加密 password
    encryptPath(Regex(“/payment/.*”), listOf(“bankCard”, “cvv”))
                                                    // 仅 /payment/* 加密 bankCard、cvv
}
```

| 模式 | 默认行为 | 适用场景 |
|---|---|---|
| `OPT_OUT`（默认） | 全量加密，`skipPath` 排除 | 大部分接口需要加密 |
| `OPT_IN` | 全量透传，`encryptPath` 纳入 | 只有少数接口需要加密 |

### 25.3 单请求控制

优先级**高于全局配置**，在单个请求上调用即可：

```kotlin
// 强制加密：无视全局 skipPath 和 OPT_IN 默认跳过
Net.instance.postJson()
    .url(“https://api.example.com/public/apply”)
    .addParam(“phone”, “13800138000”)
    .encrypt()         // ← 单请求强制加密
    .send(callback)

// 强制跳过：无视全局 encryptField 和 OPT_OUT 默认加密
Net.instance.postJson()
    .url(“https://api.example.com/user/search”)
    .addParam(“phone”, “13800138000”)
    .skipEncrypt()     // ← 单请求强制跳过
    .send(callback)
```

**优先级链**（从高到低）：

```
1. .encrypt()        → 强制加密
2. .skipEncrypt()    → 强制跳过
3. GET / 非文本 body  → 自动跳过
4. EncryptMode       → OPT_IN / OPT_OUT
5. 字段规则          → encryptField / encryptPath / encryptPattern
```

### 25.4 跳过不需要加密的接口

通过 `skipPath` 声明全局跳过规则（OPT_OUT 模式下生效）：

```kotlin
encrypt {
    encryptMode(EncryptMode.OPT_OUT)               // 全量加密模式
    encryptField(“password”)                        // 全局字段：所有接口加密 password
    skipPath(Regex(“/health-check”))                // 跳过健康检查接口
    skipPath(Regex(“/public/.*”))                   // 跳过所有公开接口
    skipPath(Regex(“/upload/.*”))                   // 跳过文件上传接口
}
```

### 25.5 按路径差异化加密

不同接口加密不同字段，使用 `encryptPath` + `encryptField` 组合：

```kotlin
encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)               // 选择 AES-CBC 算法
    secretKey(“my-32-byte-secret-key!!123456”)        // 32 字节密钥 = AES-256
    iv(“1234567890abcdef”)                             // 16 字节 IV 向量

    // ==== 全局规则：所有接口生效 ====
    encryptField(“password”)                           // 双向加密 password

    // ==== 路径规则：仅匹配路径的接口生效 ====
    encryptPath(Regex(“/user/.*”), listOf(“phone”, “email”))
                                                       // /user/* 接口加密 phone、email
    encryptPath(Regex(“/payment/.*”), listOf(“bankCard”, “cvv”, “idCard”))
                                                       // /payment/* 接口加密银行卡、CVV

    // ==== 正则规则 ====
    encryptPattern(Regex(“.*[Ss]ecret”))               // 所有以 Secret 结尾的字段

    // ==== 仅响应解密（请求不加密） ====
    decryptField(“realName”)                           // 仅解密 realName
    decryptField(“idCard”)                             // 仅解密 idCard
}
```

### 25.6 AES/CBC/PKCS7（最常用）

**算法说明**：AES 对称加密，CBC 模式需要 IV 向量（16 字节），PKCS7 填充。密钥长度支持 128/192/256 位（对应 16/24/32 字节）。

**密钥要求**：

| 密钥长度 | 字节数 | 示例 |
|---|---|---|
| AES-128 | 16 字节 | `"1234567890abcdef"` |
| AES-192 | 24 字节 | `"1234567890abcdef12345678"` |
| AES-256 | 32 字节 | `"my-32-byte-secret-key!!123456"` |

**IV 要求**：固定 16 字节，每次加密应使用不同的 IV（生产环境建议每次请求更换）。

**配置方式**：

```kotlin
// 方式一：字符串密钥 + 字符串 IV（自动转 UTF-8 字节）
encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)               // AES-CBC 算法
    secretKey("my-32-byte-secret-key!!123456")        // 32 字节密钥 = AES-256
    iv("1234567890abcdef")                             // 16 字节 IV 向量
    encryptField("password")                           // 加密 password
    encryptField("phone")                              // 加密 phone
}

// 方式二：字节数组密钥 + 字节数组 IV（精确控制每个字节）
encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)               // AES-CBC 算法
    secretKey(byteArrayOf(0x01, 0x02, ...))           // 字节数组密钥
    iv(byteArrayOf(0x0a, 0x0b, ...))                  // 字节数组 IV
    encryptField("password")                           // 加密 password
}

// 方式三：使用 EncryptUtil 生成随机密钥和 IV（推荐生产使用）
val aesKey = EncryptUtil.generateAesKey(256)         // 生成随机 AES-256 密钥
val iv = EncryptUtil.generateIv()                     // 生成随机 16 字节 IV

encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)               // AES-CBC 算法
    secretKeyObj(aesKey)                               // 传入 SecretKey 对象
    iv(iv)                                             // 传入随机 IV
    encryptField("password")                           // 加密 password
}
```

**独立使用 EncryptUtil（不走拦截器）**：

```kotlin
val key = "my-32-byte-secret-key!!123456".toByteArray() // 32 字节 AES-256 密钥
val iv = "1234567890abcdef".toByteArray()                // 16 字节 IV 向量

// 加密：明文 → Base64 密文
val ciphertext = EncryptUtil.encrypt("13800138000", key, Algorithm.AES_CBC_PKCS7, iv)
// ciphertext = "a8f5e2d9b3c7f1e0..." (Base64)

// 解密：Base64 密文 → 明文
val plaintext = EncryptUtil.decrypt(ciphertext, key, Algorithm.AES_CBC_PKCS7, iv)
// plaintext = "13800138000"
```

> **注意**：CBC 模式下，同一密钥 + 同一 IV + 同一明文会产生相同的密文，这在安全上不够理想。生产环境建议使用 GCM 模式或每次加密更换 IV。

---

### 25.7 AES/ECB/PKCS7（无 IV）

**算法说明**：ECB 模式不需要 IV 向量，相同明文始终产生相同密文。**不推荐用于生产环境**，仅适用于对安全性要求不高的场景或需要确定性加密的特殊需求。

**与 CBC 的关键区别**：不需要设置 IV。

**配置方式**：

```kotlin
encrypt {
    algorithm(Algorithm.AES_ECB_PKCS7)               // ECB 模式不需要 IV
    secretKey("my-32-byte-secret-key!!123456")        // 32 字节密钥，不需要 IV
    encryptField("password")                           // 加密 password
}
```

**独立使用**：

```kotlin
val key = "my-32-byte-secret-key!!123456".toByteArray() // 密钥字节数组

// 加密（ECB 模式不需要 IV 参数）
val ciphertext = EncryptUtil.encrypt("123456", key, Algorithm.AES_ECB_PKCS7)

// 解密（ECB 模式不需要 IV 参数）
val plaintext = EncryptUtil.decrypt(ciphertext, key, Algorithm.AES_ECB_PKCS7)
```

**ECB 与 CBC 对比**：

| 特性 | AES_ECB_PKCS7 | AES_CBC_PKCS7 |
|---|---|---|
| 需要 IV | ❌ 不需要 | ✅ 需要 16 字节 |
| 相同明文 → 相同密文 | ✅ 是（固定） | ❌ 否（依赖 IV） |
| 安全性 | 较低 | 较高 |
| 适用场景 | 简单混淆 / 非敏感数据 | 生产环境常规加密 |
| 并行加密 | ✅ 支持 | ❌ 不支持（串行） |

---

### 25.8 AES/GCM/NoPadding（推荐，带认证）

**算法说明**：GCM 模式同时提供加密和完整性认证（AEAD），能检测密文是否被篡改。**推荐用于高安全需求的生产环境**。

**IV 要求**：GCM 推荐 12 字节 IV，最大支持 2^32-1 字节。每次加密必须使用不同的 IV（Nonce），重复使用会严重破坏安全性。

**密钥要求**：与 CBC 相同，16/24/32 字节。

**配置方式**：

```kotlin
encrypt {
    algorithm(Algorithm.AES_GCM_NO_PADDING)          // GCM 模式（加密+认证一体化）
    secretKey("my-32-byte-secret-key!!123456")        // 32 字节密钥 = AES-256
    iv(EncryptUtil.generateIv())                       // GCM Nonce（每次必须不同）
    encryptField("password")                           // 加密 password
    encryptField("bankCard")                           // 加密银行卡号
    encryptField("cvv")                                // 加密 CVV
}
```

**独立使用**：

```kotlin
val key = EncryptUtil.generateAesKey(256).encoded     // 随机 AES-256 密钥
val iv = EncryptUtil.generateIv()                      // GCM Nonce（12 字节推荐）

// 加密
val ciphertext = EncryptUtil.encrypt(
    "6222021234567890", key, Algorithm.AES_GCM_NO_PADDING, iv
)

// 解密
val plaintext = EncryptUtil.decrypt(
    ciphertext, key, Algorithm.AES_GCM_NO_PADDING, iv
)
```

**GCM 与 CBC 对比**：

| 特性 | AES_GCM_NO_PADDING | AES_CBC_PKCS7 |
|---|---|---|
| 加密+认证 | ✅ 一体化 AEAD | ❌ 仅加密，需额外 HMAC |
| 防篡改 | ✅ 自动检测 | ❌ 需自行实现 |
| 性能 | 更快（硬件加速） | 较慢 |
| IV 要求 | 12 字节推荐，必须唯一 | 16 字节，应随机 |
| 推荐度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

---

### 25.9 RSA/ECB/PKCS1（非对称加密）

**算法说明**：RSA 是非对称加密，使用公钥加密、私钥解密。适合加密小数据（如 AES 密钥传输），**不适合加密大段文本**。RSA-2048 最多加密 245 字节，RSA-4096 最多加密 501 字节。

> **注意**：当前 `EncryptUtil` 对 RSA 使用 `SecretKeySpec`（AES 密钥规范）。完整 RSA 支持需要 `KeyFactory` 加载公私钥对。以下展示的是与 AES 统一的接口用法。生产环境中，推荐用 RSA 加密传输 AES 密钥，然后用 AES 加密业务数据。

**RSA 密钥对生成**（通过命令行或代码）：

```bash
# 生成 RSA-2048 私钥
openssl genrsa -out private_key.pem 2048
# 提取公钥
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

**配置方式**：

```kotlin
// 加载 RSA 密钥对
val keyFactory = KeyFactory.getInstance("RSA")
val publicKey: PublicKey = keyFactory.generatePublic(
    X509EncodedKeySpec(Base64.decode(publicKeyBase64, Base64.DEFAULT))
)
val privateKey: PrivateKey = keyFactory.generatePrivate(
    PKCS8EncodedKeySpec(Base64.decode(privateKeyBase64, Base64.DEFAULT))
)

// RSA 通常用于场景：
// 1. 客户端用 RSA 公钥加密 AES 密钥，发给服务端
// 2. 服务端用 RSA 私钥解密得到 AES 密钥
// 3. 后续通信使用 AES 加密

// 混合加密示例
encrypt {
    // 步骤 1：登录时用 RSA 公钥加密 AES 密钥传给服务端（略）
    // 步骤 2：服务端用 RSA 私钥解密，返回 AES SessionKey
    // 步骤 3：后续通信使用协商的 AES SessionKey
    algorithm(Algorithm.AES_GCM_NO_PADDING)          // 用 AES-GCM 加密业务数据
    secretKey(sessionAesKey)                           // 服务端返回的 SessionKey
    iv(sessionIv)                                      // 服务端返回的 IV
    encryptField("phone")                              // 加密手机号
}
```

**RSA 算法选择建议**：

| 场景 | 推荐 |
|---|---|
| 加密大量业务数据 | ❌ 不适合，用 AES |
| 加密 AES 密钥传输 | ✅ 最佳实践（混合加密） |
| 加密短 Token / 签名 | ✅ 适合 |
| 加密身份证号等短字段 | 可用（注意长度限制） |

---

### 25.10 独立使用 EncryptUtil

不依赖拦截器，在业务代码中直接调用加解密：

```kotlin
// 场景：本地存储敏感数据前加密
val key = "my-32-byte-secret-key!!123456".toByteArray() // 32 字节 AES-256 密钥
val iv = "1234567890abcdef".toByteArray()                // 16 字节 IV 向量

// 加密后存入 SharedPreferences
val encryptedPhone = EncryptUtil.encrypt(
    "13800138000", key, Algorithm.AES_CBC_PKCS7, iv
)
preferences.edit().putString("phone_encrypted", encryptedPhone).apply()

// 读取时解密
val phone = EncryptUtil.decrypt(
    preferences.getString("phone_encrypted", "")!!,
    key, Algorithm.AES_CBC_PKCS7, iv
)
```

**EncryptUtil 完整 API**：

| 方法 | 说明 |
|---|---|
| `encrypt(plaintext, key, algorithm, iv?)` | 加密明文字符串，返回 Base64 密文 |
| `decrypt(ciphertext, key, algorithm, iv?)` | 解密 Base64 密文，返回明文字符串 |
| `generateAesKey(keySize)` | 生成随机 AES 密钥（128/192/256） |
| `generateAesKeyFromString(keyStr, keySize)` | 从字符串构造 AES 密钥 |
| `generateIv()` | 生成随机 16 字节 IV |

### 25.11 API 速查表

#### EncryptConfig DSL 方法

| 方法 | 说明 |
|---|---|
| `encryptMode(mode)` | 设置加密模式（`OPT_OUT` / `OPT_IN`） |
| `algorithm(algorithm)` | 设置加密算法 |
| `secretKey(key)` | 设置密钥（字节数组） |
| `secretKey(keyStr)` | 设置密钥（字符串，UTF-8→字节） |
| `secretKeyObj(key)` | 设置密钥（SecretKey 对象） |
| `iv(iv)` | 设置 IV 向量（字节数组） |
| `iv(ivStr)` | 设置 IV 向量（字符串） |
| `encryptField(name)` | 按字段名精确匹配（双向加解密） |
| `decryptField(name)` | 按字段名精确匹配（仅响应解密） |
| `encryptPattern(regex)` | 按正则匹配字段（双向） |
| `encryptPath(pathRegex, fields)` | 按路径+字段列表匹配（双向） |
| `skipPath(regex)` | 跳过路径（OPT_OUT 模式生效） |
| `requestEncrypt(bool)` | 启用/禁用请求加密（默认 true） |
| `responseDecrypt(bool)` | 启用/禁用响应解密（默认 true） |
| `skipGetRequest(bool)` | 是否跳过 GET 请求（默认 true） |

#### 单请求方法（ParamsBuilder）

| 方法 | 说明 |
|---|---|
| `.encrypt()` | 强制加密当前请求（优先级最高） |
| `.skipEncrypt()` | 强制跳过当前请求（优先级最高） |

#### EncryptUtil 工具方法

| 方法 | 说明 |
|---|---|
| `encrypt(plaintext, key, algo, iv?)` | 加密明文，返回 Base64 密文 |
| `decrypt(ciphertext, key, algo, iv?)` | 解密 Base64 密文，返回明文 |
| `generateAesKey(size)` | 生成随机 AES 密钥（128/192/256） |
| `generateAesKeyFromString(str, size)` | 从字符串构造 AES 密钥 |
| `generateIv()` | 生成随机 16 字节 IV |

### 25.12 密钥管理建议

| 方案 | 安全等级 | 实现复杂度 | 适用场景 |
|---|---|---|---|
| 本地固定密钥 | ⭐ | 极低 | 开发/测试 |
| 本地固定密钥 + 代码混淆 | ⭐⭐ | 低 | 非敏感数据 |
| 服务端下发 SessionKey（登录后返回） | ⭐⭐⭐ | 中 | **生产环境推荐** |
| RSA 加密 AES 密钥（混合加密） | ⭐⭐⭐⭐ | 中高 | 高安全需求 |
| Android Keystore 硬件保护 | ⭐⭐⭐⭐⭐ | 高 | 金融/支付 |

**推荐生产方案**：

```
1. App 启动 → 生成 RSA 密钥对（或预置公钥）
2. 登录请求 → 服务端用 RSA 公钥加密 AES SessionKey 返回
3. 后续请求 → 使用 AES SessionKey + GCM 模式进行字段加解密
4. SessionKey 定期轮换（如每 30 分钟）
```

**算法推荐排序**：

| 优先级 | 算法 | 理由 |
|---|---|---|
| 1️⃣ | `AES_GCM_NO_PADDING` | 加密+认证一体化，性能最优，推荐首选 |
| 2️⃣ | `AES_CBC_PKCS7` | 兼容性最广，后端对接无压力 |
| 3️⃣ | `AES_ECB_PKCS7` | 仅限非敏感数据简单混淆 |
| 4️⃣ | `RSA_ECB_PKCS1` | 仅用于加密 AES 密钥传输，不加密业务数据 |

---


## 26. 网络监控

`MonitorInterceptor` 在 OkHttp 拦截器层自动采集网络请求质量数据（请求耗时、失败率、错误类型分布等），通过可替换的上报处理器异步发送到监控服务器。对 net / net-flow / net-retrofit 三个模块均透明，业务代码零侵入。

### 26.1 快速开始

```kotlin
// Application.onCreate()
Net.instance.configure {
    app(this@MyApp)
    url("https://api.example.com")

    monitor {
        enabled(true)                                    // 【必选】全局开启监控
        reportUrl("https://monitor.example.com/api/v1/report")
        reportMode(ReportMode.FAILURE_ONLY)              // 仅上报失败（默认）
        batchSize(30)                                    // 攒够 30 条批量上报
        flushIntervalMs(15_000L)                         // 每 15 秒刷新一次
        slowRequestThresholdMs(3000)                     // 3 秒以上算慢请求
    }
}

// 业务代码完全无感 —— 所有请求自动监控失败
Net.instance.postJson()
    .url("https://api.example.com/login")
    .addParam("username", "admin")
    .addParam("password", "123456")
    .send(object : DdCallback {
        override fun onResponse(body: String?, code: Int) { /* 正常处理 */ }
        override fun onFailure(msg: String?) {
            // 失败时自动上报到监控服务器，无需手动干预
        }
    })
```

> **核心原则**：业务方无需做任何事情即可使用默认 HTTP 批量上报。有特殊需求时注入自定义 `IMonitorReportHandler` 即可完全接管上报行为。

### 26.2 架构概览

```
Request → [EncryptInterceptor] → [MonitorInterceptor] → [HttpLoggingInterceptor] → Server
                                       │
                                       ▼
                           ┌─────────────────────┐
                           │  MonitorInterceptor  │
                           │                     │
                           │ 1. 判断开关优先级    │
                           │ 2. 记录请求时间戳    │
                           │ 3. 前置过滤（优化）  │
                           │ 4. 构建 MonitorEvent  │
                           │ 5. 投递给上报处理器  │
                           └────────┬────────────┘
                                    │
                           ┌────────▼────────────┐
                           │ IMonitorReportHandler │◄── 策略接口
                           └──┬──────────┬───────┘
                              │          │
                   ┌──────────▼──┐  ┌───▼──────────┐
                   │ 默认 HTTP   │  │ 自定义实现    │
                   │ 批量上报    │  │ Firebase/File │
                   └─────────────┘  └──────────────┘
```

**拦截器链顺序**：MonitorInterceptor 放在 EncryptInterceptor 之后，确保上报的 URL/元信息不包含请求体明文。放在 HttpLoggingInterceptor 之前，日志中能看到原始响应。

### 26.3 上报模式

```kotlin
monitor {
    // ==== 模式一：FAILURE_ONLY（默认）—— 仅上报失败的请求 ====
    reportMode(ReportMode.FAILURE_ONLY)           // 节省流量，聚焦问题排查

    // ==== 模式二：ALL —— 上报所有请求（成功 + 失败） ====
    reportMode(ReportMode.ALL)                    // 全量数据，适合分析 P50/P90/P99 耗时

    // ==== 模式三：SLOW_ONLY —— 仅上报成功但慢的请求 ====
    reportMode(ReportMode.SLOW_ONLY)              // 需配合 slowRequestThresholdMs
    slowRequestThresholdMs(2000)                  // 超过 2 秒的成功请求才上报
}
```

| 模式 | 上报对象 | 适用场景 |
|---|---|---|
| `FAILURE_ONLY` | 异常 + HTTP 4xx/5xx + 被取消 | **默认推荐**，聚焦故障排查 |
| `ALL` | 全部请求 | 全量监控、耗时分析、SLA 统计 |
| `SLOW_ONLY` | 成功但超过阈值的请求 | 性能优化、慢请求治理 |

**采样率控制**（高频接口可降低数据量）：

```kotlin
monitor {
    sampleRate(0.1f)    // 仅 10% 请求上报（当 reportMode = ALL 时有效）
}
```

> `sampleRate` 当前由 `MonitorConfig` 记录但采样逻辑由调用方在 `IMonitorReportHandler` 中自行实现。内置 `DefaultMonitorReportHandler` 默认不采样（全量上报）。

### 26.4 单请求控制

优先级**高于全局配置**，在单个请求上调用即可：

```kotlin
// 强制监控：无视全局 enabled=false
Net.instance.postJson()
    .url("https://api.example.com/payment/create")
    .addParam("amount", "100")
    .monitor()         // ← 单请求强制开启监控
    .send(callback)

// 强制跳过：无视全局 enabled=true（心跳、轮询等高频接口）
Net.instance.get()
    .url("https://api.example.com/heartbeat")
    .skipMonitor()     // ← 单请求强制跳过监控
    .send(callback)
```

**优先级链**（从高到低）：

```
1. .monitor()        → 强制开启
2. .skipMonitor()    → 强制跳过
3. MonitorConfig.enabled → 全局兜底
```

**实现原理**：`ParamsBuilder.monitorFlag` 为 `@Volatile` 字段，通过 `SendTool.combineParamsAndRCall()` 以 OkHttp Typed Tag（`MonitorMarker`）设置在 `Request` 上。`MonitorInterceptor` 在拦截时读取 Tag 判断优先级。与 `EncryptMarker` 采用相同模式，不占用通用 `tag(Any)`。

### 26.5 自定义上报处理器

上报逻辑是业务方最可能有定制需求的环节。实现 `IMonitorReportHandler` 接口即可完全接管上报行为。

> **性能提示**：实现 `IMonitorReportHandler` 时，覆写 `isAsync` 属性。若 Handler 内部已异步（如使用队列/三方 SDK），设为 `true` 可让框架省去一层专用线程。`DefaultMonitorReportHandler` 使用 `BlockingQueue` + 后台线程，`isAsync = true`；`ResilientReportHandler` 包含磁盘 I/O，`isAsync = false`。详见 [26.13 性能设计](#2613-性能设计)。

#### 场景一：接入 Firebase Crashlytics

```kotlin
class FirebaseReportHandler : IMonitorReportHandler {
    // SDK 内部已异步处理，声明 isAsync=true 避免框架额外创建线程
    override val isAsync: Boolean get() = true

    override fun onEvent(event: MonitorEvent) {
        if (!event.isSuccess) {
            // 记录失败事件到 Firebase
            FirebaseCrashlytics.getInstance().log(event.toJson().toString())

            // 严重错误（5xx）记录为自定义异常
            if (event.httpCode >= 500) {
                FirebaseCrashlytics.getInstance().recordException(
                    Exception("ServerError: ${event.url} -> ${event.httpCode}")
                )
            }
        }
    }
    override fun flush() {}    // Firebase 实时上报，无需批量
    override fun shutdown() {} // 无需额外清理
}

monitor { enabled(true); reportHandler(FirebaseReportHandler()) }
```

#### 场景二：写本地日志文件（含磁盘 I/O）

```kotlin
class FileReportHandler(private val logFile: File) : IMonitorReportHandler {
    // 保持默认 isAsync=false，框架自动创建专用线程隔离同步磁盘 I/O
    private val writer = logFile.bufferedWriter()

    override fun onEvent(event: MonitorEvent) {
        synchronized(writer) {
            writer.write(event.toJson().toString())
            writer.newLine(); writer.flush()
        }
    }
    override fun flush() {}
    override fun shutdown() { synchronized(writer) { writer.close() } }
}

monitor { enabled(true); reportHandler(FileReportHandler(File(cacheDir, "monitor.log"))) }
```

#### 场景三：多通道同时上报（HTTP + 本地文件）

```kotlin
class MultiChannelReportHandler(
    private val httpHandler: DefaultMonitorReportHandler,
    private val fileHandler: FileReportHandler
) : IMonitorReportHandler {
    // 有一个子 Handler 非异步，组合体即非异步
    override val isAsync: Boolean get() = httpHandler.isAsync && fileHandler.isAsync

    override fun onEvent(event: MonitorEvent) {
        httpHandler.onEvent(event)
        fileHandler.onEvent(event)
    }
    override fun flush() { httpHandler.flush(); fileHandler.flush() }
    override fun shutdown() { httpHandler.shutdown(); fileHandler.shutdown() }
}
```

#### 场景四：仅打印 Log（调试用）

```kotlin
monitor {
    enabled(true)
    reportHandler(object : IMonitorReportHandler {
        override val isAsync: Boolean get() = true   // 纯内存操作，极快返回
        override fun onEvent(event: MonitorEvent) {
            if (!event.isSuccess) Log.e("Monitor", "${event.errorType} ${event.url} ${event.totalCostMs}ms")
        }
        override fun flush() {}
        override fun shutdown() {}
    })
}
```

#### 场景五：组合内置 Handler（生产环境推荐）

```kotlin
monitor {
    enabled(true)
    reportHandler(ResilientReportHandler(
        delegate = DefaultMonitorReportHandler(
            reportUrl = "https://monitor.example.com/api/report",
            batchSize = 30, flushIntervalMs = 15_000L, maxQueueSize = 1000
        ),
        localFile = File(cacheDir, "network_monitor_backup.log"),
        maxLocalEvents = 200
    ))
}
```

| Handler 选择 | 说明 |
|---|---|
| 不设置 `reportHandler` | 自动使用 `DefaultMonitorReportHandler`（HTTP 批量上报） |
| `reportHandler = null` | 同上（默认行为） |
| `reportHandler = 自定义实现` | **完全接管**，`reportUrl`/`batchSize` 等参数不再生效 |

### 26.6 URL 脱敏

`MonitorEvent` 会记录完整请求 URL。为防止 token、sessionId 等敏感参数泄露到监控服务器，内置了 URL 脱敏机制：

```kotlin
// 默认脱敏规则：自动遮蔽常见敏感 query 参数
// 被遮蔽的参数：token, sessionId, jsessionid, auth, key, secret, password, access_token, api_key
//
// 原始 URL: https://api.example.com/data?token=abc123&page=1
// 脱敏后:   https://api.example.com/data?token=***&page=1

// 自定义脱敏规则
monitor {
    urlSanitizer = { url -> url.replace(Regex("([?&])(userId|customParam)=[^&]*",
        RegexOption.IGNORE_CASE), "$1$2=***") }
}

// 完全禁用脱敏（不推荐）
monitor { urlSanitizer = { url -> url } }
```

> 脱敏发生在 `MonitorInterceptor.buildEvent()` 中，在 `MonitorEvent` 对象构造之前。脱敏函数异常时回退到原始 URL，确保监控自身不影响业务。

### 26.7 崩溃安全上报（ResilientReportHandler）

内置的 `DefaultMonitorReportHandler` 使用内存队列，进程被杀会导致未上报事件丢失。`ResilientReportHandler` 增加本地文件兜底：

```kotlin
monitor {
    enabled(true)
    reportHandler(ResilientReportHandler(
        delegate = DefaultMonitorReportHandler(
            reportUrl = "https://monitor.example.com/api/report",
            batchSize = 30, flushIntervalMs = 15_000L, maxQueueSize = 1000
        ),
        localFile = File(cacheDir, "network_monitor_backup.log"),
        maxLocalEvents = 200    // 本地最多保留 200 条（环形截断）
    ))
}
```

**工作机制**：

```
onEvent(event)
  ├── delegate.onEvent(event)       // 主路径：内存队列 → 批量 HTTP POST
  └── appendToLocalFile(event)      // 兜底路径：追加到本地日志文件
        ├── 文件行数 < 200：直接追加
        └── 文件行数 ≥ 200：环形截断（移除最旧行，保留最近 199 行 + 新行）
```

**关键设计**：
- `isAsync = false`：本地文件写入包含磁盘 I/O，MonitorInterceptor 会用专用线程隔离
- 文件操作在 `synchronized(writer)` 下执行，线程安全
- `shutdown()` 时关闭 `writer`，释放文件句柄

### 26.8 生命周期管理（flushMonitor / shutdownMonitor）

`IMonitorReportHandler` 接口定义了 `flush()` 和 `shutdown()` 两个生命周期方法。

#### 26.8.1 flushMonitor() —— 主动刷新缓冲区

**用途**：立即将 Handler 内部缓冲区中积压但尚未上报的事件推送出去。

**为什么需要**：`DefaultMonitorReportHandler` 采用批量上报策略（攒够 batchSize 条或到达 flushIntervalMs 时间窗口才发 POST）。如果 App 在时间窗口内进入后台并被系统杀死，队列中未上报的事件将丢失。

```kotlin
// Application 中监听前后台切换
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        Net.instance.flushMonitor()   // App 进入后台 → 立即刷新
    }
})
```

**内部流程（两段式排空）**：

```
flush()
  ├── ① queue.drainTo(pending)         // 排空队列（事件未被消费者 take）
  │     └── doFlushAsync(pending)      // 立即异步 POST
  ├── ② forceFlush = true              // 通知消费者 flush 当前 batch
  └── ③ consumerThread.interrupt()    // 唤醒阻塞中的消费者线程
        └── 消费者检测 forceFlush → 立即 doFlushAsync(batch)
            （InterruptedException 中检查 running 仍为 true → 继续循环，不退出）
```

> **关键设计**：`flush()` 使用 `interrupt()` 唤醒消费者线程，但消费者的 `InterruptedException` 处理中检查 `running`——仅当 `running=false`（shutdown）时才 `break`，否则继续循环。这解决了"interrupt 会杀死线程"的问题。

#### 26.8.2 shutdownMonitor() —— 优雅关闭，释放资源

**用途**：关闭上报处理器，等待缓冲区排空后释放所有底层资源。

```kotlin
// 切换环境
fun switchEnvironment(baseUrl: String) {
    Net.instance.shutdownMonitor()    // 关闭旧 Handler，排空队列
    Net.instance.configure { url(baseUrl); monitor { ... } }
}

// 进程终止
override fun onTerminate() { super.onTerminate(); Net.instance.shutdownMonitor() }
```

**内部流程**：

```
shutdown()
  ├── ① running.set(false)            // 拒绝新事件
  ├── ② consumerThread.interrupt()    // 中断消费者 → InterruptedException → running=false → break
  ├── ③ consumerThread.join(5000)     // 等待线程退出（最多 5s）
  ├── ④ queue.drainTo(remaining)      // 第一次排空
  ├── ⑤ queue.drainTo(lateArrivals)   // 第二次排空（捕获 ④ 之后竞态到达的事件）
  ├── ⑥ doFlushAsync(remaining)       // 最后一次 POST
  ├── ⑦ dispatcher.executorService.shutdown()  // 释放 OkHttp 线程池
  └── ⑧ connectionPool.evictAll()     // 关闭连接池
```

#### 26.8.3 完整生命周期示例

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Net.instance.configure {
            app(this@MyApp); url("https://api.example.com")
            monitor {
                enabled(true); reportUrl("https://monitor.example.com/api/report")
                reportMode(ReportMode.FAILURE_ONLY); batchSize(30); flushIntervalMs(15_000L)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { Net.instance.flushMonitor() }
        })
    }
    override fun onTerminate() { super.onTerminate(); Net.instance.shutdownMonitor() }
}
```

### 26.9 MonitorEvent 数据模型

每条监控事件包含以下字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `requestId` | `String` | 唯一标识（`设备ID_时间戳_自增序号`），非加密级 |
| `url` | `String` | 经 `urlSanitizer` 脱敏后的请求 URL |
| `method` | `String` | GET / POST / PUT / DELETE |
| `tag` | `String?` | 请求 tag（业务标识，来自 `SendTool`） |
| `requestStartMs` | `Long` | 请求开始时间戳（epochMillis） |
| `requestEndMs` | `Long` | 请求结束时间戳 |
| `totalCostMs` | `Long` | 总耗时（毫秒） |
| `httpCode` | `Int` | HTTP 状态码（异常时为 -1） |
| `responseBodySize` | `Long` | 响应体大小（字节，chunked 为 -1） |
| `contentType` | `String?` | 响应 Content-Type |
| `isSuccess` | `Boolean` | 是否成功（无异常且 HTTP 2xx） |
| `errorType` | `ErrorType` | 错误分类枚举（见下节） |
| `errorMessage` | `String?` | 异常消息 |
| `exceptionClass` | `String?` | 异常类名（如 `SocketTimeoutException`） |
| `networkType` | `String?` | WIFI / CELLULAR / ETHERNET / NONE（取自 `NetworkTypeCache`） |
| `carrierName` | `String?` | 运营商名称（预留，当前为 null） |

**序列化示例**（`toJson()` 输出）：

```json
{
  "requestId": "a1b2c3d4_1719000000000_42",
  "url": "https://api.example.com/login",
  "method": "POST",
  "tag": "loginTask",
  "totalCostMs": 5200,
  "httpCode": 500,
  "responseBodySize": 256,
  "contentType": "application/json; charset=utf-8",
  "isSuccess": false,
  "errorType": "HTTP_SERVER_ERROR",
  "errorMessage": null,
  "exceptionClass": null,
  "networkType": "WIFI",
  "carrierName": "",
  "timestamp": 1719000000000
}
```

> **注意**：精确的 DNS/TCP/TLS 阶段耗时需配合 OkHttp EventListener 获取（后续版本支持）。当前拦截器层仅提供总耗时 `totalCostMs`。

### 26.10 错误分类详解

`MonitorInterceptor.classifyError()` 自动将 IOException 和 HTTP 状态码分类：

| ErrorType | 触发条件 | 排查方向 |
|---|---|---|
| `NONE` | 无异常且 HTTP 2xx | 正常 |
| `DNS_ERROR` | `UnknownHostException` | DNS 故障、域名拼写错误、网络断开 |
| `CONNECT_TIMEOUT` | `ConnectException`（消息不含 "refused"） | 服务器不可达、防火墙屏蔽 |
| `CONNECT_REFUSED` | `ConnectException`（消息含 "refused"） | 端口未监听、服务拒绝连接 |
| `SSL_ERROR` | `SSLException` | 证书过期/不匹配、TLS 版本不兼容 |
| `TIMEOUT` | `SocketTimeoutException` | 网络延迟过高、服务端处理超时 |
| `NO_ROUTE` | 连接池耗尽或无可用路由 | 代理配置错误、网络切换中 |
| `HTTP_CLIENT_ERROR` | HTTP 4xx | 参数错误(400)、未授权(401)、无权限(403) |
| `HTTP_SERVER_ERROR` | HTTP 5xx | 服务端错误(500)、网关超时(502/504) |
| `PARSE_ERROR` | 响应解析失败 | JSON 格式错误、数据模型不匹配 |
| `CANCELLED` | 请求被取消 | Activity 销毁、手动取消 |
| `UNKNOWN` | 其他未分类异常 | 需人工排查 |

> `SocketTimeoutException` 在拦截器层无法精确区分子类型（连接/读/写），统一归为 `TIMEOUT`。精确区分需配合 EventListener。

### 26.11 拦截器链位置

```
Request
  │
  ▼
[1. 用户自定义 Interceptor]     ← NetConfig.addInterceptor()
  │
  ▼
[2. EncryptInterceptor]          ← 字段加解密（请求体加密）
  │
  ▼
[3. MonitorInterceptor]          ← ★ 网络监控
  │
  ▼
[4. HttpLoggingInterceptor]      ← HTTP 日志（NetworkInterceptor）
  │
  ▼
Server
```

**注册代码**（`OkHttpManager.createDefaultClient()`）：

```kotlin
ddNetConfig.monitorConfig?.let { monitorConfig ->
    if (monitorConfig.enabled) {
        val handler = monitorConfig.reportHandler ?: DefaultMonitorReportHandler(...)
        monitorReportHandler = handler  // 存引用，供 Net.flushMonitor() / shutdownMonitor()
        builder.addInterceptor(MonitorInterceptor(monitorConfig, handler, application, networkTypeCache))
    }
}
```

### 26.12 与 Flow / Retrofit 配合

监控在 OkHttp 拦截器层工作，对上层调用方式完全透明：

```kotlin
// ✅ 普通回调 —— 自动监控
Net.instance.postJson().url("...").send(callback)

// ✅ Flow —— 自动监控，同样支持单请求控制
lifecycleScope.launch {
    Net.instance.get().url("https://api.example.com/data")
        .monitor()         // ← 支持单请求控制
        .flowString()
        .catch { e -> /* 异常已被 MonitorInterceptor 记录 */ }
        .collect { body -> updateUI(body) }
}

// ✅ Retrofit —— 共用 OkHttpClient，自动监控
@POST("payment/create")
suspend fun createPayment(@Body body: PaymentRequest): PaymentResponse
// 失败自动上报，无需额外配置
```

### 26.13 性能设计

监控拦截器对主请求路径的额外开销经过精心优化：

| 场景 | 额外开销 | 说明 |
|---|---|---|
| 跳过监控的请求 | **~0.01ms** | 两次 tag 读取 + boolean 判断 |
| 成功请求 + FAILURE_ONLY | **~0.02ms** | 前置过滤跳过事件构建，仅记录时间戳 |
| 失败请求 + FAILURE_ONLY | **~0.10ms** | 构建事件 + 入队（JSON 序列化在后台线程） |
| ALL 模式 | **~0.10ms** | 全部构建事件，序列化异步 |

**核心性能优化**：

| 优化项 | 技术方案 | 效果 |
|---|---|---|
| 前置过滤 | 先判断 isSuccess + reportMode，再决定是否构建事件 | 成功请求减少 ~95% 监控开销 |
| 网络状态缓存 | `NetworkTypeCache`：`registerDefaultNetworkCallback()` + TTL(30s) 兜底 | 从每次 0.1-0.5ms IPC 降至内存读取 |
| 非加密级 ID | `AtomicLong` 自增 + 时间戳 + AndroidId 前缀 | 消除 `SecureRandom` 熵池阻塞风险 |
| 智能线程隔离 | 根据 `IMonitorReportHandler.isAsync` 决定是否创建 executor | Handler 异步时内联调用，省线程 |
| 按需唤醒消费者 | batch 为空 `take()` 阻塞，非空 `poll(flushIntervalMs)` 超时 | 空闲零 CPU，不溢出 |
| 异步 HTTP 上报 | OkHttp `enqueue()` + 三态熔断器（全开/半开/关闭） | 消费者线程永不阻塞 |
| 参数安全校验 | `batchSize`/`flushIntervalMs`/`maxQueueSize` 均 `coerceAtLeast(1)` | 防止非法参数导致异常 |

### 26.14 安全设计

| 安全措施 | 说明 |
|---|---|
| URL 脱敏 | 默认遮蔽 token/sessionId/password 等 9 种敏感 query 参数 |
| HTTPS 提醒 | `reportUrl` 设置为 HTTP 时输出 `Log.w` 警告 |
| 请求体保护 | MonitorInterceptor 在 EncryptInterceptor 之后，URL 不含请求体明文 |
| 监控异常隔离 | `dispatchEvent()` 内 try/catch + `doFlushAsync()` 内 try/catch，监控异常不影响业务 |
| Context 类型安全 | 构造函数接受 `Application?`，杜绝 Activity 泄漏 |
| 守护线程 | 消费者线程 `isDaemon = true`，不阻止进程退出 |
| 参数安全 | `batchSize`/`flushIntervalMs`/`maxQueueSize` 均 `coerceAtLeast(1)` 防止非法值 |

### 26.15 API 速查表

#### MonitorConfig DSL 方法

| 方法 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled(bool)` | 必选 | `false` | 全局监控开关 |
| `reportUrl(url)` | 可选 | `null` | HTTP 批量上报地址（默认上报器使用） |
| `reportMode(mode)` | 可选 | `FAILURE_ONLY` | 上报模式 |
| `sampleRate(rate)` | 可选 | `1.0f` | 采样率 0.0~1.0 |
| `batchSize(size)` | 可选 | `20` | 批量上报最大事件数（自动 `coerceAtLeast(1)`） |
| `flushIntervalMs(ms)` | 可选 | `10000` | 上报时间窗口（自动 `coerceAtLeast(1)`） |
| `maxQueueSize(size)` | 可选 | `1000` | 内存队列最大容量（自动 `coerceAtLeast(1)`） |
| `slowRequestThresholdMs(ms)` | 可选 | `0` | 慢请求阈值（`SLOW_ONLY` 模式使用） |
| `urlSanitizer(fn)` | 可选 | 内置脱敏 | URL 脱敏函数 |
| `reportHandler(handler)` | 可选 | `null` | 自定义上报处理器（null→自动创建 `DefaultMonitorReportHandler`） |

#### ParamsBuilder 单请求方法

| 方法 | 说明 |
|---|---|
| `.monitor()` | 强制对本请求开启监控（优先级最高） |
| `.skipMonitor()` | 强制跳过本请求的监控（优先级最高） |

#### MonitorMarker（OkHttp Typed Tag）

| 常量 | 值 | 说明 |
|---|---|---|
| `MonitorMarker.MONITOR` | `"__monitor_force__"` | 强制开启 |
| `MonitorMarker.SKIP` | `"__monitor_skip__"` | 强制跳过 |
| `MonitorMarker.fromFlag(flag)` | — | 从 `ParamsBuilder.monitorFlag` 构造 Tag |

#### Net 生命周期方法

| 方法 | 说明 |
|---|---|
| `Net.instance.flushMonitor()` | 刷新缓冲区（App 进入后台时调用） |
| `Net.instance.shutdownMonitor()` | 关闭并释放资源（进程终止/切换环境时调用） |

#### ReportMode 枚举

| 枚举值 | 说明 |
|---|---|
| `FAILURE_ONLY` | 仅上报失败（默认） |
| `ALL` | 上报所有请求 |
| `SLOW_ONLY` | 仅上报慢请求（配合 `slowRequestThresholdMs`） |

#### MonitorEvent.ErrorType 枚举

| 枚举值 | 说明 |
|---|---|
| `NONE` | 成功 |
| `DNS_ERROR` | DNS 解析失败 |
| `CONNECT_TIMEOUT` | TCP 连接超时 |
| `CONNECT_REFUSED` | TCP 连接被拒绝 |
| `SSL_ERROR` | TLS/SSL 握手失败 |
| `TIMEOUT` | 超时（读/写） |
| `NO_ROUTE` | 无可用路由 |
| `HTTP_CLIENT_ERROR` | HTTP 4xx |
| `HTTP_SERVER_ERROR` | HTTP 5xx |
| `PARSE_ERROR` | 响应解析失败 |
| `CANCELLED` | 请求被取消 |
| `UNKNOWN` | 未知错误 |

#### IMonitorReportHandler 接口

| 成员 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `isAsync` | `Boolean` | `false` | `true`→框架内联调用；`false`→框架创建专用线程隔离 |
| `onEvent(event)` | 方法 | — | 接收事件，应尽快返回 |
| `flush()` | 方法 | — | 主动刷新缓冲区 |
| `shutdown()` | 方法 | — | 关闭并释放资源 |

#### 内置上报处理器

| 类 | `isAsync` | 说明 |
|---|---|---|
| `DefaultMonitorReportHandler` | `true` | HTTP 批量上报（队列+后台线程+三态熔断器+指数退避） |
| `ResilientReportHandler` | `false` | 本地文件兜底（含磁盘 I/O），`synchronized` 线程安全 |
| `IMonitorReportHandler` | 接口 | 自定义上报实现此接口 |

#### 网络类型缓存

| 类 | 说明 |
|---|---|
| `NetworkTypeCache` | `registerDefaultNetworkCallback()` + TTL 30s 兜底，由 `OkHttpManager` 自动管理 |

#### 完整生产配置

```kotlin
Net.instance.configure {
    app(this@MyApp)
    url("https://api.example.com")
    monitor {
        enabled(true)
        reportUrl("https://monitor.example.com/api/v1/report")
        reportMode(ReportMode.FAILURE_ONLY)
        sampleRate(1.0f)
        batchSize(30)
        flushIntervalMs(15_000L)
        maxQueueSize(1000)
        slowRequestThresholdMs(3000)
        // 自定义 URL 脱敏
        urlSanitizer = { url -> url.replace(Regex("([?&])(customSecret)=[^&]*",
            RegexOption.IGNORE_CASE), "$1$2=***") }
        // 崩溃安全双写（生产推荐）
        reportHandler(ResilientReportHandler(
            delegate = DefaultMonitorReportHandler(
                reportUrl = "https://monitor.example.com/api/v1/report",
                batchSize = 30, flushIntervalMs = 15_000L, maxQueueSize = 1000
            ),
            localFile = File(cacheDir, "network_monitor_backup.log"),
            maxLocalEvents = 200
        ))
    }
}
```
