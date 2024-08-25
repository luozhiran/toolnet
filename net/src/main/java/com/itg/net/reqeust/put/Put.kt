//package com.itg.net.reqeust.model
//
//import android.os.Handler
//import com.itg.net.DdNet
//import com.itg.net.reqeust.model.base.DdCallback
//import okhttp3.Call
//import okhttp3.Callback
//import okhttp3.Request
//
//class Put: PostGenerator() {
//
//     override fun createCall(): Call? {
//        val builder = Request.Builder()
//        getHeader()?.apply { builder.headers(this) }
//        if (tag.isNullOrEmpty()) {
//            builder.tag(url)
//        } else {
//            builder.tag(tag)
//        }
//        builder.url(url?:"")
//        getRequestBody()?.let { builder.put(it) }
//        return DdNet.instance.okhttpManager.okHttpClient.newCall(builder.build())
//    }
//
//    override fun send(callback: DdCallback?) {
//        TODO("Not yet implemented")
//    }
//
//    override fun send(handler: Handler?, what: Int, errorWhat: Int) {
//        TODO("Not yet implemented")
//    }
//
//    override fun send(response: Callback?, callback: ((Call?) -> Unit)?) {
//        TODO("Not yet implemented")
//    }
//}