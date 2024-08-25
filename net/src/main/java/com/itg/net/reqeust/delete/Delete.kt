//package com.itg.net.reqeust.model
//
//import com.itg.net.DdNet
//import com.itg.net.reqeust.AdapterBuilder
//import okhttp3.Call
//import okhttp3.Request
//
//class Delete: PostGenerator() {
//    override fun createCall(): Call {
//        val builder = Request.Builder()
//        getHeader()?.apply { builder.headers(this) }
//        if (tag.isNullOrEmpty()) {
//            builder.tag(url)
//        } else {
//            builder.tag(tag)
//        }
//        builder.url(url?:"")
//        builder.delete(getRequestBody())
//        return DdNet.instance.okhttpManager.okHttpClient.newCall(builder.build())
//    }
//
//}