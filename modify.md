# 为 Net 库增加 Retrofit 声明式 API 和 Kotlin Flow 流式调用 —— 整改方案

## Context

当前 `net` 库基于 OkHttp 4.9.2，提供 Builder 模式（Get/PostJson/PostForm/PostFile/PostMultipart/PostContent）和 Download 模块，所有请求通过 `DdCallback` 回调或 `Handler` 返回原始 `String`。缺乏以下能力：

1. **声明式 API 定义**：不能像 Retrofit 一样用接口 + 注解方式定义网络 API
2. **Kotlin Flow / 协程支持**：不支持 `suspend` 函数和 `Flow` 流式调用，无法利用结构化并发
3. **自动反序列化**：响应始终是 `String`，没有类型安全的 `Response<T>` 和 Converter 机制
4. **下载进度流**：下载进度只能通过回调，不能转为 `Flow<DownloadProgress>`

目标：在不破坏现有 API 的前提下，增加以上四项能力。

---

## 总体架构设计

### 方案选择：新增独立可选模块

| 模块 | 说明 |
|---|---|
| `net` (现有) | 核心库，保持不变，新增少量 Flow 扩展函数 |
| `net-retrofit` (新增) | Retrofit 声明式 API 封装，依赖 `net` + Retrofit |
| `net-flow` (新增) | Kotlin Flow 流式封装，依赖 `net` + kotlinx-coroutines |

**理由**：
- 核心库保持轻量，不引入 kotlinx-coroutines / Retrofit 等重量级依赖
- 用户按需引入：只用 Flow 引入 `net-flow`，只用声明式 API 引入 `net-retrofit`
- 两个新模块都通过 `api project(':net')` 传递核心库依赖

### 整体模块依赖关系

```
app
├── net-retrofit  (可选)
│   ├── net-flow   (可选，用于 suspend/Flow adapter)
│   │   └── net   (核心库)
│   └── net       (核心库)
├── net-flow      (可选)
│   └── net      (核心库)
└── net           (核心库，必选)
```

---

## Feature 1: net-flow 模块 —— Kotlin Flow 流式调用

### 1.1 新增依赖

`net-flow/build.gradle` 新增：
```groovy
dependencies {
    api project(':net')
    api 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4'
    api 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
}
```

### 1.2 核心类型设计

#### NetResponse<T> —— 类型化响应包装

**文件**: `net-flow/src/main/java/com/itg/net/flow/NetResponse.kt`

```kotlin
data class NetResponse<T>(
    val body: T?,
    val rawBody: String?,
    val code: Int,
    val headers: Map<String, String>,
    val isSuccessful: Boolean get() = code in 200..299
)
```

设计要点：
- `body` 是反序列化后的类型 `T`（当不需要反序列化时为 `String`）
- `rawBody` 始终保留原始字符串，用于调试和 fallback
- `code` 为 HTTP 状态码
- `isSuccessful` 为便捷判断属性

#### NetFlowException —— 类型化异常

**文件**: `net-flow/src/main/java/com/itg/net/flow/NetFlowException.kt`

```kotlin
class NetFlowException(
    val code: Int?,
    override val message: String?
) : IOException(message)
```

用于在 Flow 中 throw，携带 HTTP 状态码信息。

### 1.3 Flow 扩展函数设计

#### 针对普通请求（SentBuilder 扩展）

**文件**: `net-flow/src/main/java/com/itg/net/flow/RequestFlow.kt`

```kotlin
/**
 * 将 DdCallback 模式的请求转为 Flow<String>
 * 使用 callbackFlow 桥接
 */
fun <T : ParamsBuilder> T.flowString(): Flow<String>

/**
 * 将请求转为 Flow<NetResponse<T>>，支持反序列化
 * @param converter 将 String 转为 T 的转换器
 */
fun <T> ParamsBuilder.flowResponse(
    converter: (String?) -> T?
): Flow<NetResponse<T>>
```

核心实现思路（`callbackFlow` 桥接）：
```
1. 调用 sendTool.combineParamsAndRCall() 构建 OkHttp Call
2. 通过 call.enqueue() 发起请求
3. onResponse → trySend / trySendBlocking 发送数据并 close
4. onFailure → close(cause = NetFlowException)
5. awaitClose { call.cancel() } 确保 Flow 收集取消时取消 OkHttp 请求
```

#### SentBuilder 新增 send 重载（核心库改动）

**文件**: `net/src/main/java/com/itg/net/request/base/SentBuilder.kt` 新增方法：

```kotlin
// 新增：返回 OkHttp Call 对象，供 Flow 层使用
fun buildCall(): Call?
```

这样 Flow 层可以通过 `buildCall()` 获取 Call 并自行管理生命周期。

#### 针对下载（TaskBuilder 扩展）

**文件**: `net-flow/src/main/java/com/itg/net/flow/DownloadFlow.kt`

```kotlin
// 下载进度数据类
data class DownloadProgress(
    val task: Task,
    val phase: DownloadPhase  // Connecting, Downloading, Complete, Failed
)

enum class DownloadPhase { Connecting, Downloading, Complete, Failed }

/**
 * 将下载任务转为 Flow<DownloadProgress>
 */
fun TaskBuilder.flow(): Flow<DownloadProgress>
```

核心实现思路：
```
1. 在 TaskBuilder.start() 之前，注入一个自定义 IProgressCallback
2. 该 callback 通过 Channel 将事件发送给 Flow
3. onConnecting → trySend(DownloadProgress(Connecting))
4. onProgress → trySend(DownloadProgress(Downloading/Complete))
5. onFail → close(cause = NetFlowException)
6. awaitClose { cancelDownload(task) }
```

**需要 TaskBuilder 新增钩子**（在核心 `net` 模块）：

**文件**: `net/src/main/java/com/itg/net/download/TaskBuilder.kt` 新增：
```kotlin
// 内部方法，供 Flow 模块设置自定义进度回调
internal fun setInternalProgressCallback(callback: IProgressCallback): TaskBuilder
```

---

## Feature 2: net-retrofit 模块 —— Retrofit 声明式 API

### 2.1 新增依赖

`net-retrofit/build.gradle` 新增：
```groovy
dependencies {
    api project(':net')
    api project(':net-flow')  // 可选，用于提供 suspend/Flow 支持
    api 'com.squareup.retrofit2:retrofit:2.9.0'
    api 'com.squareup.retrofit2:converter-gson:2.9.0'  // 默认 Converter
    // compileOnly 方式，用户自行选择 Converter (Gson/Moshi/Jackson)
}
```

### 2.2 核心类设计

#### NetRetrofit —— Retrofit 构建器

**文件**: `net-retrofit/src/main/java/com/itg/net/retrofit/NetRetrofit.kt`

```kotlin
class NetRetrofit private constructor(
    val retrofit: Retrofit
) {
    companion object {
        fun builder(): Builder
    }
    
    class Builder {
        private var baseUrl: String? = null
        private var okHttpClient: OkHttpClient? = null
        private val converterFactories = mutableListOf<Converter.Factory>()
        private val callAdapterFactories = mutableListOf<CallAdapter.Factory>()
        
        fun baseUrl(url: String): Builder
        fun client(client: OkHttpClient): Builder
        fun addConverterFactory(factory: Converter.Factory): Builder
        fun addCallAdapterFactory(factory: CallAdapter.Factory): Builder
        fun build(): NetRetrofit
    }
    
    inline fun <reified T> create(): T
}
```

设计要点：
- 默认使用 `Net.instance.okhttpManager.okHttpClient` 作为 OkHttpClient，保证与现有配置一致
- 默认注册 GsonConverterFactory
- 如果引入了 `net-flow`，默认注册 `NetFlowCallAdapterFactory`
- `create<T>()` 通过 Retrofit 的 `retrofit.create(T::class.java)` 创建代理

#### NetFlowCallAdapterFactory —— 让 Retrofit 支持 suspend 和 Flow

**文件**: `net-retrofit/src/main/java/com/itg/net/retrofit/NetFlowCallAdapterFactory.kt`

这是 Retrofit 的 `CallAdapter.Factory`，将 Retrofit 的 `Call<T>` 适配为 Kotlin 的 `suspend` 函数返回值或 `Flow<T>`。

```kotlin
class NetFlowCallAdapterFactory : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>?
}
```

适配规则：
| 接口返回类型 | 适配行为 |
|---|---|
| `T` (suspend) | 内部调用 `call.enqueue()`，success → return body, failure → throw exception |
| `NetResponse<T>` (suspend) | 类似上面，返回 `NetResponse<T>` 包装 |
| `Flow<T>` | 使用 callbackFlow 桥接，每次 emission 发送数据块（流式） |
| `Flow<NetResponse<T>>` | callbackFlow 桥接，发送完整的响应包装 |
| `Call<T>` (原始 Retrofit) | 保持 Retrofit 原生行为 |

#### NetService 注解支持

用户定义的 Service 接口使用标准 Retrofit 注解：

```kotlin
interface UserService {
    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): NetResponse<User>
    
    @POST("user/login")  
    suspend fun login(@Body body: LoginRequest): NetResponse<LoginResponse>
    
    @GET("events")
    fun eventStream(): Flow<NetResponse<Event>>  // 流式响应
    
    @Streaming
    @GET("download/file")
    fun downloadFile(): Flow<ByteArray>  // 流式二进制下载
}
```

### 2.3 便捷入口

**方案 A（推荐）：在 Net 单例上增加扩展属性**

在 `net-retrofit` 模块中：

```kotlin
// Net.kt 扩展
val Net.retrofit: NetRetrofit.Builder
    get() = NetRetrofit.builder()
```

使用示例：
```kotlin
val userService = Net.retrofit
    .baseUrl("https://api.example.com")
    .addConverterFactory(MoshiConverterFactory.create())
    .build()
    .create<UserService>()
```

**方案 B：Net 类新增方法（改动核心 net 模块，不推荐）**

不推荐，因为会在核心模块引入对 Retrofit 的认知。

---

## Feature 3: 核心 net 模块所需的最小改动

为了支持 `net-flow` 和 `net-retrofit` 模块，核心 `net` 模块需要少量改动：

### 3.1 SentBuilder 新增 buildCall()

**文件**: `net/src/main/java/com/itg/net/request/base/SentBuilder.kt`

```kotlin
interface SentBuilder {
    // 现有方法保持不变
    fun send(callback: DdCallback?)
    fun send(handler: Handler?, what: Int, errorWhat: Int)
    fun send(response: Callback?, task: Task?) {}
    
    // 新增：构建 OkHttp Call 但不执行，由调用方管理生命周期
    fun buildCall(): Call? { return null }  // 默认实现，各子类 override
}
```

### 3.2 各请求类实现 buildCall()

在所有最终请求类中实现 `buildCall()`（模式一致，以 Get 为例）：

**文件**: `net/src/main/java/com/itg/net/request/get/Get.kt`
```kotlin
override fun buildCall(): Call? {
    return sendTool.combineParamsAndRCall(
        getHeader(), getUrl(), tag, null, cacheControl
    ) { builder -> builder.get() }
}
```

**同样需要在以下文件中实现**：
- `net/src/main/java/com/itg/net/request/post/json/PostJson.kt`
- `net/src/main/java/com/itg/net/request/post/form/PostForm.kt`
- `net/src/main/java/com/itg/net/request/post/file/PostFile.kt`
- `net/src/main/java/com/itg/net/request/post/multipart/PostMul.kt`
- `net/src/main/java/com/itg/net/request/post/content/PostContent.kt`
- `net/src/main/java/com/itg/net/request/post/file/PostResumeFile.kt`

**重构机会**：每个请求的 `send(DdCallback)` 实现都是：
1. 调用 `combineParamsAndRCall(...)` 
2. 调用 `sendTool.send(callback, call)`

可以重构为：`send(callback)` 内部调用 `buildCall()` 然后再 `sendTool.send(callback, call)`，消除重复代码。

但这是 nice-to-have，不影响功能实现。

### 3.3 TaskBuilder 暴露内部回调设置能力

**文件**: `net/src/main/java/com/itg/net/download/TaskBuilder.kt`

```kotlin
// 新增 internal 方法，供 net-flow 模块在 start() 前注入自定义回调
internal fun setProgressCallback(callback: IProgressCallback): TaskBuilder {
    task.progressCallback = callback
    return this
}
```

### 3.4 新增下载进度 Flow 支持所需的回调转发

**文件**: `net/src/main/java/com/itg/net/download/callback/IProgressCallback.kt`

无需修改，现有接口已足够覆盖 Flow 需要的所有事件类型。

---

## Feature 4: JSON 序列化 / 反序列化支持

### 4.1 Converter 抽象

**文件**: `net-flow/src/main/java/com/itg/net/flow/converter/NetConverter.kt`

```kotlin
interface NetConverter<T> {
    fun convert(raw: String?): T?
}
```

### 4.2 内置 GsonConverter

**文件**: `net-flow/src/main/java/com/itg/net/flow/converter/GsonNetConverter.kt`

```kotlin
class GsonNetConverter<T>(
    private val gson: Gson = Gson(),
    private val type: Type
) : NetConverter<T> {
    override fun convert(raw: String?): T? {
        return raw?.let { gson.fromJson(it, type) }
    }
}
```

### 4.3 使用示例

```kotlin
// Flow + 反序列化
Net.instance.get()
    .url("https://api.example.com/user/1")
    .flowResponse(GsonNetConverter<User>(type = User::class.java))
    .catch { e -> /* 处理异常 */ }
    .collect { response ->
        if (response.isSuccessful) {
            val user: User? = response.body
        }
    }
```

---

## 文件清单 & 改动汇总

### 新增文件

#### net-flow 模块
```
net-flow/
├── build.gradle                                      # 新增
└── src/main/java/com/itg/net/flow/
    ├── NetResponse.kt                                # 新增
    ├── NetFlowException.kt                           # 新增
    ├── RequestFlow.kt                                # 新增 (核心：callbackFlow 桥接)
    ├── DownloadFlow.kt                               # 新增 (下载进度 Flow)
    ├── FlowExtensions.kt                             # 新增 (便捷扩展函数)
    └── converter/
        ├── NetConverter.kt                           # 新增
        └── GsonNetConverter.kt                       # 新增
```

#### net-retrofit 模块
```
net-retrofit/
├── build.gradle                                      # 新增
└── src/main/java/com/itg/net/retrofit/
    ├── NetRetrofit.kt                                # 新增
    ├── NetFlowCallAdapterFactory.kt                  # 新增
    └── NetApi.kt                                     # 新增 (常用注解别名/便捷方法)
```

### 修改文件（核心 net 模块）

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `net/src/main/java/.../request/base/SentBuilder.kt` | 新增方法 | `buildCall(): Call?` 默认实现 |
| `net/src/main/java/.../request/get/Get.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/json/PostJson.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/form/PostForm.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/file/PostFile.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/multipart/PostMul.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/content/PostContent.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../request/post/file/PostResumeFile.kt` | 实现接口 | `override fun buildCall()` |
| `net/src/main/java/.../download/TaskBuilder.kt` | 新增方法 | `internal fun setProgressCallback()` |

### 修改文件（工程配置）

| 文件 | 改动 |
|---|---|
| `settings.gradle` | 添加 `include ':net-flow'` 和 `include ':net-retrofit'` |
| `build.gradle` (root) | 可能需要升级 Kotlin 版本（1.6.21 → 1.8.x，以支持更好的协程特性） |

---

## 使用示例

### 场景 1：Flow 化现有请求

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .catch { e -> Log.e("TAG", "请求失败", e) }
        .collect { responseBody -> 
            // responseBody 是 String
        }
}
```

### 场景 2：Flow + 反序列化

```kotlin
lifecycleScope.launch {
    Net.instance.postJson()
        .url("https://api.example.com/user/login")
        .addParam("username", "admin")
        .addParam("password", "123456")
        .flowResponse(GsonNetConverter<LoginResponse>(type = LoginResponse::class.java))
        .catch { e -> /* 异常处理 */ }
        .collect { response ->
            when {
                response.isSuccessful -> navigateToHome(response.body)
                response.code == 401 -> showLoginError()
            }
        }
}
```

### 场景 3：下载进度 Flow

```kotlin
lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .flow()
        .catch { e -> Log.e("TAG", "下载失败", e) }
        .collect { progress ->
            when (progress.phase) {
                DownloadPhase.Connecting -> showConnecting()
                DownloadPhase.Downloading -> updateProgressBar(progress.task)
                DownloadPhase.Complete -> onDownloadComplete(progress.task)
                DownloadPhase.Failed -> onDownloadFailed(progress.task)
            }
        }
}
```

### 场景 4：Retrofit 声明式 API

```kotlin
// 定义 Service
interface ApiService {
    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): NetResponse<User>
    
    @POST("user/login")
    suspend fun login(@Body body: LoginRequest): NetResponse<LoginResponse>
}

// 初始化（在 Application 中）
val apiService = Net.retrofit
    .baseUrl("https://api.example.com")
    .build()
    .create<ApiService>()

// 使用
lifecycleScope.launch {
    try {
        val response = apiService.login(LoginRequest("admin", "123456"))
        if (response.isSuccessful) {
            updateUI(response.body)
        }
    } catch (e: Exception) {
        showError(e.message)
    }
}
```

---

## 实施顺序建议

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 1 | 核心 net 模块微调（SentBuilder.buildCall, TaskBuilder.setProgressCallback） | 无 |
| 2 | 新建 net-flow 模块，实现 RequestFlow + DownloadFlow + NetResponse | 阶段 1 |
| 3 | 新建 net-retrofit 模块，实现 NetRetrofit + CallAdapterFactory | 阶段 2 |
| 4 | app 模块集成演示 | 阶段 3 |
| 5 | 单元测试 + 集成测试 | 阶段 4 |

---

## 风险与注意事项

1. **Kotlin 版本**：当前 Kotlin 1.6.21，建议升级到 1.8.x 以获得更好的协程支持（`callbackFlow` 在 1.6 上可用但 1.8 更稳定）
2. **OkHttp 版本**：4.9.2 与 Retrofit 2.9.0 兼容，不需要升级
3. **AndroidX Lifecycle**：`callbackFlow` + `lifecycleScope` 自动处理生命周期，比现有 WeakHashMap 方案更优雅
4. **ProGuard/R8**：新增 Retrofit 后需要添加对应的 ProGuard 规则
5. **模块间依赖**：`net-flow` 作为 `net-retrofit` 的可选依赖，当用户只使用 Retrofit 不用 Flow 时不应强制引入
6. **向后兼容**：所有现有 `DdCallback` 和 `Handler` 的 `send()` 方法保持不变，新 API 是额外增加的

---

## 验证方案

1. **单元测试**：
   - `RequestFlowTest`：Mock OkHttp Call，验证 callbackFlow 的正确桥接和取消行为
   - `DownloadFlowTest`：Mock Task/Download，验证下载进度 Flow 的事件顺序
   - `NetResponseTest`：验证各种 HTTP 状态码下 isSuccessful 逻辑

2. **集成测试**：
   - 在 app 模块中添加 Flow 请求示例 Activity
   - 在 app 模块中添加 Retrofit Service 使用示例
   - 验证协程取消时 OkHttp Call 是否被正确 cancel

3. **手动验证场景**：
   - Activity 销毁时 Flow 收集取消 → OkHttp Call 取消
   - 下载中断后 Flow 重试
   - 网络异常时 Flow 的 catch 处理
