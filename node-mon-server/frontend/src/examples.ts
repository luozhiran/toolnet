export const examples: Record<string, { title: string; code: string }> = {
  'api-get': {
    title: '📱 OkHttp GET /api/data 示例',
    code: `// GET 请求示例
val client = OkHttpClient()
val request = Request.Builder()
    .url("http://192.168.1.100:3000/api/data")
    .get()
    .build()

client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        val jsonData = response.body?.string()
        println("收到数据: $jsonData")
    } else {
       println("请求失败: \${response.code}")
    }
}

// 使用协程（推荐）
suspend fun fetchData(): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("http://192.168.1.100:3000/api/data")
        .build()
    client.newCall(request).execute().body?.string() ?: ""
}`
  },
  'api-echo': {
    title: '📱 OkHttp GET /api/echo (带Query参数) 示例',
    code: `// 带 Query 参数的 GET 请求
val client = OkHttpClient()
val url = HttpUrl.parse("http://192.168.1.100:3000/api/echo")!!.newBuilder()
    .addQueryParameter("name", "张三")
    .addQueryParameter("age", "25")
    .build()

val request = Request.Builder().url(url).get().build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'api-user': {
    title: '📱 OkHttp GET /api/user/:id (路径+Query) 示例',
    code: `// 路径参数 + Query 参数
val userId = "123"
val url = HttpUrl.parse("http://192.168.1.100:3000/api/user/$userId")!!.newBuilder()
    .addQueryParameter("name", "张三")
    .build()

val request = Request.Builder().url(url).get().build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'api-json': {
    title: '📱 OkHttp POST /api/json 示例',
    code: `// POST JSON 数据
val client = OkHttpClient()
val jsonBody = """
    {
        "name": "测试",
        "value": 123,
        "message": "Hello Server"
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

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'api-form': {
    title: '📱 OkHttp POST /api/form (x-www-form-urlencoded) 示例',
    code: `// POST 表单数据
val client = OkHttpClient()
val formBody = FormBody.Builder()
    .add("username", "testuser")
    .add("password", "123456")
    .build()

val request = Request.Builder()
    .url("http://192.168.1.100:3000/api/form")
    .post(formBody)
    .build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'upload-single': {
    title: '📱 OkHttp 单文件上传 (/upload/single) 示例',
    code: `// 单文件上传 (multipart/form-data)
val client = OkHttpClient()
val file = File("/sdcard/test.png")
val fileBody = RequestBody.create(
    MediaType.parse("image/png"),
    file
)

val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("file", file.name, fileBody)
    .addFormDataPart("description", "测试上传")
    .build()

val request = Request.Builder()
    .url("http://192.168.1.100:3000/upload/single")
    .post(requestBody)
    .build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'upload-multiple': {
    title: '📱 OkHttp 多文件上传 (/upload/multiple) 示例',
    code: `// 多文件上传
val client = OkHttpClient()
val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("files", "file1.png",
        RequestBody.create(MediaType.parse("image/png"), File("/sdcard/file1.png")))
    .addFormDataPart("files", "file2.jpg",
        RequestBody.create(MediaType.parse("image/jpeg"), File("/sdcard/file2.jpg")))
    .build()

val request = Request.Builder()
    .url("http://192.168.1.100:3000/upload/multiple")
    .post(requestBody)
    .build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'upload-mixed': {
    title: '📱 OkHttp 混合上传 (/upload/mixed) 示例',
    code: `// 混合上传：文件+文本+JSON+表单
val client = OkHttpClient()
val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("files", "photo.png",
        RequestBody.create(MediaType.parse("image/png"), File("/sdcard/photo.png")))
    .addFormDataPart("content", "这是文本内容")
    .addFormDataPart("jsonData", "{\\"userId\\":123,\\"action\\":\\"upload\\"}")
    .addFormDataPart("extraKey", "extraValue")
    .build()

val request = Request.Builder()
    .url("http://192.168.1.100:3000/upload/mixed")
    .post(requestBody)
    .build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}`
  },
  'download': {
    title: '📱 OkHttp 文件下载与断点续传示例',
    code: `// 1. 普通下载
suspend fun downloadFile(url: String, destFile: File) = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
        response.body?.byteStream()?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

// 2. 获取文件大小（HEAD）
suspend fun getContentLength(url: String): Long = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).head().build()
    client.newCall(request).execute().use { response ->
        response.header("Content-Length")?.toLongOrNull() ?: -1L
    }
}

// 3. 断点续传
suspend fun resumeDownload(url: String, localFile: File) {
    val totalSize = getContentLength(url)
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

// 4. 分片并发下载
suspend fun downloadWithChunks(url: String, chunkCount: Int = 3) {
    val totalSize = getContentLength(url)
    val chunkSize = ceil(totalSize / chunkCount.toDouble()).toLong()
    val chunks = (0 until chunkCount).map { i ->
        val start = i * chunkSize
        val end = if (i == chunkCount - 1) totalSize - 1 else start + chunkSize - 1
        async(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=$start-$end")
                .build()
            client.newCall(req).execute().body?.bytes()
        }
    }.awaitAll()
    // 合并分片
    val merged = chunks.flatMap { it?.toList() ?: emptyList() }.toByteArray()
    File("merged_file").writeBytes(merged)
}`
  },
  'chunk-demo': {
    title: '📱 OkHttp 分片下载演示（断点续传核心）',
    code: `// 分片下载核心：使用 Range 头请求指定字节范围
val url = "http://192.168.1.100:3000/download/example.zip"
val start = 0L
val end = 1024 * 1024  // 1MB

val request = Request.Builder()
    .url(url)
    .header("Range", "bytes=$start-$end")
    .build()

client.newCall(request).execute().use { response ->
    if (response.code == 206) {
        val chunkData = response.body?.bytes()
        // 保存或合并分片
    }
}

// 完整的分片下载合并见上一个示例`
  }
};
