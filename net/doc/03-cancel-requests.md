# 3. 请求取消与生命周期管理

如何取消正在执行的网络请求，以及如何将请求与 Activity/Fragment 生命周期绑定以自动取消。

## 适用条件

- 已发起网络请求（GET/POST/下载）
- 需要主动取消请求或防止页面销毁后的无效回调

## 推荐做法

### 取消所有请求

```kotlin
// 取消所有排队中和执行中的请求（包括普通请求和下载请求）
Net.instance.cancelAll()
```

### 按 Tag 取消请求

```kotlin
// 发起时设置 tag
Net.instance.get()
    .url("https://api.example.com/search")
    .addParam("q", keyword)
    .tag("searchTask")
    .send(callback)

// 取消所有匹配 tag 的请求
Net.instance.cancel("searchTask")
// 或等价写法
Net.instance.cancelTag("searchTask")

// 只取消第一个匹配 tag 的请求
val found = Net.instance.cancelFirstTag("uploadTask")
```

### 绑定 Activity 生命周期（推荐）

```kotlin
// Activity 中发起请求，销毁时自动取消
Net.instance.get()
    .url("https://api.example.com/data")
    .autoCancel(this)  // this 为 FragmentActivity
    .send(callback)
```

> **建议在所有 Activity 发起的请求中使用 `autoCancel()`**，防止页面销毁后回调持有已销毁的 View 引用导致崩溃或内存泄露。

### 取消下载

```kotlin
// 按 URL 取消下载
Net.instance.cancelDownload("https://example.com/file.zip")
// 别名（语义更明确）
Net.instance.cancelDownloadByUrl("https://example.com/file.zip")

// 按 Task 取消下载
val task = Net.instance.newDownload()/*...*/.start()
Net.instance.cancelDownload(task)
// 别名
Net.instance.cancelDownloadTask(task)
```

## 关键说明

- `cancelAll()` 会取消所有 OkHttp Call，影响范围大，一般用于退出登录、切换环境等场景
- `cancel(tag)` 是 `cancelTag(tag)` 的别名，取消所有排队中和执行中匹配该 tag 的请求
- `cancelFirstTag` 只取消第一个匹配的请求（优先从排队队列查找），找到后立即返回 `true`
- `autoCancel(activity)` 通过 `LifecycleEventObserver` 监听 Activity 销毁事件，销毁时自动调用 `call.cancel()`
- 使用 Flow 方式时（net-flow 模块），协程取消会自动触发 OkHttp Call 取消，无需额外调用 `autoCancel()`，详见 [07-Flow 基础](../../net-flow/doc/01-flow-basics.md)
- 下载任务取消后，已下载的部分文件保留在磁盘（断点续传可复用）

## 生命周期管理方案对比

| 方案 | 适用场景 | 取消机制 |
|---|---|---|
| `autoCancel(activity)` | 回调模式 (`DdCallback`) | LifecycleEventObserver → call.cancel() |
| `lifecycleScope.launch` | Flow 模式 | 协程取消 → awaitClose → call.cancel() |
| `viewModelScope.launch` | ViewModel + Flow | 协程取消 → awaitClose → call.cancel() |
| `tag + cancel(tag)` | 手动管理 | 遍历队列和运行中请求，匹配后 cancel |
| `cancelAll()` | 全局清理 | 取消所有 Call |

## 验证方式

- 在 Activity 中发起请求后立即 `finish()`，确认 `onFailure` 不会被回调
- 使用 `cancelAll()` 后，确认所有排队中的 Call 均被移除
- 通过 Log 确认 `cancelFirstTag` 的返回值是否符合预期

[返回 README](../../README.md)
