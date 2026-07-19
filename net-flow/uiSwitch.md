# Net 库线程切换 & UI 线程安全说明

Net 库内部基于 OkHttp 的 `enqueue()` 异步执行网络请求，**库内部不会自动将结果切回主线程**。不同调用方式的线程行为差异较大，本文档逐一分析。

---

## 1. DdCallback 回调方式

### 结果运行在 OkHttp 线程池（后台线程）

`SendTool.send(callback: DdCallback?, call: Call?)` 的核心逻辑：

```kotlin
// SendTool.kt:56-88
call.enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        // 当前线程：OkHttp Dispatcher 线程池（后台线程）
        callback?.onResponse(response.body?.string(), response.code)
        // ↑ 直接回调，库内部没有做任何线程切换
    }
    override fun onFailure(call: Call, e: IOException) {
        // 同样在后台线程
        callback?.onFailure(e.message)
    }
})
```

**线程链路**：

```
OkHttp 线程池（后台线程）
  → call.enqueue()
    → onResponse 在后台线程
      → DdCallback.onResponse()  ← 仍在后台线程
```

**用户需要手动切回主线程**：

```kotlin
Net.instance.get()
    .url("https://api.example.com/data")
    .send(object : DdCallback {
        override fun onResponse(result: String?, code: Int) {
            // ⚠️ 当前在后台线程，直接操作 View 会抛出 CalledFromWrongThreadException
            runOnUiThread {
                textView.text = result
            }
        }
        override fun onFailure(er: String?) {
            runOnUiThread {
                Toast.makeText(context, er, Toast.LENGTH_SHORT).show()
            }
        }
    })
```

> **注意**：`NetConfig` 中存在 `uiHandler`（主线程 Handler），`ThreadTool` 也提供了 `runOnUIThread` 方法，但它们只在 Download 模块的生命周期绑定等场景使用，**普通请求的 DdCallback 回调路径中不会自动调用**。

---

## 2. Handler 方式

### 结果由 Handler 关联的 Looper 决定

`SendTool.send(handler, what, errorWhat, call)` 通过 Message 投递：

```kotlin
// SendTool.kt:90-131
override fun onResponse(call: Call, response: Response) {
    val msg = Message.obtain()
    msg.what = what
    msg.obj = response.body?.string()
    handler?.sendMessage(msg)  // 投递到 Handler 所在 Looper
}
```

**线程链路**：

```
OkHttp 线程池（后台线程）
  → call.enqueue()
    → onResponse 在后台线程
      → handler.sendMessage(msg)  ← 投递 Message
         
Handler 关联的 Looper 线程
  → handleMessage(msg)  ← 结果在此线程处理
```

**示例**：

```kotlin
// 主线程 Handler — 结果在主线程
val mainHandler = Handler(Looper.getMainLooper()) { msg ->
    when (msg.what) {
        1 -> textView.text = msg.obj.toString()    // 主线程，安全
        2 -> showError(msg.obj.toString())          // 主线程，安全
    }
    true
}
Net.instance.get().url("...").send(mainHandler, what = 1, errorWhat = 2)

// 后台线程 Handler — 结果在后台线程
val bgThread = HandlerThread("bg")
bgThread.start()
val bgHandler = Handler(bgThread.looper) { msg ->
    // 后台线程，不能直接操作 UI
    when (msg.what) {
        1 -> saveToDatabase(msg.obj.toString())
    }
    true
}
```

---

## 3. OkHttp 原生 Callback 方式

### 结果在 OkHttp 线程池（后台线程）

与 DdCallback 行为一致，`SendTool.send(response: Callback?, call: Call?)` 也是直接透传：

```kotlin
// SendTool.kt:133-149
call.enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        callback.onResponse(call, response)  // 后台线程
    }
    override fun onFailure(call: Call, e: IOException) {
        callback.onFailure(call, e)          // 后台线程
    }
})
```

---

## 4. Flow 方式（net-flow 模块）

### 结果在 collect 所在的协程调度器上执行

`callbackFlow` 内部使用 `Channel` 作为桥梁，实现线程安全的跨线程数据传输：

```kotlin
// RequestFlow.kt
fun <T : ParamsBuilder> T.flowString(): Flow<String> = callbackFlow {
    val call = buildCall()
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // 当前：OkHttp 线程池（后台线程）
            val body = response.body?.string()
            trySend(body.orEmpty())  // 写入 Channel，线程安全，非阻塞
            close()
        }
    })
    awaitClose { call.cancel() }
}
```

**线程链路**：

```
OkHttp 线程池（后台线程）
  → call.enqueue()
    → onResponse 在后台线程
      → trySend(data)  ← 写入 Channel（线程安全）

Channel 内部线程切换

协程调度器（取决于 launch 的 Dispatchers）
  → Channel 收到数据
    → collect { }  ← 在 launch 所在的调度器上执行
```

### 4.1 默认主线程（推荐）

```kotlin
// lifecycleScope.launch 默认使用 Dispatchers.Main
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .catch { e -> Log.e("TAG", "失败", e) }
        .collect { body ->
            textView.text = body  // ✅ 主线程，可以直接操作 UI
        }
}
```

### 4.2 显式后台线程

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .collect { body ->
            // ⚠️ 当前在 IO 线程，不能直接操作 UI
            saveToDatabase(body)
            
            // 需要操作 UI 时切回主线程
            withContext(Dispatchers.Main) {
                textView.text = body
            }
        }
}
```

### 4.3 使用 flowOn 改变上游执行线程

`flowOn` 只影响上游（网络请求之前的准备操作），不影响 `collect` 所在的线程：

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .flowOn(Dispatchers.IO)   // 不影响 collect 线程
        .collect { body ->
            // lifecycleScope 默认 Dispatchers.Main，这里仍然是主线程
            textView.text = body   // ✅ 主线程
        }
}
```

---

## 5. 下载进度 Flow

下载模块内部使用 `DdNet` 的自定义线程池（`Dispatch` / `DispatchTool`），不是 OkHttp 的 Dispatcher 线程池。但 `DownloadFlow.kt` 也是通过 `callbackFlow` + `trySend` 桥接，行为与请求 Flow 一致：

```kotlin
// DownloadFlow.kt
fun TaskBuilder.flow(): Flow<DownloadProgress> = callbackFlow {
    val flowCallback = object : IProgressCallback {
        override fun onProgress(task: Task, complete: Boolean) {
            // 当前：DdNet 下载线程池（后台线程）
            trySend(DownloadProgress(task, DownloadPhase.Downloading))
        }
    }
    taskRef.addDownloadListener(flowCallback)
    taskRef.start()
    awaitClose { Net.instance.cancelDownload(task) }
}
```

```
DdNet 下载线程池（后台线程）
  → onProgress 在后台线程
    → trySend(progress)  ← 写入 Channel（线程安全）

协程调度器
  → collect { progress -> }  ← launch 所在调度器（默认主线程）
```

使用方式与请求 Flow 相同：

```kotlin
lifecycleScope.launch {
    Net.instance.newDownload()
        .savePath("${filesDir}/video.mp4")
        .url("https://example.com/video.mp4")
        .flow()
        .collect { progress ->
            // ✅ lifecycleScope 默认主线程，可以直接更新 UI
            progressBar.progress = (progress.task.downloadSize * 100 / 
                maxOf(progress.task.contentLength, 1L)).toInt()
        }
}
```

---

## 6. Retrofit suspend 方式（net-retrofit 模块）

Retrofit 的 `suspend` 函数内部使用 `suspendCancellableCoroutine` 桥接，回调发生在 OkHttp 线程池，但 `suspend` 恢复时会回到调用方的协程上下文：

```kotlin
lifecycleScope.launch {
    // launch 默认 Dispatchers.Main
    try {
        val user = userService.getUser("123")
        // ✅ 主线程，可以直接操作 UI
        textView.text = user.name
    } catch (e: Exception) {
        // ✅ 主线程
        showError(e.message)
    }
}
```

---

## 总结对比

| 调用方式 | 网络执行线程 | 结果回调线程 | 能否直接操作 UI |
|---|---|---|---|
| `send(DdCallback)` | OkHttp 线程池（后台） | **后台线程** | ❌ 需手动 `runOnUiThread` |
| `send(Handler, what, errorWhat)` | OkHttp 线程池（后台） | **Handler 所在 Looper** | 取决于 Handler |
| `send(OkHttp Callback)` | OkHttp 线程池（后台） | **后台线程** | ❌ |
| `flowString().collect {}` | OkHttp 线程池（后台） | **launch 的 Dispatchers** | ✅ 默认主线程 |
| `flowResponse().collect {}` | OkHttp 线程池（后台） | **launch 的 Dispatchers** | ✅ 默认主线程 |
| `TaskBuilder.flow().collect {}` | DdNet 下载线程池（后台） | **launch 的 Dispatchers** | ✅ 默认主线程 |
| Retrofit `suspend fun` | OkHttp 线程池（后台） | **调用方协程上下文** | ✅ 默认主线程 |

### 核心结论

1. **库内部不做线程切换**：`SendTool` 和下载模块的 DdCallback/OkHttp Callback 都在后台线程直接回调，不经过任何 Handler 转发
2. **Flow 利用 Channel 实现线程安全**：`trySend()` 在后台线程写入，`collect {}` 在协程调度器上消费，天然解耦
3. **推荐 Flow 方式**：无需手动 `runOnUiThread`，利用协程的 `Dispatchers` 机制自动处理线程切换
4. **NetConfig.uiHandler 和 ThreadTool.runOnUIThread**：只在下载模块的 Activity 生命周期绑定等内部场景使用，普通请求路径不会自动调用
