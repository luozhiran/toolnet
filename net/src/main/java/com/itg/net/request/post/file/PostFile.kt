package com.itg.net.request.post.file

import android.app.Activity
import android.os.Handler
import com.itg.net.request.base.DdCallback
import com.itg.net.request.SendTool
import com.itg.net.download.data.Task
import okhttp3.Call
import okhttp3.Callback

class PostFile: PostFileGenerator() {

    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostFile {
        super.autoCancel(activity)
        return this
    }

    override fun send(callback: DdCallback?) {
        val body = getRequestBody()
        val call = body?.let { sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,it, cacheControl, encryptFlag, monitorFlag, monitorExtra) }
        sendTool.send(callback, call, consumeAutoCancelActivity())
    }

    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
        val body = getRequestBody()
        val call = body?.let { sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,it, cacheControl, encryptFlag, monitorFlag, monitorExtra) }
        sendTool.send(handler,what,errorWhat,call, consumeAutoCancelActivity())
    }

    override fun send(response: Callback?, task: Task?) {
        val body = getRequestBody()
        val call = body?.let { sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, it, cacheControl, encryptFlag, monitorFlag, monitorExtra) }
        sendTool.send(response, call, consumeAutoCancelActivity())
    }

    override fun buildCall(): Call? {
        val body = getRequestBody() ?: return null
        return sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, body, cacheControl, encryptFlag, monitorFlag, monitorExtra)
    }

}
