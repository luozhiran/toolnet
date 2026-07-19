# net-flow - Kotlin Flow 扩展

`net-flow` 将 `net` 的回调式请求和下载能力桥接为 Kotlin Flow，适合使用协程、生命周期感知收集和统一异常处理的 Android 项目。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件 | 选择理由 |
| --- | --- | --- | --- |
| [请求返回字符串 Flow](./doc/01-flow-basics.md) | `flowString()` | 只需要原始响应体 | 最轻量，适合已有业务解析逻辑 |
| [请求结构化结果 Flow](./doc/01-flow-basics.md) | `flowResult()` | 需要区分 HTTP 错误和网络异常 | 与 `sendResult()` 语义一致，便于统一错误处理 |
| [业务码 Flow](./doc/01-flow-basics.md) | `flowBusinessResult()` | 后端使用 `code/message/data` 等业务包装 | 复用 `ApiEnvelopeParser` 和业务码责任链 |
| [类型化业务数据 Flow](./doc/01-flow-basics.md) | `flowTypedBusinessResult<T>()` | 需要把 `data` 直接转成业务类 | 复用全局 `BusinessDataConverter`，默认使用 Gson |
| [下载进度 Flow](./doc/02-flow-download.md) | `TaskBuilder.flow()` / `flowDownload { ... }` | 需要用协程观察下载进度 | 接入 `net` 统一下载监听注册表，取消 Flow 会取消底层下载 |

## 快速开始

```kotlin
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.itg.net.Net
import com.itg.net.flow.flowString
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

lifecycleScope.launch {
    Net.instance.get()
        .path("/user/profile")
        .flowString()
        .catch { error ->
            Log.e("NetFlow", "请求失败", error)
        }
        .collect { body ->
            updateUi(body)
        }
}
```

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Flow 化请求](./doc/01-flow-basics.md) | `flowString`、`flowResult`、`flowBusinessResult`、`flowTypedBusinessResult`、`flowResponse` |
| [02. 下载进度 Flow](./doc/02-flow-download.md) | `TaskBuilder.flow`、`DownloadProgress`、下载取消和事件生命周期 |

## 线程模型

| 调用方式 | 结果所在上下文 | 是否可直接更新 UI |
| --- | --- | --- |
| `flowString().collect {}` | 启动 `collect` 的协程上下文 | 在 `lifecycleScope.launch {}` 默认主线程中可以 |
| `flowResponse().collect {}` | 启动 `collect` 的协程上下文 | 在 `lifecycleScope.launch {}` 默认主线程中可以 |
| `TaskBuilder.flow().collect {}` | 启动 `collect` 的协程上下文 | 在 `lifecycleScope.launch {}` 默认主线程中可以 |

Flow API 与 `net` 的回调 API 共存，底层共享同一个 OkHttpClient、请求配置、业务码解析和下载调度体系。

完整项目场景请查看 [项目 README](../README.md)。
