package com.itg.net.reqeust.post.content

import android.app.Activity
import android.os.Handler
import com.itg.net.reqeust.base.DdCallback
import com.itg.net.reqeust.SendTool

class PostContent: PostContentGenerator() {
    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): PostContent {
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