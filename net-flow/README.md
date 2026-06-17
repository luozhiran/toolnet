# Net-Flow 使用教程

Net-Flow 是基于 `kotlinx.coroutines` 对 Net 网络库的 Kotlin Flow 扩展，将原有的 `DdCallback` / `Handler` 回调模型桥接到 `Flow`，支持结构化并发、背压、自动取消，并提供类型安全的响应反序列化能力。

---

## 目录

1. [快速开始 - 依赖引入](#1-快速开始---依赖引入)
2. [Flow 化普通请求](#2-flow-化普通请求)
3. [Flow + 反序列化](#3-flow--反序列化)
4. [NetResponse 类型详解](#4-netresponse-类型详解)
5. [异常处理](#5-异常处理)
6. [下载进度 Flow](#6-下载进度-flow)
7. [便捷扩展方法](#7-便捷扩展方法)
8. [自定义 Converter](#8-自定义-converter)
9. [协程取消与生命周期](#9-协程取消与生命周期)
10. [API 速查表](#10-api-速查表)

---

## 1. 快速开始 - 依赖引入

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    implementation project(':net')
    implementation project(':net-flow')
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
}
```

> **说明**：`net-flow` 通过 `api project(':net')` 自动传递核心库依赖，无需重复引入 `:net`。`kotlinx-coroutines-android` 提供 `Dispatchers.Main` 和 Android 生命周期感知的协程作用域。

---

## 2. Flow 化普通请求

### 2.1 基础 Flow 请求

通过 `.flowString()` 扩展方法，将任意 Builder 模式的请求转为 `Flow<String>`：

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/info")
        .flowString()
        .catch { e -> Log.e("TAG", "请求失败: ${e.message}") }
        .collect { body ->
            Log.d("TAG", "响应体: $body")
        }
}
```

> **对比原有回调写法**：
> ```kotlin
> // 原有 DdCallback 方式
> Net.instance.get()
>     .url("https://api.example.com/user/info")
>     .send(object : DdCallback {
>         override fun onFailure(er: String?) { Log.e("TAG", "失败: $er") }
>         override fun onResponse(result: String?, code: Int) { Log.d("TAG", "成功: $result") }
>     })
> ```

### 2.2 所有请求类型均支持 `.flowString()`

因为 `.flowString()` 是定义在 `ParamsBuilder` 上的扩展函数，所有请求类型均可使用：

```kotlin
// GET 请求
Net.instance.get()
    .url("https://api.example.com/data")
    .addParam("page", "1")
    .flowString()
    .collect { body -> /* ... */ }

// POST JSON 请求
Net.instance.postJson()
    .url("https://api.example.com/submit")
    .addParam("name", "hello")
    .flowString()
    .collect { body -> /* ... */ }

// POST Form 请求
Net.instance.postForm()
    .url("https://api.example.com/register")
    .addParam("username", "user1")
    .flowString()
    .collect { body -> /* ... */ }

// POST Multipart 请求
Net.instance.postMultipart()
    .url("https://api.example.com/upload")
    .addFile("file", "application/octet-stream", File("/sdcard/data.bin"))
    .flowString()
    .collect { body -> /* ... */ }
```

### 2.3 Flow 只发射一次

每个请求的 Flow 只发射一次响应体字符串后自动完成。如果需要多次发射（如轮询），可以在外层使用 `flow { }` 构建器：

```kotlin
flow {
    while (true) {
        val body = Net.instance.get()
            .url("https://api.example.com/polling")
            .flowString()
            .first()  // 取第一个值
        emit(body)
        delay(5000)  // 5 秒轮询一次
    }
}.collect { /* ... */ }
```

---

## 3. Flow + 反序列化

### 3.1 使用 flowResponse 自动反序列化

通过 `.flowResponse(converter)` 将响应体自动转换为指定类型：

```kotlin
data class User(val id: Int, val name: String, val email: String)

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/1")
        .flowResponse { raw -> Gson().fromJson(raw, User::class.java) }
        .catch { e -> Log.e("TAG", "请求失败", e) }
        .collect { response ->
            if (response.isSuccessful) {
                val user: User? = response.body
                updateUI(user)
            } else {
                showError("HTTP ${response.code}")
            }
        }
}
```

### 3.2 使用 GsonNetConverter

内置的 `GsonNetConverter` 封装了 Gson 反序列化逻辑：

```kotlin
import com.itg.net.flow.converter.GsonNetConverter

Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(GsonNetConverter<User>(type = User::class.java))
    .collect { response ->
        val user = response.body  // 类型为 User?
    }
```

### 3.3 自定义 Gson 实例

如果 Gson 需要特殊配置（如日期格式、命名策略），可以传入自定义实例：

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()

Net.instance.get()
    .url("https://api.example.com/orders")
    .flowResponse(GsonNetConverter<OrderList>(gson = gson, type = OrderList::class.java))
    .collect { response -> /* ... */ }
```

### 3.4 Lambda 转换器（最简单）

对于简单场景，可以直接使用 lambda：

```kotlin
// String → Int
Net.instance.get()
    .url("https://api.example.com/count")
    .flowResponse { raw -> raw?.toIntOrNull() }
    .collect { response -> showCount(response.body ?: 0) }

// String → JSONObject
Net.instance.get()
    .url("https://api.example.com/data")
    .flowResponse { raw -> raw?.let { JSONObject(it) } }
    .collect { response -> /* ... */ }
```

---

## 4. NetResponse 类型详解

`NetResponse<T>` 是所有含反序列化的 Flow 的统一返回类型：

```kotlin
data class NetResponse<T>(
    val body: T?,                      // 反序列化后的对象
    val rawBody: String?,              // 原始响应字符串（始终保留）
    val code: Int,                     // HTTP 状态码
    val headers: Map<String, String>,  // 响应头
    val isSuccessful: Boolean          // code 是否在 200..299
)
```

### 4.1 按状态码处理

```kotlin
Net.instance.postJson()
    .url("https://api.example.com/login")
    .addParam("username", "admin")
    .addParam("password", "123456")
    .flowResponse { raw -> Gson().fromJson(raw, LoginResult::class.java) }
    .collect { response ->
        when {
            response.isSuccessful -> {
                // 200-299：请求成功
                navigateToHome(response.body)
            }
            response.code == 401 -> {
                // 未授权
                showLoginError("用户名或密码错误")
            }
            response.code == 403 -> {
                // 禁止访问
                showError("无权限")
            }
            response.code in 500..599 -> {
                // 服务器错误，可使用 rawBody 调试
                Log.e("TAG", "服务器错误: ${response.rawBody}")
                showError("服务器繁忙，请稍后重试")
            }
            else -> {
                showError("请求失败: ${response.code}")
            }
        }
    }
```

### 4.2 headers 访问

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .flowResponse { raw -> raw }
    .collect { response ->
        val contentType = response.headers["content-type"]
        val serverTime = response.headers["date"]
        Log.d("TAG", "Content-Type: $contentType, Server-Time: $serverTime")
    }
```

---

## 5. 异常处理

### 5.1 NetFlowException

当请求失败（网络断开、超时、URL 非法等）时，Flow 会以 `NetFlowException` 关闭。可以通过 `catch` 操作符捕获：

```kotlin
import com.itg.net.flow.NetFlowException

Net.instance.get()
    .url("https://api.example.com/data")
    .flowString()
    .catch { e ->
        when (e) {
            is NetFlowException -> {
                if (e.code != null) {
                    Log.e("TAG", "HTTP 错误: ${e.code}, ${e.message}")
                } else {
                    Log.e("TAG", "网络错误: ${e.message}")
                }
            }
            else -> Log.e("TAG", "未知错误", e)
        }
    }
    .collect { body -> /* ... */ }
```

### 5.2 使用 Result 包装（不抛异常）

如果不想使用 `catch`，可以通过 Kotlin 标准库的 `runCatching` 或自定义扩展将 Flow 包装为 Result：

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .flowString()
    .catch { e -> emit("ERROR: ${e.message}") }  // 降级处理
    .collect { body ->
        if (body.startsWith("ERROR:")) {
            showError(body)
        } else {
            updateUI(body)
        }
    }
```

### 5.3 retry 操作符

Kotlin Flow 内置 `retry` 操作符，可以轻松实现失败重试：

```kotlin
Net.instance.get()
    .url("https://api.example.com/unstable")
    .flowString()
    .retry(3) { e ->
        Log.w("TAG", "请求失败，正在重试...", e)
        delay(1000)  // 延迟 1 秒后重试
        true         // 返回 true 表示需要重试
    }
    .catch { e -> showError("重试 3 次后仍失败") }
    .collect { body -> updateUI(body) }
```

---

## 6. 下载进度 Flow

### 6.1 基础下载 Flow

通过 `.flow()` 将下载任务转为 `Flow<DownloadProgress>`：

```kotlin
import com.itg.net.flow.DownloadPhase

lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .flow()
        .catch { e -> Log.e("TAG", "下载失败: ${e.message}") }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> {
                    progressBar.isIndeterminate = true
                    statusText.text = "正在连接服务器..."
                }
                DownloadPhase.Downloading -> {
                    progressBar.isIndeterminate = false
                    val percent = progress.task.downloadSize * 100L / 
                        maxOf(progress.task.contentLength, 1L)
                    progressBar.progress = percent.toInt()
                    statusText.text = "下载中 $percent%"
                }
                DownloadPhase.Complete -> {
                    progressBar.progress = 100
                    statusText.text = "下载完成！"
                }
                DownloadPhase.Failed -> {
                    statusText.text = "下载失败"
                }
            }
        }
}
```

### 6.2 DownloadProgress 结构

```kotlin
data class DownloadProgress(
    val task: Task,           // 下载任务，包含 url、path、downloadSize、contentLength 等
    val phase: DownloadPhase  // 当前阶段
)

enum class DownloadPhase {
    Connecting,    // 正在建立连接
    Downloading,   // 正在下载数据（会多次发射）
    Complete,      // 下载完成
    Failed         // 下载失败
}
```

### 6.3 Task 关键属性

| 属性 | 类型 | 说明 |
|---|---|---|
| `task.url` | `String?` | 下载地址 |
| `task.path` | `String?` | 保存路径 |
| `task.downloadSize` | `Long` | 已下载字节数 |
| `task.contentLength` | `Long` | 文件总字节数（服务器未返回时为 -1） |
| `task.append` | `Boolean` | 是否开启断点续传 |

### 6.4 下载 Flow 生命周期

```
   ┌─ Connecting ─→ Downloading ─→ Downloading ─→ ... ─→ Complete ──→ Flow 关闭
   │                                        │
   └─ Connecting ─→ Failed ─────────────────────→ Flow 关闭（抛 NetFlowException）
```

- `Connecting`: 只发射一次
- `Downloading`: 持续多次发射，直到下载完成
- `Complete`: 下载成功，发射后 Flow 自动关闭
- `Failed`: 下载失败（最终失败，不含重试中的临时失败），Flow 以 `NetFlowException` 关闭

### 6.5 取消下载

Flow 收集被取消时（如协程被取消），底层下载任务会自动取消：

```kotlin
val job = lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/large.zip")
        .url("https://example.com/large.zip")
        .flow()
        .collect { /* ... */ }
}

// 点击取消按钮
btnCancel.setOnClickListener {
    job.cancel()  // 协程取消 → Flow 关闭 → 下载任务自动取消
}
```

### 6.6 使用 flowDownload 便捷方法

```kotlin
Net.instance.flowDownload {
    savePath("${filesDir}/file.zip")
    url("https://example.com/file.zip")
    overwrite(true)
    retryCount(3)
}
    .catch { e -> showError(e.message) }
    .collect { progress -> updateUI(progress) }
```

---

## 7. 便捷扩展方法

`FlowExtensions.kt` 提供了在 `Net` 实例上直接使用的便捷扩展方法：

### 7.1 flowGet / flowPostJson / flowPostForm

```kotlin
// 便捷 GET Flow
Net.instance.flowGet {
    url("https://api.example.com/user/info")
    addParam("id", "123")
}
    .catch { e -> handleError(e) }
    .collect { body -> updateUI(body) }
```

### 7.2 flowGetResponse / flowPostJsonResponse（含反序列化）

```kotlin
Net.instance.flowGetResponse(
    converter = GsonNetConverter<User>(type = User::class.java)
) {
    url("https://api.example.com/user/1")
}
    .collect { response ->
        if (response.isSuccessful) {
            showUser(response.body)
        }
    }
```

### 7.3 完整便捷方法列表

| 方法 | 返回类型 | 说明 |
|---|---|---|
| `Net.flowGet { }` | `Flow<String>` | GET 请求 |
| `Net.flowPostJson { }` | `Flow<String>` | POST JSON 请求 |
| `Net.flowPostForm { }` | `Flow<String>` | POST Form 请求 |
| `Net.flowGetResponse<T>(converter) { }` | `Flow<NetResponse<T>>` | GET + 反序列化 |
| `Net.flowPostJsonResponse<T>(converter) { }` | `Flow<NetResponse<T>>` | POST JSON + 反序列化 |
| `Net.flowDownload { }` | `Flow<DownloadProgress>` | 下载进度 |

---

## 8. 自定义 Converter

### 8.1 实现 NetConverter 接口

如果不想用 Gson，可以实现自己的 Converter：

```kotlin
class MoshiNetConverter<T>(
    private val moshi: Moshi,
    private val type: Type
) : NetConverter<T> {
    override fun convert(raw: String?): T? {
        return raw?.let {
            val adapter = moshi.adapter<T>(type)
            adapter.fromJson(it)
        }
    }
}

// 使用
val moshi = Moshi.Builder().build()
Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(MoshiNetConverter<User>(moshi, User::class.java))
    .collect { response -> /* ... */ }
```

### 8.2 使用 Lambda（无需实现接口）

`flowResponse` 接受 `(String?) -> T?` 类型的 lambda，最简单的自定义转换：

```kotlin
// Jackson
Net.instance.get()
    .url("https://api.example.com/data")
    .flowResponse { raw -> ObjectMapper().readValue(raw, User::class.java) }

// kotlinx.serialization
Net.instance.get()
    .url("https://api.example.com/data")
    .flowResponse { raw -> raw?.let { Json.decodeFromString<User>(it) } }
```

---

## 9. 协程取消与生命周期

### 9.1 使用 lifecycleScope

推荐在 Activity/Fragment 中使用 `lifecycleScope`，页面销毁时自动取消协程：

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            Net.instance.get()
                .url("https://api.example.com/data")
                .flowString()
                .collect { body -> updateUI(body) }
        }
        // Activity 销毁时，lifecycleScope 自动取消 → Flow 关闭 → OkHttp Call 取消
    }
}
```

### 9.2 使用 viewModelScope

在 ViewModel 中推荐使用 `viewModelScope`：

```kotlin
class UserViewModel : ViewModel() {
    val userData = liveData {
        Net.instance.get()
            .url("https://api.example.com/user/1")
            .flowString()
            .collect { body -> emit(body) }
    }
}
```

### 9.3 与原有 autoCancel 的对比

| 机制 | 原有 `autoCancel(activity)` | Flow + lifecycleScope |
|---|---|---|
| 取消方式 | LifecycleEventObserver → call.cancel() | 协程取消 → awaitClose → call.cancel() |
| 粒度 | 按单个请求/Activity 绑定 | 按协程作用域统一管理 |
| 侵入性 | 每个请求需显式调用 | 自动继承 launch 所在作用域 |
| 与 ViewModel 配合 | 不支持 | viewModelScope 原生支持 |

---

## 10. API 速查表

### 请求 Flow（定义在 ParamsBuilder 扩展上）

| API | 签名 | 说明 |
|---|---|---|
| `flowString()` | `ParamsBuilder.() -> Flow<String>` | 响应体字符串 Flow |
| `flowResponse(converter)` | `ParamsBuilder.(converter) -> Flow<NetResponse<T>>` | 含反序列化的响应 Flow |

### 下载 Flow

| API | 签名 | 说明 |
|---|---|---|
| `TaskBuilder.flow()` | `TaskBuilder.() -> Flow<DownloadProgress>` | 下载进度 Flow |

### 便捷方法（定义在 Net 扩展上）

| API | 签名 | 说明 |
|---|---|---|
| `Net.flowGet { }` | `Net.(block) -> Flow<String>` | 便捷 GET |
| `Net.flowPostJson { }` | `Net.(block) -> Flow<String>` | 便捷 POST JSON |
| `Net.flowPostForm { }` | `Net.(block) -> Flow<String>` | 便捷 POST Form |
| `Net.flowGetResponse(converter) { }` | `Net.(converter, block) -> Flow<NetResponse<T>>` | 便捷 GET + 转换 |
| `Net.flowPostJsonResponse(converter) { }` | `Net.(converter, block) -> Flow<NetResponse<T>>` | 便捷 POST JSON + 转换 |
| `Net.flowDownload { }` | `Net.(block) -> Flow<DownloadProgress>` | 便捷下载 |

### 数据类型

| 类型 | 说明 |
|---|---|
| `NetResponse<T>` | 类型化响应包装（body, rawBody, code, headers, isSuccessful） |
| `NetFlowException` | Flow 异常（code, message） |
| `DownloadProgress` | 下载进度（task, phase） |
| `DownloadPhase` | 下载阶段枚举（Connecting, Downloading, Complete, Failed） |
| `NetConverter<T>` | 转换器接口 |
| `GsonNetConverter<T>` | Gson 转换器实现 |

---

## 与原有 API 共存

所有 Flow API 与原有的 `DdCallback` / `Handler` API **完全共存**，可以同时使用：

```kotlin
// 原有回调方式（仍然可用）
Net.instance.get()
    .url("https://api.example.com/data")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { /* ... */ }
        override fun onResponse(result: String?, code: Int) { /* ... */ }
    })

// Flow 方式（新增）
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .collect { body -> /* ... */ }
}
```

两种方式底层共享同一个 `OkHttpClient`（通过 `Net.instance.okhttpManager.okHttpClient`），共享所有全局配置（Base URL、全局参数、拦截器等）。
