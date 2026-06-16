package com.itg.net.request

import android.app.Activity
import android.os.Handler
import android.os.Message
import com.itg.net.Net
import com.itg.net.request.base.DdCallback
import com.itg.net.download.operations.PrincipalLife
import com.itg.net.util.PrintLog
import okhttp3.*
import java.io.IOException

class SendTool {
    private var activity:Activity? = null

    fun autoCancel(activity: Activity?): SendTool {
        this.activity = activity
        return this
    }

    fun combineParamsAndRCall(
        headers: Headers?,
        url: String?,
        tag: String?,
        body: RequestBody?,
        cacheControl: CacheControl?,
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


    fun send(callback: DdCallback?, call: Call?) {
        if (call == null) {
            callback?.onFailure("url is error,please check url")
            return
        }
        PrincipalLife.observeActivityLife(call, this.activity)
        this.activity = null
        PrintLog.logr("开始发起请求 ${call.request().url}")
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    if (!call.isCanceled()) {
                        callback?.onFailure(e.message)
                    }
                    PrintLog.logr("请求失败 ${call.request().url}")
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!call.isCanceled()) {
                        callback?.onResponse(response.body?.string(), response.code)
                    }
                    PrintLog.logr("请求成功 ${call.request().url}")
                } finally {
                    response.close()
                    PrincipalLife.removeCall(call)
                }
            }
        })
    }

    fun send(handler: Handler?, what: Int, errorWhat: Int, call: Call?) {
        if (call == null) {
            val msg = Message.obtain()
            msg.what = errorWhat
            msg.obj = "url is error,please check url"
            handler?.sendMessage(msg)
            return
        }
        PrincipalLife.observeActivityLife(call, this.activity)
        this.activity = null
        PrintLog.logr("开始发起请求 ${call.request().url}")
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    if (!call.isCanceled()) {
                        val msg = Message.obtain()
                        msg.what = errorWhat
                        msg.obj = e.message
                        handler?.sendMessage(msg)
                    }
                    PrintLog.logr("请求失败 ${call.request().url}")
                } finally {
                    PrincipalLife.removeCall(call)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!call.isCanceled()) {
                        val msg = Message.obtain()
                        msg.what = what
                        msg.obj = response.body?.string()
                        handler?.sendMessage(msg)
                    }
                    PrintLog.logr("请求成功 ${call.request().url}")
                } finally {
                    response.close()
                    PrincipalLife.removeCall(call)
                }
            }
        })
    }

    fun send(callback: Callback?, call: Call?) {
        call ?: return
        callback?:return
        PrintLog.logr("开始发起请求 ${call.request().url}")
        call.enqueue(object :Callback{
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure(call,e)
                PrintLog.logr("请求失败 ${call.request().url}")
            }

            override fun onResponse(call: Call, response: Response) {
               callback.onResponse(call,response)
                PrintLog.logr("请求成功 ${call.request().url}")
            }

        })
    }


}
