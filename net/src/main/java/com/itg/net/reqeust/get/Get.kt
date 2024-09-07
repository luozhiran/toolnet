package com.itg.net.reqeust.get

import android.app.Activity
import android.os.Handler
import com.itg.net.reqeust.base.DdCallback
import com.itg.net.download.data.Task
import com.itg.net.reqeust.SendTool
import okhttp3.Callback

class Get : GetGenerator() {
    private val sendTool by lazy { SendTool() }

    override fun autoCancel(activity: Activity?): Get {
        super.autoCancel(activity)
        sendTool.autoCancel(activity)
        return this
    }


    override fun send(callback: DdCallback?) {
        val call = sendTool.combineParamsAndRCall(
            getHeader(),
            getUrl(),
            tag,
            null,
            cacheControl
        ) { builder -> builder.get() }
        sendTool.send(callback, call)
    }

    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
        val call = sendTool.combineParamsAndRCall(
            getHeader(),
            getUrl(),
            tag,
            null,
            cacheControl
        ) { builder -> builder.get() }
        sendTool.send(handler,what,errorWhat, call)

    }

    override fun send(response: Callback?,task: Task?) {
        val call = sendTool.combineParamsAndRCall(
            getHeader(),
            getUrl(),
            tag,
            null,
            cacheControl
        ) { builder -> builder.get() }
        sendTool.send(response, call)
    }




}