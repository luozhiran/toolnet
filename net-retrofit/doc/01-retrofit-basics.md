# 01. Retrofit 基础

这个文档说明如何用 `net-retrofit` 定义声明式网络接口。

## 初始化 Net

```kotlin
Net.configure {
    application(app)
    baseUrl("https://api.example.com/")
    enableHttpLog(BuildConfig.DEBUG)
}
```

`NetRetrofit` 默认复用 `Net.instance` 中的 OkHttpClient，所以拦截器、超时、缓存、加密、监控等底层能力都来自 `net` 的配置。

## 定义 Service

```kotlin
interface UserService {
    @GET("user/profile")
    suspend fun profile(): UserInfo

    @POST("user/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("user/profile")
    fun profileResult(): Flow<NetResult>
}
```

## 创建实例

```kotlin
val userService = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

`baseUrl` 必须以 `/` 结尾，这是 Retrofit 的要求。

## suspend 接口

```kotlin
lifecycleScope.launch {
    try {
        val user = userService.profile()
        render(user)
    } catch (e: HttpException) {
        showError("HTTP ${e.code()}")
    } catch (e: IOException) {
        showError(e.message)
    }
}
```

普通 `suspend fun` 使用 Retrofit 原生行为：

- HTTP 2xx：返回反序列化后的对象。
- HTTP 4xx/5xx：抛 `HttpException`。
- 网络错误：抛 `IOException`。

如果你想使用 `NetResult`、`BusinessResult` 或 `ResponseTooLarge` 这类统一结果模型，请声明为 Flow 返回类型，见 [Retrofit 进阶](./02-retrofit-advanced.md)。

## 自定义 OkHttpClient

```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()

val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .client(client)
    .build()
    .create<UserService>()
```

传入 `client(...)` 后，这个 Retrofit 实例会使用该 client。是否包含日志、加密、监控、业务自己的拦截器，取决于你传入的 client。

[返回模块 README](../README.md)
