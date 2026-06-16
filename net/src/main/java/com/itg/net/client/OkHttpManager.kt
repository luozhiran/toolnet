package com.itg.net.client

import com.itg.net.config.NetConfig
import com.itg.net.logging.HttpLogger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class OkHttpManager(ddNetConfig: NetConfig) {
    var okHttpClient: OkHttpClient = ddNetConfig.getOkHttpClient() ?: run {
        createDefaultClient(ddNetConfig)
    }

    private fun createDefaultClient(ddNetConfig: NetConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(15, TimeUnit.SECONDS)
        builder.readTimeout(20, TimeUnit.SECONDS)
        builder.writeTimeout(35, TimeUnit.SECONDS)
        ddNetConfig.getInterceptors().forEach { builder.addInterceptor(it) }
        ddNetConfig.getCache()?.let {
            builder.cache(it)
        }
        if (ddNetConfig.useHttpLog()) {
            val logInterceptor = HttpLoggingInterceptor(HttpLogger())
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            builder.addNetworkInterceptor(logInterceptor)
        }

        return builder.build()
    }
}
