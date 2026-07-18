# 9. HTTP 错误与网络异常处理

说明如何科学地区分业务可处理的 HTTP 错误和真正的网络异常，避免把 4xx/5xx 当成普通成功结果处理。

## 适用条件

- 已使用 `net` 模块发送普通 GET/POST 请求
- 需要明确区分 `2xx`、`4xx/5xx`、断网/超时/DNS 等不同失败类型
- 希望 4xx/5xx 不再混在旧版 `onResponse(result, code)` 中自行判断

## 场景选择

| 问题场景 | 推荐 API | 适用条件 | 处理方式 | 为什么这样做 |
|---|---|---|---|---|
| 只关心旧逻辑兼容 | `send(DdCallback)` | 老代码不想改 | `onResponse(result, code)` 内自行判断 `code` | 保持历史行为，不破坏现有调用方 |
| 需要明确处理 4xx/5xx | `sendResult(NetResultCallback)` | 新代码或准备治理错误处理 | `onHttpError(error)` 处理 HTTP 状态码错误 | HTTP 请求已经收到响应，说明网络层成功，但业务结果不是成功 |
| 需要处理断网/超时/DNS | `sendResult(NetResultCallback)` | 需要给用户网络提示或重试 | `onNetworkError(error)` 处理 IOException | 这类问题没有可用 HTTP 响应，和 4xx/5xx 处理策略不同 |
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
- 旧版 `send(DdCallback)` 不会改变行为，迁移时可以逐个接口替换为 `sendResult()`。

## 验证方式

- 请求一个返回 `200` 的接口，应进入 `onSuccess`。
- 请求一个返回 `404` 或 `500` 的接口，应进入 `onHttpError`，并能拿到 `code` 和 `body`。
- 关闭网络或请求不可达地址，应进入 `onNetworkError`。
- Flow 使用 `flowResult()` 时，三类结果都应在 `collect` 的 `when(result)` 中处理。

[返回 README](../../README.md)
