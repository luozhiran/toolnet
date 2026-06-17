package com.itg.net.flow

import com.itg.net.Net
import com.itg.net.download.TaskBuilder
import com.itg.net.download.data.Task
import com.itg.net.flow.converter.NetConverter
import kotlinx.coroutines.flow.Flow

/**
 * 网络库 Kotlin Flow 扩展入口
 *
 * 提供将现有 Builder 模式请求和下载任务转为 [Flow] 的便捷方法。
 * 所有扩展方法均以 `flow` 前缀命名，与原有的 `send` 回调系列区分。
 */

// ==================== 普通请求 Flow 扩展 ====================

/**
 * 将 GET 请求转为 [Flow]<[String]>
 *
 * 等价于 `get().flowString()`
 */
fun Net.flowGet(block: (com.itg.net.request.get.Get.() -> Unit)? = null): Flow<String> {
    val builder = get()
    block?.invoke(builder)
    return builder.flowString()
}

/**
 * 将 POST JSON 请求转为 [Flow]<[String]>
 *
 * 等价于 `postJson().flowString()`
 */
fun Net.flowPostJson(block: (com.itg.net.request.post.json.PostJson.() -> Unit)? = null): Flow<String> {
    val builder = postJson()
    block?.invoke(builder)
    return builder.flowString()
}

/**
 * 将 POST Form 请求转为 [Flow]<[String]>
 *
 * 等价于 `postForm().flowString()`
 */
fun Net.flowPostForm(block: (com.itg.net.request.post.form.PostForm.() -> Unit)? = null): Flow<String> {
    val builder = postForm()
    block?.invoke(builder)
    return builder.flowString()
}

// ==================== 下载 Flow 扩展 ====================

/**
 * 便捷创建下载 Flow 任务
 *
 * ## 使用示例
 * ```
 * Net.instance.flowDownload {
 *     savePath("${filesDir}/file.zip")
 *     url("https://example.com/file.zip")
 * }
 *     .catch { e -> handleError(e) }
 *     .collect { progress -> updateUI(progress) }
 * ```
 *
 * @param block 下载配置闭包，接收 [TaskBuilder] 作为接收者
 * @return 下载进度 Flow
 */
fun Net.flowDownload(block: TaskBuilder.() -> Unit): Flow<DownloadProgress> {
    val builder = newDownload()
    block(builder)
    return builder.flow()
}

// ==================== 带反序列化的 Flow 扩展 ====================

/**
 * GET 请求 + 自动反序列化
 *
 * ```
 * Net.instance.flowGet<User> { url("user/1") }
 *     .collect { response -> updateUI(response.body) }
 * ```
 */
inline fun <reified T> Net.flowGetResponse(
    converter: NetConverter<T>,
    noinline block: (com.itg.net.request.get.Get.() -> Unit)? = null
): Flow<NetResponse<T>> {
    val builder = get()
    block?.invoke(builder)
    return builder.flowResponse { raw -> converter.convert(raw) }
}

/**
 * POST JSON 请求 + 自动反序列化
 */
inline fun <reified T> Net.flowPostJsonResponse(
    converter: NetConverter<T>,
    noinline block: (com.itg.net.request.post.json.PostJson.() -> Unit)? = null
): Flow<NetResponse<T>> {
    val builder = postJson()
    block?.invoke(builder)
    return builder.flowResponse { raw -> converter.convert(raw) }
}
