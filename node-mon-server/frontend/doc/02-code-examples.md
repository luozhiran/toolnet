# 2. OkHttp 示例代码

每个 API 端点都配有 Android OkHttp（Kotlin）示例代码，可直接复制到 Android 项目中使用。

## 适用条件

- Android 项目使用 OkHttp 库（`com.squareup.okhttp3:okhttp`）
- 手机能访问服务器 IP

## 示例索引

| 示例 Key | 对应端点 | 内容 |
| --- | --- | --- |
| `api-get` | `GET /api/data` | 基本 GET 请求 + 协程封装 |
| `api-echo` | `GET /api/echo` | Query 参数构造（`HttpUrl.Builder`） |
| `api-user` | `GET /api/user/:id` | 路径参数 + Query 参数组合 |
| `api-json` | `POST /api/json` | JSON body 发送（`RequestBody.create`） |
| `api-form` | `POST /api/form` | 表单提交（`FormBody.Builder`） |
| `upload-single` | `POST /upload/single` | 单文件上传（`MultipartBody.Builder`） |
| `upload-multiple` | `POST /upload/multiple` | 多文件上传 |
| `upload-mixed` | `POST /upload/mixed` | 混合上传（文件 + 文本 + JSON + 表单） |
| `download` | `GET /download/:filename` | 普通下载、获取文件大小、断点续传、分片并发下载 |
| `chunk-demo` | `GET /download/:filename` | 分片下载核心：Range 请求 |

定义位置：`frontend/src/examples.js`。

## 查看方式

### 方式一：React UI 中点击

在每个功能区域旁边有绿色的 **"📱 示例"** 按钮，点击后在模态框中显示。

### 方式二：直接查看源码

```bash
cat frontend/src/examples.js
```

## 可复制 Demo

### GET 请求

```kotlin
val client = OkHttpClient()
val request = Request.Builder()
    .url("http://192.168.1.100:3000/api/data")
    .get()
    .build()

client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        val jsonData = response.body?.string()
        println("收到数据: $jsonData")
    }
}
```

### POST JSON

```kotlin
val jsonBody = """
    {
        "name": "测试",
        "value": 123
    }
""".trimIndent()

val body = RequestBody.create(
    MediaType.parse("application/json; charset=utf-8"),
    jsonBody
)
val request = Request.Builder()
    .url("http://192.168.1.100:3000/api/json")
    .post(body)
    .build()
```

### 单文件上传

```kotlin
val file = File("/sdcard/test.png")
val fileBody = RequestBody.create(MediaType.parse("image/png"), file)

val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("file", file.name, fileBody)
    .addFormDataPart("description", "测试上传")
    .build()

val request = Request.Builder()
    .url("http://192.168.1.100:3000/upload/single")
    .post(requestBody)
    .build()
```

### 断点续传

```kotlin
suspend fun resumeDownload(url: String, localFile: File) {
    val totalSize = getContentLength(url)  // HEAD 请求获取文件大小
    val downloaded = if (localFile.exists()) localFile.length() else 0L
    if (downloaded >= totalSize) return

    val request = Request.Builder()
        .url(url)
        .header("Range", "bytes=$downloaded-")
        .build()

    client.newCall(request).execute().use { response ->
        if (response.code == 206 || response.code == 200) {
            response.body?.byteStream()?.use { input ->
                FileOutputStream(localFile, true).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
```

## 关键说明

- **IP 地址替换**：所有示例使用 `192.168.1.100` 作为占位符，使用前替换为服务器的实际 IP 或域名。
- **示例是纯 Kotlin + OkHttp**，不依赖 Android Context，可直接用于单元测试或纯 JVM 项目。
- **协程示例**：示例中包含 `suspend` 函数版本，使用 `withContext(Dispatchers.IO)` 确保网络请求不在主线程。
- **分片下载示例**：展示了 `async/awaitAll` 并发下载多个分片后合并的完整流程，是断点续传的核心实现。
- **添加新示例**：在 `frontend/src/examples.js` 中添加新的 key，格式为 `{ title, code }`，然后在对应组件的示例按钮中引用该 key。

## 示例代码结构

```javascript
// frontend/src/examples.js
export const examples = {
  'api-get': {
    title: '📱 OkHttp GET /api/data 示例',
    code: `// 完整的 Kotlin/OkHttp 代码`
  },
  // ... 更多示例
};
```

## 验证方式

```bash
# 检查所有示例 key 都有 title 和 code
node -e "
const e = require('./frontend/src/examples.js');  // 注意：此为 ES module，可能需要改扩展名
// 或在浏览器控制台中：Object.keys(examples).length 应输出 10
"
```

[返回 README](../../README.md)
