# 05. 配置、加密和监控

这个文档说明 `NetConfig` 的核心配置、字段加解密和网络监控。

## 全局配置

```kotlin
Net.configure {
    application(app)
    baseUrl("https://api.example.com/")
    globalParam("appVersion", BuildConfig.VERSION_NAME)
    globalParams(mapOf("platform" to "android"))
    enableHttpLog(BuildConfig.DEBUG)
    maxConcurrentDownloads(3)
    maxResponseBodyBytes(2L * 1024L * 1024L)
}
```

常用配置：

- `application(...)`：提供上下文、默认日志目录、主线程 Handler。
- `baseUrl(...)`：请求没有完整 URL 时使用。
- `globalParam(...)` / `globalParams(...)`：全局请求参数。
- `removeGlobalParams(...)` / `clearGlobalParameters()`：移除全局参数。
- `interceptor(...)`：添加 OkHttp 拦截器。
- `client(...)`：使用自定义 `OkHttpClient`。
- `maxResponseBodyBytes(...)`：限制普通请求读取到内存的最大响应体，默认 2MB。

如果调用了 `client(customClient)`，库会直接使用你的 `OkHttpClient`。这时全局拦截器、缓存、HTTP 日志这类默认客户端配置不会自动追加到自定义 client，应该在创建 client 时自行配置。

## 字段加解密

字段加解密适合只加密 JSON/Form 中的敏感字段，例如密码、手机号、身份证号。

```kotlin
Net.configure {
    encrypt {
        algorithm(Algorithm.AES_CBC_PKCS7)
        secretKey("1234567890abcdef")
        iv("abcdef1234567890")
        encryptField("password")
        decryptField("phone")
        maxBodyBytes(64L * 1024L)
    }
}
```

支持算法：

- `AES_CBC_PKCS7`：需要 `secretKey` 和 16 字节 IV。
- `AES_ECB_PKCS7`：只需要 `secretKey`，不使用 IV。
- `AES_GCM_NO_PADDING`：需要 `secretKey`。库会为每次加密生成随机 12 字节 IV，并把 IV 前缀写入密文，不需要手动配置 IV。

加密规则：

- `encryptField("password")`：按字段名精确匹配，请求加密、响应解密都生效。
- `decryptField("phone")`：仅响应解密。
- `encryptPattern(Regex(".*Token"))`：按字段名正则匹配。
- `encryptPath(Regex(".*/login"), listOf("password"))`：只对匹配路径的指定字段处理。
- `skipPath(Regex(".*/public/.*"))`：`OPT_OUT` 模式下跳过某些路径。

模式选择：

- `EncryptMode.OPT_OUT`：默认模式。配置有效规则后，符合条件的请求默认尝试处理，`skipPath` 可排除。
- `EncryptMode.OPT_IN`：按需模式。只有命中规则的路径或字段才处理。

容易出错的点：

- GET 默认跳过，因为 GET 通常没有 body。
- 字段加密会读取 JSON/Form body。超过 `maxBodyBytes` 的 body 会跳过处理，避免内存风险。
- 请求体没有任何匹配字段时，会原样发送，不会变成空 body。
- 不支持 RSA。旧文档里的 RSA 示例已经不适用。

## 单个请求覆盖

```kotlin
Net.postJson()
    .url("public/report")
    .skipEncrypt()
    .addJsonStr(json)
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) = Unit
        override fun onHttpError(error: NetResult.HttpError) = Unit
        override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) = Unit
        override fun onNetworkError(error: NetResult.NetworkError) = Unit
    })

Net.postJson()
    .url("secure/update")
    .encrypt()
    .addJsonStr(json)
    .sendResult(object : NetResultCallback {
        override fun onSuccess(result: NetResult.Success) = Unit
        override fun onHttpError(error: NetResult.HttpError) = Unit
        override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) = Unit
        override fun onNetworkError(error: NetResult.NetworkError) = Unit
    })
```

## 网络监控

```kotlin
Net.configure {
    monitor {
        enabled(true)
        reportUrl("https://monitor.example.com/api/report")
        reportMode(ReportMode.FAILURE_ONLY)
        sampleRate(1.0f)
        batchSize(20)
        flushIntervalMs(10_000L)
        maxQueueSize(1000)
        slowRequestThresholdMs(1500L)
    }
}
```

上报模式：

- `ReportMode.FAILURE_ONLY`：只上报失败请求。
- `ReportMode.ALL`：成功和失败都上报。
- `ReportMode.SLOW_ONLY`：只上报超过 `slowRequestThresholdMs` 的成功请求。

安全注意：

- `reportUrl` 推荐 HTTPS。非 HTTPS 会打印警告。
- 默认 `urlSanitizer` 会脱敏常见 query 参数，例如 `token`、`password`、`access_token`。
- 设置 `reportHandler(...)` 后，自定义处理器会完全接管上报；`reportUrl`、`batchSize`、`flushIntervalMs`、`maxQueueSize` 是否生效取决于你的实现。

## 日志

```kotlin
Net.configure {
    application(app)
    logPath(File(app.filesDir, "network-log").absolutePath)
    enableHttpLog(BuildConfig.DEBUG)
}
```

- `httpLog.txt`：HTTP 请求日志。
- `debug.txt`：下载、内部调试或错误信息。
- 日志文件超过内部上限会重建，避免无限增长。

[返回模块 README](../README.md)
