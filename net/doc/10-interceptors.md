# 10. 拦截器体系

详细讲解 Net 库中所有拦截器的设计、工作原理、执行顺序、与单请求控制的交互，以及如何添加自定义拦截器。

## 适用条件

- 已完成初始化配置（见 [01-快速开始](./01-quick-start.md)）
- 需要使用字段加密、网络监控、HTTP 日志或自定义拦截器
- 需要理解拦截器链的执行顺序以排查问题

---

## 拦截器链全景

```
请求发起
    │
    ▼
┌──────────────────────────────────────────────┐
│              用户自定义 Interceptor            │  ← addInterceptor()
├──────────────────────────────────────────────┤
│               EncryptInterceptor              │  ← 字段加解密（encrypt {} 配置后自动注册）
├──────────────────────────────────────────────┤
│              MonitorInterceptor               │  ← 网络监控（monitor {} 配置后自动注册）
├──────────────────────────────────────────────┤
│   OkHttp 内置（重定向、缓存、连接等）            │
├──────────────────────────────────────────────┤
│              HttpLogger (Network)             │  ← HTTP 日志（enableHttpLog() 后自动注册）
└──────────────────────────────────────────────┘
    │
    ▼
  服务器
```

**关键设计决策：**

1. **用户拦截器最先执行** — 用户可以修改请求（如添加 Auth Token Header），后续拦截器看到的已经是修改后的请求
2. **EncryptInterceptor 在 MonitorInterceptor 之前** — 监控上报的 URL 和元信息中不会包含请求体明文（加密已完成）
3. **HttpLogger 使用 `NetworkInterceptor`** — 注册在最外层，能记录真实的网络请求/响应（包括 OkHttp 自动添加的 Headers）
4. **所有拦截器对上层透明** — 无论使用回调（`send()`）、Flow（`flowString()`）、Retrofit（`@GET`），都会经过相同的拦截器链

---

## 拦截器类型对比

| 类型 | 注册方式 | 回调时机 | 适用场景 |
|------|---------|---------|---------|
| **Application Interceptor** | `addInterceptor()` | 请求发起前（不经过 OkHttp 内部处理） | 添加 Header、参数、重试逻辑、加密、监控 |
| **Network Interceptor** | `addNetworkInterceptor()` | 请求即将发送到网络时（经过 OkHttp 内部处理） | 日志记录、缓存调试 |

```kotlin
// Application Interceptor —— 用户自定义和框架内置都用这个
Net.instance.configure {
    interceptor(object : Interceptor {
        override fun intercept(chain: Chain): Response {
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            return chain.proceed(request)
        }
    })
}

// Network Interceptor —— 仅 HttpLogger 使用
Net.instance.configure {
    enableHttpLog()
}
```

---

## 1. EncryptInterceptor（字段加解密拦截器）

### 职责

在 OkHttp 拦截器层对 JSON/Form 请求体中匹配规则的字段自动加密，对响应体中匹配规则的字段自动解密。**一次配置，所有模块生效**（net / net-flow / net-retrofit）。

### 注册条件

```kotlin
Net.instance.configure {
    encrypt {
        secretKey("my-32-byte-secret-key!!123456")
        encryptField("password")
    }
}
```

只有当 `encryptConfig` 不为 null 且 `hasValidConfig()` 返回 true（密钥已配置 + 至少一条规则）时才会注册。

### 内部工作流

```
intercept(chain)
    │
    ├── [请求阶段] shouldProcessRequest(request)?
    │       ├── true  → encryptRequest(request) → 修改 Request Body
    │       └── false → 原样透传
    │
    ├── chain.proceed(request) → 执行后续拦截器链，获取 Response
    │
    └── [响应阶段] shouldProcessResponse(response)?
            ├── true  → decryptResponse(response) → 修改 Response Body
            └── false → 原样透传
```

### 优先级链（由高到低）

| 优先级 | 判断条件 | 结果 | 使用方式 |
|--------|---------|------|---------|
| **1（最高）** | 请求级 `.encrypt()` | 强制加密，无视全局跳过规则 | `builder.encrypt().send()` |
| **2** | 请求级 `.skipEncrypt()` | 强制跳过，无视全局加密规则 | `builder.skipEncrypt().send()` |
| **3** | GET 请求 + `skipGetRequest = true` | 自动跳过（GET 没有 Body） | 全局配置默认行为 |
| **4** | 非文本 Body | 自动跳过（二进制/文件流不加密） | 自动检测 Content-Type |
| **5（最低）** | 全局 `EncryptMode` | 根据模式决定 | `encryptMode(OPT_IN)` / `encryptMode(OPT_OUT)` |

**单请求控制 > GET/非文本跳过 > 全局模式**

### 请求加密流程（`encryptRequest`）

```
RequestBody
    │
    ├── contentType 不是 application/json 或 x-www-form-urlencoded？ → 跳过
    ├── Body 字节数超过 maxBodyBytes？ → 跳过（保护内存）
    ├── body.readString() → 将 Body 读入内存
    │       │
    │       ├── 是 JSON → encryptJson()
    │       │       ├── hasRuleMatch() 快速预检（O(n) 子串匹配）
    │       │       │       ├── 无匹配 → 跳过（避免昂贵解析）
    │       │       │       └── 有匹配 → 解析 JSONObject/JSONArray
    │       │       │               ├── 递归遍历所有字段
    │       │       │               ├── 匹配规则 → EncryptUtil.encrypt()
    │       │       │               └── 不匹配 → 原样保留
    │       │       └── 重新序列化为 JSON 字符串 → 构建新 RequestBody
    │       │
    │       └── 是 Form → encryptForm()
    │               ├── 按 & 拆分 key=value 对
    │               ├── 匹配规则 → URL 解码 → 加密 → URL 编码
    │               └── 重新拼接为 Form 字符串 → 构建新 RequestBody
    │
    └── 构建新 Request，替换 Body
```

### 响应解密流程（`decryptResponse`）

结构与请求加密对称，但方向相反：
- 加密 → 解密
- `Request.Body` → `Response.Body`
- JSON → JSON
- Form → Form

### 快速预检机制（性能优化）

在完整解析 JSONObject 之前，先用 O(n) 子串匹配检查 body 中是否包含任何需要的字段名：

```kotlin
// 快速预检：检查 bodyString 中是否包含 "\"password\""
if (bodyString.contains("\"${rule.fieldName}\"")) return true
```

- `ByFieldName` 规则：检查 `"<字段名>"`
- `ByPath` 规则：先匹配路径正则，再检查字段名
- `ByFieldPattern` 规则：无法预检，保守返回 true（走完整解析）

**效果：** 对不需要加密的请求，避免了昂贵的 JSONObject 解析，仅付出一次 O(n) 字符串扫描的代价。

### 大小限制

```kotlin
// 全局配置
Net.instance.configure {
    encrypt {
        maxBodyBytes(128 * 1024)  // 默认 64KB
    }
}
```

- 超过限制的 Body **原样透传**（不加密不解密），保护内存
- 流式/分块传输的 Body（`contentLength = -1`）也跳过
- 不匹配规则的空 Body 用 `request.withBody(bodyString, contentType)` 缓冲重建（防止 OkHttp 的 `writeTo` 只能调用一次）

### 关键说明

- **Body 会被读入内存** — 加密操作需要完整 Body。默认 64KB 限制防止 OOM。大文件应走下载接口而非普通请求
- **递归深度无硬限制** — 依赖 JSONObject 的嵌套解析天然限制（栈深度 ≈ JSON 嵌套深度）
- **加密失败不阻塞请求** — 加密/解密异常被 catch 后，请求以未加密形式继续发送（日志中记录 warning）
- **Body 缓冲重建** — 即使字段不匹配也不需要加密，Body 也会被缓冲并用新 RequestBody 替换，因为原 RequestBody 的 `writeTo()` 已被消耗一次

---

## 2. MonitorInterceptor（网络监控拦截器）

### 职责

自动采集每次网络请求的元信息（URL、耗时、错误类型、HTTP 状态码等），通过可替换的上报处理器发送到监控服务。**对业务代码零侵入**。

### 注册条件

```kotlin
Net.instance.configure {
    monitor {
        enabled(true)
        reportUrl("https://monitor.example.com/api/report")
    }
}
```

只有当 `monitorConfig` 不为 null、且 `reportUrl` 或 `reportHandler` 有效时才会注册。

### 内部工作流

```
intercept(chain)
    │
    ├── [Step 1] shouldMonitor(request)?
    │       ├── false → chain.proceed(request)  快速路径，零开销
    │       └── true  → 继续
    │
    ├── [Step 2] 记录 requestStartMs → chain.proceed(request) → 记录 requestEndMs
    │       ├── 正常返回 → response
    │       └── 抛 IOException → 捕获 ioException
    │
    ├── [Step 3] 前置过滤（防止无效事件构建）
    │       ├── FAILURE_ONLY + isSuccess → 跳过（~95% 请求零开销）
    │       ├── ALL → 全部上报
    │       └── SLOW_ONLY + totalCostMs > threshold → 上报慢请求
    │
    └── [Step 4] needsReport?
            ├── true  → 构建 MonitorEvent → 投递给 IMonitorReportHandler
            │              ├── handler.isAsync → 内联调用（无线程切换）
            │              └── !handler.isAsync → executor.submit()（线程隔离）
            └── false → 返回 response
```

### 开关优先级

| 优先级 | 判断条件 | 结果 | 使用方式 |
|--------|---------|------|---------|
| **1（最高）** | 请求级 `.skipMonitor()` | 强制跳过 | `builder.skipMonitor().send()` |
| **2** | 请求级 `.monitor()` | 强制开启 | `builder.monitor().send()` |
| **3（最低）** | 全局 `enabled + sampleRate` | 兜底 | 全局配置 |

### 性能设计

| 场景 | 额外开销 | 说明 |
|------|---------|------|
| 跳过的请求（`shouldMonitor=false`） | ~0.01ms | 一次 Tag 读取 + boolean 判断 |
| 成功请求 + FAILURE_ONLY | ~0.02ms | 前置过滤跳过事件构建 |
| 失败请求 | ~0.1ms | 构建 MonitorEvent + 投递（JSON 序列化在后台线程） |
| ALL 模式 | ~0.1ms | 每个请求都构建事件 |

**关键优化：**

1. **前置过滤** — `FAILURE_ONLY` 模式下，成功的请求在第 3 步就被过滤掉，不会构建 `MonitorEvent`（避免约 20 个字段的对象分配和 URL 脱敏处理）
2. **非加密级请求 ID** — 使用 `AtomicLong + currentTimeMillis` 而非 `UUID.randomUUID()`（避免 `SecureRandom` 的熵池阻塞）
3. **智能线程隔离** — 根据 `IMonitorReportHandler.isAsync` 决定是否创建线程：Handler 已异步（如 `DefaultMonitorReportHandler`）时内联调用，消除线程切换开销；Handler 同步（如写文件）时才用独立线程

### 错误分类

`classifyError()` 将异常自动归类为 15 种类型：

| ErrorType | 触发条件的异常 |
|-----------|--------------|
| `DNS_ERROR` | `UnknownHostException` |
| `CONNECT_TIMEOUT` | `ConnectException`（不含 "refused"） |
| `CONNECT_REFUSED` | `ConnectException`（含 "refused"） |
| `SSL_ERROR` | `SSLException` |
| `TIMEOUT` | `SocketTimeoutException` |
| `HTTP_CLIENT_ERROR` | 无异常 + HTTP 4xx |
| `HTTP_SERVER_ERROR` | 无异常 + HTTP 5xx |
| `CANCELLED` | 请求被取消 |
| `NONE` | 无异常 + HTTP 2xx |
| `UNKNOWN` | 其他未分类异常 |

### 监控事件结构

```kotlin
MonitorEvent(
    requestId = "net_1719000000000_42",    // 非加密级唯一 ID
    url = "https://api.example.com/login", // 经 urlSanitizer 脱敏后
    method = "POST",
    tag = "loginTask",                     // 来自 builder.tag()
    requestStartMs = 1719000000000,
    requestEndMs = 1719000005200,
    totalCostMs = 5200,
    httpCode = 500,
    responseBodySize = 256,
    contentType = "application/json",
    isSuccess = false,
    errorType = HTTP_SERVER_ERROR,
    errorMessage = null,
    exceptionClass = null,
    networkType = "WIFI",                  // 来自 NetworkTypeCache 缓存
    extra = "orderId=ORD-2024",           // 来自 builder.monitorExtra()
)
```

### 关键说明

- **监控异常不影响业务** — `dispatchEvent()` 内部所有异常被静默捕获
- **URL 脱敏** — 默认移除 token、sessionId、password 等 9 种敏感 Query 参数
- **网络状态缓存** — 使用 `NetworkTypeCache` 避免每次都查询系统服务
- **关闭清理** — `shutdownMonitor()` 依次关闭 executor、monitorInterceptor、monitorReportHandler、networkTypeCache

---

## 3. HttpLogger（HTTP 日志拦截器）

### 职责

以结构化格式打印完整的 HTTP 请求/响应用于调试。**使用 `NetworkInterceptor`**，能看到 OkHttp 自动添加的 Headers（如 Host、Content-Length）。

### 注册条件

```kotlin
Net.instance.configure {
    enableHttpLog()     // 开启
    // disableHttpLog() // 关闭
}
```

只有当 `isHttpLogEnabled == true` 时才会注册为 `NetworkInterceptor`。

### 日志输出格式

```
+---------------- HTTP OK ----------------
| POST https://api.example.com/login
| Status   : 200 OK
| Duration : 320ms
| Type     : application/json; charset=utf-8
| Size     : 1.2KB
| Headers
|   Content-Type: application/json
|   Server: nginx
| Body
|   {"code":"0","message":"ok","data":{"id":1}}
+---------------------------------------------
```

### 关键设计

- **NetworkInterceptor** — 注册在最外层，能记录 OkHttp 自动添加的 Headers（如 `Host`、`Content-Length`、`Accept-Encoding`），而 Application Interceptor 看不到这些
- **Body 预览限制** — 超过 64KB 的响应体不预览（防止 OOM），超过 16K 字符截断，超过 120 行截断
- **流式内容跳过** — `event-stream`、`stream` 类型以及 `contentLength < 0` 的响应不缓冲预览
- **JSON 美化** — 识别 JSON 响应并自动 `formatJson()` 缩进美化
- **异常日志** — `chain.proceed()` 抛异常时单独输出错误格式日志

---

## 4. 用户自定义拦截器

### 添加方式

```kotlin
Net.instance.configure {
    // 单个拦截器
    interceptor(authInterceptor)

    // 批量添加
    interceptors(listOf(authInterceptor, headerInterceptor, retryInterceptor))

    // 读取已注册的拦截器（只读快照）
    val current = interceptors()
}
```

### 典型场景

```kotlin
// 1. 认证拦截器：自动添加 Token
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val token = tokenProvider() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

// 2. 请求重试拦截器（网络错误时自动重试）
class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Chain): Response {
        var retryCount = 0
        var lastException: IOException? = null
        while (retryCount <= maxRetries) {
            try {
                return chain.proceed(chain.request())
            } catch (e: IOException) {
                lastException = e
                retryCount++
                if (retryCount > maxRetries) throw e
            }
        }
        throw lastException!!
    }
}

// 3. 参数签名拦截器
class SignInterceptor(private val secret: String) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder()
            .addQueryParameter("timestamp", "${System.currentTimeMillis()}")
            .addQueryParameter("sign", generateSign(request, secret))
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
```

### 注意事项

- **不要修改已消费的 Body** — 拦截器链中的 `RequestBody.writeTo()` 只能调用一次。如果需要在拦截器中读取 Body，参考 `EncryptInterceptor` 的 `readString() + 重建` 模式
- **不要忘记调用 `chain.proceed()`** — 否则请求永远不会发送
- **不要做耗时操作** — 拦截器在 OkHttp 线程池中运行，阻塞会影响所有并发请求
- **自定义 `OkHttpClient` 时的行为** — 调用 `client(customClient)` 后，`interceptor()` 添加的拦截器**依然会**添加到该 client 上（`createClient()` 通过 `newBuilder()` 追加），但自定义 client 原本的拦截器也会被继承

---

## 5. 拦截器执行顺序验证

```
配置阶段 (OkHttpManager.createClient):

1. customClient.newBuilder() 或 OkHttpClient.Builder()
2. builder.addInterceptor(用户拦截器 1)      ← interceptor()
3. builder.addInterceptor(用户拦截器 2)
4. builder.addInterceptor(EncryptInterceptor)  ← encrypt {}
5. builder.addInterceptor(MonitorInterceptor)  ← monitor {}
6. builder.addNetworkInterceptor(HttpLogger)   ← enableHttpLog()
7. builder.build()

运行时:

Request
    → 用户拦截器 1.intercept()
        → 用户拦截器 2.intercept()
            → EncryptInterceptor.intercept()
                → MonitorInterceptor.intercept()
                    → OkHttp 内部（重定向/缓存/连接池）
                        → HttpLogger.intercept()  ← NetworkInterceptor
                            → 发送到服务器
```

---

## 6. 自定义 OkHttpClient 场景

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(MyCustomInterceptor())  // 自定义的拦截器
    .build()

Net.instance.configure {
    client(customClient)  // 使用自定义 client
    encrypt { ... }       // EncryptInterceptor 仍会被追加
    monitor { ... }       // MonitorInterceptor 仍会被追加
    enableHttpLog()       // HttpLogger 仍会被追加
}
```

使用自定义 client 时：
- 自定义 client 的**原有拦截器也被保留**（通过 `newBuilder()` 继承）
- 框架的 EncryptInterceptor、MonitorInterceptor、HttpLogger **仍然追加**
- 自定义 client 的 Dispatcher 和 ConnectionPool 被**替换**为新的（防止连接池共享问题）
- 用户通过 `interceptor()` 添加的拦截器也会追加

**这意味着：** 如果自定义 client 和框架配置中同时有日志拦截器，会出现**双重日志**。

---

## 7. 常见问题

### Q: 如何确认拦截器已正确注册？

```kotlin
// 在 configure 后检查
val client = Net.instance.okHttpClient
val interceptors = client.interceptors  // Application Interceptors
val networkInterceptors = client.networkInterceptors  // Network Interceptors
Log.d("TAG", "Application interceptors: ${interceptors.size}")
Log.d("TAG", "Network interceptors: ${networkInterceptors.size}")
```

### Q: 加密和监控的顺序为什么是这样？

- **加密在前** — 监控采集的是加密后的请求（URL 不含明文参数），保护敏感数据不泄露到监控系统
- **监控在后** — 监控记录的是包括加密耗时在内的**总耗时**，更接近用户真实体验

### Q: 取消的请求会被监控吗？

会。取消请求的 `IOException`（含 "Canceled" 消息）被识别为 `CANCELLED` 错误类型，且取消请求**始终上报**（不受 `reportMode` 限制）。

### Q: 如何仅在特定环境下开启日志/监控？

```kotlin
Net.instance.configure {
    if (BuildConfig.DEBUG) {
        enableHttpLog()
    }
    monitor {
        enabled(!BuildConfig.DEBUG)  // 仅线上开启监控
        reportUrl("https://monitor.example.com/report")
    }
}
```

---

## 8. 验证方式

- 开启 `enableHttpLog()`，发送一个测试请求，确认日志中能看到完整的请求/响应
- 配置 `encrypt {}` 并发送包含加密字段的请求，确认日志中的字段值已被替换为 Base64 密文
- 配置 `monitor {}`，查看监控服务端是否收到事件
- 添加自定义拦截器，在 `intercept()` 中打日志确认执行顺序
- 使用 `.skipEncrypt()` 和 `.skipMonitor()` 确认单请求控制优先级高于全局配置

[返回 README](../../README.md)
