# 6. 工具类

Net 库提供的辅助工具类：字符串处理、下载进度计算、缓存控制、JSON 处理和调试日志。

## 适用条件

- 需要使用 Net 库内置的工具方法
- 避免重复实现常见的网络相关工具函数

## 推荐做法

### StrTools — 字符串与编码工具

| 方法 | 说明 |
|---|---|
| `getCookieString(cookie: List<Cookie?>?): String?` | Cookie 列表转请求头字符串 |
| `getMd5(input: String): String?` | 计算字符串 MD5 |
| `extractUrlFileName(url: String?, defaultName: String?): String?` | 从 URL 提取文件名并 URL 解码 |

```kotlin
// 从 URL 提取文件名
val fileName = StrTools.extractUrlFileName(
    "https://example.com/files/report.pdf?token=abc",
    "download.pdf"
)
// fileName = "report.pdf"

// 计算 MD5
val md5 = StrTools.getMd5("hello world")
```

### TaskTools — 下载进度计算

| 方法 | 说明 |
|---|---|
| `getDownloadProgress(task: Task): Int` | 计算下载进度百分比（0..100） |

```kotlin
override fun onProgress(task: Task, complete: Boolean) {
    val percent = TaskTools.getDownloadProgress(task)
    progressBar.progress = percent
}
```

### CacheControlFactory — 缓存策略预设

| 预设 | 说明 |
|---|---|
| `FORCE_NETWORK` | 强制使用网络，不使用缓存 |
| `FORCE_CACHE` | 强制使用缓存，不使用网络 |
| `CHECK_CACHE` | maxAge=0，先验证缓存有效性 |
| `getCacheControlForSecond(n)` | 缓存 n 秒 |
| `getCacheControlForMILLISECONDS(n)` | 缓存 n 毫秒 |
| `getCacheControl(n, unit)` | 自定义时间和单位 |

```kotlin
Net.instance.get()
    .url("https://api.example.com/config")
    .addCacheControl(CacheControlFactory.getCacheControlForSecond(30))
    .send(callback)
```

> 需先在全局配置中通过 `useCacheControl(Cache)` 开启缓存。

### CacheFactory — 缓存实例创建

| 方法 | 说明 |
|---|---|
| `getCache(context): Cache` | 默认 10MB 缓存 |
| `getCache(context, maxSize): Cache` | 自定义大小缓存 |

```kotlin
// 在 configure {} 中使用
useCacheControl(CacheFactory.getCache(this@MyApp, 50 * 1024 * 1024))
```

### PrintLog — 调试日志

仅在 `BuildConfig.DEBUG` 为 true 时输出，避免生产环境日志泄露：

| 方法 | 说明 |
|---|---|
| `PrintLog.logr(message)` | 普通请求日志 |
| `PrintLog.logd(message)` | 下载相关日志 |
| `PrintLog.logSubd(message)` | 下载子步骤日志 |

### JsonTools — JSON 处理

| 方法 | 说明 |
|---|---|
| `formatJson(jsonStr): String` | 格式化 JSON 字符串（缩进美化） |
| `decodeUnicode(str): String` | Unicode 转中文 |
| `deepMerge(source, target): JSONObject` | 深度合并两个 JSONObject |

```kotlin
// 深度合并 JSON
val base = JSONObject("""{"a":1,"b":{"x":1}}""")
val override = JSONObject("""{"b":{"y":2},"c":3}""")
val merged = JsonTools.deepMerge(base, override)
// merged = {"a":1,"b":{"x":1,"y":2},"c":3}
```

## 关键说明

- `PrintLog` 在 Release 构建中自动静默，无需手动判断 `BuildConfig.DEBUG`
- `TaskTools.getDownloadProgress()` 内部已处理 `contentLength` 为 0 或 -1 的情况
- `JsonTools.deepMerge` 是深度合并（递归合并嵌套 JSONObject），而非浅覆盖
- `StrTools.extractUrlFileName` 会自动处理 URL 编码的文件名

## 验证方式

- 在 DEBUG 构建中确认 `PrintLog` 有输出
- 在 RELEASE 构建中确认 `PrintLog` 无输出
- 使用 `JsonTools.formatJson` 验证 JSON 格式化结果

[返回 README](../../README.md)
