package com.itg.net.request.post.content

import android.app.Activity
import android.os.Handler
import com.itg.net.request.base.DdCallback
import com.itg.net.request.SendTool
import com.itg.net.download.data.Task
import okhttp3.Call
import okhttp3.Callback

class PostContent: PostContentGenerator() {
    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostContent {
        super.autoCancel(activity)
        return this
    }

    override fun send(callback: DdCallback?) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(),cacheControl, encryptFlag, monitorFlag, monitorExtra)
        sendTool.send(callback, call, consumeAutoCancelActivity())
    }

    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(),cacheControl, encryptFlag, monitorFlag, monitorExtra)
        sendTool.send(handler,what,errorWhat,call, consumeAutoCancelActivity())
    }

    override fun send(response: Callback?, task: Task?) {
        val call = sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, getRequestBody(), cacheControl, encryptFlag, monitorFlag, monitorExtra)
        sendTool.send(response, call, consumeAutoCancelActivity())
    }

    override fun buildCall(): Call? {
        return sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, getRequestBody(), cacheControl, encryptFlag, monitorFlag, monitorExtra)
    }

}
