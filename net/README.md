# ITG Net 使用教程

ITG Net 是一个基于 OkHttp 的 Android 网络请求与文件下载库，提供 GET、常见 POST、文件上传、Multipart 上传、下载队列、下载取消、重试、MD5 校验和生命周期自动取消能力。

## 1. 引入依赖

```gradle
implementation "com.itg:itg-net:0.1.0"
```

如果当前项目直接依赖本地 module：

```gradle
implementation project(":net")
```

## 2. 初始化

建议在 `Application.onCreate()` 中初始化：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.instance.configure {
            app(this@App)
            url("https://api.example.com")
            maxDownloadNum(3)
            setGlobalParams("platform", "android")
            setGlobalParams("appVersion", "1.0.0")
        }
    }
}
```

常用配置：

```kotlin
Net.instance.configure {
    app(application)
    url("https://api.example.com")
    maxDownloadNum(3)
    useHttpLog(true)
    addInterceptor(authInterceptor)
    useCacheControl(CacheFactory.getCache(application))
}
```

自定义 OkHttpClient：

```kotlin
Net.instance.configure {
    okHttpClient(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .build()
    )
}
```

## 3. 通用回调

普通请求使用 `DdCallback`：

```kotlin
val callback = object : DdCallback {
    override fun onFailure(er: String?) {
        // 请求失败
    }

    override fun onResponse(result: String?, code: Int) {
        // 请求成功，code 是 HTTP 状态码
    }
}
```

下载使用 `IProgressCallback`：

```kotlin
val progressCallback = object : IProgressCallback {
    override fun onConnecting(task: Task) {
        // 开始连接服务器
    }

    override fun onProgress(task: Task, complete: Boolean) {
        val progress = if (task.contentLength > 0) {
            (task.downloadSize * 100 / task.contentLength).toInt()
        } else {
            0
        }
    }

    override fun onFail(error: String?, task: Task) {
        // 下载失败或取消
    }
}
```

## 4. 通用请求能力

所有请求 Builder 都支持部分通用能力：

```kotlin
.url("https://api.example.com/users")
.path("users/list")
.addHeader("Authorization", "Bearer token")
.addCookie(cookie)
.addTag("request_tag")
.autoCancel(activity)
.noUseGlobalParams()
.addCacheControl(CacheControl.FORCE_NETWORK)
```

说明：

- `url()`：设置完整 URL。未设置时使用初始化配置的 `NetConfig.url`。
- `path()`：在基础 URL 后追加 path。
- `addHeader()`：添加请求头。
- `addTag()`：设置 OkHttp tag，可用于取消请求。
- `autoCancel(activity)`：Activity 销毁时自动取消该请求。
- `noUseGlobalParams()`：本次请求不携带全局参数。
- `addCacheControl()`：设置 OkHttp CacheControl。

取消普通网络请求：

```kotlin
Net.instance.cancel("request_tag")
Net.instance.cancelAll()
```

## 5. GET 请求

```kotlin
Net.instance.get()
    .url("https://api.example.com/users")
    .addParam("page", "1")
    .addParam("pageSize", "20")
    .addHeader("Authorization", "Bearer token")
    .addTag("user_list")
    .autoCancel(activity)
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
        }

        override fun onResponse(result: String?, code: Int) {
        }
    })
```

使用全局基础 URL：

```kotlin
Net.instance.get()
    .path("users")
    .addParam("id", "1001")
    .send(callback)
```

## 6. POST Form

`postForm()` 发送 `application/x-www-form-urlencoded` 表单。

```kotlin
Net.instance.postForm()
    .url("https://api.example.com/login")
    .addParam("account", "user@example.com")
    .addParam("password", "123456")
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
        }

        override fun onResponse(result: String?, code: Int) {
        }
    })
```

批量添加参数：

```kotlin
val params = mutableMapOf<String, String?>(
    "account" to "user@example.com",
    "password" to "123456"
)

Net.instance.postForm()
    .path("login")
    .addParam(params)
    .send(callback)
```

## 7. POST JSON

`postJson()` 发送 `application/json;charset=utf-8` 请求体。

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/user/update")
    .addJson("name", "Tom")
    .addJson("age", 28)
    .addJson("vip", true)
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
        }

        override fun onResponse(result: String?, code: Int) {
        }
    })
```

添加 JSON 字符串：

```kotlin
Net.instance.postJson()
    .path("order/create")
    .addJsonStr("""{"skuId":"1001","count":2}""")
    .send(callback)
```

添加 `JSONObject`：

```kotlin
val json = JSONObject()
json.put("keyword", "android")

Net.instance.postJson()
    .path("search")
    .addParam(json)
    .send(callback)
```

## 8. POST Content

`postContent()` 用于直接发送原始文本内容，例如纯文本、JSON 字符串、XML 等。

```kotlin
Net.instance.postContent()
    .url("https://api.example.com/raw")
    .addContent(
        """{"name":"Tom","age":28}""",
        "application/json;charset=utf-8"
    )
    .send(callback)
```

发送纯文本：

```kotlin
Net.instance.postContent()
    .path("log/report")
    .addContent("client log content", "text/plain;charset=utf-8")
    .send(callback)
```

## 9. POST File

`postFile()` 用于上传单个文件，请求体就是文件内容，不是 multipart。

```kotlin
val imageFile = File(context.cacheDir, "avatar.jpg")

Net.instance.postFile()
    .url("https://api.example.com/avatar/upload")
    .addFile("avatar", "image/jpeg", imageFile)
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
        }

        override fun onResponse(result: String?, code: Int) {
        }
    })
```

简化写法：

```kotlin
Net.instance.postFile()
    .path("file/upload")
    .addFile(File("/sdcard/Download/a.pdf"))
    .send(callback)
```

说明：

- `postFile()` 是单文件原始请求体上传，不是 `multipart/form-data`，服务端应直接读取 request body。
- `addFile(file)`：添加要上传的文件。
- `addFile(fileName, file)`：保留和 multipart 一致的调用形式；单文件原始上传时服务端不会收到 form-data 字段名。
- `addFile(fileName, mediaType, file)`：设置文件 MIME 类型；`fileName` 在单文件原始上传中不作为 form-data 字段发送。
- `.png`、`.jpg`、`.jpeg` 会自动识别常见图片类型，其他默认 `application/octet-stream`。

## 10. POST Multipart

`postMultipart()` 用于标准 `multipart/form-data` 上传，适合同时上传普通参数、多个文本 body、JSON part 和多个文件。

### 10.1 上传表单字段和文件

```kotlin
val avatar = File(context.cacheDir, "avatar.jpg")

Net.instance.postMultipart()
    .url("https://api.example.com/profile/upload")
    .addParam("userId", "1001")
    .addParam("scene", "avatar")
    .addFile("avatar", "image/jpeg", avatar)
    .send(callback)
```

### 10.2 同时上传多个文件

```kotlin
val front = File(context.cacheDir, "front.jpg")
val back = File(context.cacheDir, "back.jpg")

Net.instance.postMultipart()
    .path("card/upload")
    .addParam("userId", "1001")
    .addFile("frontImage", "image/jpeg", front)
    .addFile("backImage", "image/jpeg", back)
    .send(callback)
```

### 10.3 同时上传多个 body

未指定名称时，库会自动生成 `body`、`body1`、`body2`：

```kotlin
Net.instance.postMultipart()
    .path("content/upload")
    .addContent("first text", "text/plain;charset=utf-8")
    .addContent("second text", "text/plain;charset=utf-8")
    .send(callback)
```

指定 part 名称：

```kotlin
Net.instance.postMultipart()
    .path("content/upload")
    .addContent("summary text", "summary", "text/plain;charset=utf-8")
    .addContent("""{"type":"article"}""", "metadata", "application/json;charset=utf-8")
    .send(callback)
```

### 10.4 Multipart 中携带 JSON

`addJson()` 会把 JSON 作为名为 `json` 的 part 放入 multipart。

```kotlin
Net.instance.postMultipart()
    .path("publish")
    .addParam("userId", "1001")
    .addJson("title", "Hello")
    .addJson("category", "android")
    .addFile("cover", "image/jpeg", coverFile)
    .send(callback)
```

### 10.5 Multipart 注意事项

- 普通参数使用 `addParam()`，会作为标准 form-data 字段上传。
- 文本内容使用 `addContent()`，可以上传多个 body。
- 文件使用 `addFile()`，可以上传多个文件。
- JSON 使用 `addJson()`，会作为 `json` part 上传。
- 如果你调用 `noUseGlobalParams()`，全局参数不会进入 multipart；但通过 `addAppendParams()` 添加到 URL 上的参数仍会保留。

## 11. 下载文件 newDownload

基础下载：

```kotlin
val task = Net.instance.newDownload()
    .url("https://example.com/app.apk")
    .savePath(File(context.filesDir, "app.apk").absolutePath)
    .retryCount(3)
    .overwrite(false)
    .listener(object : IProgressCallback {
        override fun onConnecting(task: Task) {
        }

        override fun onProgress(task: Task, complete: Boolean) {
            val progress = if (task.contentLength > 0) {
                (task.downloadSize * 100 / task.contentLength).toInt()
            } else {
                0
            }
        }

        override fun onFail(error: String?, task: Task) {
        }
    })
    .start()
```

常用配置：

```kotlin
Net.instance.newDownload()
    .url(fileUrl)
    .savePath(targetFile.absolutePath)
    .retryCount(3)
    .overwrite(true)
    .autoRemoveActivity(activity)
    .listener(progressCallback)
    .start()
```

说明：

- `url(url)`：下载地址，必填。
- `savePath(path)` / `path(path)`：保存路径，必填。
- `retryCount(count)` / `tryAgainCount(count)`：失败重试次数，最小为 1。
- `overwrite(false)`：目标文件已存在时不覆盖，直接失败并回调 `ERROR_TARGET_FILE_EXISTS`。
- `overwrite(true)`：目标文件已存在时覆盖。
- `listener(callback)` / `setDownloadListener(callback)`：下载进度回调。
- `autoRemoveActivity(activity)`：Activity 销毁时移除回调，避免 Activity 被下载监听器持有。注意它只移除回调，不会取消下载任务。

## 12. 取消下载

### 12.1 按 Task 取消

启动下载时保存 `Task`：

```kotlin
val task = Net.instance.newDownload()
    .url(fileUrl)
    .savePath(targetFile.absolutePath)
    .listener(progressCallback)
    .start()
```

取消：

```kotlin
Net.instance.download.cancel(task)
```

### 12.2 按 URL 取消

```kotlin
Net.instance.download.cancel(fileUrl)
```

### 12.3 判断是否在队列中

```kotlin
val downloadingOrWaiting = Net.instance.download.isQueued(fileUrl)
```

兼容旧方法：

```kotlin
val queued = Net.instance.download.isQueue(fileUrl)
```

### 12.4 移除下载监听器

只移除某个任务的所有监听器：

```kotlin
Net.instance.download.removeAllProgressListener(task)
```

只移除某个监听器：

```kotlin
Net.instance.download.removeProgressListener(task, progressCallback)
```

注意：移除监听器不等于取消下载。如果要停止任务，必须调用：

```kotlin
Net.instance.download.cancel(task)
```

## 13. 全局下载监听

监听所有下载任务：

```kotlin
Net.instance.download.setGlobalProgressListener(object : IProgressCallback {
    override fun onConnecting(task: Task) {
    }

    override fun onProgress(task: Task, complete: Boolean) {
    }

    override fun onFail(error: String?, task: Task) {
    }
})
```

移除全局监听：

```kotlin
Net.instance.download.removeGlobalProgressListener(progressCallback)
```

## 14. 常见错误码

下载错误常量在 `com.itg.net.download.data.Tag.kt`：

```kotlin
ERROR_INVALID_DOWNLOAD_TASK
ERROR_TARGET_FILE_EXISTS
ERROR_DOWNLOAD_CANCELED
ERROR_RANGE_NOT_SUPPORTED
ERROR_MD5_CHECK_FAILED
ERROR_EMPTY_RESPONSE_BODY
ERROR_CREATE_DOWNLOAD_DIR_FAILED
ERROR_RENAME_TEMP_FILE_FAILED
```

示例：

```kotlin
override fun onFail(error: String?, task: Task) {
    when (error) {
        ERROR_TARGET_FILE_EXISTS -> {
            // 文件已存在，且 overwrite(false)
        }
        ERROR_DOWNLOAD_CANCELED -> {
            // 用户取消下载
        }
    }
}
```

## 15. 推荐实践

- 请求列表页、详情页使用 `get()`。
- 登录、提交普通表单使用 `postForm()`。
- API 接收 JSON 时使用 `postJson()`。
- 直接上传原始字符串或 JSON 字符串时使用 `postContent()`。
- 单文件原始上传使用 `postFile()`。
- 同时上传参数、多个文件、多个文本 body 时使用 `postMultipart()`。
- 下载文件使用 `newDownload()`，并保存返回的 `Task`，方便后续取消。
- Activity/Fragment 页面请求建议使用 `autoCancel(activity)`。
- Activity 页面下载建议使用 `autoRemoveActivity(activity)` 加 `cancel(task)`，分别处理“释放监听器”和“停止任务”。
