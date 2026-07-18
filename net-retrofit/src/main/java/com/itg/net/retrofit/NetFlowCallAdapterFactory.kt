package com.itg.net.retrofit

import com.itg.net.flow.NetFlowException
import com.itg.net.flow.NetResponse
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.toBusinessResult
import com.itg.net.request.business.toTypedBusinessResult
import com.itg.net.request.result.NetResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Retrofit Flow 适配器。
 *
 * 将 Retrofit Call 适配成 Kotlin Flow，并支持 Net 体系里的结构化结果类型：
 * - Flow<T>
 * - Flow<NetResponse<T>>
 * - Flow<NetResult>
 * - Flow<BusinessResult>
 * - Flow<TypedBusinessResult<T>>
 */
class NetFlowCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Flow::class.java) {
            return null
        }

        check(returnType is ParameterizedType) {
            "Flow return type must be parameterized as Flow<T>, Flow<NetResponse<T>>, Flow<NetResult>, or Flow<BusinessResult>"
        }

        val responseType = getParameterUpperBound(0, returnType)
        val responseRawType = getRawType(responseType)
        val mode = when (responseRawType) {
            NetResponse::class.java -> ResultMode.NetResponse
            NetResult::class.java -> ResultMode.NetResult
            BusinessResult::class.java -> ResultMode.BusinessResult
            TypedBusinessResult::class.java -> ResultMode.TypedBusinessResult
            else -> ResultMode.Body
        }

        val bodyType: Type = when (mode) {
            ResultMode.NetResponse -> {
                check(responseType is ParameterizedType) {
                    "Flow<NetResponse<T>> must include the response body type"
                }
                getParameterUpperBound(0, responseType)
            }
            ResultMode.NetResult,
            ResultMode.BusinessResult,
            ResultMode.TypedBusinessResult -> ResponseBody::class.java
            ResultMode.Body -> responseType
        }

        val typedBusinessType = if (mode == ResultMode.TypedBusinessResult) {
            check(responseType is ParameterizedType) {
                "Flow<TypedBusinessResult<T>> must include the business data type"
            }
            getParameterUpperBound(0, responseType)
        } else {
            null
        }

        return FlowCallAdapter<Any>(bodyType, mode, typedBusinessType)
    }

    private enum class ResultMode {
        Body,
        NetResponse,
        NetResult,
        BusinessResult,
        TypedBusinessResult
    }

    private class FlowCallAdapter<T>(
        private val bodyType: Type,
        private val mode: ResultMode,
        private val typedBusinessType: Type?
    ) : CallAdapter<T, Flow<Any>> {

        override fun responseType(): Type = bodyType

        override fun adapt(call: Call<T>): Flow<Any> = callbackFlow {
            call.enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    if (call.isCanceled) return

                    when (mode) {
                        ResultMode.Body -> emitBody(response)
                        ResultMode.NetResponse -> emitNetResponse(response)
                        ResultMode.NetResult -> {
                            trySend(response.toNetResult())
                        }
                        ResultMode.BusinessResult -> {
                            trySend(response.toNetResult().toBusinessResult())
                        }
                        ResultMode.TypedBusinessResult -> {
                            val result = response.toNetResult()
                                .toBusinessResult()
                                .toTypedBusinessResult<Any>(typedBusinessType ?: Any::class.java)
                            trySend(result)
                        }
                    }
                    close()
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    if (call.isCanceled) return

                    when (mode) {
                        ResultMode.NetResult -> {
                            trySend(NetResult.NetworkError(t.asIOException()))
                            close()
                        }
                        ResultMode.BusinessResult -> {
                            trySend(NetResult.NetworkError(t.asIOException()).toBusinessResult())
                            close()
                        }
                        ResultMode.TypedBusinessResult -> {
                            val result = NetResult.NetworkError(t.asIOException())
                                .toBusinessResult()
                                .toTypedBusinessResult<Any>(typedBusinessType ?: Any::class.java)
                            trySend(result)
                            close()
                        }
                        else -> close(NetFlowException(null, t.message))
                    }
                }
            })

            awaitClose {
                call.cancel()
            }
        }

        private fun kotlinx.coroutines.channels.ProducerScope<Any>.emitBody(response: Response<T>) {
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    trySend(body as Any)
                    return
                }
                close(NetFlowException(response.code(), "response body is null"))
                return
            }

            close(NetFlowException(response.code(), response.errorBody()?.string()))
        }

        private fun kotlinx.coroutines.channels.ProducerScope<Any>.emitNetResponse(response: Response<T>) {
            val headers = response.headers().toMultimap()
                .mapValues { (_, values) -> values.joinToString(", ") }
            val netResponse = if (response.isSuccessful) {
                NetResponse(
                    body = response.body() as? Any,
                    rawBody = null,
                    code = response.code(),
                    headers = headers
                )
            } else {
                NetResponse(
                    body = null,
                    rawBody = response.errorBody()?.string(),
                    code = response.code(),
                    headers = headers
                )
            }
            trySend(netResponse)
        }

        private fun Response<T>.toNetResult(): NetResult {
            val headers = headers().toMultimap()
                .mapValues { (_, values) -> values.joinToString(", ") }
            val rawBody = if (isSuccessful) {
                (body() as? ResponseBody)?.string() ?: body()?.toString()
            } else {
                errorBody()?.string()
            }

            return if (isSuccessful) {
                NetResult.Success(
                    body = rawBody,
                    code = code(),
                    headers = headers
                )
            } else {
                NetResult.HttpError(
                    body = rawBody,
                    code = code(),
                    message = message(),
                    headers = headers
                )
            }
        }

        private fun Throwable.asIOException(): IOException {
            return this as? IOException ?: IOException(message, this)
        }
    }
}
