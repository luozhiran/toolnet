package com.itg.net.retrofit

import com.itg.net.Net
import com.itg.net.flow.NetFlowException
import com.itg.net.flow.NetResponse
import com.itg.net.response.BodyReadResult
import com.itg.net.response.ResponseBodyReader
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.toBusinessResult
import com.itg.net.request.business.toTypedBusinessResult
import com.itg.net.request.result.NetResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.CancellationException
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

        val netResponseBodyType: Type? = if (mode == ResultMode.NetResponse) {
            check(responseType is ParameterizedType) {
                "Flow<NetResponse<T>> must include the response body type"
            }
            getParameterUpperBound(0, responseType)
        } else {
            null
        }

        val bodyType: Type = when (mode) {
            ResultMode.NetResponse -> ResponseBody::class.java
            ResultMode.NetResult,
            ResultMode.BusinessResult,
            ResultMode.TypedBusinessResult -> ResponseBody::class.java
            ResultMode.Body -> responseType
        }

        val netResponseConverter = netResponseBodyType?.let {
            retrofit.nextResponseBodyConverter<Any?>(null, it, annotations)
        }

        val typedBusinessType = if (mode == ResultMode.TypedBusinessResult) {
            check(responseType is ParameterizedType) {
                "Flow<TypedBusinessResult<T>> must include the business data type"
            }
            getParameterUpperBound(0, responseType)
        } else {
            null
        }

        return FlowCallAdapter<Any>(bodyType, mode, typedBusinessType, netResponseBodyType, netResponseConverter)
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
        private val typedBusinessType: Type?,
        private val netResponseBodyType: Type?,
        private val netResponseConverter: Converter<ResponseBody, Any?>?
    ) : CallAdapter<T, Flow<Any>> {

        override fun responseType(): Type = bodyType

        override fun adapt(call: Call<T>): Flow<Any> = callbackFlow {
            val flowCall = call.clone()
            flowCall.enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    if (flowCall.isCanceled) {
                        response.closeAllBody()
                        close(requestCancellation())
                        return
                    }

                    try {
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
                    } catch (e: Exception) {
                        emitResponseException(flowCall, e)
                    } finally {
                        response.closeConsumedBody(mode)
                    }
                    close()
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    if (flowCall.isCanceled) {
                        close(requestCancellation(t))
                        return
                    }
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
                flowCall.cancel()
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

            when (val bodyResult = response.errorBody().readSafely()) {
                is BodyReadResult.Text -> close(NetFlowException(response.code(), bodyResult.value))
                is BodyReadResult.TooLarge -> close(NetFlowException(response.code(), bodyResult.message))
            }
        }

        private fun kotlinx.coroutines.channels.ProducerScope<Any>.emitNetResponse(response: Response<T>) {
            val headers = response.headers().toMultimap()
                .mapValues { (_, values) -> values.joinToString(", ") }
            val contentType = (response.body() as? ResponseBody)?.contentType()
                ?: response.errorBody()?.contentType()
            val rawBodyResult = if (response.isSuccessful) {
                (response.body() as? ResponseBody).readSafelyOrObjectString(response.body())
            } else {
                response.errorBody().readSafely()
            }
            if (rawBodyResult is BodyReadResult.TooLarge) {
                val netResponse = NetResponse<Any?>(
                    body = null,
                    rawBody = null,
                    code = response.code(),
                    headers = headers
                )
                trySend(netResponse)
                return
            }
            val rawBody = when (rawBodyResult) {
                is BodyReadResult.Text -> rawBodyResult.value
                is BodyReadResult.TooLarge -> null
            }
            val convertedBody = if (response.isSuccessful) {
                convertNetResponseBody(rawBody, contentType)
            } else {
                null
            }
            val netResponse = if (response.isSuccessful) {
                NetResponse(
                    body = convertedBody,
                    rawBody = rawBody,
                    code = response.code(),
                    headers = headers
                )
            } else {
                NetResponse(
                    body = null,
                    rawBody = rawBody,
                    code = response.code(),
                    headers = headers
                )
            }
            trySend(netResponse)
        }

        private fun convertNetResponseBody(rawBody: String?, contentType: MediaType?): Any? {
            if (rawBody == null) return null
            if (netResponseBodyType == String::class.java) return rawBody
            if (netResponseBodyType == ResponseBody::class.java) return rawBody.toResponseBody(contentType)
            val converter = netResponseConverter
                ?: throw IllegalStateException("No Retrofit converter found for $netResponseBodyType")
            return converter.convert(rawBody.toResponseBody(contentType))
        }

        private fun Response<T>.toNetResult(): NetResult {
            val headers = headers().toMultimap()
                .mapValues { (_, values) -> values.joinToString(", ") }
            val rawBodyResult = if (isSuccessful) {
                (body() as? ResponseBody).readSafelyOrObjectString(body())
            } else {
                errorBody().readSafely()
            }
            if (rawBodyResult is BodyReadResult.TooLarge) {
                return if (isSuccessful) {
                    NetResult.NetworkError(rawBodyResult.asIOException())
                } else {
                    NetResult.HttpError(
                        body = null,
                        code = code(),
                        message = rawBodyResult.message,
                        headers = headers
                    )
                }
            }
            val rawBody = when (rawBodyResult) {
                is BodyReadResult.Text -> rawBodyResult.value
                is BodyReadResult.TooLarge -> null
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

        private fun kotlinx.coroutines.channels.ProducerScope<Any>.emitResponseException(
            call: Call<T>,
            error: Exception
        ) {
            if (call.isCanceled) {
                close(requestCancellation(error))
                return
            }
            when (mode) {
                ResultMode.NetResult -> trySend(NetResult.NetworkError(error.asIOException()))
                ResultMode.BusinessResult -> trySend(NetResult.NetworkError(error.asIOException()).toBusinessResult())
                ResultMode.TypedBusinessResult -> {
                    val result = NetResult.NetworkError(error.asIOException())
                        .toBusinessResult()
                        .toTypedBusinessResult<Any>(typedBusinessType ?: Any::class.java)
                    trySend(result)
                }
                else -> close(NetFlowException(null, error.message))
            }
        }

        private fun requestCancellation(cause: Throwable? = null): CancellationException {
            return CancellationException("request canceled").apply {
                if (cause != null) initCause(cause)
            }
        }

        private fun ResponseBody?.readSafely(): BodyReadResult {
            return ResponseBodyReader.readText(this, Net.instance.ddNetConfig.maxResponseBodyBytes)
        }

        private fun ResponseBody?.readSafelyOrObjectString(body: Any?): BodyReadResult {
            return if (this != null) {
                readSafely()
            } else {
                BodyReadResult.Text(body?.toString())
            }
        }

        private fun Response<T>.closeConsumedBody(mode: ResultMode) {
            if (mode != ResultMode.Body || !isSuccessful) {
                (body() as? ResponseBody)?.close()
                errorBody()?.close()
            }
        }

        private fun Response<T>.closeAllBody() {
            (body() as? ResponseBody)?.close()
            errorBody()?.close()
        }
    }
}
