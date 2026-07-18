# 9. HTTP 错误与网络异常处理

说明如何科学地区分业务可处理的 HTTP 错误、真正的网络异常，以及 HTTP 2xx 下的业务码拦截，避免把登录失效、权限不足等逻辑散落在每个页面。

## 适用条件

- 已使用 `net` 模块发送普通 GET/POST 请求
- 需要明确区分 `2xx`、`4xx/5xx`、断网/超时/DNS 等不同失败类型
- 希望 4xx/5xx 不再混在旧版 `onResponse(result, code)` 中自行判断
- 希望 HTTP 2xx 里的业务码，例如登录失效、权限不足、维护模式，可以在统一层级处理

## 场景选择

| 问题场景 | 推荐 API | 适用条件 | 处理方式 | 为什么这样做 |
|---|---|---|---|---|
| 只关心旧逻辑兼容 | `send(DdCallback)` | 老代码不想改 | `onResponse(result, code)` 内自行判断 `code` | 保持历史行为，不破坏现有调用方 |
| 需要明确处理 4xx/5xx | `sendResult(NetResultCallback)` | 新代码或准备治理错误处理 | `onHttpError(error)` 处理 HTTP 状态码错误 | HTTP 请求已经收到响应，说明网络层成功，但业务结果不是成功 |
| 需要处理断网/超时/DNS | `sendResult(NetResultCallback)` | 需要给用户网络提示或重试 | `onNetworkError(error)` 处理 IOException | 这类问题没有可用 HTTP 响应，和 4xx/5xx 处理策略不同 |
| HTTP 2xx 里还有业务码 | `sendBusinessResult(BusinessResultCallback)` | 后端统一返回 `code/message/data` 或类似结构 | 登录失效、权限不足、风控、维护模式等统一处理 | 业务责任链在回调前执行，可以消费结果或继续传递 |
| Flow 场景需要结构化分支 | `flowResult()` | 已引入 `net-flow` | `when(result)` 分支处理 | 不依赖异常通道表达业务状态码 |

## 推荐做法

新代码优先使用 `sendResult()`：

```kotlin
import com.itg.net.Net
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.NetResultCallback
import com.itg.net.request.result.sendResult

Net.instance.get()
    .url("https://api.example.com/user/info")
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            // HTTP 2xx。这里处理真正成功的响应。
            val body = result.body
            val code = result.code
        }

        override fun onHttpError(error: NetResult.HttpError) {
            // HTTP 4xx/5xx。请求到达了服务器，也收到了响应，但状态码不是成功。
            when (error.code) {
                400 -> showError("请求参数不正确")
                401 -> showLoginExpired()
                404 -> showError("接口不存在")
                in 500..599 -> showError("服务器繁忙")
                else -> showError("请求失败: ${error.code}")
            }
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            // 没有拿到有效 HTTP 响应，常见原因是断网、超时、DNS 失败、连接被拒绝。
            showError("网络不可用，请稍后重试")
        }
    })
```

## `onHttpError` 的作用

`onHttpError` 表示：客户端已经连上服务器并收到了 HTTP 响应，但状态码不在 `200..299`。

常见例子：

- `400 Bad Request`：请求参数错误
- `401 Unauthorized`：未登录或 token 过期
- `403 Forbidden`：没有权限
- `404 Not Found`：接口路径不存在
- `500 Internal Server Error`：服务器内部错误

这类错误通常需要读取 `error.code` 和 `error.body`：

```kotlin
override fun onHttpError(error: NetResult.HttpError) {
    Log.e("API", "HTTP ${error.code}, body=${error.body}")
}
```

`error.body` 是服务端返回的错误响应体，适合解析后展示业务提示，例如后端返回：

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "登录已过期"
}
```

## `onNetworkError` 的作用

`onNetworkError` 表示：请求没有拿到可用 HTTP 响应，底层 OkHttp 触发了 `IOException`。

常见例子：

- 手机没有网络
- DNS 解析失败
- 连接超时
- 读写超时
- 服务器端口不可达
- 请求被取消时底层触发连接异常

这类错误没有 HTTP 状态码，所以 `error.code` 为 `null`，通常只适合做网络提示、重试或降级：

```kotlin
override fun onNetworkError(error: NetResult.NetworkError) {
    Log.e("API", "network failed", error.error)
    showError("网络连接失败")
}
```

## 和旧版 `DdCallback` 的区别

旧版 `send(DdCallback)` 保持兼容：

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/info")
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
            // 只表示网络异常、超时等 IOException
        }

        override fun onResponse(result: String?, code: Int) {
            // 2xx、4xx、5xx 都会进入这里，需要调用方自己判断 code
        }
    })
```

这和 OkHttp 的语义一致：只要服务器返回了 HTTP 响应，就会进入 OkHttp 的 `onResponse`；断网、超时、DNS 失败才进入 `onFailure`。

`sendResult()` 是在库层帮你把 `onResponse` 再按状态码拆开：

```text
OkHttp onResponse + 2xx      -> onSuccess
OkHttp onResponse + 4xx/5xx  -> onHttpError
OkHttp onFailure             -> onNetworkError
```

## Flow 场景

如果使用 `net-flow`，推荐按需求选择：

```kotlin
import com.itg.net.flow.flowResult
import com.itg.net.request.result.NetResult

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/info")
        .flowResult()
        .collect { result ->
            when (result) {
                is NetResult.Success -> render(result.body)
                is NetResult.HttpError -> showError("HTTP ${result.code}")
                is NetResult.NetworkError -> showError("网络不可用")
            }
        }
}
```

## 业务码责任链

有些接口 HTTP 状态码是 `200`，但响应体里的业务码表示失败，例如：

```json
{
  "code": "401001",
  "message": "登录已过期",
  "data": null
}
```

这种情况不应该放到 `onHttpError`，因为 HTTP 层是成功的；也不应该每个页面都判断一次。推荐在全局配置中注册 `BusinessResultInterceptor`：

```kotlin
import android.util.Log
import com.itg.net.Net
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor

Net.instance.configure {
    addBusinessResultInterceptor(object : BusinessResultInterceptor {
        override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
            val code = chain.envelope.code
            if (code == "TOKEN_EXPIRED" || code == "401001") {
                // 在这里跳登录页、清 token、发全局事件等。
                Log.w("API", "login expired")
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

调用方使用 `sendBusinessResult()`：

```kotlin
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultCallback
import com.itg.net.request.business.sendBusinessResult

Net.instance.get()
    .url("https://api.example.com/user/info")
    .sendBusinessResult(object : BusinessResultCallback {
        override fun onSuccess(result: BusinessResult.Success) {
            render(result.dataRaw)
        }

        override fun onBusinessError(error: BusinessResult.BusinessError) {
            showError(error.message ?: "业务处理失败")
        }

        override fun onHttpError(error: BusinessResult.HttpError) {
            showError("HTTP ${error.httpCode}")
        }

        override fun onNetworkError(error: BusinessResult.NetworkError) {
            showError("网络不可用")
        }

        override fun onConsumed(result: BusinessResult.Consumed) {
            // 登录失效这类全局逻辑已经被责任链处理，页面通常无需再处理。
        }
    })
```

### ApiEnvelopeParser 的作用

`ApiEnvelopeParser` 是业务协议适配层。它负责把后端原始响应字符串解析成统一的 `ApiEnvelope`，后面的 `BusinessResultInterceptor` 只需要读取 `chain.envelope.code`、`message`、`dataRaw` 和 `success`，不需要关心后端字段到底叫 `code`、`status`、`bizCode` 还是别的。

处理流程：

```text
HTTP 200 原始 body
  -> ApiEnvelopeParser 解析业务协议
  -> ApiEnvelope(code/message/dataRaw/success)
  -> BusinessResultInterceptor 责任链
  -> onSuccess/onBusinessError/onConsumed
```

`ApiEnvelope` 字段含义：

| 字段 | 含义 | 谁会使用 |
|---|---|---|
| `code` | 后端业务码，例如 `0`、`200`、`401001`、`TOKEN_EXPIRED` | 责任链和业务错误判断 |
| `message` | 后端提示文案，例如 `ok`、`登录已过期` | `onBusinessError` 或全局提示 |
| `dataRaw` | `data` / `result` 等数据字段的原始字符串 | 页面成功后继续反序列化或展示 |
| `rawBody` | 完整原始响应体 | 日志、排查、兜底解析 |
| `success` | 当前业务响应是否成功 | 决定进入 `onSuccess` 还是 `onBusinessError` |

例如默认解析器读取这个响应：

```json
{
  "code": "0",
  "message": "ok",
  "data": {
    "name": "Tom"
  }
}
```

会得到：

```kotlin
ApiEnvelope(
    code = "0",
    message = "ok",
    dataRaw = """{"name":"Tom"}""",
    rawBody = 原始完整响应,
    success = true
)
```

如果响应是：

```json
{
  "code": "401001",
  "message": "登录已过期",
  "data": null
}
```

会得到：

```kotlin
ApiEnvelope(
    code = "401001",
    message = "登录已过期",
    dataRaw = null,
    rawBody = 原始完整响应,
    success = false
)
```

然后登录失效拦截器就可以统一判断：

```kotlin
override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
    if (chain.envelope.code == "401001") {
        // 清 token、跳登录页、发全局事件等。
        return BusinessResult.Consumed(reason = "login expired")
    }
    return chain.proceed()
}
```

默认解析器会读取常见字段：

| 字段类型 | 默认字段名 |
|---|---|
| 业务码 | `code`、`status` |
| 提示文案 | `message`、`msg`、`error` |
| 数据体 | `data`、`result` |
| 成功码 | `0`、`200`、`success`、`true` |

如果后端协议不同，可以替换解析器：

```kotlin
import com.itg.net.request.business.ApiEnvelope
import com.itg.net.request.business.ApiEnvelopeParser
import org.json.JSONObject

Net.instance.configure {
    businessResultParser(object : ApiEnvelopeParser {
        override fun parse(rawBody: String?): ApiEnvelope {
            val json = JSONObject(rawBody.orEmpty())
            val code = json.optString("bizCode")
            return ApiEnvelope(
                code = code,
                message = json.optString("bizMsg"),
                dataRaw = json.opt("payload")?.toString(),
                rawBody = rawBody,
                success = code == "OK"
            )
        }
    })
}
```

Flow 业务码处理：

```kotlin
import com.itg.net.flow.flowBusinessResult
import com.itg.net.request.business.BusinessResult

lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/user/info")
        .flowBusinessResult()
        .collect { result ->
            when (result) {
                is BusinessResult.Success -> render(result.dataRaw)
                is BusinessResult.BusinessError -> showError(result.message)
                is BusinessResult.HttpError -> showError("HTTP ${result.httpCode}")
                is BusinessResult.NetworkError -> showError("网络不可用")
                is BusinessResult.Consumed -> Unit
            }
        }
}
```

执行顺序：

```text
HTTP 2xx -> ApiEnvelopeParser -> BusinessResultInterceptor 链 -> onSuccess/onBusinessError/onConsumed
HTTP 4xx/5xx -> onHttpError
IOException -> onNetworkError
```

`flowString()` 适合只想拿成功响应体的简单场景。遇到非 `2xx` 时，它会以 `NetFlowException(code, message)` 结束，便于在 `catch` 中统一处理：

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/info")
    .flowString()
    .catch { e ->
        if (e is NetFlowException && e.code != null) {
            showError("HTTP ${e.code}: ${e.message}")
        } else {
            showError("网络请求失败")
        }
    }
    .collect { body -> render(body) }
```

## 常见错误

- 不要把所有失败都放到 `onNetworkError`。4xx/5xx 是服务器明确返回的 HTTP 响应，应走 `onHttpError`。
- 不要只判断 `body != null`。错误响应也可能有 body，应该优先判断 HTTP 状态码。
- 不要在 `onSuccess` 中处理登录过期。`401` 应该放在 `onHttpError` 中统一拦截。
- 不要把 HTTP 2xx 内的业务码写进 OkHttp Interceptor。业务码属于业务协议层，应该放在 `BusinessResultInterceptor`。
- 旧版 `send(DdCallback)` 不会改变行为，迁移时可以逐个接口替换为 `sendResult()`。

## 验证方式

- 请求一个返回 `200` 的接口，应进入 `onSuccess`。
- 请求一个返回 `404` 或 `500` 的接口，应进入 `onHttpError`，并能拿到 `code` 和 `body`。
- 关闭网络或请求不可达地址，应进入 `onNetworkError`。
- Flow 使用 `flowResult()` 时，三类结果都应在 `collect` 的 `when(result)` 中处理。
- 自定义接口返回 `{"code":"401001","message":"登录已过期"}` 且 HTTP 状态为 200 时，应被登录失效拦截器消费，进入 `onConsumed` 或触发统一跳转逻辑。

[返回 README](../../README.md)
