# Net-Retrofit 使用教程

Net-Retrofit 将 [Retrofit](https://square.github.io/retrofit/) 的声明式 API 能力集成到 Net 网络库中，支持通过 Kotlin 接口 + 注解定义网络 API，自动与 Net 库的全局配置（OkHttpClient、超时、拦截器等）保持一致，并原生支持 `suspend` 函数和 `Flow` 返回类型。

---

## 目录

1. [快速开始 - 依赖引入](#1-快速开始---依赖引入)
2. [定义 Service 接口](#2-定义-service-接口)
3. [构建 NetRetrofit 实例](#3-构建-netretrofit-实例)
4. [suspend 函数请求](#4-suspend-函数请求)
5. [返回 NetResponse 包装](#5-返回-netresponse-包装)
6. [Flow 流式返回](#6-flow-流式返回)
7. [自定义 Converter](#7-自定义-converter)
8. [自定义 CallAdapter](#8-自定义-calladapter)
9. [异常处理](#9-异常处理)
10. [与 Net 全局配置共享](#10-与-net-全局配置共享)
11. [多个 Service 实例管理](#11-多个-service-实例管理)
12. [API 速查表](#12-api-速查表)

---

## 1. 快速开始 - 依赖引入

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    implementation project(':net')
    implementation project(':net-retrofit')
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
}
```

> **说明**：`net-retrofit` 通过 `api project(':net')` 和 `api project(':net-flow')` 自动传递核心库和 Flow 依赖。同时已内置 `retrofit:3.0.0` 和 `converter-gson:3.0.0`。

---

## 2. 定义 Service 接口

使用标准 Retrofit 注解定义网络 API：

```kotlin
import retrofit2.http.*

interface UserService {

    // GET 请求 + 路径参数
    @GET("user/{id}")
    suspend fun getUser(@Path("id") userId: String): User

    // POST 请求 + JSON Body
    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // 带 Query 参数
    @GET("user/list")
    suspend fun listUsers(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): List<User>

    // 带 Header
    @GET("user/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): User

    // 带 HeaderMap（动态 Header）
    @GET("user/settings")
    suspend fun getSettings(
        @HeaderMap headers: Map<String, String>
    ): Settings

    // Form 表单提交
    @FormUrlEncoded
    @POST("user/update")
    suspend fun updateProfile(
        @Field("name") name: String,
        @Field("email") email: String
    ): User

    // Multipart 上传
    @Multipart
    @POST("upload/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part
    ): UploadResult

    // DELETE 请求
    @DELETE("user/{id}")
    suspend fun deleteUser(@Path("id") userId: String): Response<Unit>
}
```

> **支持的注解**：`@GET`、`@POST`、`@PUT`、`@DELETE`、`@PATCH`、`@HEAD`、`@OPTIONS`、`@HTTP`、`@Path`、`@Query`、`@QueryMap`、`@Body`、`@Field`、`@FieldMap`、`@Part`、`@PartMap`、`@Header`、`@HeaderMap`、`@Url`、`@FormUrlEncoded`、`@Multipart`、`@Streaming`、`@Headers`

---

## 3. 构建 NetRetrofit 实例

### 3.1 通过便捷入口 Net.retrofit（推荐）

```kotlin
// 在 Application 或合适的初始化位置
val userService = Net.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

> **重要**：`baseUrl` 必须以 `/` 结尾，这与 Retrofit 原生要求一致。

### 3.2 通过 NetRetrofit.builder() 创建

```kotlin
import com.itg.net.retrofit.NetRetrofit

val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .build()

val userService = retrofit.create<UserService>()
val orderService = retrofit.create<OrderService>()
```

### 3.3 默认行为

`NetRetrofit.Builder.build()` 的默认行为：

| 配置项 | 默认值 |
|---|---|
| OkHttpClient | `Net.instance.okhttpManager.okHttpClient` |
| Converter.Factory | `GsonConverterFactory.create()`（使用默认 Gson） |
| CallAdapter.Factory | `NetFlowCallAdapterFactory()`（支持 `Flow<T>` 和 `Flow<NetResponse<T>>`） |

这意味着 Retrofit 会自动使用 Net 库中配置的拦截器、超时、缓存、日志等所有全局设置。

---

## 4. suspend 函数请求

### 4.1 基础用法

Retrofit 原生支持 `suspend` 函数，直接声明即可：

```kotlin
interface UserService {
    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): User
}

// 使用
lifecycleScope.launch {
    try {
        val user = userService.getUser("123")
        updateUI(user)  // user 是反序列化后的 User 对象
    } catch (e: Exception) {
        showError("请求失败: ${e.message}")
    }
}
```

### 4.2 返回 Retrofit Response

如果需要在 suspend 函数中获取完整的 HTTP 响应信息，可以使用 Retrofit 的 `Response<T>`：

```kotlin
interface UserService {
    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): Response<User>
}

lifecycleScope.launch {
    try {
        val response = userService.getUser("123")
        if (response.isSuccessful) {
            val user = response.body()
            val serverTime = response.headers()["date"]
            updateUI(user)
        } else {
            when (response.code()) {
                401 -> showLoginError()
                404 -> showNotFound()
                else -> showError("HTTP ${response.code()}")
            }
        }
    } catch (e: Exception) {
        showError("网络错误: ${e.message}")
    }
}
```

### 4.3 泛型类型支持

Retrofit 的 Gson Converter 支持泛型类型，自动处理 `List<T>` 等泛型参数：

```kotlin
interface UserService {
    @GET("user/list")
    suspend fun listUsers(): List<User>  // Gson 自动反序列化为 List
}

interface OrderService {
    @GET("orders")
    suspend fun getOrders(
        @Query("userId") userId: String
    ): List<Order>
}
```

---

## 5. 返回 NetResponse 包装

通过将返回类型声明为 `NetResponse<T>`，可以同时获取反序列化的 body、原始响应字符串、HTTP 状态码和响应头：

### 5.1 suspend 函数中使用 NetResponse

```kotlin
import com.itg.net.flow.NetResponse

interface UserService {
    @POST("user/login")
    suspend fun login(
        @Body request: LoginRequest
    ): NetResponse<LoginResponse>
}

lifecycleScope.launch {
    try {
        val response = userService.login(LoginRequest("admin", "123456"))
        when {
            response.isSuccessful -> {
                // response.body 的类型为 LoginResponse?
                navigateToHome(response.body)
            }
            response.code == 401 -> {
                Log.w("TAG", "登录失败: ${response.rawBody}")
                showLoginError("用户名或密码错误")
            }
            else -> {
                showError("请求失败: ${response.code}")
            }
        }
    } catch (e: Exception) {
        showError("网络错误: ${e.message}")
    }
}
```

### 5.2 NetResponse 属性

| 属性 | 类型 | 说明 |
|---|---|---|
| `body` | `T?` | 反序列化后的对象 |
| `rawBody` | `String?` | 原始响应字符串 |
| `code` | `Int` | HTTP 状态码 |
| `headers` | `Map<String, String>` | 响应头键值对 |
| `isSuccessful` | `Boolean` | 状态码是否在 200..299 |

---

## 6. Flow 流式返回

### 6.1 Flow<T> 基础用法

通过将接口方法返回类型声明为 `Flow<T>`（非 suspend），可以获取流式响应：

```kotlin
import kotlinx.coroutines.flow.Flow

interface EventService {
    // SSE 或流式 JSON 响应
    @GET("events")
    fun eventStream(): Flow<Event>
}

lifecycleScope.launch {
    eventService.eventStream()
        .catch { e -> Log.e("TAG", "事件流中断", e) }
        .collect { event ->
            Log.d("TAG", "收到事件: $event")
        }
}
```

### 6.2 Flow<NetResponse<T>>

如果需要同时访问状态码和响应头：

```kotlin
interface DataService {
    @GET("data/stream")
    fun dataStream(): Flow<NetResponse<DataChunk>>
}

lifecycleScope.launch {
    dataService.dataStream()
        .collect { response ->
            if (response.isSuccessful) {
                processData(response.body)
            } else {
                Log.w("TAG", "HTTP ${response.code}: ${response.rawBody}")
            }
        }
}
```

### 6.3 取消 Flow 与网络取消

Flow 收集被取消时，底层 Retrofit Call 自动取消：

```kotlin
val job = lifecycleScope.launch {
    eventService.eventStream()
        .collect { event -> processEvent(event) }
}

// 取消后，底层网络请求自动中断
btnStop.setOnClickListener {
    job.cancel()
}
```

### 6.4 返回类型适配规则总结

| Service 方法返回类型 | 处理方式 | 需要 NetFlowCallAdapterFactory |
|---|---|---|
| `T`（suspend） | Retrofit 内置处理 | 否 |
| `Response<T>`（suspend） | Retrofit 内置处理 | 否 |
| `NetResponse<T>`（suspend） | Retrofit 内置 + 自定义 Converter | 否 |
| `Flow<T>` | 本模块 callbackFlow 适配 | 是（默认已注册） |
| `Flow<NetResponse<T>>` | 本模块 callbackFlow 适配 | 是（默认已注册） |
| `Call<T>` | Retrofit 原生行为 | 否 |

---

## 7. 自定义 Converter

### 7.1 使用 Moshi 代替 Gson

```kotlin
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create<UserService>()
```

> **注意**：使用自定义 Converter 时需要添加对应的依赖，如 `com.squareup.retrofit2:converter-moshi:3.0.0`。

### 7.2 添加多个 Converter

```kotlin
val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .addConverterFactory(ScalarsConverterFactory.create())  // 支持 String 响应
    .build()
```

> Retrofit 会按添加顺序依次尝试 Converter，找到第一个能处理的为止。

### 7.3 自定义 Gson 配置

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .serializeNulls()
    .create()

// 注意：NetRetrofit 默认已注册 GsonConverterFactory，
// 如果使用自定义 Gson，需要自行 addConverterFactory 覆盖默认行为
val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()
```

---

## 8. 自定义 CallAdapter

### 8.1 添加 RxJava 适配器

```kotlin
val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
    .build()
```

### 8.2 保持 NetFlowCallAdapterFactory

如果添加自定义 CallAdapter 后仍想使用 Flow 返回类型，需要同时添加：

```kotlin
val retrofit = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .addCallAdapterFactory(NetFlowCallAdapterFactory())  // 支持 Flow
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())  // 支持 RxJava
    .build()
```

> **注意**：一旦调用了 `addCallAdapterFactory()`，默认的 `NetFlowCallAdapterFactory` 不会被自动注册，需要手动添加。

---

## 9. 异常处理

### 9.1 suspend 函数异常

```kotlin
lifecycleScope.launch {
    try {
        val user = userService.getUser("123")
        updateUI(user)
    } catch (e: HttpException) {
        // Retrofit 在非 2xx 响应时抛出 HttpException
        showError("HTTP ${e.code()}: ${e.message()}")
    } catch (e: IOException) {
        // 网络断开、超时等
        showError("网络连接异常: ${e.message}")
    } catch (e: Exception) {
        // 其他异常
        showError("未知错误: ${e.message}")
    }
}
```

### 9.2 Flow 异常

```kotlin
import com.itg.net.flow.NetFlowException

eventService.eventStream()
    .catch { e ->
        when (e) {
            is NetFlowException -> {
                if (e.code != null) {
                    Log.e("TAG", "HTTP 错误: ${e.code}, ${e.message}")
                } else {
                    Log.e("TAG", "网络错误: ${e.message}")
                }
            }
            is HttpException -> {
                Log.e("TAG", "Retrofit HTTP 异常: ${e.code()}")
            }
            else -> Log.e("TAG", "未知错误", e)
        }
    }
    .collect { event -> processEvent(event) }
```

### 9.3 suspend 函数使用 NetResponse 避免异常

使用 `NetResponse<T>` 作为返回类型时，即使 HTTP 状态码非 2xx 也不会抛出异常：

```kotlin
interface UserService {
    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): NetResponse<LoginResponse>
}

lifecycleScope.launch {
    try {
        val response = userService.login(LoginRequest("admin", "123456"))
        // 非 2xx 状态码也正常返回，不会抛 HttpException
        if (response.isSuccessful) {
            navigateToHome(response.body)
        } else {
            // 自己处理错误码
            showError("登录失败: ${response.rawBody}")
        }
    } catch (e: IOException) {
        // 仍然会有网络层 IOException
        showError("网络连接异常: ${e.message}")
    }
}
```

---

## 10. 与 Net 全局配置共享

### 10.1 自动继承拦截器

```kotlin
// 在 Application 中配置（一次配置，全局生效）
Net.instance.configure {
    app(this@MyApp)
    url("https://api.example.com")
    addInterceptor(authInterceptor)       // Retrofit 请求也会经过此拦截器
    addInterceptor(loggingInterceptor)     // Retrofit 请求也会经过此拦截器
    setGlobalParams("platform", "android") // Retrofit 请求中不会自动附加
    useHttpLog(true)                       // 日志记录也会生效
}

// NetRetrofit 构建的 Service 自动使用上述配置
val userService = Net.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

### 10.2 使用独立的 OkHttpClient

如果某些 Service 需要独立的超时或拦截器配置：

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .addInterceptor(specialInterceptor)
    .build()

val userService = NetRetrofit.builder()
    .baseUrl("https://api.example.com/")
    .client(customClient)  // 使用独立 OkHttpClient
    .build()
    .create<UserService>()
```

### 10.3 共享配置注意事项

| 配置项 | 自动继承 | 说明 |
|---|---|---|
| OkHttpClient（拦截器、超时、缓存） | ✅ | 通过 `Net.instance.okhttpManager.okHttpClient` |
| globalParams | ❌ | 全局参数只影响 Builder 模式请求，Retrofit 接口需自行传参 |
| Base URL（NetConfig.url） | ❌ | Retrofit 需要独立的 `baseUrl()` 设置 |
| HTTP 日志 | ✅ | 通过共享 OkHttpClient 的拦截器生效 |

---

## 11. 多个 Service 实例管理

### 11.1 共享同一 Retrofit 实例

```kotlin
val retrofit = Net.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val userService = retrofit.create<UserService>()
val orderService = retrofit.create<OrderService>()
val productService = retrofit.create<ProductService>()
```

三个 Service 共享同一个 OkHttpClient 和 Converter，推荐此方式。

### 11.2 不同 Base URL 的多个 Retrofit 实例

```kotlin
val apiRetrofit = Net.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val cdnRetrofit = NetRetrofit.builder()
    .baseUrl("https://cdn.example.com/")
    .build()

val userService = apiRetrofit.create<UserService>()
val fileService = cdnRetrofit.create<FileService>()
```

### 11.3 单例管理（推荐）

```kotlin
object ApiServices {
    val retrofit = Net.retrofit
        .baseUrl("https://api.example.com/")
        .build()

    val user: UserService by lazy { retrofit.create() }
    val order: OrderService by lazy { retrofit.create() }
    val product: ProductService by lazy { retrofit.create() }
}

// 全局使用
val user = ApiServices.user.getUser("123")
```

---

## 12. API 速查表

### NetRetrofit.Builder 方法

| 方法 | 说明 |
|---|---|
| `baseUrl(url: String)` | 设置 Base URL（必选，须以 `/` 结尾） |
| `client(client: OkHttpClient)` | 自定义 OkHttpClient（默认使用 Net 库全局 client） |
| `addConverterFactory(factory)` | 添加 Converter.Factory（默认 GsonConverterFactory） |
| `addCallAdapterFactory(factory)` | 添加 CallAdapter.Factory（默认 NetFlowCallAdapterFactory） |
| `build(): NetRetrofit` | 构建实例 |

### NetRetrofit 方法

| 方法 | 说明 |
|---|---|
| `create<T>(): T` | 创建 Service 接口的代理实例 |

### 便捷入口

| 入口 | 说明 |
|---|---|
| `Net.retrofit` | 返回 `NetRetrofit.Builder`，快速开始配置 |

### Service 支持的返回类型

| 返回类型 | 说明 | 异常行为 |
|---|---|---|
| `T` (suspend) | 直接返回反序列化对象 | 非 2xx 抛 HttpException |
| `Response<T>` (suspend) | Retrofit 原生响应包装 | 不抛 HttpException |
| `NetResponse<T>` (suspend) | Net 库响应包装（body + rawBody + 状态码 + headers） | 不抛 HttpException |
| `Flow<T>` | 流式发射反序列化对象 | 错误通过 close(cause) 传递 |
| `Flow<NetResponse<T>>` | 流式发射 Net 响应包装 | 错误通过响应体 .code 体现 |
| `Call<T>` | Retrofit 原始 Call | 需手动 enqueue |

### ProGuard / R8 规则

如果启用了代码混淆，确保以下规则已包含（`net-retrofit/consumer-rules.pro` 已默认包含）：

```proguard
# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# 保持你的数据类不被混淆
-keep class com.yourpackage.model.** { *; }
```
