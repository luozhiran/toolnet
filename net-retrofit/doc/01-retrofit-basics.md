# 9. Retrofit 声明式 API（net-retrofit 模块）

如何使用 Net 库的 Retrofit 集成，通过 Kotlin 接口 + 注解定义网络 API，并利用 `suspend` 函数和 `Flow` 返回类型。

## 适用条件

- 项目已引入 `net-retrofit` 模块
- 熟悉 Retrofit 注解（`@GET`、`@POST`、`@Path`、`@Query`、`@Body` 等）
- 希望用声明式方式管理 API 接口

## 推荐做法

### 定义 Service 接口

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

### 构建 NetRetrofit 实例

#### 便捷入口（推荐）

```kotlin
val userService = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

#### 多 Service 共享实例

```kotlin
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val userService = retrofit.create<UserService>()
val orderService = retrofit.create<OrderService>()
```

#### 单例管理（推荐）

```kotlin
object ApiServices {
    val retrofit = Net.instance.retrofit
        .baseUrl("https://api.example.com/")
        .build()

    val user: UserService by lazy { retrofit.create() }
    val order: OrderService by lazy { retrofit.create() }
}
```

### 默认行为

| 配置项 | 默认值 |
|---|---|
| OkHttpClient | `Net.instance.okhttpManager.okHttpClient`（继承全部拦截器/超时/缓存） |
| Converter.Factory | `GsonConverterFactory.create()` |
| CallAdapter.Factory | `NetFlowCallAdapterFactory()`（支持 `Flow<T>` / `Flow<NetResponse<T>>`） |

### suspend 函数请求

#### 直接返回反序列化对象

```kotlin
lifecycleScope.launch {
    try {
        val user = userService.getUser("123")  // 返回 User 对象
        updateUI(user)                          // 主线程，安全
    } catch (e: HttpException) {
        showError("HTTP ${e.code()}: ${e.message()}")  // 非 2xx 抛此异常
    } catch (e: IOException) {
        showError("网络连接异常: ${e.message}")
    }
}
```

#### 返回 NetResponse（不抛 HttpException）

```kotlin
@POST("user/login")
suspend fun login(@Body request: LoginRequest): NetResponse<LoginResponse>

// 使用
val response = userService.login(LoginRequest("admin", "123456"))
if (response.isSuccessful) {
    navigateToHome(response.body)
} else {
    showError("登录失败: ${response.rawBody}")
}
```

#### 返回 Retrofit Response

```kotlin
@GET("user/{id}")
suspend fun getUser(@Path("id") id: String): Response<User>

// 使用
val response = userService.getUser("123")
when {
    response.isSuccessful -> updateUI(response.body())
    response.code() == 404 -> showNotFound()
}
```

### Flow 流式返回

```kotlin
// Flow<T>
@GET("events")
fun eventStream(): Flow<Event>

lifecycleScope.launch {
    eventService.eventStream()
        .catch { e -> Log.e("TAG", "事件流中断", e) }
        .collect { event -> processEvent(event) }
}

// Flow<NetResponse<T>>
@GET("data/stream")
fun dataStream(): Flow<NetResponse<DataChunk>>

lifecycleScope.launch {
    dataService.dataStream()
        .collect { response ->
            if (response.isSuccessful) processData(response.body)
        }
}
```

## 返回类型对比

| Service 返回类型 | 非 2xx 行为 | 需要 CallAdapter |
|---|---|---|
| `T` (suspend) | 抛 `HttpException` | Retrofit 内置 |
| `Response<T>` (suspend) | 正常返回，body() 为 null | Retrofit 内置 |
| `NetResponse<T>` (suspend) | 正常返回，rawBody 含错误信息 | Retrofit 内置 |
| `Flow<T>` | 以 `NetFlowException` 关闭 | `NetFlowCallAdapterFactory` |
| `Flow<NetResponse<T>>` | 正常发送，.code 体现错误 | `NetFlowCallAdapterFactory` |
| `Call<T>` | Retrofit 原生 | 否 |

## 关键说明

- `baseUrl` 必须以 `/` 结尾，与 Retrofit 原生要求一致
- Retrofit Service 自动使用 Net 库中配置的 OkHttpClient（包含所有拦截器、超时、缓存等）
- `globalParams` 不会自动附加到 Retrofit 请求（需在接口中自行添加 `@Query` 参数）
- `NetResponse<T>` 作为返回类型时，非 2xx 不会抛异常，可通过 `response.code` 自行判断
- `Flow<T>` 和 `Flow<NetResponse<T>>` 依赖 `NetFlowCallAdapterFactory`（默认已注册）

## 验证方式

- 编译通过并确认 Service 接口方法可正常调用
- 在 Retrofit Service 中设置断点确认 OkHttpClient 与全局配置一致
- 测试 `NetResponse<T>` 在非 2xx 时不抛异常

[返回 README](../../README.md)
