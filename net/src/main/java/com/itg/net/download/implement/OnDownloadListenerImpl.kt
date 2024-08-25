package com.itg.net.download.implement

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class OnDownloadListenerImpl(
    private val onResponse: ((Call, Response) -> Unit)?,
    private val onFailure: ((Call, IOException) -> Unit)?
) : Callback {
    override fun onFailure(call: Call, e: IOException) {
        onFailure?.invoke(call, e)
    }

    override fun onResponse(call: Call, response: Response) {
        onResponse?.invoke(call, response)
    }
}