package com.itg.net.retrofit

import com.itg.net.flow.NetFlowException
import com.itg.net.flow.NetResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Retrofit [CallAdapter.Factory]，将 [Call] 适配为 Kotlin [Flow]
 *
 * 使 Retrofit Service 接口可以直接声明返回 [Flow] 或 [Flow]<[NetResponse]>，
 * 底层使用 [callbackFlow] 桥接 Retrofit 回调，
 * Flow 收集取消时自动取消底层网络请求。
 *
 * ## 支持的返回类型
 *
 * | Service 返回类型                     | 行为                                       |
 * |--------------------------------------|--------------------------------------------|
 * | `Flow<T>`                            | 发射单个反序列化后的 body 后完成            |
 * | `Flow<NetResponse<T>>`               | 发射包含状态码和反序列化 body 的响应包装    |
 * | `Call<T>`                            | 保持 Retrofit 原生行为（透传）              |
 * | `suspend fun ...: T`                 | Retrofit 内置支持，不需要本 Adapter         |
 *
 * ## 使用示例
 * ```
 * interface ApiService {
 *     // suspend 函数 — Retrofit 内置支持
 *     @GET("user/{id}")
 *     suspend fun getUser(@Path("id") id: String): User
 *
 *     // Flow 流式 — 需要本 Adapter
 *     @GET("events")
 *     fun eventStream(): Flow<Event>
 * }
 * ```
 */
class NetFlowCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // 只处理 Flow 返回类型
        if (getRawType(returnType) != Flow::class.java) {
            return null
        }

        // Flow<T> 或 Flow<NetResponse<T>>
        check(returnType is ParameterizedType) {
            "Flow return type must be parameterized as Flow<T> or Flow<NetResponse<T>>"
        }

        val flowType = returnType
        val responseType = getParameterUpperBound(0, flowType)
        val isNetResponse = getRawType(responseType) == NetResponse::class.java

        val bodyType: Type = if (isNetResponse) {
            // Flow<NetResponse<T>> → 提取 T
            check(responseType is ParameterizedType)
            getParameterUpperBound(0, responseType)
        } else {
            // Flow<T>
            responseType
        }

        return FlowCallAdapter<Any>(bodyType, isNetResponse)
    }

    /**
     * Flow 适配器实现 — 将 Retrofit Call 适配为 Flow
     */
    private class FlowCallAdapter<T>(
        private val bodyType: Type,
        private val isNetResponse: Boolean
    ) : CallAdapter<T, Flow<Any>> {

        override fun responseType(): Type = bodyType

        override fun adapt(call: Call<T>): Flow<Any> = callbackFlow {
            call.enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    if (call.isCanceled) return

                    val headers = response.headers().toMultimap()
                        .mapValues { (_, values) -> values.joinToString(", ") }

                    if (isNetResponse) {
                        val netResp = if (response.isSuccessful) {
                            NetResponse(
                                body = response.body() as? Any,
                                rawBody = null,
                                code = response.code(),
                                headers = headers
                            )
                        } else {
                            val errorBody = response.errorBody()?.string()
                            NetResponse(
                                body = null,
                                rawBody = errorBody,
                                code = response.code(),
                                headers = headers
                            )
                        }
                        trySend(netResp)
                    } else {
                        if (response.isSuccessful) {
                            trySend(response.body() as Any)
                        } else {
                            close(
                                NetFlowException(
                                    response.code(),
                                    response.errorBody()?.string()
                                )
                            )
                            return
                        }
                    }
                    close()
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    if (!call.isCanceled) {
                        close(NetFlowException(null, t.message))
                    }
                }
            })

            awaitClose {
                call.cancel()
            }
        }
    }
}
