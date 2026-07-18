# net-flow — Kotlin Flow 扩展

基于 `kotlinx.coroutines` 对 Net 核心库的 Flow 扩展，将回调模型桥接到 Flow，支持结构化并发、背压和自动取消。

## 包含功能

- `flowString()` — 将任意请求转为 `Flow<String>`，非 2xx 会进入 `catch`
- `flowResult()` — 将请求转为 `Flow<NetResult>`，明确区分成功、HTTP 错误和网络异常
- `flowResponse(converter)` — 含反序列化的 `Flow<NetResponse<T>>`
- `TaskBuilder.flow()` — 下载进度 `Flow<DownloadProgress>`
- `NetConverter<T>` 接口 + `GsonNetConverter<T>` 实现
- 便捷扩展：`flowGet` / `flowPostJson` / `flowPostForm` / `flowDownload` 等

## 快速开始

```kotlin
// 依赖引入
implementation project(':net-flow')
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

// 使用
lifecycleScope.launch {
    Net.instance.get()
        .url("https://api.example.com/data")
        .flowString()
        .catch { e -> Log.e("TAG", "请求失败", e) }
        .collect { body -> updateUI(body) }
}
```

## 详细文档

| 文档 | 内容 |
|---|---|
| [01. Flow 化请求](./doc/01-flow-basics.md) | flowString、flowResult、flowResponse、反序列化、NetResponse |
| [02. 下载进度 Flow](./doc/02-flow-download.md) | TaskBuilder.flow、DownloadProgress、取消 |

完整场景总览请查看 [项目 README](../README.md)。

## 线程模型

| 调用方式 | 结果所在线程 | 能否直接操作 UI |
|---|---|---|
| `flowString().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |
| `flowResponse().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |
| `TaskBuilder.flow().collect {}` | launch 的 Dispatchers（默认 Main） | ✅ |

> Flow API 与原有的 `DdCallback` / `Handler` API **完全共存**，底层共享同一个 OkHttpClient。
