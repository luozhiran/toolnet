# net-retrofit — Retrofit 声明式 API 集成

将 Retrofit 2.9.0 的声明式 API 能力集成到 Net 网络库中，自动继承 Net 的全局 OkHttpClient（拦截器、超时、缓存等），原生支持 `suspend` 函数和 `Flow` 返回类型。

## 包含功能

- `NetRetrofit.Builder` — 快速构建 Retrofit 实例
- `Net.retrofit` 入口 — 一行代码获取 Builder
- `NetFlowCallAdapterFactory` — 支持 `Flow<T>` / `Flow<NetResponse<T>>` / `Flow<NetResult>` / `Flow<BusinessResult>` / `Flow<TypedBusinessResult<T>>` 返回类型
- 自动继承 Net 全局 OkHttpClient、拦截器、加密、监控
- 支持自定义 Converter（Moshi/Jackson/Scalars）和 CallAdapter（RxJava）

## 快速开始

```kotlin
// 依赖引入
implementation project(':net-retrofit')

// 定义 Service
interface UserService {
    @GET("user/{id}")
    suspend fun getUser(@Path("id") id: String): User

    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): NetResponse<LoginResponse>
}

// 构建实例
val userService = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()

// 使用
lifecycleScope.launch {
    val response = userService.login(LoginRequest("admin", "123456"))
    if (response.isSuccessful) navigateToHome(response.body)
}
```

## 详细文档

| 文档 | 内容 |
|---|---|
| [01. Retrofit 声明式 API](./doc/01-retrofit-basics.md) | Service 定义、构建、suspend/Flow/NetResponse |
| [02. Retrofit 高级配置](./doc/02-retrofit-advanced.md) | 自定义 Converter/CallAdapter、多 Base URL、ProGuard |

完整场景总览请查看 [项目 README](../README.md)。

## 返回类型速查

| Service 返回类型 | 非 2xx 行为 |
|---|---|
| `T` (suspend) | 抛 `HttpException` |
| `Response<T>` (suspend) | 正常返回，body() 为 null |
| `NetResponse<T>` (suspend) | 正常返回，rawBody 含错误信息 |
| `Flow<T>` | 以 `NetFlowException` 关闭 |
| `Flow<NetResponse<T>>` | 正常发送，.code 体现错误 |
| `Flow<NetResult>` | 发射 `Success` / `HttpError` / `NetworkError` |
| `Flow<BusinessResult>` | 发射业务责任链处理后的结果 |
| `Flow<TypedBusinessResult<T>>` | 业务成功时把 `data` 直接转换成 `T` |
