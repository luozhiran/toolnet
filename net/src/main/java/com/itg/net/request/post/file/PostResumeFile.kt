package com.itg.net.request.post.file

import android.app.Activity
import android.os.Handler
import com.itg.net.request.base.DdCallback
import com.itg.net.request.SendTool

class PostResumeFile: PostResumeGenerator() {

    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostResumeFile {
        sendTool.autoCancel(activity)
        return this
    }

    override fun send(callback: DdCallback?) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(),cacheControl)
        sendTool.send(callback, call)
    }

    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
        val call = sendTool.combineParamsAndRCall(getHeader(),getUrl(),tag,getRequestBody(),cacheControl)
        sendTool.send(handler,what,errorWhat,call)
    }
}
