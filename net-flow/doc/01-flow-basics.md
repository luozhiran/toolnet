# 7. Flow 化请求（net-flow 模块）

如何将 Net 库的请求转为 Kotlin Flow，利用协程的结构化并发、背压和自动取消能力。

## 适用条件

- 项目已引入 `net-flow` 模块
- 使用 Kotlin 协程（`lifecycleScope` / `viewModelScope`）
- 需要将回调风格的请求转为 Flow 风格

## 推荐做法

### flowString() —— 响应体字符串 Flow

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .catch { e -> Log.e("TAG", "请求失败: ${e.message}") }
        .collect { body -> updateUI(body) }
}
```

`.flowString()` 定义在 `ParamsBuilder` 上，**所有请求类型**均可使用：

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

`flowString()` 只适合“成功时拿响应字符串”的简单场景。HTTP 状态码不是 `2xx` 时，Flow 会以 `NetFlowException(code, message)` 结束，并进入 `catch`。

### flowResult() —— 结构化区分成功、HTTP 错误、网络异常

如果调用方需要明确区分 `4xx/5xx` 和断网/超时，推荐使用 `flowResult()`：

```kotlin
import com.itg.net.flow.flowResult
import com.itg.net.request.result.NetResult

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/info")
        .flowResult()
        .collect { result ->
            when (result) {
                is NetResult.Success -> updateUI(result.body)
                is NetResult.HttpError -> showError("HTTP ${result.code}: ${result.body}")
                is NetResult.NetworkError -> showError("网络不可用: ${result.message}")
            }
        }
}
```

`NetResult.HttpError` 表示服务器已经返回 HTTP 响应，但状态码不是成功；`NetResult.NetworkError` 表示没有拿到 HTTP 响应，例如断网、超时、DNS 失败。

更完整的错误处理策略见 [net 09. HTTP 错误与网络异常处理](../../net/doc/09-error-handling.md)。

### flowBusinessResult() —— 支持业务码责任链

如果 HTTP 2xx 响应体里还有业务码，例如 `code=401001` 表示登录失效，使用 `flowBusinessResult()`：

```kotlin
import com.itg.net.flow.flowBusinessResult
import com.itg.net.request.business.BusinessResult

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/info")
        .flowBusinessResult()
        .collect { result ->
            when (result) {
                is BusinessResult.Success -> updateUI(result.dataRaw)
                is BusinessResult.BusinessError -> showError(result.message)
                is BusinessResult.HttpError -> showError("HTTP ${result.httpCode}")
                is BusinessResult.NetworkError -> showError("网络不可用")
                is BusinessResult.Consumed -> Unit
            }
        }
}
```

业务码解析器和责任链在 `Net.instance.configure { }` 中配置，详见 [net 09. HTTP 错误与网络异常处理](../../net/doc/09-error-handling.md)。

### 与 DdCallback 对比

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

### Flow + 反序列化

#### flowResponse(lambda)

```kotlin
data class User(val id: Int, val name: String)

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/1")
        .flowResponse { raw -> Gson().fromJson(raw, User::class.java) }
        .collect { response ->
            if (response.isSuccessful) {
                updateUI(response.body)  // response.body 类型为 User?
            }
        }
}
```

#### GsonNetConverter

```kotlin
import com.itg.net.flow.converter.GsonNetConverter

Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(GsonNetConverter<User>(type = User::class.java))
    .collect { response -> /* response.body: User? */ }
```

#### 自定义 Gson 配置

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .create()

Net.instance.get()
    .url("https://api.example.com/orders")
    .flowResponse(GsonNetConverter<OrderList>(gson = gson, type = OrderList::class.java))
    .collect { /* ... */ }
```

#### 自定义 Converter

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

### retry 重试

```kotlin
Net.instance.get()
    .url("https://api.example.com/unstable")
    .flowString()
    .retry(3) { e ->
        Log.w("TAG", "重试中...", e)
        delay(1000)
        true  // 返回 true 表示需要重试
    }
    .collect { body -> updateUI(body) }
```

## NetResponse 类型详解

```kotlin
data class NetResponse<T>(
    val body: T?,                       // 反序列化后的对象
    val rawBody: String?,               // 原始响应字符串
    val code: Int,                      // HTTP 状态码
    val headers: Map<String, String>,    // 响应头
    val isSuccessful: Boolean           // code ∈ 200..299
)
```

按状态码处理：

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

## NetFlowException

当请求失败时，Flow 以 `NetFlowException` 关闭。网络断开、超时等异常的 `code` 为 `null`；HTTP 非 2xx 的 `code` 为服务器返回的状态码：

```kotlin
class NetFlowException(
    val code: Int?,           // HTTP 状态码（非 HTTP 错误为 null）
    override val message: String?
) : IOException(message)
```

## 便捷扩展方法

`FlowExtensions.kt` 提供在 `Net` 实例上直接使用的便捷方法：

```kotlin
// 便捷 GET
Net.instance.flowGet {
    url("https://api.example.com/user/info")
    addParam("id", "123")
}.collect { body -> updateUI(body) }

// 便捷 GET + 反序列化
Net.instance.flowGetResponse(GsonNetConverter<User>(type = User::class.java)) {
    url("https://api.example.com/user/1")
}.collect { response -> /* ... */ }
```

| 方法 | 返回类型 | 说明 |
|---|---|---|
| `Net.flowGet { }` | `Flow<String>` | GET 请求 |
| `Net.flowPostJson { }` | `Flow<String>` | POST JSON 请求 |
| `Net.flowPostForm { }` | `Flow<String>` | POST Form 请求 |
| `ParamsBuilder.flowResult()` | `Flow<NetResult>` | 区分 2xx、4xx/5xx、网络异常 |
| `Net.flowGetResponse(converter) { }` | `Flow<NetResponse<T>>` | GET + 反序列化 |
| `Net.flowPostJsonResponse(converter) { }` | `Flow<NetResponse<T>>` | POST JSON + 反序列化 |
| `Net.flowDownload { }` | `Flow<DownloadProgress>` | 下载进度 |

## 关键说明

- `callbackFlow` 内部使用 `Channel` 作为桥梁，后台线程通过 `trySend()` 写入
- `lifecycleScope.launch` 默认使用 `Dispatchers.Main`，所以 Flow collect 在主线程执行
- 协程取消会自动触发 `awaitClose { call.cancel() }`，无需手动管理请求取消
- 每个请求的 Flow 只发射一次（下载 Flow 除外），发射后自动关闭
- Flow API 与原有的 `DdCallback` / `Handler` API **完全共存**，底层共享同一个 `OkHttpClient`

## 验证方式

- 在 Activity 中发起 Flow 请求，`finish()` 后确认协程取消且 OkHttp Call 被取消
- 通过 Log 确认 `collect` 在主线程执行（可直接更新 UI）
- 使用 `retry` 操作符模拟网络失败后的重试行为

[返回 README](../../README.md)
