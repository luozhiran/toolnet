# net - 核心网络库

`net` 是基于 OkHttp 的 Android 网络核心库，提供链式请求、文件下载、生命周期取消、结构化错误处理、业务码责任链、字段加解密和网络监控能力。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件 | 选择理由 |
| --- | --- | --- | --- |
| [快速接入网络库](./doc/01-quick-start.md) | `Net.configure { ... }` | Application 初始化阶段 | 统一配置 baseUrl、OkHttp、监控、加密和业务协议 |
| [发送基础请求](./doc/02-basic-requests.md) | `get()` / `postJson()` / `postForm()` / `postFile()` | 普通 HTTP 请求 | 使用链式 Builder 配置 URL、Header、参数和回调 |
| [请求取消和生命周期绑定](./doc/03-cancel-requests.md) | `tag(...)` / `autoCancel(...)` / `cancelTag(...)` | 页面销毁或业务取消 | 避免无效回调和页面引用泄露 |
| [文件下载](./doc/04-file-download.md) | `newDownload().savePath(...).url(...).listener(...).start()` | 下载文件并观察进度 | 支持断点续传、任务监听、全局监听和生命周期清理 |
| [全局配置](./doc/05-global-config.md) | `Net.configure { ... }` | 需要统一请求策略 | 集中管理全局参数、拦截器、超时、缓存和转换器 |
| [工具方法](./doc/06-utilities.md) | `StrTools` / `TaskTools` / `JsonTools` | 需要复用库内工具 | 降低业务层重复实现 |
| [字段加解密](./doc/07-field-encryption.md) | `encrypt { ... }` / `encrypt()` / `skipEncrypt()` | 只加密部分请求或字段 | 支持 AES/RSA 等策略，并能按请求控制 |
| [网络监控](./doc/08-network-monitor.md) | `monitor { ... }` / `monitor()` / `skipMonitor()` | 需要排查质量、耗时或错误 | 请求和下载都可以输出结构化监控事件 |
| [错误和业务码处理](./doc/09-error-handling.md) | `sendResult()` / `sendBusinessResult()` / `sendTypedBusinessResult()` | 新业务推荐使用 | 区分 HTTP 错误、网络异常和业务失败，支持全局责任链 |

## 快速开始

```kotlin
import com.itg.net.Net
import com.itg.net.callback.DdCallback

Net.configure {
    application(this@MyApp)
    baseUrl("https://api.example.com")
}

Net.instance.get()
    .path("/user/profile")
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
            // 兼容旧式回调
        }

        override fun onResponse(result: String?, code: Int) {
            // HTTP 2xx 响应体
        }
    })
```

新代码更推荐使用结构化结果：

```kotlin
Net.instance.get()
    .path("/user/profile")
    .sendResult(callback)
```

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 快速开始与初始化](./doc/01-quick-start.md) | 依赖引入、Application 初始化、最小请求示例 |
| [02. 基本请求](./doc/02-basic-requests.md) | GET、POST JSON、POST Form、文件上传、Multipart |
| [03. 请求取消与生命周期](./doc/03-cancel-requests.md) | 按 tag、URL、Activity 生命周期取消请求 |
| [04. 文件下载](./doc/04-file-download.md) | 下载、断点续传、进度监听、全局监听、事件分发 |
| [05. 全局配置](./doc/05-global-config.md) | `NetConfig` 可选项和 DSL 示例 |
| [06. 工具类](./doc/06-utilities.md) | 字符串、任务、JSON 等工具 |
| [07. 字段加解密](./doc/07-field-encryption.md) | 字段级加解密配置和请求级开关 |
| [08. 网络监控](./doc/08-network-monitor.md) | 网络质量监控、日志、上报处理 |
| [09. HTTP 错误、网络异常与业务码处理](./doc/09-error-handling.md) | `onHttpError`、`onNetworkError`、业务码责任链、类型化结果 |

完整项目场景请查看 [项目 README](../README.md)。
