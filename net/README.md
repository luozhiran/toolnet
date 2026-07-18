# net — 核心库

基于 OkHttp 4.9.2 封装的 Android 网络请求核心库，提供 Builder 模式链式调用 API。

## 包含功能

- GET / POST JSON / POST Form / 文件上传 / Multipart / 自定义 Content-Type 请求
- 文件下载（断点续传、进度监听、生命周期绑定）
- 请求取消（按 Tag / URL / 全部取消）
- 结构化错误处理（`onHttpError` / `onNetworkError` / 业务码责任链 / 类型化业务结果）
- 全局配置 DSL（Base URL、全局参数、拦截器、缓存）
- 字段级加解密（AES-CBC/ECB/GCM、RSA）
- 网络质量监控上报

## 快速开始

```kotlin
Net.configure {
    application(this@MyApp)
    baseUrl("https://api.example.com")
}

Net.instance.get()
    .url("https://api.example.com/data")
    .send(object : DdCallback {
        override fun onFailure(er: String?) { }
        override fun onResponse(result: String?, code: Int) { }
    })
```

## 详细文档

| 文档 | 内容 |
|---|---|
| [01. 快速开始与初始化](./doc/01-quick-start.md) | 依赖引入、Application 初始化 |
| [02. 基本请求](./doc/02-basic-requests.md) | GET/POST/文件上传/Multipart 等 |
| [03. 请求取消与生命周期](./doc/03-cancel-requests.md) | cancel/tag/autoCancel |
| [04. 文件下载](./doc/04-file-download.md) | 下载、断点续传、进度监听 |
| [05. 全局配置](./doc/05-global-config.md) | NetConfig 所有配置项 |
| [06. 工具类](./doc/06-utilities.md) | StrTools/TaskTools/JsonTools 等 |
| [07. 字段加密](./doc/07-field-encryption.md) | 字段级加解密配置与使用 |
| [08. 网络监控](./doc/08-network-monitor.md) | 网络质量监控与上报 |
| [09. HTTP 错误、网络异常与业务码处理](./doc/09-error-handling.md) | sendResult、onHttpError、onNetworkError、业务码责任链、TypedBusinessResult |

完整场景总览请查看 [项目 README](../README.md)。
