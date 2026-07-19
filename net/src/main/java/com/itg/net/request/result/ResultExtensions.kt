package com.itg.net.request.result

import com.itg.net.Net
import com.itg.net.download.operations.PrincipalLife
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.response.BodyReadResult
import com.itg.net.response.ResponseBodyReader
import com.itg.net.util.PrintLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

fun ParamsBuilder.sendResult(callback: NetResultCallback?) {
    val call = buildCall()
    if (call == null) {
        callback?.onNetworkError(
            NetResult.NetworkError(IOException("url is error, please check url"))
        )
        return
    }

    PrincipalLife.observeActivityLife(call, consumeAutoCancelActivity())
    PrintLog.logr { "start request ${call.request().url}" }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            try {
                if (!call.isCanceled()) {
                    callback?.onNetworkError(NetResult.NetworkError(e))
                }
                PrintLog.logr { "request failed ${call.request().url}" }
            } finally {
                PrincipalLife.removeCall(call)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!call.isCanceled()) {
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(", ") }
                    when (val bodyResult = ResponseBodyReader.readText(
                        response.body,
                        Net.instance.ddNetConfig.maxResponseBodyBytes
                    )) {
                        is BodyReadResult.Text -> {
                            if (response.isSuccessful) {
                                callback?.onSuccess(
                                    NetResult.Success(
                                        body = bodyResult.value,
                                        code = response.code,
                                        headers = headers
                                    )
                                )
                            } else {
                                callback?.onHttpError(
                                    NetResult.HttpError(
                                        body = bodyResult.value,
                                        code = response.code,
                                        message = response.message,
                                        headers = headers
                                    )
                                )
                            }
                        }
                        is BodyReadResult.TooLarge -> {
                            callback?.onNetworkError(
                                NetResult.NetworkError(bodyResult.asIOException())
                            )
                        }
                    }
                }
                PrintLog.logr { "request finished ${call.request().url} code=${response.code}" }
            } finally {
                response.close()
                PrincipalLife.removeCall(call)
            }
        }
    })
}
