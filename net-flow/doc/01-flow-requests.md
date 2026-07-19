# 01. Flow 请求

这个文档说明如何用 Flow 发起普通请求，以及如何选择错误处理模型。

## flowString

```kotlin
lifecycleScope.launch {
    Net.get()
        .url("user/profile")
        .flowString()
        .catch { e ->
            val error = e as? NetFlowException
            showError(error?.message)
        }
        .collect { body ->
            render(body)
        }
}
```

行为：

- HTTP 2xx：发射响应体字符串。
- HTTP 4xx/5xx：关闭 Flow，并抛出 `NetFlowException(code, message)`。
- 网络错误：关闭 Flow，并抛出 `NetFlowException(null, message)`。
- 响应体超过 `maxResponseBodyBytes`：关闭 Flow，并抛出 `NetFlowException`。

`flowString()` 适合简单接口。需要明确区分失败类型时，优先使用 `flowResult()`。

## flowResult

```kotlin
lifecycleScope.launch {
    Net.postJson()
        .url("order/create")
        .addJsonStr(orderJson)
        .flowResult()
        .collect { result ->
            when (result) {
                is NetResult.Success -> render(result.body)
                is NetResult.HttpError -> showError("HTTP ${result.code}: ${result.body}")
                is NetResult.ResponseTooLarge -> showError(result.message)
                is NetResult.NetworkError -> showError(result.message)
            }
        }
}
```

`flowResult()` 不抛业务错误，也不解析业务码，只处理 HTTP 层和网络层。

## flowBusinessResult

```kotlin
lifecycleScope.launch {
    Net.get()
        .url("user/profile")
        .flowBusinessResult()
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

它会复用 `net` 的 `ApiEnvelopeParser` 和业务责任链。业务拦截器返回 `Consumed` 时，Flow 发射 `BusinessResult.Consumed`，业务方不应再继续按成功处理。

## flowTypedBusinessResult

```kotlin
data class UserInfo(val id: String, val name: String)

lifecycleScope.launch {
    Net.get()
        .url("user/profile")
        .flowTypedBusinessResult<UserInfo>()
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

默认使用 `NetConfig.businessConverter` 转换 `dataRaw`。转换失败不会抛异常，而是发射 `DataConvertError`。

## flowResponse

```kotlin
lifecycleScope.launch {
    Net.get()
        .url("user/profile")
        .flowResponse { raw ->
            Gson().fromJson(raw, UserInfo::class.java)
        }
        .collect { response ->
            if (response.isSuccessful) {
                render(response.body)
            } else {
                showError("HTTP ${response.code}: ${response.rawBody}")
            }
        }
}
```

`flowResponse` 会发射 `NetResponse<T>`，不把 HTTP 4xx/5xx 转成异常或错误分支。调用方必须检查 `response.isSuccessful` 或 `response.code`。

## 取消行为

Flow 是冷流：

- 只有开始 `collect` 才会发起请求。
- collect 所在协程取消时，请求会取消。
- 请求已经完成后，Flow 正常结束。

[返回模块 README](../README.md)
