# 5. 全局配置详解

说明 `NetConfig` 中可配置项的用途和推荐写法。

## 适用场景

- 需要在 `Application.onCreate()` 中初始化网络库
- 需要统一配置 Base URL、全局参数、日志、缓存、拦截器、下载并发数
- 需要统一配置业务码解析、业务数据转换或业务责任链

## 配置项

当前模块还没有对外发布，`NetConfig` 只保留语义化 API，不再保留旧命名兼容方法。

| 配置目标 | 写法 | 默认值 | 说明 |
|---|---|---|---|
| Application | `application(application)` | — | 设置 Application 实例 |
| Base URL | `baseUrl(url)` | null | 全局 Base URL |
| 单个全局参数 | `globalParam(key, value)` | — | 添加或覆盖一个全局参数 |
| 批量全局参数 | `globalParams(map)` | — | 批量添加或覆盖全局参数 |
| 移除全局参数 | `removeGlobalParams(vararg)` | — | 移除一个或多个全局参数 |
| 清空全局参数 | `clearGlobalParameters()` | — | 清空所有全局参数 |
| 最大并行下载数 | `maxConcurrentDownloads(max)` | 3 | 小于 1 时自动修正为 1 |
| HTTP 日志 | `enableHttpLog()` / `disableHttpLog()` | false | 是否开启 HTTP 日志 |
| 日志路径 | `logPath(path)` | null | 自定义日志目录或日志文件路径 |
| OkHttpClient | `client(client)` | null | 自定义 OkHttpClient |
| OkHttp 拦截器 | `interceptor(interceptor)` | — | 添加 OkHttp 拦截器 |
| 批量 OkHttp 拦截器 | `interceptors(list)` | — | 批量添加 OkHttp 拦截器 |
| 读取 OkHttp 拦截器 | `interceptors()` | — | 获取已注册拦截器快照 |
| HTTP 缓存 | `cache(cache)` | null | 设置 OkHttp 缓存 |
| 读取 HTTP 缓存 | `cache()` | null | 获取当前缓存配置 |
| 字段加解密 | `encrypt { }` | null | 详见 [07-字段加密](./07-field-encryption.md) |
| 网络监控 | `monitor { }` | null | 详见 [08-网络监控](./08-network-monitor.md) |
| 业务协议解析 | `businessEnvelopeParser(parser)` | `DefaultApiEnvelopeParser()` | 将原始响应解析为 `ApiEnvelope` |
| 业务数据转换 | `businessConverter(converter)` | `GsonBusinessDataConverter()` | 将 `dataRaw` 转成目标类型 |
| 业务责任链 | `businessInterceptor(interceptor)` | — | 添加业务结果拦截器 |
| 批量业务责任链 | `businessInterceptors(list)` | — | 批量添加业务结果拦截器 |
| 清空业务责任链 | `clearBusinessInterceptors()` | — | 清空所有业务结果拦截器 |
| 读取业务责任链 | `businessInterceptors()` | — | 获取业务结果拦截器快照 |
| 响应体大小限制 | `maxResponseBodyBytes(bytes)` | 2097152 (2MB) | 超过此大小的响应会触发 `ResponseTooLarge` 而非 `Success` |
| 主线程 Handler | `uiHandler` | — | 主线程 `Handler`（只读，供内部及扩展模块使用） |
| 加密配置 | `encryptConfig` | null | 获取当前字段加密配置（只读） |
| 监控配置 | `monitorConfig` | null | 获取当前网络监控配置（只读） |
| 业务解析器 | `businessEnvelopeParser` | — | 获取当前业务协议解析器（只读） |
| 业务转换器 | `businessConverter` | — | 获取当前业务数据转换器（只读） |
| Base URL | `baseUrl` | — | 获取当前全局 Base URL（只读） |
| 包名 | `packageName` | — | 获取 Application 包名（只读） |
| 最大并行下载数 | `maxConcurrentDownloadCount` | 3 | 获取当前并行下载上限（只读） |
| HTTP 日志状态 | `isHttpLogEnabled` | false | 获取 HTTP 日志是否开启（只读） |
| 响应体限制 | `maxResponseBodyBytes` | 2097152 | 获取当前响应体大小限制（只读） |

## 完整示例

```kotlin
Net.configure {
    application(this@App)
    baseUrl("https://api.example.com/")

    globalParam("platform", "android")
    globalParams(
        mapOf(
            "version" to BuildConfig.VERSION_NAME,
            "channel" to "official"
        )
    )

    maxConcurrentDownloads(5)
    enableHttpLog(BuildConfig.DEBUG)
    logPath("/sdcard/myapp/logs")

    client(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    )
    interceptor(authInterceptor)
    cache(Cache(File(cacheDir, "http_cache"), 50 * 1024 * 1024))

    encrypt {
        algorithm(Algorithm.AES_GCM_NO_PADDING)
        secretKey("my-32-byte-secret-key!!123456")
        iv("1234567890abcdef")
        encryptField("password")
    }

    monitor {
        enabled(true)
        reportUrl("https://monitor.example.com/api/v1/report")
        reportMode(ReportMode.FAILURE_ONLY)
    }

    businessEnvelopeParser(customApiEnvelopeParser)
    businessConverter(
        GsonBusinessDataConverter(
            GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create()
        )
    )
    businessInterceptor(loginExpiredInterceptor)
}
```

## 关键说明

- `configure {}` 建议只在 `Application.onCreate()` 中调用一次。
- `application(...)` 是基础配置，日志目录、主线程 Handler、包名相关能力都依赖它。
- `client(...)` 配置自定义 OkHttpClient 后，库会优先使用该客户端。
- `interceptor(...)`、`cache(...)`、`enableHttpLog(...)` 只会参与库默认创建的 OkHttpClient；如果传入完全自定义的 `client(...)`，这些能力需要调用方在自定义 client 中自行配置。
- `globalParams` 会自动附加到普通请求，单个请求可通过 `noUseGlobalParams()` 跳过。
- Retrofit 模块复用 OkHttpClient 和拦截器能力，但不会自动把 `globalParams` 注入到 Retrofit 接口参数中。
- `businessEnvelopeParser(...)` 负责解析业务信封，`businessInterceptor(...)` 负责统一处理登录失效、权限不足等业务状态。

## 验证方式

- 编译通过：`./gradlew :net:compileDebugKotlin`
- 运行 App，确认 `Net.configure {}` 无异常
- 发送一个请求，确认 Base URL、全局参数、日志和业务责任链按预期生效

[返回 README](../../README.md)
