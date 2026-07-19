package com.itg.net.request.post.file

import android.app.Activity
import android.os.Handler
import com.itg.net.request.base.DdCallback
import com.itg.net.request.SendTool
import okhttp3.Call

class PostResumeFile: PostResumeGenerator() {

    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostResumeFile {
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

    override fun buildCall(): Call? {
        return sendTool.combineParamsAndRCall(getHeader(), getUrl(), tag, getRequestBody(), cacheControl, encryptFlag, monitorFlag, monitorExtra)
    }
}
