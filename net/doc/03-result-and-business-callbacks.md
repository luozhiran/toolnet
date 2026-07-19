# 03. 结果和业务错误

这个文档说明 `NetResult`、`BusinessResultCallback`、业务责任链和类型化业务结果的区别。

## 先选结果模型

| 目标 | 推荐 API | 返回模型 | 适用场景 |
| --- | --- | --- | --- |
| 只关心 HTTP 和网络错误 | `sendResult` | `NetResult` | 工具接口、无统一业务码接口 |
| 需要处理 `code/message/data` | `sendBusinessResult` | `BusinessResult` | 登录失效、权限不足、业务失败 |
| 希望 `data` 直接转成类 | `sendTypedBusinessResult<T>` | `TypedBusinessResult<T>` | Gson 或自定义转换器解析业务数据 |

## NetResult

```kotlin
Net.get()
    .url("order/list")
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            render(result.body)
        }

        override fun onHttpError(error: NetResult.HttpError) {
            showHttpError(error.code, error.body)
        }

        override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) {
            showError(error.message)
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            showError(error.message)
        }
    })
```

- `Success`：HTTP 2xx，响应体没有超过读取上限。
- `HttpError`：HTTP 4xx/5xx。OkHttp 会进入 `onResponse`，但库会把它归为失败结果。
- `ResponseTooLarge`：响应体超过 `NetConfig.maxResponseBodyBytes`。库停止读取 body，避免大响应进入内存。
- `NetworkError`：请求没有拿到 HTTP 响应，例如断网、超时、DNS、连接失败。

## BusinessResultCallback

```kotlin
Net.postJson()
    .url("user/login")
    .addJsonStr("""{"username":"tom","password":"123456"}""")
    .sendBusinessResult(object : BusinessResultCallback {
        override fun onSuccess(result: BusinessResult.Success) {
            // HTTP 2xx，并且业务信封 success == true。
        }

        override fun onBusinessError(error: BusinessResult.BusinessError) {
            // HTTP 2xx，但业务码表示失败。
        }

        override fun onHttpError(error: BusinessResult.HttpError) {
            // HTTP 4xx/5xx。
        }

        override fun onResponseTooLarge(error: BusinessResult.ResponseTooLarge) {
            // 响应体超过 maxResponseBodyBytes，未进入业务解析。
        }

        override fun onNetworkError(error: BusinessResult.NetworkError) {
            // IOException。
        }

        override fun onInterceptorError(error: BusinessResult.InterceptorError) {
            // 业务责任链抛出异常。
        }

        override fun onConsumed(result: BusinessResult.Consumed) {
            // 业务责任链已经消费结果，业务回调到此结束。
        }
    })
```

## onSuccess 和 onResponseTooLarge 的区别

- `onSuccess`：已经读到完整响应体，并通过 `ApiEnvelopeParser` 解析为业务成功。
- `onResponseTooLarge`：HTTP 响应可能已经返回，但响应体超过 `maxResponseBodyBytes`。为了保护内存，库不会继续读取，也不会解析业务码，所以不会触发 `onSuccess`。

如果接口本来就会返回大 JSON，不要简单调大上限。更稳妥的做法是让服务端分页、压缩字段、改成文件下载，或者只返回必要字段。

## ApiEnvelopeParser

`ApiEnvelopeParser` 用来把原始 JSON 转成统一业务信封：

```kotlin
data class ApiEnvelope(
    val code: String?,
    val message: String?,
    val dataRaw: String?,
    val rawBody: String?,
    val success: Boolean
)
```

默认解析器会读取常见的 `code`、`message`、`data` 结构，并根据成功码判断 `success`。如果你的后端字段不是这个格式，需要自定义解析器。

```kotlin
Net.configure {
    businessEnvelopeParser(object : ApiEnvelopeParser {
        override fun parse(rawBody: String?): ApiEnvelope {
            val json = JsonParser.parseString(rawBody).asJsonObject
            val status = json["status"]?.asString
            return ApiEnvelope(
                code = status,
                message = json["msg"]?.asString,
                dataRaw = json["payload"]?.toString(),
                rawBody = rawBody,
                success = status == "OK"
            )
        }
    })
}
```

## 业务责任链

业务责任链适合统一处理登录失效、权限不足、维护模式等业务码。

```kotlin
Net.configure {
    businessInterceptor(object : BusinessResultInterceptor {
        override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
            if (chain.envelope.code == "40101") {
                loginNavigator.openLogin()
                return BusinessResult.Consumed(
                    reason = "login expired",
                    httpCode = chain.response.code,
                    rawBody = chain.response.body
                )
            }
            return chain.proceed()
        }
    })
}
```

关键行为：

- 拦截器返回 `BusinessResult.Consumed` 时，后续业务成功或失败回调不会继续执行，调用方只会收到 `onConsumed`。
- 拦截器抛异常时，会进入 `onInterceptorError`。
- 拦截器不应该做耗时阻塞操作；需要异步处理时，只做状态分发或导航触发。

## 类型化业务结果

```kotlin
data class UserInfo(val id: String, val name: String)

Net.get()
    .url("user/profile")
    .sendTypedBusinessResult<UserInfo>(object : TypedBusinessResultCallback<UserInfo> {
        override fun onSuccess(result: TypedBusinessResult.Success<UserInfo>) {
            render(result.data)
        }

        override fun onDataConvertError(error: TypedBusinessResult.DataConvertError) {
            showError("data 字段解析失败")
        }

        override fun onBusinessError(error: TypedBusinessResult.BusinessError) {
            showError(error.message)
        }

        override fun onHttpError(error: TypedBusinessResult.HttpError) {
            showError("HTTP ${error.httpCode}")
        }

        override fun onResponseTooLarge(error: TypedBusinessResult.ResponseTooLarge) {
            showError("响应过大")
        }

        override fun onNetworkError(error: TypedBusinessResult.NetworkError) {
            showError(error.rawBody)
        }

        override fun onInterceptorError(error: TypedBusinessResult.InterceptorError) {
            showError(error.error.error.message)
        }

        override fun onConsumed(result: TypedBusinessResult.Consumed) = Unit
    })
```

默认转换器是 Gson。需要 Moshi、kotlinx.serialization 或特殊日期格式时，配置 `businessConverter(...)`。

[返回模块 README](../README.md)
