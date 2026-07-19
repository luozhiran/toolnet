package com.itg.net.request

import android.app.Activity
import android.os.Handler
import android.os.Message
import com.itg.net.Net
import com.itg.net.encrypt.EncryptMarker
import com.itg.net.monitor.MonitorExtra
import com.itg.net.monitor.MonitorMarker
import com.itg.net.request.base.DdCallback
import com.itg.net.response.BodyReadResult
import com.itg.net.response.ResponseBodyReader
import com.itg.net.download.operations.PrincipalLife
import com.itg.net.util.PrintLog
import okhttp3.*
import java.io.IOException

class SendTool {
    fun combineParamsAndRCall(
        headers: Headers?,
        url: String?,
        tag: String?,
        body: RequestBody?,
        cacheControl: CacheControl?,
        encryptFlag: String? = null,
        monitorFlag: String? = null,
        monitorExtra: String? = null,
        endCallback:((Request.Builder)->Unit)? = null
    ): Call? {
        val builder = Request.Builder()
        headers?.let {
            builder.headers(it)
        }

        if (tag.isNullOrEmpty()) {
            builder.tag(url)
        } else {
            builder.tag(tag)
        }
        // 加密标记：通过 OkHttp Typed Tag 传递，不占用通用的 tag(Any)
        EncryptMarker.fromFlag(encryptFlag)?.let {
            builder.tag(EncryptMarker::class.java, it)
        }
        // 监控标记：通过 OkHttp Typed Tag 传递，不占用通用的 tag(Any)
        MonitorMarker.fromFlag(monitorFlag)?.let {
            builder.tag(MonitorMarker::class.java, it)
        }
        // 业务附加字段：通过 OkHttp Typed Tag 传递，不占用通用的 tag(Any)
        if (!monitorExtra.isNullOrBlank()) {
            builder.tag(MonitorExtra::class.java, MonitorExtra(monitorExtra))
        }
        if (url.orEmpty().isBlank()) {
            return null
        }
        cacheControl?.let {
            builder.cacheControl(cacheControl)
        }
        url?.let {
            builder.url(it)
        }
        body?.let {
            builder.post(it)
        }
        endCallback?.invoke(builder)
        return Net.instance.okhttpManager.okHttpClient.newCall(builder.build())
    }


    fun send(callback: DdCallback?, call: Call?, autoCancelActivity: Activity?) {
        if (call == null) {
            callback?.onFailure("url is error,please check url")
            return
        }
        PrincipalLife.observeActivityLife(call, autoCancelActivity)
        PrintLog.logr { "开始发起请求 ${call.request().url}" }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    if (!call.isCanceled()) {
                        callback?.onFailure(e.message)
                    }
                    PrintLog.logr { "请求失败 ${call.request().url}" }
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!call.isCanceled()) {
                        when (val bodyResult = response.readBodySafely()) {
                            is BodyReadResult.Text -> {
                                if (response.isSuccessful) {
                                    callback?.onResponse(bodyResult.value, response.code)
                                } else {
                                    callback?.onFailure(response.failureMessage(bodyResult.value))
                                }
                            }
                            is BodyReadResult.TooLarge -> callback?.onFailure(bodyResult.message)
                        }
                    }
                    PrintLog.logr { "请求成功 ${call.request().url}" }
                } finally {
                    response.close()
                    PrincipalLife.removeCall(call)
                }
            }
        })
    }

    fun send(handler: Handler?, what: Int, errorWhat: Int, call: Call?, autoCancelActivity: Activity?) {
        if (call == null) {
            val msg = Message.obtain()
            msg.what = errorWhat
            msg.obj = "url is error,please check url"
            handler?.sendMessage(msg)
            return
        }
        PrincipalLife.observeActivityLife(call, autoCancelActivity)
        PrintLog.logr { "开始发起请求 ${call.request().url}" }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    if (!call.isCanceled()) {
                        val msg = Message.obtain()
                        msg.what = errorWhat
                        msg.obj = e.message
                        handler?.sendMessage(msg)
                    }
                    PrintLog.logr { "请求失败 ${call.request().url}" }
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!call.isCanceled()) {
                        val msg = Message.obtain()
                        when (val bodyResult = response.readBodySafely()) {
                            is BodyReadResult.Text -> {
                                if (response.isSuccessful) {
                                    msg.what = what
                                    msg.obj = bodyResult.value
                                } else {
                                    msg.what = errorWhat
                                    msg.obj = response.failureMessage(bodyResult.value)
                                }
                            }
                            is BodyReadResult.TooLarge -> {
                                msg.what = errorWhat
                                msg.obj = bodyResult.message
                            }
                        }
                        handler?.sendMessage(msg)
                    }
                    PrintLog.logr { "请求成功 ${call.request().url}" }
                } finally {
                    response.close()
                    PrincipalLife.removeCall(call)
                }
            }
        })
    }

    fun send(callback: Callback?, call: Call?, autoCancelActivity: Activity?) {
        call ?: return
        callback ?: return
        PrincipalLife.observeActivityLife(call, autoCancelActivity)
        PrintLog.logr { "开始发起请求 ${call.request().url}" }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    callback.onFailure(call, e)
                    PrintLog.logr { "请求失败 ${call.request().url}" }
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    callback.onResponse(call, response)
                    PrintLog.logr { "请求成功 ${call.request().url}" }
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

        })
    }

    private fun Response.failureMessage(body: String?): String {
        val detail = body?.takeIf { it.isNotBlank() } ?: message
        return "HTTP $code: $detail"
    }

    private fun Response.readBodySafely(): BodyReadResult {
        return ResponseBodyReader.readText(body, Net.instance.ddNetConfig.maxResponseBodyBytes)
    }

}
