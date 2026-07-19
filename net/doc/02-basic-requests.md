# 02. 普通请求

这个文档说明 GET、POST、上传、取消以及回调线程的使用方式。

## GET

下面的示例复用同一个 `NetResultCallback`：

```kotlin
val netResultCallback = object : NetResultCallback {
    override fun onSuccess(result: NetResult.Success) {
        render(result.body)
    }

    override fun onHttpError(error: NetResult.HttpError) {
        showError("HTTP ${error.code}: ${error.body}")
    }

    override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) {
        showError(error.message)
    }

    override fun onNetworkError(error: NetResult.NetworkError) {
        showError(error.message)
    }
}
```

```kotlin
Net.get()
    .url("user/list")
    .addParam("page", "1")
    .addParam("pageSize", "20")
    .sendResult(netResultCallback)
```

`addParam(...)` 会拼接到 URL Query。全局参数也会合并到 Query。

## POST JSON

```kotlin
val json = """{"username":"tom","password":"123456"}"""

Net.postJson()
    .url("user/login")
    .addJsonStr(json)
    .sendResult(netResultCallback)
```

如果接口请求体本来就是完整 JSON，优先使用 `postJson()`。

## POST Form

```kotlin
Net.postForm()
    .url("user/login")
    .addParam("username", "tom")
    .addParam("password", "123456")
    .sendResult(netResultCallback)
```

`postForm()` 即使没有参数，也会发送 POST 空 body。这样可以避免服务端看到 GET 后返回难排查的 405。

## POST Content

```kotlin
Net.postContent()
    .url("event/report")
    .addContent("""{"event":"open_page"}""", "application/json; charset=utf-8")
    .sendResult(netResultCallback)
```

适合发送字符串、二进制文本或业务自定义 body。

## Multipart 上传

```kotlin
Net.postMultipart()
    .url("file/upload")
    .addParam("bizType", "avatar")
    .addFile(File(filePath))
    .sendResult(netResultCallback)
```

Multipart 请求中，全局参数不会重复同时放到 URL 和 body；按当前实现只会按上传请求规则发送一次，避免 token 暴露到 URL。

## 自动取消

```kotlin
Net.get()
    .url("user/profile")
    .autoCancel(activity)
    .sendResult(netResultCallback)
```

`autoCancel(activity)` 适合和页面生命周期绑定的请求。不要把长时间后台任务绑定到 Activity，否则页面关闭时会被取消。

## 手动取消

```kotlin
val tag = "profile"

Net.get()
    .url("user/profile")
    .addTag(tag)
    .sendResult(netResultCallback)

Net.cancelRequest(tag)
```

## 回调线程

- `send(callback)`、`sendResult(callback)`、`sendBusinessResult(callback)` 默认来自 OkHttp 回调线程，不保证在主线程。
- 需要更新 UI 时，使用库提供的 Handler 重载，或者在回调里切到主线程。
- `net-flow` 场景下，`collect` 的线程由协程上下文决定。

```kotlin
val mainHandler = Handler(Looper.getMainLooper())

Net.get()
    .url("user/profile")
    .send(mainHandler, object : DdCallback {
        override fun onResponse(result: String?, code: Int) {
            textView.text = result
        }

        override fun onFailure(er: String?) {
            textView.text = er
        }
    })
```

[返回模块 README](../README.md)
