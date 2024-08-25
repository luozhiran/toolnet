package com.itg.net.reqeust

import android.app.Activity
import android.os.Handler
import android.os.Message
import com.itg.net.Net
import com.itg.net.reqeust.base.DdCallback
import com.itg.net.download.operations.PrincipalLife
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
        if (call == null) callback?.onFailure("url is error,please check url")
        PrincipalLife.observeActivityLife(call,this.activity)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    callback?.onFailure(e.message)
                }
                PrincipalLife.removeCall(call)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!call.isCanceled()) {
                    callback?.onResponse(response.body?.string(), response.code)
                }
                response.body?.close()
                PrincipalLife.removeCall(call)
            }
        })
    }

    fun send(handler: Handler?, what: Int, errorWhat: Int, call: Call?) {
        if (call == null) {
            val msg = Message.obtain()
            msg.what = errorWhat
            msg.obj = "url is error,please check url"
            handler?.sendMessage(msg)
        }
        PrincipalLife.observeActivityLife(call,this.activity)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    val msg = Message.obtain()
                    msg.what = errorWhat
                    msg.obj = e.message
                    handler?.sendMessage(msg)
                }
                PrincipalLife.removeCall(call)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!call.isCanceled()) {
                    val msg = Message.obtain()
                    msg.what = what
                    msg.obj = response
                    msg.obj = response.body?.string()
                    handler?.sendMessage(msg)
                }
                response.body?.close()
                PrincipalLife.removeCall(call)
            }
        })
    }

    fun send(callback: Callback?, call: Call?) {
        call ?: return
        callback?:return
        call.enqueue(object :Callback{
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure(call,e)
            }

            override fun onResponse(call: Call, response: Response) {
               callback.onResponse(call,response)
            }

        })
    }


}