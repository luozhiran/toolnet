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
| [快速开始与初始化](./net/doc/01-quick-start.md) | `Net.configure { application(...); baseUrl(...) }` DSL 全局配置 | Android 5.0+，Kotlin/Java | 首次集成 Net 库，需要初始化 | `NetConfig` DSL 统一入口，一次配置全局生效 |
| [发送 GET/POST 请求](./net/doc/02-basic-requests.md) | `get()` / `postJson()` / `postForm()` / `postFile()` / `postMultipart()` / `postContent()` / `builder(ModeType.PostResume)` | GET、POST JSON、Form、文件上传、Multipart 混合、自定义 Content-Type、断点续传上传 | 发送各类 HTTP 请求，上传文件，提交表单 | Builder 模式链式调用，底层通过 OkHttp 执行 |
| [Handler 回调接收结果](./net/doc/02-basic-requests.md) | `send(handler, what, errorWhat)` | 需要在特定 Looper 线程接收结果 | 不想手写 DdCallback，直接通过 Handler Message 在主线程处理成功/失败 | 内部构造 OkHttp Callback，收到响应后通过 Handler.sendMessage 分发 |
| [构建 Call 不发送](./net/doc/02-basic-requests.md) | `buildCall()` | 需自行管理 OkHttp Call 生命周期 | 扩展模块（net-flow）或需手动 enqueue/execute 场景 | 复用 Builder 的参数组装逻辑，开放 OkHttp Call 给调用方 |
| [监控业务附加字段](./net/doc/02-basic-requests.md) | `builder.monitorExtra("orderId=123")` | 全局监控已开启 | 需要在监控事件中携带业务标识（订单号、场景等） | 通过 OkHttp Typed Tag 透传到 MonitorEvent.extra |
| [区分 HTTP 错误、网络异常与业务码](./net/doc/09-error-handling.md) | `sendResult()` / `sendBusinessResult()` / `sendTypedBusinessResult<T>()` | 适用于回调模式、Flow 模式和 Retrofit Flow | 需要把 4xx/5xx、断网超时、登录失效等业务码分开处理，或把 `data` 直接转成业务类 | HTTP 层先分流，再通过业务责任链统一处理 `code/status`，成功后可用 Gson 转成 `T` |
| [自定义业务协议解析](./net/doc/09-error-handling.md) | `businessEnvelopeParser(customParser)` | 后端业务协议与默认 `code/message/data` 不同 | 后端用 `bizCode/bizMsg/payload` 等非标准字段 | 实现 `ApiEnvelopeParser` 接口替换默认解析器 |
| [自定义业务数据转换器](./net/doc/09-error-handling.md) | `businessConverter(moshiConverter)` | 不想用 Gson，需要 Moshi/Jackson 等 | 替换 JSON 库或自定义反序列化逻辑 | 实现 `BusinessDataConverter` 接口替换默认 Gson 转换 |
| [取消请求与生命周期](./net/doc/03-cancel-requests.md) | `cancel(tag)` / `autoCancel(activity)` / 协程取消 / `cancelAll()` / `cancelFirstTag(tag)` | 回调模式和 Flow 模式均支持 | 页面销毁时避免无效回调，手动取消请求 | LifecycleEventObserver 或协程结构化并发 |
| [文件下载](./net/doc/04-file-download.md) | `newDownload()` + `IProgressCallback` | 普通下载、断点续传需服务器支持 Range | 下载大文件、需要进度回调、支持断点续传 | OkHttp 下载 + 断点续传请求头 + 下载队列管理 + 统一下载监听注册表 |
| [断点续传下载](./net/doc/04-file-download.md) | `newDownload().supportCheckpoint().start()` | 服务器需支持 Range 请求（返回 206） | 大文件下载中断后从断点继续 | 首次请求获取已接收偏移量，以 Range 头续传 |
| [下载 + Activity 生命周期](./net/doc/04-file-download.md) | `newDownload().bindActivity(activity).start()` | FragmentActivity | Activity 销毁时自动取消下载并释放监听器 | LifecycleEventObserver 监听 ON_DESTROY |
| [下载监控事件](./net/doc/04-file-download.md) | `newDownload().monitor().monitorExtra(...)` + 全局 `monitor { enabled(true) }` | 下载任务 | 需要监控下载速度、流读取错误、磁盘写入错误、MD5 校验失败 | BaseRequest 在流读写各阶段调用 reportDownloadEvent，eventStage="DOWNLOAD" |
| [全局配置](./net/doc/05-global-config.md) | `application` / `baseUrl` / `globalParam` / `interceptor` / `httpCache` / `businessInterceptor` | 所有配置集中管理 | 需要统一配置网络行为 | `Net` 单例持有全局配置，所有请求继承 |
| [工具类](./net/doc/06-utilities.md) | `StrTools` / `TaskTools` / `CacheControlFactory` / `JsonTools` / `PrintLog` | 无特殊限制 | 需要 MD5、JSON 合并、进度计算、调试日志等辅助功能 | 内置工具类覆盖常见网络开发需求 |
| [HTTP 日志](./net/doc/05-global-config.md) | `enableHttpLog(true)` + `logPath(path)` | 开发/测试环境 | 调试时需要查看完整的 HTTP 请求/响应内容 | HttpLogger 作为 NetworkInterceptor 打印方法、URL、状态码、耗时、Headers、Body 预览 |
| [HTTP 缓存](./net/doc/05-global-config.md) | `cache(Cache(dir, size))` + `addCacheControl(cacheControl)` | 需缓存 GET 响应以减少网络请求 | 设置 OkHttp Cache，通过 CacheControl 控制策略 |
| [全局参数管理](./net/doc/05-global-config.md) | `globalParam(key, value)` / `globalParams(map)` / `removeGlobalParams(key)` / `clearGlobalParameters()` | 所有请求自动附带 token、版本号等公共参数 | 全局参数在 URL 拼接或 Body 构建时自动注入，单请求可跳过 |
| [自定义 OkHttpClient](./net/doc/05-global-config.md) | `client(customClient)` | 需完全控制 OkHttp 配置（证书固定、代理、DNS） | 直接注入构建好的 OkHttpClient |
| [字段加密 — 全局配置](./net/doc/07-field-encryption.md) | `encrypt { algorithm(...); secretKey(...); encryptField("password") }` | AES 对称加密，对 JSON/Form Body 中匹配字段透明加解密 | 保护密码、手机号、银行卡号等敏感字段 | EncryptInterceptor 在 OkHttp 拦截器层工作 |
| [字段加密 — 加密模式](./net/doc/07-field-encryption.md) | `OPT_OUT`（全量+skipPath排除）/ `OPT_IN`（仅encryptPath生效） | 根据接口加密比例选择 | 大部分接口需要加密选 OPT_OUT，少数接口需要选 OPT_IN | 单请求可通过 `.encrypt()` / `.skipEncrypt()` 覆盖 |
| [字段加密 — 规则匹配](./net/doc/07-field-encryption.md) | `encryptField(name)` / `encryptPattern(regex)` / `encryptPath(pathRegex, fields)` / `decryptField(name)` | 字段名精确匹配、正则匹配、路径+字段组合 | 不同接口加密不同字段 | 规则支持双向/仅请求/仅响应三种方向 |
| [字段加密 — EncryptUtil 独立使用](./net/doc/07-field-encryption.md) | `EncryptUtil.encrypt()` / `EncryptUtil.decrypt()` / `generateAesKey()` / `generateIv()` | 不使用拦截器，在业务代码中直接加解密 | SharedPreferences 加密存储、本地数据保护 | ThreadLocal 缓存 Cipher 实例，线程安全 |
| [网络监控 — 快速开始](./net/doc/08-network-monitor.md) | `monitor { enabled(true); reportUrl(...); reportMode(...) }` | 生产环境 | 需要监控线上请求的成功率、耗时、错误分布 | MonitorInterceptor 在 OkHttp 层自动采集并上报 |
| [网络监控 — 上报模式](./net/doc/08-network-monitor.md) | `FAILURE_ONLY` / `ALL` / `SLOW_ONLY` | 不同监控粒度需求 | 仅关注故障选 FAILURE_ONLY，全量分析选 ALL，性能优化选 SLOW_ONLY | reportMode + slowRequestThresholdMs 控制采样 |
| [网络监控 — 自定义上报](./net/doc/08-network-monitor.md) | 实现 `IMonitorReportHandler` + `reportHandler(...)` | 需对接 Firebase、自建平台、写本地文件 | 内置 `DefaultMonitorReportHandler`（HTTP 批量上报+熔断）+ `ResilientReportHandler`（本地兜底） | 可完全替换上报实现，支持同时多个后端 |
| [网络监控 — 错误分类](./net/doc/08-network-monitor.md) | DNS_ERROR / CONNECT_TIMEOUT / CONNECT_REFUSED / SSL_ERROR / TIMEOUT / HTTP_CLIENT_ERROR / HTTP_SERVER_ERROR / CANCELLED / DOWNLOAD_STREAM_ERROR / DISK_WRITE_ERROR / MD5_MISMATCH 等 | 自动分类，业务方只需读取 errorType | MonitorInterceptor.classifyError() 根据异常类型和 HTTP 状态码自动分类 |
| [网络监控 — 生命周期](./net/doc/08-network-monitor.md) | `Net.instance.flushMonitor()` / `Net.instance.shutdownMonitor()` | App 进后台时刷盘 / OkHttpClient 重建前释放 | flush 清空内存队列并 POST；shutdown 等待完成并关闭线程池 |
| [网络监控 — URL 脱敏](./net/doc/08-network-monitor.md) | `urlSanitizer = { url -> ... }` | 上线前必配 | 避免 token/sessionId/password 等敏感参数泄露到监控系统 | 内置脱敏默认移除 9 种常见敏感参数，可替换 |
| [Flow 化请求（net-flow）](./net-flow/doc/01-flow-basics.md) | `flowString()` / `flowResult()` / `flowBusinessResult()` / `flowTypedBusinessResult<T>()` | 依赖 `net-flow` 模块，需 kotlinx-coroutines | 需要协程/Flow 风格调用，结构化并发 | `callbackFlow` 桥接 OkHttp Call，自动生命周期管理 |
| [Flow + 反序列化（net-flow）](./net-flow/doc/01-flow-basics.md) | `flowResponse(converter)` / `GsonNetConverter(type)` / 自定义 `NetConverter` | 需在 Flow 中直接将响应体转为业务对象 | flowResponse 接收 NetConverter，回调中同步执行 |
| [便捷 Flow 入口（net-flow）](./net-flow/doc/01-flow-basics.md) | `Net.flowGet { }` / `Net.flowPostJson { }` / `Net.flowPostForm { }` / `Net.flowGetResponse(converter) { }` / `Net.flowPostJsonResponse(converter) { }` | 快速创建 Flow 请求 | 封装 get().flowString() 调用链，减少 Builder 样板代码 |
| [下载进度 Flow（net-flow）](./net-flow/doc/02-flow-download.md) | `TaskBuilder.flow()` → `Flow<DownloadProgress>` / `Net.flowDownload { }` | 依赖 `net-flow` 模块 | 需要通过 Flow 获取下载进度（替代回调） | 通过 `addDownloadListener` 接入统一下载监听注册表，Connecting/Downloading/Complete/Failed 四阶段 |
| [Retrofit 声明式 API（net-retrofit）](./net-retrofit/doc/01-retrofit-basics.md) | `@GET`/`@POST` 注解 + `suspend` 函数 + `Flow<BusinessResult>` | 依赖 `net-retrofit` 模块，需 Retrofit 2.9.0 | 需要接口+注解方式定义 API，统一管理 | 复用 Net 全局 OkHttpClient，支持 suspend/Flow/NetResponse/NetResult/BusinessResult/TypedBusinessResult |
| [Retrofit Flow 返回类型（net-retrofit）](./net-retrofit/doc/01-retrofit-basics.md) | `Flow<T>` / `Flow<NetResponse<T>>` / `Flow<NetResult>` / `Flow<BusinessResult>` / `Flow<TypedBusinessResult<T>>` | 声明式接口支持所有 Net 体系的结构化结果类型 | NetFlowCallAdapterFactory 自动解析返回类型，桥接 Retrofit Call |
| [Retrofit 高级配置（net-retrofit）](./net-retrofit/doc/02-retrofit-advanced.md) | 自定义 Converter/CallAdapter、多 Base URL、独立 OkHttpClient | 需要 Moshi/Jackson 替代 Gson，或对接多个后端 | `NetRetrofit.Builder` 提供完全自定义能力 |

## 文档目录

| 文档 | 内容 |
|---|---|
| **net 核心库** | |
| [01. 快速开始与初始化](./net/doc/01-quick-start.md) | 依赖引入、Application 初始化、模块关系 |
| [02. 基本请求](./net/doc/02-basic-requests.md) | GET/POST JSON/Form/File/Multipart/Content/Resume + 通用 Builder 功能 + Handler 回调 + buildCall + monitorExtra |
| [03. 请求取消与生命周期](./net/doc/03-cancel-requests.md) | cancel/tag/cancelAll/cancelFirstTag、autoCancel、协程取消、取消下载 |
| [04. 文件下载](./net/doc/04-file-download.md) | 基础下载、断点续传、进度监听、生命周期绑定、全局监听、下载监控事件 |
| [05. 全局配置](./net/doc/05-global-config.md) | NetConfig 所有配置项、完整配置示例、业务结果处理器配置 |
| [06. 工具类](./net/doc/06-utilities.md) | StrTools、TaskTools、CacheControlFactory、CacheFactory、PrintLog、JsonTools |
| [07. 字段加密](./net/doc/07-field-encryption.md) | 快速开始、加密模式、算法选择、密钥管理、EncryptUtil、规则匹配 |
| [08. 网络监控](./net/doc/08-network-monitor.md) | 快速开始、上报模式、自定义上报处理器、错误分类、生命周期管理 |
| [09. HTTP 错误、网络异常与业务码处理](./net/doc/09-error-handling.md) | sendResult、onHttpError、onNetworkError、业务码责任链、ApiEnvelopeParser、TypedBusinessResult、BusinessDataConverter |
| **net-flow 模块** | |
| [10. Flow 化请求](./net-flow/doc/01-flow-basics.md) | flowString、flowResult、flowBusinessResult、flowTypedBusinessResult、flowResponse、反序列化、NetResponse、retry、便捷方法 |
| [11. 下载进度 Flow](./net-flow/doc/02-flow-download.md) | TaskBuilder.flow、DownloadProgress、DownloadPhase、取消、Net.flowDownload |
| **net-retrofit 模块** | |
| [12. Retrofit 声明式 API](./net-retrofit/doc/01-retrofit-basics.md) | Service 定义、NetRetrofit 构建、suspend/Flow/NetResponse/NetResult/BusinessResult/TypedBusinessResult 返回类型 |
| [13. Retrofit 高级配置](./net-retrofit/doc/02-retrofit-advanced.md) | 自定义 Converter/CallAdapter、多 Base URL、ProGuard |

## 快速开始

在 `Application.onCreate()` 中进行最小初始化：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Net.configure {
            application(this@MyApp)
            baseUrl("https://api.example.com")
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
