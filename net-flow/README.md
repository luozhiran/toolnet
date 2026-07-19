# net-flow

`net-flow` 为 `net` 增加 Kotlin Flow API。它适合 Kotlin + 协程项目，把回调式请求转换成可 `collect`、`catch`、随协程取消自动取消网络请求的冷流。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件 | 什么时候使用 | 关键约束 |
| --- | --- | --- | --- | --- |
| [普通接口转 Flow](./doc/01-flow-requests.md) | `flowString` / `flowResult` / `flowBusinessResult` / `flowTypedBusinessResult` / `flowResponse` | Kotlin 协程环境 | ViewModel、Repository、Compose 或 LifecycleScope | Flow 是冷流，只有 collect 后才会发请求 |
| [下载转 Flow](./doc/02-flow-download.md) | `TaskBuilder.flow()` / `Net.instance.flowDownload { ... }` | 需要下载进度流 | 下载页、资源预加载、离线包 | collect 被取消且任务未结束时，会取消下载 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Flow 请求](./doc/01-flow-requests.md) | Flow 请求入口、错误模型、业务结果、类型转换 |
| [02. Flow 下载](./doc/02-flow-download.md) | 下载进度阶段、取消、无 Content-Length 场景 |

## 最小示例

```kotlin
lifecycleScope.launch {
    Net.instance.flowGet {
        url("user/profile")
    }
        .catch { e ->
            // flowString 或 Flow<T> 类型会用异常表示 HTTP/网络错误。
        }
        .collect { body ->
            render(body)
        }
}
```

更推荐结构化处理：

```kotlin
lifecycleScope.launch {
    Net.instance.get()
        .url("user/profile")
        .flowResult()
        .collect { result ->
            when (result) {
                is NetResult.Success -> render(result.body)
                is NetResult.HttpError -> showError("HTTP ${result.code}")
                is NetResult.ResponseTooLarge -> showError(result.message)
                is NetResult.NetworkError -> showError(result.message)
            }
        }
}
```
