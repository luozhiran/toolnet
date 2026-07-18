package com.itg.net.flow

import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.toBusinessResult
import com.itg.net.request.result.NetResult
import com.itg.net.request.base.ParamsBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/**
 * Flow 扩展：将现有 Builder 模式的请求转为 Kotlin [Flow]
 *
 * 使用 [callbackFlow] 桥接 OkHttp 的异步回调模型，实现背压感知的流式网络请求。
 * Flow 收集取消时自动取消底层 OkHttp [Call]。
 */

/**
 * 将当前请求构建器转为 [Flow]<[String]>，emission 为响应体字符串
 *
 * ## 使用示例
 * ```
 * lifecycleScope.launch {
 *     Net.instance.get()
 *         .url("https://api.example.com/data")
 *         .flowString()
 *         .catch { e -> Log.e("TAG", "失败", e) }
 *         .collect { body -> updateUI(body) }
 * }
 * ```
 *
 * @receiver [ParamsBuilder] 子类（Get / PostJson / PostForm 等）
 * @return 发射单个响应体字符串后完成的冷流
 */
fun <T : ParamsBuilder> T.flowString(): Flow<String> = callbackFlow {
    val call: Call? = buildCall()
    if (call == null) {
        close(NetFlowException(null, "url is error, please check url"))
        return@callbackFlow
    }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!call.isCanceled()) {
                close(NetFlowException(null, e.message))
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!call.isCanceled()) {
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        trySend(body.orEmpty())
                        close()
                    } else {
                        close(NetFlowException(response.code, body ?: response.message))
                    }
                }
            } finally {
                response.close()
            }
        }
    })

    awaitClose {
        call.cancel()
    }
}

fun <T : ParamsBuilder> T.flowResult(): Flow<NetResult> = callbackFlow {
    val call: Call? = buildCall()
    if (call == null) {
        trySend(NetResult.NetworkError(IOException("url is error, please check url")))
        close()
        return@callbackFlow
    }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!call.isCanceled()) {
                trySend(NetResult.NetworkError(e))
                close()
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!call.isCanceled()) {
                    val rawBody = response.body?.string()
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(", ") }
                    val result = if (response.isSuccessful) {
                        NetResult.Success(
                            body = rawBody,
                            code = response.code,
                            headers = headers
                        )
                    } else {
                        NetResult.HttpError(
                            body = rawBody,
                            code = response.code,
                            message = response.message,
                            headers = headers
                        )
                    }
                    trySend(result)
                    close()
                }
            } finally {
                response.close()
            }
        }
    })

    awaitClose {
        call.cancel()
    }
}

fun <T : ParamsBuilder> T.flowBusinessResult(): Flow<BusinessResult> {
    return flowResult().map { result -> result.toBusinessResult() }
}

/**
 * 将当前请求构建器转为 [Flow]<[NetResponse]<[T]>>，支持自定义反序列化
 *
 * ## 使用示例
 * ```
 * lifecycleScope.launch {
 *     Net.instance.postJson()
 *         .url("https://api.example.com/login")
 *         .addParam("username", "admin")
 *         .flowResponse { raw -> Gson().fromJson(raw, User::class.java) }
 *         .catch { e -> handleError(e) }
 *         .collect { response -> ... }
 * }
 * ```
 *
 * @param converter 将原始响应字符串转为类型 [T] 的转换器，默认为 identity（返回字符串本身）
 * @receiver [ParamsBuilder] 子类
 * @return 发射单个 [NetResponse] 后完成的冷流
 */
fun <T> ParamsBuilder.flowResponse(
    converter: (String?) -> T?
): Flow<NetResponse<T>> = callbackFlow {
    val call: Call? = buildCall()
    if (call == null) {
        close(NetFlowException(null, "url is error, please check url"))
        return@callbackFlow
    }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!call.isCanceled()) {
                close(NetFlowException(null, e.message))
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!call.isCanceled()) {
                    val rawBody = response.body?.string()
                    val body = converter(rawBody)
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(", ") }

                    trySend(
                        NetResponse(
                            body = body,
                            rawBody = rawBody,
                            code = response.code,
                            headers = headers
                        )
                    )
                    close()
                }
            } finally {
                response.close()
            }
        }
    })

    awaitClose {
        call.cancel()
    }
}
