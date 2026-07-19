package com.itg.net.flow

import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.toBusinessResult
import com.itg.net.request.business.toTypedBusinessResult
import com.itg.net.request.result.NetResult
import com.itg.net.request.base.ParamsBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * 将当前请求构建器转为 [Flow]<[String]>，emission 为响应体字符串
 *。
 *
 * 这是最轻量的 Flow 请求入口，适合只关心 HTTP 2xx 响应体字符串的场景。
 * 如果服务器返回 4xx/5xx，会通过 [NetFlowException] 关闭 Flow；
 * 如果网络断开、超时或 DNS 失败，也会通过 [NetFlowException] 关闭 Flow，
 * 其中 HTTP 错误会携带状态码，网络异常的状态码为 null。
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
 * @return 发射单个响应体字符串后完成的冷流。
 */
fun <T : ParamsBuilder> T.flowString(): Flow<String> = callbackFlow {
    val call: Call? = buildCall()
    if (call == null) {
        close(NetFlowException(null, "url is error, please check url"))
        return@callbackFlow
    }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            closeFlowForFailure(call, e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { response ->
                try {
                    if (call.isCanceled()) {
                        close(requestCancellation())
                        return@use
                    }
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        trySend(body.orEmpty())
                        close()
                    } else {
                        close(NetFlowException(response.code, body ?: response.message))
                    }
                } catch (e: Exception) {
                    closeFlowForResponseException(call, e)
                }
            }
        }
    })

    awaitClose {
        call.cancel()
    }
}

/**
 * 将当前请求构建器转为结构化 HTTP 结果流。
 *
 * 这个方法只处理 HTTP 层和网络层：
 * - HTTP 2xx 发射 [NetResult.Success]
 * - HTTP 4xx/5xx 发射 [NetResult.HttpError]
 * - 断网、超时、DNS 失败等 IOException 发射 [NetResult.NetworkError]
 *
 * 它不会解析业务码，也不会执行业务责任链。需要处理 `code/message/data`
 * 这类业务协议时，使用 [flowBusinessResult] 或 [flowTypedBusinessResult]。
 *
 * @receiver [ParamsBuilder] 子类（Get / PostJson / PostForm 等）
 * @return 发射单个 [NetResult] 后完成的冷流。
 */
fun <T : ParamsBuilder> T.flowResult(): Flow<NetResult> = callbackFlow {
    val call: Call? = buildCall()
    if (call == null) {
        trySend(NetResult.NetworkError(IOException("url is error, please check url")))
        close()
        return@callbackFlow
    }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (call.isCanceled()) {
                close(requestCancellation(e))
                return
            }
            trySend(NetResult.NetworkError(e))
            close()
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { response ->
                try {
                    if (call.isCanceled()) {
                        close(requestCancellation())
                        return@use
                    }
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
                } catch (e: Exception) {
                    if (call.isCanceled()) {
                        close(requestCancellation(e))
                    } else {
                        trySend(NetResult.NetworkError(e.asIOException()))
                        close()
                    }
                }
            }
        }
    })

    awaitClose {
        call.cancel()
    }
}

/**
 * 将当前请求构建器转为业务结果流。
 *
 * 这个方法在 [flowResult] 的基础上增加业务协议处理：
 * - HTTP 2xx 会先通过 `NetConfig.businessEnvelopeParser` 解析成 [com.itg.net.request.business.ApiEnvelope]
 * - 然后执行 `NetConfig` 中注册的 [com.itg.net.request.business.BusinessResultInterceptor] 责任链
 * - 业务成功发射 [BusinessResult.Success]
 * - 业务失败发射 [BusinessResult.BusinessError]
 * - 被责任链消费时发射 [BusinessResult.Consumed]
 * - HTTP 4xx/5xx 和网络异常分别发射 [BusinessResult.HttpError]、[BusinessResult.NetworkError]
 *
 * 适合统一处理登录失效、权限不足、维护模式等 HTTP 200 里的业务码。
 *
 * @receiver [ParamsBuilder] 子类（Get / PostJson / PostForm 等）
 * @return 发射单个 [BusinessResult] 后完成的冷流。
 */
fun <T : ParamsBuilder> T.flowBusinessResult(): Flow<BusinessResult> {
    return flowResult().map { result -> result.toBusinessResult() }
}

/**
 * 将当前请求构建器转为类型化业务结果流。
 *
 * 这个方法在 [flowBusinessResult] 的基础上继续把业务成功时的
 * [com.itg.net.request.business.ApiEnvelope.dataRaw] 转换为调用方声明的类型 [R]。
 * 默认转换器是 `GsonBusinessDataConverter`，可通过 `NetConfig.businessConverter(...)`
 * 替换为自定义转换器。
 *
 * 转换成功时发射 [TypedBusinessResult.Success]，其中 `data` 类型为 [R]。
 * 转换失败时不会抛出异常，而是发射 [TypedBusinessResult.DataConvertError]。
 *
 * ## 使用示例
 * ```
 * data class UserInfo(val id: Int, val name: String)
 *
 * lifecycleScope.launch {
 *     Net.instance.get()
 *         .url("https://api.example.com/user/info")
 *         .flowTypedBusinessResult<UserInfo>()
 *         .collect { result ->
 *             when (result) {
 *                 is TypedBusinessResult.Success -> render(result.data)
 *                 is TypedBusinessResult.DataConvertError -> showError("数据解析失败")
 *                 else -> Unit
 *             }
 *         }
 * }
 * ```
 *
 * @receiver [ParamsBuilder] 子类（Get / PostJson / PostForm 等）
 * @return 发射单个 [TypedBusinessResult] 后完成的冷流。
 */
inline fun <reified R> ParamsBuilder.flowTypedBusinessResult(): Flow<TypedBusinessResult<R>> {
    val type = object : TypeToken<R>() {}.type
    return flowBusinessResult().map { result -> result.toTypedBusinessResult(type) }
}


/**
 * 将当前请求构建器转为带自定义反序列化的响应流。
 *
 * 这个方法会始终发射 [NetResponse]，其中包含：
 * - [NetResponse.body]：调用 [converter] 后得到的对象
 * - [NetResponse.rawBody]：原始响应体字符串
 * - [NetResponse.code]：HTTP 状态码
 * - [NetResponse.headers]：响应头
 *
 * 和 [flowResult] 不同，当前方法不会把 HTTP 4xx/5xx 转成异常或错误分支，
 * 调用方需要通过 [NetResponse.isSuccessful] 或 [NetResponse.code] 自行判断。
 *
 * @param converter 将原始响应体字符串转换为目标类型的函数。
 * @receiver [ParamsBuilder] 子类（Get / PostJson / PostForm 等）
 * @return 发射单个 [NetResponse] 后完成的冷流。
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
            closeFlowForFailure(call, e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (call.isCanceled()) {
                    close(requestCancellation())
                    return
                }
                try {
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
                } catch (e: Exception) {
                    closeFlowForResponseException(call, e)
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

private fun kotlinx.coroutines.channels.ProducerScope<*>.closeFlowForFailure(call: Call, error: IOException) {
    if (call.isCanceled()) {
        close(requestCancellation(error))
    } else {
        close(NetFlowException(null, error.message))
    }
}

private fun kotlinx.coroutines.channels.ProducerScope<*>.closeFlowForResponseException(
    call: Call,
    error: Exception
) {
    if (call.isCanceled()) {
        close(requestCancellation(error))
    } else {
        close(NetFlowException(null, error.message))
    }
}

private fun requestCancellation(cause: Throwable? = null): CancellationException {
    return CancellationException("request canceled").apply {
        if (cause != null) initCause(cause)
    }
}

private fun Throwable.asIOException(): IOException {
    return this as? IOException ?: IOException(message, this)
}
