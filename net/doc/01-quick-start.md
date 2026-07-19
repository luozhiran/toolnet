# 01. 快速接入

这个文档说明如何初始化 `net`，以及第一次请求应该怎么写。

## 适用条件

- Android 项目已经依赖 `net` 模块。
- App 启动阶段可以拿到 `Application`。
- 服务端有固定 Base URL，或者每个请求都传完整 URL。

## 推荐初始化

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.configure {
            application(this@App)
            baseUrl("https://api.example.com/")
            enableHttpLog(BuildConfig.DEBUG)
            maxConcurrentDownloads(3)
            maxResponseBodyBytes(2L * 1024L * 1024L)
        }
    }
}
```

## 发起一个 GET 请求

```kotlin
Net.get()
    .url("user/profile")
    .addParam("userId", "10001")
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) {
            val body = result.body
        }

        override fun onHttpError(error: NetResult.HttpError) {
            // HTTP 4xx/5xx，例如 401、404、500。
        }

        override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) {
            // 响应体超过 maxResponseBodyBytes，库不会继续读入内存。
        }

        override fun onNetworkError(error: NetResult.NetworkError) {
            // 断网、DNS、超时、连接失败等 IOException。
        }
    })
```

## 推荐默认值

- `application(...)`：建议配置。下载日志路径、主线程 Handler、包名等能力会使用它。
- `baseUrl(...)`：请求只传相对路径时会使用它。
- `enableHttpLog(BuildConfig.DEBUG)`：调试环境开启，生产环境按需关闭。
- `maxResponseBodyBytes(...)`：默认 2MB。普通请求会把响应体读成字符串，必须有上限保护。
- `maxConcurrentDownloads(...)`：默认 3，传小于 1 的值会自动修正为 1。

## 验证方式

- 调用相对路径接口时，确认最终请求地址等于 `baseUrl + path`。
- 触发一个 404，应该进入 `NetResult.HttpError`。
- 断网后请求，应该进入 `NetResult.NetworkError`。

[返回模块 README](../README.md)
