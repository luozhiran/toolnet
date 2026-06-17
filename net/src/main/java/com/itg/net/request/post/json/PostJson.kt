package com.itg.net.request.post.json

import android.app.Activity
import android.os.Handler
import com.itg.net.request.base.DdCallback
import com.itg.net.request.SendTool
import okhttp3.Call

class PostJson: PostJsonGenerator() {

    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostJson {
        sendTool.autoCancel(activity)
        return this
    }

    override fun send(callback: DdCallback?) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(), cacheControl)
        sendTool.send(callback, call)
    }

    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(), cacheControl)
        sendTool.send(handler,what,errorWhat,call)
    }

    override fun buildCall(): Call? {
        return sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, getRequestBody(), cacheControl)
    }

}
