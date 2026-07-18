# Net 网络请求库

Net 是一款基于 OkHttp 4.9.2 封装的 Android 网络请求库，提供链式 Builder API、Kotlin Flow 协程支持、Retrofit 声明式集成、字段级加解密和网络质量监控。包含三个模块，按需引入。

## 模块概览

| 模块 | 说明 | 依赖方式 |
|---|---|---|
| `net` | 核心库，Builder 模式请求 + 文件下载 + 字段加密 + 网络监控 | `implementation project(':net')` |
| `net-flow` | Kotlin Flow 扩展，协程/流式调用 + 反序列化 | `implementation project(':net-flow')` |
| `net-retrofit` | Retrofit 声明式 API 集成 | `implementation project(':net-retrofit')` |

## 使用场景总览

| 使用场景 | 推荐做法 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
|---|---|---|---|---|
| [快速开始与初始化](./net/doc/01-quick-start.md) | `Net.instance.configure { }` DSL 全局配置 | Android 5.0+，Kotlin/Java | 首次集成 Net 库，需要初始化 | `NetConfig` DSL 统一入口，一次配置全局生效 |
| [发送各类 HTTP 请求](./net/doc/02-basic-requests.md) | `get()` / `postJson()` / `postForm()` / `postFile()` / `postMultipart()` / `postContent()` | 所有请求类型，支持 Header/Cookie/Tag/缓存/生命周期 | 需要发送 GET/POST 请求，上传文件，提交表单 | Builder 模式链式调用，底层通过 OkHttp 执行 |
| [区分 HTTP 错误与网络异常](./net/doc/09-error-handling.md) | `sendResult(NetResultCallback)` / `flowResult()` | 适用于普通请求和 Flow 请求 | 需要把 4xx/5xx 与断网、超时、DNS 失败分开处理 | 2xx 进入成功分支，4xx/5xx 进入 `onHttpError`，IOException 进入 `onNetworkError` |
| [取消请求与生命周期](./net/doc/03-cancel-requests.md) | `cancel(tag)` / `autoCancel(activity)` / 协程取消 | 回调模式和 Flow 模式均支持 | 页面销毁时避免无效回调，手动取消请求 | LifecycleEventObserver 或协程结构化并发 |
| [文件下载](./net/doc/04-file-download.md) | `newDownload()` + `IProgressCallback` | 普通下载、断点续传需服务器支持 Range | 下载大文件、需要进度回调、支持断点续传 | OkHttp 下载 + 断点续传请求头 + 下载队列管理 |
| [全局配置](./net/doc/05-global-config.md) | `NetConfig` DSL 配置 Base URL/全局参数/拦截器/缓存/最大并行下载数 | 所有配置集中管理 | 需要统一配置网络行为 | `Net.instance` 单例持有全局配置，所有请求继承 |
| [工具类](./net/doc/06-utilities.md) | `StrTools` / `TaskTools` / `CacheControlFactory` / `JsonTools` / `PrintLog` | 无特殊限制 | 需要 MD5、JSON 合并、进度计算、调试日志等辅助功能 | 内置工具类覆盖常见网络开发需求 |
| [Flow 化请求](./net-flow/doc/01-flow-basics.md) | `flowString()` / `flowResponse(converter)` | 依赖 `net-flow` 模块，需 kotlinx-coroutines | 需要协程/Flow 风格调用，结构化并发 | `callbackFlow` 桥接 OkHttp Call，自动生命周期管理 |
| [下载进度 Flow](./net-flow/doc/02-flow-download.md) | `TaskBuilder.flow()` → `Flow<DownloadProgress>` | 依赖 `net-flow` 模块 | 需要通过 Flow 获取下载进度（替代回调） | `callbackFlow` 桥接下载回调事件 |
| [Retrofit 声明式 API](./net-retrofit/doc/01-retrofit-basics.md) | `@GET`/`@POST` 注解 + `suspend` 函数 + `NetResponse<T>` | 依赖 `net-retrofit` 模块，需 Retrofit 2.9.0 | 需要接口+注解方式定义 API，统一管理 | 复用 Net 全局 OkHttpClient，支持 suspend/Flow/NetResponse |
| [Retrofit 高级配置](./net-retrofit/doc/02-retrofit-advanced.md) | 自定义 Converter/CallAdapter、多 Base URL、独立 OkHttpClient | 依赖 `net-retrofit` 模块 | 需要 Moshi/Jackson 替代 Gson，或对接多个后端 | `NetRetrofit.Builder` 提供完全自定义能力 |
| [字段加密](./net/doc/07-field-encryption.md) | `encrypt { }` DSL + `Algorithm.AES_GCM_NO_PADDING` | `net` 核心库内置，对 Flow/Retrofit 透明 | 需要保护密码、手机号等敏感字段在传输中的安全 | `EncryptInterceptor` 在 OkHttp 层透明加解密 |
| [网络监控](./net/doc/08-network-monitor.md) | `monitor { }` DSL + `FAILURE_ONLY` / `ALL` / `SLOW_ONLY` 模式 | `net` 核心库内置，对 Flow/Retrofit 透明 | 需要监控线上请求质量、排查 DNS/超时/HTTP 错误 | `MonitorInterceptor` 在 OkHttp 层自动采集并上报 |

## 文档目录

| 文档 | 内容 |
|---|---|
| **net 核心库** | |
| [01. 快速开始与初始化](./net/doc/01-quick-start.md) | 依赖引入、Application 初始化、模块关系 |
| [02. 基本请求](./net/doc/02-basic-requests.md) | GET/POST JSON/Form/File/Multipart/Content/Resume + 通用 Builder 功能 |
| [03. 请求取消与生命周期](./net/doc/03-cancel-requests.md) | cancel/tag/autoCancel、协程取消、取消下载 |
| [04. 文件下载](./net/doc/04-file-download.md) | 基础下载、断点续传、进度监听、生命周期绑定、全局监听 |
| [05. 全局配置](./net/doc/05-global-config.md) | NetConfig 所有配置项、完整配置示例 |
| [06. 工具类](./net/doc/06-utilities.md) | StrTools、TaskTools、CacheControlFactory、JsonTools、PrintLog |
| [07. 字段加密](./net/doc/07-field-encryption.md) | 快速开始、加密模式、算法选择、密钥管理、EncryptUtil |
| [08. 网络监控](./net/doc/08-network-monitor.md) | 快速开始、上报模式、自定义上报处理器、错误分类、生命周期管理 |
| [09. HTTP 错误与网络异常处理](./net/doc/09-error-handling.md) | sendResult、onHttpError、onNetworkError、flowResult、4xx/5xx 处理 |
| **net-flow 模块** | |
| [09. Flow 化请求](./net-flow/doc/01-flow-basics.md) | flowString、flowResponse、反序列化、NetResponse、retry、便捷方法 |
| [10. 下载进度 Flow](./net-flow/doc/02-flow-download.md) | TaskBuilder.flow、DownloadProgress、DownloadPhase、取消 |
| **net-retrofit 模块** | |
| [11. Retrofit 声明式 API](./net-retrofit/doc/01-retrofit-basics.md) | Service 定义、NetRetrofit 构建、suspend/Flow/NetResponse 返回类型 |
| [12. Retrofit 高级配置](./net-retrofit/doc/02-retrofit-advanced.md) | 自定义 Converter/CallAdapter、多 Base URL、ProGuard |

## 快速开始

在 `Application.onCreate()` 中进行最小初始化：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.instance.configure {
            app(this@MyApp)
            url("https://api.example.com")
        }
    }
}
```

发起第一个请求：

```kotlin
Net.instance.get()
    .url("https://api.example.com/user/info")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { Log.e("TAG", "失败: $er") }
        override fun onResponse(result: String?, code: Int) { Log.d("TAG", "成功: $result") }
    })
```

更详细的初始化配置和模块依赖说明见 [01. 快速开始与初始化](./net/doc/01-quick-start.md)。

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

- `net` — 核心库，必选依赖。所有上层模块通过它共享 OkHttpClient 和全局配置
- `net-flow` — 可选，通过 `callbackFlow` 将回调桥接为 Kotlin Flow
- `net-retrofit` — 可选，将 Retrofit 的 OkHttpClient 替换为 Net 的全局实例

## 线程模型速查

| 调用方式 | 结果所在线程 | 能否直接操作 UI |
|---|---|---|
| `send(DdCallback)` | OkHttp 线程池（后台） | ❌ 需 `runOnUiThread` |
| `send(Handler, what, errorWhat)` | Handler 对应 Looper | 取决于 Handler |
| `flowString().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |
| `flowResponse().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |
| Retrofit `suspend fun` | 调用方协程上下文（默认 Main） | ✅ |
| `TaskBuilder.flow().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |

## 拦截器链顺序

```
Request → [用户自定义 Interceptor] → [EncryptInterceptor] → [MonitorInterceptor] → [HttpLoggingInterceptor] → Server
```

> `EncryptInterceptor` 在 `MonitorInterceptor` 之前，确保监控上报的 URL 不包含请求体明文。

## 发布

通过 JitPack 发布多模块：

```yaml
# jitpack.yml
jdk:
  - openjdk11
install:
  - ./gradlew clean :net:publishToMavenLocal :net-flow:publishToMavenLocal :net-retrofit:publishToMavenLocal
```
