package com.itg.net.reqeust.base

import android.os.Handler
import com.itg.net.download.data.Task
import okhttp3.Callback

interface SentBuilder {
    fun send(callback: DdCallback?)
    fun send(handler: Handler?, what: Int, errorWhat: Int)
    fun send(response: Callback?,task: Task?){}
}