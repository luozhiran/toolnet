# 1. 快速开始与初始化

如何在 Android 项目中集成 Net 网络请求库并完成初始化配置。

## 适用条件

- Android 项目使用 Gradle 构建
- 最低 SDK 版本 ≥ 21
- 使用 Kotlin 或 Java

## 推荐做法

### 依赖引入

在 `settings.gradle` 中确认模块已包含：

```groovy
include ':net'
include ':net-flow'      // 可选，需要 Kotlin Flow 时引入
include ':net-retrofit'   // 可选，需要 Retrofit 声明式 API 时引入
```

在 app 模块 `build.gradle` 中添加依赖：

```groovy
dependencies {
    // 核心库（必选）
    implementation project(':net')

    // Kotlin Flow 扩展（可选）
    implementation project(':net-flow')
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Retrofit 声明式 API（可选）
    implementation project(':net-retrofit')
}
```

### 初始化配置

在 `Application.onCreate()` 中进行一次性全局配置：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.configure {
            // 【必选】传入 Application 实例
            application(this@MyApp)

            // 【可选】全局 Base URL，后续可用 path() 拼接相对路径
            baseUrl("https://api.example.com")

            // 【可选】全局参数，所有请求自动附带
            globalParam("platform", "android")
            globalParam("version", BuildConfig.VERSION_NAME)

            // 【可选】最大并行下载数，默认 3，最小 1
            maxConcurrentDownloads(5)

            // 【可选】HTTP 日志（请求/响应体写入文件）
            enableHttpLog(BuildConfig.DEBUG)

            // 【可选】自定义日志文件目录
            logPath("/sdcard/myapp/logs")

            // 【可选】自定义 OkHttpClient
            client(myCustomOkHttpClient)

            // 【可选】HTTP 缓存
            cache(Cache(File(cacheDir, "http_cache"), 50 * 1024 * 1024))

            // 【可选】添加拦截器
            interceptor(authInterceptor)
            interceptor(loggingInterceptor)

            // 【可选】字段加密配置（详见 07-字段加密）
            encrypt {
                algorithm(Algorithm.AES_GCM_NO_PADDING)
                secretKey("my-32-byte-secret-key!!123456")
                iv("1234567890abcdef")
                encryptField("password")
            }

            // 【可选】网络监控配置（详见 08-网络监控）
            monitor {
                enabled(true)
                reportUrl("https://monitor.example.com/api/v1/report")
                reportMode(ReportMode.FAILURE_ONLY)
            }
        }
    }
}
```

## 可复制 Demo

```kotlin
// 最小初始化
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Net.configure {
            application(this@MyApp)
            baseUrl("https://api.example.com")
        }
    }
}

// 发起第一个请求验证配置正确
Net.instance.get()
    .url("https://api.example.com/ping")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { Log.e("TAG", "初始化失败: $er") }
        override fun onResponse(result: String?, code: Int) { Log.d("TAG", "初始化成功") }
    })
```

## 关键说明

- `configure {}` 采用 Kotlin DSL 风格，闭包内 `this` 为 `NetConfig` 实例
- 所有功能统一通过 `Net.instance` 单例入口访问
- 全局配置建议在 `Application.onCreate()` 中一次性完成，避免多次调用
- `application()` 为基础配置，否则部分功能（如缓存、日志）无法正常工作
- 全局参数（`globalParam` / `globalParams`）会自动附加到所有请求，单个请求可通过 `noUseGlobalParams()` 跳过

## 常用入口

除了通过 `Net.instance.configure {}` 统一配置外，以下入口提供了对网络库内部组件的直接访问：

```kotlin
// 直接获取共享的 OkHttpClient（用于第三方库集成）
val okHttpClient = Net.instance.okHttpClient

// 获取当前 NetConfig（只读）
val config = Net.instance.config

// Retrofit 声明式 API 入口（需引入 net-retrofit 模块）
val api = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<MyApiService>()
```

| 入口 | 类型 | 说明 |
|------|------|------|
| `Net.instance.okHttpClient` | `OkHttpClient` | 共享的 OkHttpClient，继承全部拦截器、超时、缓存配置 |
| `Net.instance.config` | `NetConfig` | 获取当前全局配置的只读快照 |
| `Net.instance.retrofit` | `NetRetrofit.Builder` | Retrofit 声明式 API 构建器入口（依赖 `net-retrofit` 模块） |

## 静态便捷方法

除了 `Net.instance.get()` 等形式，`Net` 也提供了静态便捷方法，效果完全相同：

```kotlin
// 以下两行等价
Net.get().url("...").send(callback)
Net.instance.get().url("...").send(callback)
```

| 静态方法 | 等价于 | 说明 |
|------|------|------|
| `Net.get()` | `Net.instance.get()` | GET 请求 |
| `Net.postJson()` | `Net.instance.postJson()` | POST JSON 请求 |
| `Net.postForm()` | `Net.instance.postForm()` | POST Form 请求 |
| `Net.postFile()` | `Net.instance.postFile()` | 文件上传 |
| `Net.postMultipart()` | `Net.instance.postMultipart()` | Multipart 上传 |
| `Net.postContent()` | `Net.instance.postContent()` | 自定义 Content-Type |
| `Net.download()` | `Net.instance.newDownload()` | 创建下载任务 |
| `Net.cancelAllRequests()` | `Net.instance.cancelAll()` | 取消所有请求 |
| `Net.cancelRequest(tag)` | `Net.instance.cancel(tag)` | 按 tag 取消请求 |

## 模块关系

```
app
└── implementation project(':net-retrofit')
    ├── api project(':net-flow')
    │   ├── api project(':net')
    │   │   ├── OkHttp 4.9.2
    │   │   └── AndroidX appcompat / core-ktx
    │   └── kotlinx-coroutines 1.7.3 + Gson
    └── Retrofit 2.9.0 + converter-gson + converter-scalars
```

## 验证方式

- 编译通过：`./gradlew :app:assembleDebug`
- 运行时确认 `Net.instance.configure {}` 无异常
- 发一个测试请求确认能收到回调

[返回 README](../../README.md)
