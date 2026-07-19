# net

`net` 是基础网络库，提供 OkHttp 请求构建、统一结果回调、业务码处理、字段加解密、网络监控和文件下载能力。这个模块适合不想直接暴露 Retrofit Service 的场景，也适合作为 `net-flow`、`net-retrofit` 的底层配置入口。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件 | 什么时候使用 | 关键约束 |
| --- | --- | --- | --- | --- |
| [快速接入](./doc/01-quick-start.md) | `Net.configure { ... }` + `Net.get()` / `Net.postJson()` | App 启动时能拿到 `Application` | 首次接入或统一配置网络库 | 建议先配置 `application`、`baseUrl`、日志和响应体大小上限 |
| [普通请求](./doc/02-basic-requests.md) | `sendResult` / `sendBusinessResult` / `sendTypedBusinessResult` | 需要回调式 API | 页面、工具类、Java 调用方使用 | 默认回调不保证在主线程；更新 UI 要切主线程或使用 Handler 重载 |
| [结果和业务错误](./doc/03-result-and-business-callbacks.md) | `NetResult` / `BusinessResult` / `TypedBusinessResult` | 服务端有 HTTP 状态码和业务码 | 需要区分 4xx、断网、业务失败、登录失效 | HTTP 2xx 不等于业务成功；业务拦截器消费后业务成功回调不会继续执行 |
| [文件下载](./doc/04-download.md) | `Net.instance.newDownload()` / `Net.download()` | 需要下载文件、断点续传、取消 | APK、图片、文档、离线资源下载 | 必须提供 `url` 和 `savePath`；无 `Content-Length` 也能完成，但进度百分比可能不可计算 |
| [配置、加密和监控](./doc/05-config-encryption-monitor.md) | `Net.configure { encrypt { ... } monitor { ... } }` | 需要全局参数、字段加密、监控上报 | 登录态、敏感字段、故障排查、慢请求采样 | 字段加密会读取小 body；大 body 会跳过，避免内存风险 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 快速接入](./doc/01-quick-start.md) | 初始化、最小请求示例、推荐默认配置 |
| [02. 普通请求](./doc/02-basic-requests.md) | GET、POST JSON、表单、Multipart、取消和线程说明 |
| [03. 结果和业务错误](./doc/03-result-and-business-callbacks.md) | `NetResult`、`BusinessResultCallback`、业务责任链、类型转换 |
| [04. 文件下载](./doc/04-download.md) | 下载、断点续传、监听、取消、常见坑 |
| [05. 配置、加密和监控](./doc/05-config-encryption-monitor.md) | `NetConfig`、字段加解密、监控上报、日志和响应体上限 |

## 最小示例

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.configure {
            application(this@App)
            baseUrl("https://api.example.com/")
            enableHttpLog(BuildConfig.DEBUG)
            maxResponseBodyBytes(2L * 1024L * 1024L)
        }
    }
}

Net.get()
    .url("user/profile")
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            println(result.body)
        }

        override fun onHttpError(error: NetResult.HttpError) {
            println("HTTP ${error.code}: ${error.body}")
        }

        override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) {
            println(error.message)
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            println(error.message)
        }
    })
```

## 容易误解的点

- `response.isSuccessful == true` 只表示 HTTP 2xx，不代表业务成功。业务码请用 `sendBusinessResult` 或 `sendTypedBusinessResult`。
- `onSuccess` 是业务成功回调；`onResponseTooLarge` 是响应体超过 `maxResponseBodyBytes` 后的保护回调，两者不会同时触发。
- `postForm()`、`postContent()` 即使没有参数也会发送 POST 空 body，不会静默变成 GET。
- 如果配置了自定义 `OkHttpClient`，全局 OkHttp 拦截器、缓存、HTTP 日志这类默认客户端配置需要你自己加到该 client 上。
- 字段加密只支持 AES：`AES_CBC_PKCS7`、`AES_ECB_PKCS7`、`AES_GCM_NO_PADDING`。不要再按旧文档配置 RSA。
