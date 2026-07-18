# 5. 全局配置详解

`NetConfig` 中所有可配置项的详细说明和完整示例。

## 适用条件

- 需要在 `Application.onCreate()` 中进行全局配置
- 需要定制 OkHttp 行为（超时、缓存、拦截器等）

## 推荐做法

### NetConfig 所有配置项

| 方法 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `app(application)` | 必选 | — | 设置 Application 实例 |
| `url(url)` | 可选 | null | 全局 Base URL |
| `setGlobalParams(key, value)` | 可选 | — | 添加全局参数（多次调用可添加多个） |
| `removeGlobalParam(key)` | 可选 | — | 移除单个全局参数 |
| `clearGlobalParams()` | 可选 | — | 清空所有全局参数 |
| `maxDownloadNum(max)` | 可选 | 3 | 最大并行下载数（自动限制 ≥1） |
| `useHttpLog(bool)` | 可选 | false | 是否开启 HTTP 日志（写入文件） |
| `log(path)` | 可选 | null | 自定义日志文件路径 |
| `okHttpClient(client)` | 可选 | null | 自定义 OkHttpClient（不设置则使用库内置默认） |
| `addInterceptor(interceptor)` | 可选 | — | 添加 OkHttp 拦截器（可多次调用） |
| `getInterceptors()` | 只读 | — | 获取所有已注册拦截器 |
| `useCacheControl(cache)` | 可选 | null | 设置 OkHttp 缓存（需传入 `okhttp3.Cache` 实例） |
| `encrypt { }` | 可选 | — | 字段级加解密配置（详见 [07-字段加密](./07-field-encryption.md)） |
| `monitor { }` | 可选 | — | 网络监控上报配置（详见 [08-网络监控](./08-network-monitor.md)） |

### 完整配置示例

```kotlin
Net.instance.configure {
    // 必选
    app(application)

    // 网络
    url("https://api.example.com")

    // 全局参数
    setGlobalParams("platform", "android")
    setGlobalParams("version", BuildConfig.VERSION_NAME)
    setGlobalParams("channel", "official")

    // 下载
    maxDownloadNum(5)

    // 日志
    useHttpLog(BuildConfig.DEBUG)

    // 自定义 OkHttpClient
    okHttpClient(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    )

    // 拦截器
    addInterceptor(authInterceptor)
    addInterceptor(loggingInterceptor)

    // 缓存
    useCacheControl(Cache(File(cacheDir, "http_cache"), 50 * 1024 * 1024))

    // 字段加密
    encrypt {
        algorithm(Algorithm.AES_GCM_NO_PADDING)
        secretKey("my-32-byte-secret-key!!123456")
        iv("1234567890abcdef")
        encryptField("password")
    }

    // 网络监控
    monitor {
        enabled(BuildConfig.DEBUG.not())
        reportUrl("https://monitor.example.com/api/v1/report")
        reportMode(ReportMode.FAILURE_ONLY)
    }
}
```

## 关键说明

- `configure {}` 建议只调用一次，在 `Application.onCreate()` 中完成
- 自定义 `okHttpClient` 后，通过 `addInterceptor` 添加的拦截器仍然会添加到该 client 上
- 全局参数（`globalParams`）会自动附加到所有请求 URL 上（作为 Query 参数），单个请求可通过 `noUseGlobalParams()` 跳过
- Retrofit 模块的请求会自动继承 OkHttpClient 和拦截器配置，但不会自动附加 `globalParams`（Retrofit 接口需自行传参）
- `maxDownloadNum` 最小值为 1，传入更小的值会被自动修正
- `encrypt {}` 和 `monitor {}` 是可选 DSL 闭包，不配置则功能不生效

## 验证方式

- 编译通过并运行 App，确认 `configure {}` 无异常
- 检查日志确认拦截器生效
- 发一个请求确认全局参数自动附加到 URL 上

[返回 README](../../README.md)
