# 11. BusinessResultCallback 回调说明

本文说明 `BusinessResultCallback` 每个回调方法的作用、触发时机，以及 `onSuccess` 和 `onResponseTooLarge` 的区别。

## 适用场景

- 使用 `sendBusinessResult(callback)` 处理接口结果。
- 后端有统一业务协议，例如 `code/message/data`。
- 希望区分 HTTP 错误、网络错误、业务错误、响应体过大、全局拦截器消费等不同结果。

## 回调总览

| 回调方法 | 触发时机 | 代表含义 | 常见处理 |
| --- | --- | --- | --- |
| `onSuccess(result)` | HTTP 2xx，响应体可读取，业务协议解析成功，业务码为成功，并且没有被业务责任链消费 | 真正的业务成功 | 渲染页面、读取 `result.dataRaw` |
| `onBusinessError(error)` | HTTP 2xx，响应体可读取，业务协议解析成功，但业务码表示失败 | 业务失败，不是网络失败 | 展示后端 `message`，按业务码处理 |
| `onHttpError(error)` | 收到 HTTP 响应，但状态码不是 2xx | HTTP 层失败 | 处理 401、403、404、5xx 等 |
| `onResponseTooLarge(error)` | 收到 HTTP 响应，但响应体超过 `NetConfig.maxResponseBodyBytes` 限制 | 响应过大，库没有继续读完整 body | 提示数据过大、调大限制、改分页或下载 |
| `onNetworkError(error)` | OkHttp `onFailure`，没有拿到可用 HTTP 响应 | 网络层失败 | 展示断网、超时、DNS、连接失败提示 |
| `onInterceptorError(error)` | `BusinessResultInterceptor` 执行时抛出异常 | 业务责任链自身出错 | 记录日志、修复拦截器逻辑 |
| `onConsumed(result)` | 业务责任链返回 `BusinessResult.Consumed` | 结果已被全局逻辑消费 | 页面通常不再处理，例如登录失效跳转 |

## 调用流程

```text
sendBusinessResult()
  -> sendResult()
  -> HTTP 2xx
       -> ApiEnvelopeParser 解析 code/message/data
       -> BusinessResultInterceptor 责任链
       -> onSuccess / onBusinessError / onConsumed / onInterceptorError
  -> HTTP 4xx/5xx
       -> onHttpError
  -> 响应体超过 maxResponseBodyBytes
       -> onResponseTooLarge
  -> IOException
       -> onNetworkError
```

## 可复制示例

```kotlin
import com.itg.net.Net
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultCallback
import com.itg.net.request.business.sendBusinessResult

Net.instance.get()
    .url("https://api.example.com/user/profile")
    .sendBusinessResult(object : BusinessResultCallback {
        override fun onSuccess(result: BusinessResult.Success) {
            // HTTP 2xx + 业务成功。
            renderUser(result.dataRaw)
        }

        override fun onBusinessError(error: BusinessResult.BusinessError) {
            // HTTP 2xx，但业务码失败，例如 code=401001、PERMISSION_DENIED。
            showMessage(error.message ?: "业务处理失败")
        }

        override fun onHttpError(error: BusinessResult.HttpError) {
            // 服务端返回了 HTTP 响应，但状态码不是 2xx。
            when (error.httpCode) {
                401 -> showLoginExpired()
                in 500..599 -> showMessage("服务器繁忙")
                else -> showMessage("请求失败: HTTP ${error.httpCode}")
            }
        }

        override fun onResponseTooLarge(error: BusinessResult.ResponseTooLarge) {
            // 收到了 HTTP 响应，但 body 超过读取上限。
            showMessage("响应数据过大，请分页请求或调大读取上限")
        }

        override fun onNetworkError(error: BusinessResult.NetworkError) {
            // 没有拿到有效 HTTP 响应，通常是断网、超时、DNS 失败、连接失败。
            showMessage("网络不可用，请稍后重试")
        }

        override fun onInterceptorError(error: BusinessResult.InterceptorError) {
            // BusinessResultInterceptor 抛出异常。
            logError("业务拦截器异常", error.error)
        }

        override fun onConsumed(result: BusinessResult.Consumed) {
            // 结果已被全局责任链消费，例如登录失效已经统一跳转登录页。
        }
    })
```

## 每个回调的详细说明

### onSuccess

`onSuccess` 表示业务成功，不只是 HTTP 成功。

必须同时满足：

- HTTP 状态码是 `200..299`。
- 响应体没有超过 `maxResponseBodyBytes`。
- `ApiEnvelopeParser` 能解析出业务信封。
- `ApiEnvelope.success == true`。
- `BusinessResultInterceptor` 没有返回 `Consumed`，也没有抛异常。

可以读取：

| 字段 | 说明 |
| --- | --- |
| `result.httpCode` | HTTP 状态码，通常是 200 |
| `result.rawBody` | 完整原始响应体 |
| `result.dataRaw` | 业务 `data` 字段的原始字符串 |
| `result.envelope.code` | 后端业务码 |
| `result.envelope.message` | 后端业务提示 |
| `result.headers` | HTTP 响应头 |

### onBusinessError

`onBusinessError` 表示 HTTP 层成功，但业务层失败。

典型响应：

```json
{
  "code": "401001",
  "message": "登录已过期",
  "data": null
}
```

这类错误不应该进入 `onHttpError`，因为 HTTP 状态码可能仍然是 200。

### onHttpError

`onHttpError` 表示服务端返回了 HTTP 响应，但状态码不是 2xx。

典型场景：

- `400` 参数错误。
- `401` 未登录或 token 失效。
- `403` 权限不足。
- `404` 接口不存在。
- `500..599` 服务端错误。

它和 `onNetworkError` 的关键区别是：`onHttpError` 已经收到服务端响应，有 HTTP 状态码；`onNetworkError` 没有可用 HTTP 响应。

### onResponseTooLarge

`onResponseTooLarge` 表示已经收到 HTTP 响应，但响应体超过了 `NetConfig.maxResponseBodyBytes` 限制。

这个回调不是业务成功，也不是网络失败。库不会为了回调 `onSuccess` 而继续读取一个超过上限的 body，因为那可能带来内存峰值和卡顿风险。

可以读取：

| 字段 | 说明 |
| --- | --- |
| `error.httpCode` | HTTP 状态码，可能是 2xx，也可能是 4xx/5xx |
| `error.error.contentLength` | 响应体声明或实际检测到的大小 |
| `error.error.maxBytes` | 当前配置允许读取的最大字节数 |
| `error.error.message` | 过大提示文案 |

处理建议：

- 列表数据改分页。
- 大文件改走下载接口，不要走普通 JSON 请求。
- 确认确实需要一次性读取时，通过 `Net.configure { maxResponseBodyBytes(...) }` 调大限制。

### onNetworkError

`onNetworkError` 表示没有拿到可用 HTTP 响应。

常见原因：

- 手机断网。
- DNS 解析失败。
- 连接超时。
- 读写超时。
- 服务端端口不可达。
- 请求被取消后底层触发 `IOException`。

这类错误通常只适合做网络提示、重试或降级，不适合解析业务码。

### onInterceptorError

`onInterceptorError` 表示 `BusinessResultInterceptor.intercept()` 内部抛出了异常。

它不是网络异常。这个分支的存在是为了把“业务责任链写错了”和“真实网络不可用”区分开。

可以读取：

| 字段 | 说明 |
| --- | --- |
| `error.error` | 拦截器抛出的原始异常 |
| `error.index` | 第几个业务拦截器抛出异常 |
| `error.httpCode` | 当时对应的 HTTP 状态码 |
| `error.rawBody` | 当时对应的原始响应体 |

### onConsumed

`onConsumed` 表示结果已经被全局业务责任链消费。

典型场景：

- 登录失效，责任链已经清 token 并跳转登录页。
- 命中全局风控，责任链已经展示统一弹窗。
- 服务维护，责任链已经进入统一维护页。

页面收到 `onConsumed` 后通常不需要再展示错误，避免重复提示。

## onSuccess 和 onResponseTooLarge 的区别

| 对比项 | `onSuccess` | `onResponseTooLarge` |
| --- | --- | --- |
| 是否收到 HTTP 响应 | 是 | 是 |
| HTTP 状态码 | 必须是 2xx | 可能是 2xx，也可能不是 2xx |
| 是否完整读取 body | 是 | 否 |
| 是否解析业务码 | 是 | 否 |
| 是否代表业务成功 | 是 | 否 |
| 是否能读取 `dataRaw` | 能 | 不能 |
| 主要处理方式 | 渲染业务数据 | 调整接口数据大小、分页、下载或调大限制 |

核心判断：

```text
onSuccess = HTTP 成功 + body 可读取 + 业务成功
onResponseTooLarge = HTTP 有响应 + body 太大，库为了内存安全没有继续读取
```

因此不要在 `onResponseTooLarge` 中尝试读取业务 `data`，因为库没有完整读取响应体，也不会执行 `ApiEnvelopeParser`。

## 配置响应体读取上限

```kotlin
Net.configure {
    maxResponseBodyBytes(2L * 1024L * 1024L) // 2MB
}
```

注意：

- 这个限制只影响普通字符串响应、结构化结果、业务结果、Flow/Retrofit 中需要把 body 读成字符串的场景。
- 文件下载不应该通过调大这个值解决，应使用下载 API。
- 上限越大，单次请求的内存峰值越高。

## 验证方式

- 返回 HTTP 200 且业务 `code=0`：应进入 `onSuccess`。
- 返回 HTTP 200 且业务 `code=401001`：应进入 `onBusinessError`，或被责任链消费后进入 `onConsumed`。
- 返回 HTTP 404/500：应进入 `onHttpError`。
- 关闭网络或请求不可达地址：应进入 `onNetworkError`。
- 将 `maxResponseBodyBytes` 设置很小，并请求大响应体：应进入 `onResponseTooLarge`。
- 在 `BusinessResultInterceptor` 中主动抛异常：应进入 `onInterceptorError`。

[返回 README](../README.md)
