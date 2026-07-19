# 12. 网络监控

如何使用 `MonitorInterceptor` 自动采集网络请求质量数据（耗时、失败率、错误类型等），通过可替换的上报处理器发送到监控服务器。

## 适用条件

- 需要监控线上网络请求质量
- 需要排查 DNS/连接/超时/HTTP 错误等网络问题
- 对 net / net-flow / net-retrofit 三个模块均透明，业务代码零侵入

## 推荐做法

### 快速开始

```kotlin
Net.instance.configure {
    app(this@MyApp)

    monitor {
        enabled(true)                                    // 【必选】全局开启
        reportUrl("https://monitor.example.com/api/v1/report")
        reportMode(ReportMode.FAILURE_ONLY)              // 仅上报失败（默认）
        batchSize(30)                                    // 攒够 30 条批量上报
        flushIntervalMs(15_000L)                         // 每 15 秒刷新一次
        slowRequestThresholdMs(3000)                     // 3 秒以上算慢请求
    }
}

// 业务代码完全无感 —— 所有请求自动监控失败
Net.instance.postJson()
    .url("https://api.example.com/login")
    .addParam("username", "admin")
    .send(callback)
// 失败时自动上报到监控服务器，无需手动干预
```

### 上报模式

```kotlin
monitor {
    // 模式一：FAILURE_ONLY（默认）—— 仅上报失败
    reportMode(ReportMode.FAILURE_ONLY)

    // 模式二：ALL —— 上报所有请求（成功 + 失败）
    reportMode(ReportMode.ALL)

    // 模式三：SLOW_ONLY —— 仅上报成功但慢的请求
    reportMode(ReportMode.SLOW_ONLY)
    slowRequestThresholdMs(2000)  // 超过 2 秒的成功请求才上报
}
```

| 模式 | 上报对象 | 适用场景 |
|---|---|---|
| `FAILURE_ONLY` | 异常 + HTTP 4xx/5xx + 被取消 | **默认推荐**，聚焦故障排查 |
| `ALL` | 全部请求 | 全量监控、耗时分析、SLA 统计 |
| `SLOW_ONLY` | 成功但超过阈值的请求 | 性能优化、慢请求治理 |

### 采样率控制

```kotlin
monitor {
    sampleRate(0.1f)  // 仅 10% 请求上报（reportMode = ALL 时有效）
}
```

### 单请求控制

优先级**高于全局配置**：

```kotlin
// 强制监控：无视全局 enabled=false
Net.instance.postJson()
    .url("https://api.example.com/payment/create")
    .addParam("amount", "100")
    .monitor()  // ← 单请求强制开启监控
    .send(callback)

// 强制跳过：无视全局 enabled=true（心跳、轮询等高频接口）
Net.instance.get()
    .url("https://api.example.com/heartbeat")
    .skipMonitor()  // ← 单请求强制跳过
    .send(callback)
```

**优先级链**：

```
1. .monitor()        → 强制开启
2. .skipMonitor()    → 强制跳过
3. MonitorConfig.enabled → 全局兜底
```

下载任务也支持相同的控制语义：

```kotlin
Net.instance.newDownload()
    .savePath("${filesDir}/patch.apk")
    .url("https://cdn.example.com/patch.apk")
    .monitor()   // 强制监控该下载任务
    .start()

Net.instance.newDownload()
    .savePath("${cacheDir}/temp.bin")
    .url("https://cdn.example.com/temp.bin")
    .skipMonitor()  // 强制跳过
    .start()
```

### 自定义上报处理器

#### 接入 Firebase Crashlytics

```kotlin
class FirebaseReportHandler : IMonitorReportHandler {
    override val isAsync: Boolean get() = true  // SDK 内部已异步

    override fun onEvent(event: MonitorEvent) {
        if (!event.isSuccess) {
            FirebaseCrashlytics.getInstance().log(event.toJson().toString())
            if (event.httpCode >= 500) {
                FirebaseCrashlytics.getInstance().recordException(
                    Exception("ServerError: ${event.url} -> ${event.httpCode}")
                )
            }
        }
    }
    override fun flush() {}
    override fun shutdown() {}
}

monitor { enabled(true); reportHandler(FirebaseReportHandler()) }
```

#### 写本地日志文件

```kotlin
class FileReportHandler(private val logFile: File) : IMonitorReportHandler {
    private val writer = logFile.bufferedWriter()

    override fun onEvent(event: MonitorEvent) {
        synchronized(writer) {
            writer.write(event.toJson().toString())
            writer.newLine(); writer.flush()
        }
    }
    override fun flush() {}
    override fun shutdown() { synchronized(writer) { writer.close() } }
}
```

#### 组合内置 Handler（生产环境推荐）

```kotlin
monitor {
    enabled(true)
    reportHandler(ResilientReportHandler(
        delegate = DefaultMonitorReportHandler(
            reportUrl = "https://monitor.example.com/api/report",
            batchSize = 30, flushIntervalMs = 15_000L, maxQueueSize = 1000
        ),
        localFile = File(cacheDir, "network_monitor_backup.log"),
        maxLocalEvents = 200
    ))
}
```

### URL 脱敏

默认自动遮蔽常见敏感 query 参数（token, sessionId, password 等 9 种）：

```kotlin
// 自定义脱敏
monitor {
    urlSanitizer = { url ->
        url.replace(Regex("([?&])(userId|customParam)=[^&]*",
            RegexOption.IGNORE_CASE), "$1$2=***")
    }
}
```

### 生命周期管理

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Net.instance.configure {
            app(this@MyApp)
            monitor {
                enabled(true)
                reportUrl("https://monitor.example.com/api/report")
            }
        }
        // App 进入后台时刷新
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                Net.instance.flushMonitor()
            }
        })
    }
    override fun onTerminate() {
        super.onTerminate()
        Net.instance.shutdownMonitor()
    }
}
```

## 错误分类

`MonitorInterceptor.classifyError()` 自动将异常分类：

| ErrorType | 触发条件 | 排查方向 |
|---|---|---|
| `NONE` | 无异常且 HTTP 2xx | 正常 |
| `DNS_ERROR` | `UnknownHostException` | DNS 故障、域名拼写错误 |
| `CONNECT_TIMEOUT` | `ConnectException`（不含 "refused"） | 服务器不可达、防火墙屏蔽 |
| `CONNECT_REFUSED` | `ConnectException`（含 "refused"） | 端口未监听、服务拒绝连接 |
| `SSL_ERROR` | `SSLException` | 证书过期/不匹配、TLS 版本不兼容 |
| `TIMEOUT` | `SocketTimeoutException` | 网络延迟过高、服务端处理超时 |
| `NO_ROUTE` | 连接池耗尽或无可用路由 | 代理配置错误、网络切换中 |
| `HTTP_CLIENT_ERROR` | HTTP 4xx | 参数错误、未授权、无权限 |
| `HTTP_SERVER_ERROR` | HTTP 5xx | 服务端错误、网关超时 |
| `PARSE_ERROR` | 响应解析失败 | JSON 格式错误、数据模型不匹配 |
| `CANCELLED` | 请求被取消 | Activity 销毁、手动取消 |
| `DOWNLOAD_STREAM_ERROR` | 下载流读取/完整性异常 | 响应体为空、流读取中断 |
| `DISK_WRITE_ERROR` | 磁盘写入/文件操作失败 | 目录创建失败、写文件失败 |
| `MD5_MISMATCH` | 下载完成后 MD5 校验失败 | 文件损坏、断点续传内容不一致 |
| `UNKNOWN` | 其他未分类异常 | 需人工排查 |

## MonitorEvent 数据模型

```json
{
  "requestId": "a1b2c3d4_1719000000000_42",
  "url": "https://api.example.com/login",
  "method": "POST",
  "tag": "loginTask",
  "totalCostMs": 5200,
  "httpCode": 500,
  "responseBodySize": 256,
  "contentType": "application/json; charset=utf-8",
  "isSuccess": false,
  "errorType": "HTTP_SERVER_ERROR",
  "errorMessage": null,
  "exceptionClass": null,
  "networkType": "WIFI",
  "eventStage": "HTTP",
  "timestamp": 1719000000000,
  "extra": "orderId=ORD-2024",
  "downloadSize": 0,
  "contentLength": 0,
  "isAppend": false,
  "retryCount": 0,
  "downloadSpeed": 0,
  "downloadError": "NONE",
  "carrierName": null
}
```

## MonitorConfig DSL 方法速查

| 方法 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled(bool)` | 必选 | `false` | 全局监控开关 |
| `disabled()` | 可选 | — | 等同于 `enabled(false)`，语义更明确 |
| `reportUrl(url)` | 可选 | `null` | HTTP 批量上报地址 |
| `reportMode(mode)` | 可选 | `FAILURE_ONLY` | 上报模式 |
| `sampleRate(rate)` | 可选 | `1.0f` | 采样率 0.0~1.0 |
| `batchSize(size)` | 可选 | `20` | 批量上报最大事件数 |
| `flushIntervalMs(ms)` | 可选 | `10000` | 上报时间窗口 |
| `maxQueueSize(size)` | 可选 | `1000` | 内存队列最大容量 |
| `slowRequestThresholdMs(ms)` | 可选 | `0` | 慢请求阈值 |
| `urlSanitizer(fn)` | 可选 | 内置脱敏 | URL 脱敏函数 |
| `reportHandler(handler)` | 可选 | `null` | 自定义上报处理器 |

## IMonitorReportHandler 接口

| 成员 | 类型 | 说明 |
|---|---|---|
| `isAsync` | `Boolean` | `true`→框架内联调用；`false`→框架创建专用线程 |
| `onEvent(event)` | 方法 | 接收事件，应尽快返回 |
| `flush()` | 方法 | 主动刷新缓冲区 |
| `shutdown()` | 方法 | 关闭并释放资源 |

## 内置上报处理器

| 类 | `isAsync` | 说明 |
|---|---|---|
| `DefaultMonitorReportHandler` | `true` | HTTP 批量上报（队列+后台线程+三态熔断器+指数退避） |
| `ResilientReportHandler` | `false` | 本地文件兜底（含磁盘 I/O），线程安全 |

## Net 级别监控生命周期 API

```kotlin
// 主动刷新缓冲区（App 进后台时调用）
Net.instance.flushMonitor()

// 异步关闭监控，优雅释放资源（OkHttpClient 重建前）
Net.instance.shutdownMonitorAsync()

// 同步阻塞关闭监控，确保数据不丢失（进程终止前）
Net.instance.shutdownMonitorBlocking()
```

| 方法 | 行为 | 调用时机 |
|------|------|---------|
| `flushMonitor()` | 立即排空内存队列并 POST | App 进后台、即将终止 |
| `shutdownMonitorAsync()` | 异步关闭：等待队列排空 → 释放线程池 → 释放网络连接 | 切换环境 / 重建 OkHttpClient |
| `shutdownMonitorBlocking()` | 同步阻塞关闭，等待全部完成后返回 | 进程 `onTerminate()` |

## 架构概览

```
Request → [EncryptInterceptor] → [MonitorInterceptor] → [HttpLoggingInterceptor] → Server
                                       │
                                       ▼
                           ┌─────────────────────┐
                           │  MonitorInterceptor  │
                           │  1. 判断开关优先级    │
                           │  2. 记录请求时间戳    │
                           │  3. 前置过滤          │
                           │  4. 构建 MonitorEvent  │
                           │  5. 投递给上报处理器  │
                           └────────┬────────────┘
                                    │
                           ┌────────▼────────────┐
                           │ IMonitorReportHandler │
                           └──┬──────────┬───────┘
                              │          │
                   ┌──────────▼──┐  ┌───▼──────────┐
                   │ 默认 HTTP   │  │ 自定义实现    │
                   │ 批量上报    │  │ Firebase/File │
                   └─────────────┘  └──────────────┘
```

## 性能设计

| 场景 | 额外开销 | 说明 |
|---|---|---|
| 跳过监控的请求 | ~0.01ms | 两次 tag 读取 + boolean 判断 |
| 成功请求 + FAILURE_ONLY | ~0.02ms | 前置过滤跳过事件构建 |
| 失败请求 + FAILURE_ONLY | ~0.10ms | 构建事件 + 入队（JSON 序列化在后台线程） |
| ALL 模式 | ~0.10ms | 全部构建事件，序列化异步 |

## 关键说明

- 监控在 OkHttp 拦截器层工作，对上层调用方式（回调/Flow/Retrofit）完全透明
- `MonitorInterceptor` 在 `EncryptInterceptor` 之后，确保上报的 URL 不包含请求体明文
- `DefaultMonitorReportHandler` 使用三态熔断器（全开/半开/关闭）+ 指数退避重试
- `ResilientReportHandler` 在进程被杀时通过本地文件兜底未上报事件
- `flushMonitor()` 在 App 进入后台时调用，防止队列中事件丢失
- `shutdownMonitor()` 在进程终止/切换环境时调用，优雅释放资源

## 验证方式

- 使用 Log 上报处理器（见上方示例），确认失败请求被监控
- 在 `reportUrl` 的服务器端查看是否收到上报数据
- 使用 `.skipMonitor()` 标记心跳接口，确认不产生监控事件

[返回 README](../../README.md)
