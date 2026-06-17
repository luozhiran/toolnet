# Net 网络请求库使用教程

Net 是一款基于 OkHttp 封装的 Android 网络请求库，提供简洁的链式调用 API，支持 GET/POST（JSON、Form、文件、Multipart、自定义内容）、文件下载（含断点续传）、请求取消、生命周期绑定等功能。所有功能统一通过 `Net.instance` 入口访问。

---

## 目录

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

---

## 1. 快速开始 - 初始化配置

在使用任何网络请求之前，建议在 `Application.onCreate()` 中进行一次全局配置。

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.instance.configure {
            // 【必选】传入 Application 实例
            app(this@MyApp)

            // 【可选】设置全局 Base URL，之后每个请求无需再写完整域名
            url("https://api.example.com")

            // 【可选】添加全局参数，所有请求自动附带（支持多次调用）
            setGlobalParams("platform", "android")
            setGlobalParams("version", "1.0.0")

            // 【可选】设置最大并行下载数，默认为 3
            maxDownloadNum(5)

            // 【可选】开启 HTTP 日志（请求/响应体打印到文件）
            useHttpLog(true)

            // 【可选】自定义 OkHttpClient（如设置拦截器、超时等）
            okHttpClient(myCustomOkHttpClient)

            // 【可选】设置 HTTP 缓存
            useCacheControl(Cache(cacheDir, 10 * 1024 * 1024))

            // 【可选】添加全局拦截器（多个）
            addInterceptor(loggingInterceptor)
            addInterceptor(authInterceptor)
        }
    }
}
```

> **说明**：`configure` 采用 Kotlin DSL 风格，内部 `this` 为 `NetConfig` 实例，可调用其所有方法进行配置。

---

## 2. GET 请求

### 2.1 基础 GET 请求

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/info")
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
            // 请求失败（网络异常、超时等），er 为错误信息
            Log.e("TAG", "请求失败: $er")
        }

        override fun onResponse(result: String?, code: Int) {
            // 请求成功，result 为响应体字符串，code 为 HTTP 状态码
            Log.d("TAG", "响应码: $code, 响应体: $result")
        }
    })
```

> **说明**：`DdCallback` 是回调接口，`onFailure` 在请求失败时触发，`onResponse` 在请求成功时返回响应体和 HTTP 状态码。

### 2.2 GET 请求带 Query 参数

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/list")
    .addParam("page", "1")               // 添加单个键值对 → ?page=1
    .addParam("size", "20")              // 添加单个键值对 → &size=20
    .addParam(mapOf("status" to "active", "sort" to "desc"))  // 批量添加
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`addParam` 会将参数拼接到 URL 后面形成 Query String。支持单个添加和 Map 批量添加。

### 2.3 使用 path 拼接路径

```kotlin
// 如果全局 url 已配为 "https://api.example.com"
// 实际请求地址：https://api.example.com/api/v2/user/profile
Net.instance.get()
    .path("api/v2/user/profile")         // path 会自动与全局 url 拼接
    .send(callback)
```

> **说明**：`path` 与全局 `url` 配合使用，如果全局 `url` 末尾或 `path` 开头有 `/` 会自动处理，不会出现双斜杠。

### 2.4 使用 Handler 接收回调

```kotlin
val handler = Handler(Looper.getMainLooper()) { msg ->
    when (msg.what) {
        1 -> Log.d("TAG", "成功: ${msg.obj}")
        2 -> Log.e("TAG", "失败: ${msg.obj}")
    }
    true
}

Net.instance.get()
    .url("https://api.example.com/data")
    .send(handler, what = 1, errorWhat = 2)
```

> **说明**：除了 `DdCallback`，也支持通过 `Handler` 接收回调，`what` 为成功时的 Message.what，`errorWhat` 为失败时的 Message.what。

---

## 3. POST JSON 请求

### 3.1 基础 JSON POST

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/user/login")
    .addParam("username", "admin")       // 自动放入 JSON body
    .addParam("password", "123456")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`addParam` 在 `postJson` 中会将键值对放入 JSON 请求体中，即 `{"username":"admin","password":"123456"}`。

### 3.2 支持多种类型的参数值

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addParam("name", "hello")           // String
    .addParam("age", 25)                 // Int
    .addParam("score", 98.5f)            // Float
    .addParam("count", 100L)             // Long
    .send(callback)
```

> **说明**：`addParam` 支持 `String`、`Int`、`Float`、`Long` 等多种类型，自动写入 JSON。

### 3.3 使用 JSONObject 作为请求体

```kotlin
val jsonObj = JSONObject().apply {
    put("orderId", "2024001")
    put("amount", 99.99)
    put("items", JSONArray().apply {
        put(JSONObject().apply { put("id", 1); put("qty", 2) })
        put(JSONObject().apply { put("id", 2); put("qty", 1) })
    })
}

Net.instance.postJson()
    .url("https://api.example.com/order/create")
    .addParam(jsonObj)                   // 直接传入 JSONObject，深度合并
    .send(callback)
```

> **说明**：`addParam(JSONObject)` 会将传入的 JSONObject 与已有的 JSON 参数**深度合并**，适合复杂嵌套结构的请求。

### 3.4 使用 JSON 字符串

```kotlin
val jsonString = """{"type":"push","target":"all","data":{"title":"通知标题"}}"""

Net.instance.postJson()
    .url("https://api.example.com/message/send")
    .addJsonStr(jsonString)              // 传入 JSON 字符串，会自动解析并深度合并
    .send(callback)
```

> **说明**：`addJsonStr` 接收 JSON 字符串，内部解析为 JSONObject 后与已有参数深度合并，非法 JSON 字符串会被静默忽略。

### 3.5 addJson 直接插入

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/data")
    .addJson("nested", JSONObject().apply { put("key", "value") })
    .send(callback)
```

> **说明**：`addJson` 直接向 JSON 请求体中插入一个 key-value 对，value 可以是任意类型。

### 3.6 拼接 URL 参数

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/order/query")
    .addAppendParams("page", "1")        // 拼接到 URL 上的 Query 参数
    .addAppendParams("limit", "10")
    .addParam("status", "pending")       // 进入 JSON body
    .send(callback)
```

> **说明**：`postJson` 中 `addAppendParams` 将参数拼接到 URL 上（Query String），而 `addParam` 将参数放入 JSON body 中，二者互不干扰。

---

## 4. POST Form 表单请求

```kotlin
Net.instance.postForm()
    .url("https://api.example.com/user/register")
    .addParam("username", "newUser")     // 以 application/x-www-form-urlencoded 形式提交
    .addParam("email", "user@test.com")
    .addParam("password", "123456")
    .addParam(mapOf("gender" to "male", "city" to "Beijing"))
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`postForm` 使用 `application/x-www-form-urlencoded` 编码格式提交表单数据，参数最终编码为 `key1=value1&key2=value2` 的形式放入请求体。

---

## 5. POST 文件上传

```kotlin
val file = File("/sdcard/photo.jpg")

Net.instance.postFile()
    .url("https://api.example.com/upload")
    .addFile(file)                       // 以 "file" 作为表单字段名上传
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`postFile` 将文件上传，Content-Type 根据文件扩展名自动推断（`.png` → `image/png`，`.jpg` → `image/jpeg`，其他 → `application/octet-stream`）。

---

## 6. POST Multipart 请求

multipart 请求可以同时提交文件、文本内容、JSON 和表单参数。

```kotlin
val file = File("/sdcard/report.pdf")

Net.instance.postMultipart()
    .url("https://api.example.com/report/submit")
    // 添加文件
    .addFile("report", "application/pdf", file)
    // 添加纯文本内容
    .addContent("这是一段自定义文本", "text/plain")
    .addContent("<xml>...</xml>", "xml_body", "application/xml")
    // 添加 JSON
    .addJson("meta", JSONObject().apply { put("author", "张三") })
    // 添加表单参数
    .addParam("title", "2024年报告")
    .addParam("department", "技术部")
    .addAppendParams("token", "abc123")  // 拼接到 URL
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`postMultipart` 是最灵活的请求方式，一次请求中可以混合多种内容类型：
> - `addFile(name, mediaType, file)`：添加文件部分
> - `addContent(content, mediaType)`：添加自定义文本内容部分，`contentFlag` 作为标识名（默认 `"body"`）
> - `addJson(key, value)`：添加 JSON 键值对
> - `addParam(key, value)`：添加表单参数
> - `addAppendParams(key, value)`：拼接 URL Query 参数

---

## 7. POST Content 自定义请求

适用于需要自定义 Content-Type 的请求场景（如 `application/xml`、`text/plain` 等）。

```kotlin
Net.instance.postContent()
    .url("https://api.example.com/soap")
    .addContent("<soap:Envelope>...</soap:Envelope>", "application/xml")
    .addHeader("SOAPAction", "urn:example")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`postContent` 允许自定义请求体的内容类型，无需预设格式。配合 `addHeader` 可设置特殊协议头。

---

## 8. POST 断点续传上传

```kotlin
val file = File("/sdcard/large_video.mp4")

Net.instance.builder(ModeType.PostResume) as PostResumeFile
    .url("https://api.example.com/upload/resume")
    .addFile(file)
    .addResumeFileOffset(1024 * 1024 * 5)  // 从 5MB 偏移开始上传
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

> **说明**：`PostResume` 模式用于大文件断点续传上传。通过 `addResumeFileOffset` 设置已上传的字节偏移量，服务器应从该位置继续接收数据。需要通过 `builder(ModeType.PostResume)` 创建。

---

## 9. 通用 Builder 功能

以下功能适用于所有类型的请求构建器（GET、POST JSON、POST Form、POST File、POST Multipart、POST Content）。

### 9.1 设置请求 Header

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .addHeader("Authorization", "Bearer xxxx-token-xxxx")
    .addHeader("X-Request-Id", UUID.randomUUID().toString())
    .addHeader(mapOf("Accept" to "application/json", "X-Version" to "2.0"))
    .send(callback)
```

> **说明**：`addHeader` 支持单个键值对和 Map 批量添加，所有 Header 会附加到请求中。

### 9.2 设置 Cookie

```kotlin
val cookie = Cookie.Builder()
    .name("sessionId")
    .value("abc123def456")
    .domain("api.example.com")
    .build()

Net.instance.get()
    .url("https://api.example.com/data")
    .addCookie(cookie)                   // 单个 Cookie
    // .addCookie(listOf(cookie1, cookie2))  // 多个 Cookie
    .send(callback)
```

> **说明**：`addCookie` 使用 OkHttp 的 `Cookie` 类设置 Cookie，内部会将所有 Cookie 拼接为 `Cookie` 请求头。

### 9.3 设置请求 Tag（用于后续取消）

```kotlin
Net.instance.get()
    .url("https://api.example.com/long-polling")
    .tag("longPollingTask")              // 设置唯一标识
    .send(callback)

// 在其他地方可以取消所有带此 tag 的请求
Net.instance.cancel("longPollingTask")
```

> **说明**：`tag` 为请求设置唯一标识，配合 `cancel(tag)` 或 `cancelFirstTag(tag)` 可以精确取消某个或某类请求。

### 9.4 设置缓存策略

```kotlin
import okhttp3.CacheControl

Net.instance.get()
    .url("https://api.example.com/config")
    .addCacheControl(CacheControl.FORCE_CACHE)   // 强制使用缓存
    .send(callback)

// 或者构建自定义缓存策略
val cacheControl = CacheControl.Builder()
    .maxAge(5, TimeUnit.MINUTES)
    .build()
```

> **说明**：`addCacheControl` 设置 OkHttp 的 `CacheControl`，控制缓存行为。需要先在全局配置中通过 `useCacheControl(Cache)` 开启缓存。

### 9.5 跳过全局参数

```kotlin
Net.instance.postJson()
    .url("https://third-party.example.com/api")  // 第三方接口，不应携带全局参数
    .noUseGlobalParams()                         // 跳过全局参数
    .addParam("data", "value")
    .send(callback)
```

> **说明**：`noUseGlobalParams()` 让当前请求不附带全局配置中设置的 `globalParams`，适用于请求第三方接口时避免泄露内部参数。

### 9.6 绑定 Activity 生命周期

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .autoCancel(this)                    // Activity 销毁时自动取消请求
    .send(callback)
```

> **说明**：`autoCancel(activity)` 绑定 Activity 生命周期，当 Activity 销毁时自动取消未完成的请求，防止内存泄露和无效回调，**建议在所有 Activity 发起的请求中使用**。

---

## 10. 取消请求

### 10.1 取消所有请求

```kotlin
Net.instance.cancelAll()
```

> **说明**：取消当前所有排队中和正在执行的 OkHttp 请求（包括普通请求和下载请求）。

### 10.2 按 Tag 取消请求

```kotlin
// 取消所有 tag 为 "searchTask" 的请求
Net.instance.cancel("searchTask")

// 等价于
Net.instance.cancelTag("searchTask")
```

> **说明**：`cancel(tag)` 是 `cancelTag(tag)` 的别名，会取消所有排队中和执行中匹配该 tag 的请求。

### 10.3 取消第一个匹配 Tag 的请求

```kotlin
val found = Net.instance.cancelFirstTag("uploadTask")
if (found) {
    Log.d("TAG", "成功取消了一个上传任务")
} else {
    Log.d("TAG", "未找到匹配的上传任务")
}
```

> **说明**：`cancelFirstTag` 只取消第一个匹配 tag 的请求（优先从排队队列查找），找到后立即返回 `true`，不会继续遍历。

### 10.4 按 URL 取消下载

```kotlin
Net.instance.cancelDownload("https://example.com/file.zip")
```

> **说明**：取消指定 URL 的下载任务，优先取消正在下载中的，其次从等待队列中移除。

### 10.5 按 Task 取消下载

```kotlin
val task: Task = Net.instance.newDownload()
    .url("https://example.com/file.zip")
    .savePath("/sdcard/file.zip")
    .start()

// 取消该任务
Net.instance.cancelDownload(task)
```

> **说明**：通过 `Task` 实例取消对应的下载任务。

---

## 11. 文件下载

### 11.1 基础下载

```kotlin
val task = Net.instance.newDownload()
    .savePath("${filesDir}/video.mp4")   // 文件保存路径
    .url("https://example.com/video.mp4") // 下载地址
    .listener(object : IProgressCallback {
        override fun onConnecting(task: Task) {
            // 正在与服务器建立连接
            Log.d("TAG", "连接中...")
        }

        override fun onProgress(task: Task, complete: Boolean) {
            // 下载进度更新
            val progress = task.downloadSize * 100 / maxOf(task.contentLength, 1L)
            Log.d("TAG", "下载进度: $progress% (${task.downloadSize}/${task.contentLength})")
            if (complete) {
                Log.d("TAG", "下载完成！文件路径: ${task.path}")
            }
        }

        override fun onFail(error: String?, task: Task) {
            // 下载失败，error 为失败原因
            Log.e("TAG", "下载失败: $error")
        }
    })
    .start()
```

> **说明**：下载通过 `newDownload()` 创建 `TaskBuilder`，链式配置后调用 `start()` 发起下载。`IProgressCallback` 提供三个回调：
> - `onConnecting`：正在建立服务器连接
> - `onProgress`：下载进度更新，`complete=true` 表示下载完成
> - `onFail`：下载失败

### 11.2 覆盖已存在的文件

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/data.zip")
    .url("https://example.com/data.zip")
    .overwrite(true)                     // 如果目标文件已存在，覆盖它
    .listener(callback)
    .start()
```

> **说明**：`overwrite(false)`（默认）时，如果目标文件已存在，会通过 `onFail` 回调 `"目标文件已存在，未开启覆盖"` 错误。

### 11.3 设置重试次数

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(downloadUrl)
    .retryCount(3)                       // 失败后最多重试 3 次
    .listener(callback)
    .start()
```

> **说明**：`retryCount` 设置下载失败后允许的最大重试次数，默认为 1，传入的值至少为 1。

### 11.4 断点续传下载

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/large_file.zip")
    .url("https://example.com/large_file.zip")
    .supportCheckpoint()                 // 开启断点续传
    .listener(callback)
    .start()
```

> **说明**：`supportCheckpoint()` 开启断点续传功能。如果下载中断后重新发起相同的下载请求（相同 URL 和保存路径），会先向服务器发送 Range 请求获取已下载的字节数，从断点位置继续下载。**依赖服务器支持 Range 请求**。

### 11.5 绑定 Activity 生命周期

```kotlin
Net.instance.newDownload()
    .savePath(path)
    .url(downloadUrl)
    .bindActivity(this)                  // Activity 销毁时自动取消下载，释放监听器
    .listener(callback)
    .start()
```

> **说明**：`bindActivity(activity)` 将下载任务与 `FragmentActivity` 生命周期绑定。Activity 销毁时自动取消下载任务、释放回调监听器、移除生命周期观察者，**防止内存泄露**。

### 11.6 全局下载进度监听

```kotlin
val globalListener = object : IProgressCallback {
    override fun onConnecting(task: Task) {
        Log.d("TAG", "某个下载开始连接: ${task.url}")
    }

    override fun onProgress(task: Task, complete: Boolean) {
        val progress = task.downloadSize * 100 / maxOf(task.contentLength, 1L)
        Log.d("TAG", "[${task.url}] 进度: $progress%")
    }

    override fun onFail(error: String?, task: Task) {
        Log.e("TAG", "[${task.url}] 下载失败: $error")
    }
}

// 注册全局监听（监听所有下载任务）
Net.instance.addGlobalDownloadListener(globalListener)

// 移除全局监听（如 Activity onDestroy 时）
Net.instance.removeGlobalDownloadListener(globalListener)
```

> **说明**：全局下载监听器可以监听**所有**下载任务的状态变化，适合在全局通知栏、下载管理中心等场景使用。使用完后务必调用 `removeGlobalDownloadListener` 移除，避免内存泄露。

### 11.7 移除下载监听器

```kotlin
val task: Task = ... // 已启动的下载任务

// 移除该任务的所有进度监听器
Net.instance.removeDownloadListeners(task)

// 移除该任务的某个特定监听器
Net.instance.removeDownloadListener(task, specificCallback)
```

> **说明**：对于那些未调用 `bindActivity()` 且未取消的任务，下载会在后台继续执行。此时必须手动调用 `removeDownloadListeners` 释放监听器，**否则持有 Activity 引用的回调会导致内存泄露**。

### 11.8 查询下载状态

```kotlin
val url = "https://example.com/file.zip"

if (Net.instance.isDownloadQueued(url)) {
    Log.d("TAG", "该 URL 正在下载或排队等待下载")
} else {
    Log.d("TAG", "该 URL 没有下载任务")
}
```

> **说明**：`isDownloadQueued(url)` 判断指定 URL 是否正在下载（running queue）或排队等待下载（waiting queue），返回 `true` 表示存在。

---

## 12. 全局配置详解

以下是 `NetConfig` 中所有可配置项：

| 方法 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `app(Application)` | 必选 | - | 设置 Application 实例，用于获取 Context |
| `url(String?)` | 可选 | null | 全局 Base URL，后续请求可通过 `path()` 拼接相对路径 |
| `setGlobalParams(key, value)` | 可选 | - | 添加全局参数，所有请求自动附带 |
| `removeGlobalParam(key)` | 可选 | - | 移除单个全局参数 |
| `clearGlobalParams()` | 可选 | - | 清空所有全局参数 |
| `maxDownloadNum(Int)` | 可选 | 3 | 最大并行下载数，最小值 1 |
| `useHttpLog(Boolean)` | 可选 | false | 是否开启 HTTP 请求/响应日志 |
| `okHttpClient(OkHttpClient?)` | 可选 | null | 自定义 OkHttpClient，不设置则使用库内置默认 |
| `addInterceptor(Interceptor)` | 可选 | - | 添加 OkHttp 拦截器 |
| `useCacheControl(Cache)` | 可选 | null | 设置 OkHttp 缓存目录和大小 |
| `log(String?)` | 可选 | null | 自定义日志文件路径 |

### 示例：完整配置

```kotlin
Net.instance.configure {
    app(application)
    url("https://api.example.com")
    setGlobalParams("platform", "android")
    setGlobalParams("version", BuildConfig.VERSION_NAME)
    setGlobalParams("channel", "official")
    maxDownloadNum(5)
    useHttpLog(BuildConfig.DEBUG)        // 仅在 Debug 模式开启日志
    addInterceptor(authInterceptor)
    useCacheControl(Cache(File(cacheDir, "http_cache"), 50 * 1024 * 1024))
}
```

> **说明**：全局配置建议在 `Application.onCreate()` 中一次性完成。配置完成后，所有后续请求自动继承这些设置（如全局参数、Base URL 等），单个请求可通过 `noUseGlobalParams()` 临时跳过。
