# 02. Retrofit 进阶

这个文档说明 Retrofit Flow 返回类型、自定义 Converter、CallAdapter 和容易误解的行为。

## 支持的 Flow 返回类型

```kotlin
interface ApiService {
    @GET("text")
    fun text(): Flow<String>

    @GET("user/profile")
    fun profileResponse(): Flow<NetResponse<UserInfo>>

    @GET("user/profile")
    fun profileNetResult(): Flow<NetResult>

    @GET("user/profile")
    fun profileBusiness(): Flow<BusinessResult>

    @GET("user/profile")
    fun profileTypedBusiness(): Flow<TypedBusinessResult<UserInfo>>
}
```

## Flow<T>

`Flow<T>` 适合只关心 HTTP 2xx 成功体的接口。

- HTTP 2xx 且 body 不为空：发射 `T`。
- HTTP 4xx/5xx：关闭 Flow，并抛出 `NetFlowException(code, message)`。
- 网络错误：关闭 Flow，并抛出 `NetFlowException(null, message)`。

```kotlin
lifecycleScope.launch {
    apiService.text()
        .catch { e -> showError(e.message) }
        .collect { text -> render(text) }
}
```

## Flow<NetResponse<T>>

`Flow<NetResponse<T>>` 会保留 HTTP 状态码、响应头和原始 body。

```kotlin
lifecycleScope.launch {
    apiService.profileResponse()
        .collect { response ->
            if (response.isSuccessful) {
                render(response.body)
            } else {
                showError("HTTP ${response.code}: ${response.rawBody}")
            }
        }
}
```

注意：

- HTTP 4xx/5xx 不会抛异常，会发射 `NetResponse(body = null, code = ...)`。
- 响应体超过 `maxResponseBodyBytes` 时，`rawBody` 为 null。调用方应把它当作“大响应被保护性截断”处理。

## Flow<NetResult>

```kotlin
lifecycleScope.launch {
    apiService.profileNetResult()
        .collect { result ->
            when (result) {
                is NetResult.Success -> render(result.body)
                is NetResult.HttpError -> showError("HTTP ${result.code}")
                is NetResult.ResponseTooLarge -> showError(result.message)
                is NetResult.NetworkError -> showError(result.message)
            }
        }
}
```

这是最适合统一处理 HTTP、网络错误和响应体上限的 Retrofit 返回类型。

## Flow<BusinessResult>

```kotlin
lifecycleScope.launch {
    apiService.profileBusiness()
        .collect { result ->
            when (result) {
                is BusinessResult.Success -> render(result.dataRaw)
                is BusinessResult.BusinessError -> showError(result.message)
                is BusinessResult.HttpError -> showError("HTTP ${result.httpCode}")
                is BusinessResult.ResponseTooLarge -> showError("响应过大")
                is BusinessResult.NetworkError -> showError(result.rawBody)
                is BusinessResult.InterceptorError -> showError(result.error.message)
                is BusinessResult.Consumed -> Unit
            }
        }
}
```

这个类型会复用 `NetConfig.businessEnvelopeParser` 和业务责任链。登录失效、权限不足等业务码建议在责任链统一处理。

## Flow<TypedBusinessResult<T>>

```kotlin
lifecycleScope.launch {
    apiService.profileTypedBusiness()
        .collect { result ->
            when (result) {
                is TypedBusinessResult.Success -> render(result.data)
                is TypedBusinessResult.DataConvertError -> showError("data 解析失败")
                is TypedBusinessResult.BusinessError -> showError(result.message)
                is TypedBusinessResult.HttpError -> showError("HTTP ${result.httpCode}")
                is TypedBusinessResult.ResponseTooLarge -> showError("响应过大")
                is TypedBusinessResult.NetworkError -> showError(result.rawBody)
                is TypedBusinessResult.InterceptorError -> showError(result.error.error.message)
                is TypedBusinessResult.Consumed -> Unit
            }
        }
}
```

成功时会把业务信封里的 `dataRaw` 转成 `T`。转换失败会发射 `DataConvertError`，不会抛出异常。

## 自定义 Converter

```kotlin
val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(MoshiConverterFactory.create())
    .build()
    .create<ApiService>()
```

当前行为：

- 自定义 Converter 会先注册，优先处理响应。
- 如果你没有添加 `GsonConverterFactory`，库会追加默认 Gson 兜底。
- 因此添加 Moshi 不会导致默认 Gson 静默消失。

## 自定义 CallAdapter

```kotlin
val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
    .build()
    .create<ApiService>()
```

当前行为：

- 自定义 CallAdapter 会先注册。
- 如果你没有添加 `NetFlowCallAdapterFactory`，库会追加默认 Flow 适配器。
- 因此添加 RxJava 适配器不会导致 `Flow<NetResult>` 等能力失效。

## 容易误解的点

- `NetConfig.globalParam(...)` 不会自动变成 Retrofit 注解参数。Retrofit 接口里仍需要按 Retrofit 规则声明 `@Query`、`@Header`、`@Body`。
- 普通 `suspend fun` 不会自动返回 `NetResult` 或 `BusinessResult`。要使用统一结果模型，请声明 Flow 返回类型。
- `Flow<NetResponse<T>>` 不等于业务成功模型，它只表达 HTTP 响应。业务码请用 `Flow<BusinessResult>` 或 `Flow<TypedBusinessResult<T>>`。

[返回模块 README](../README.md)
