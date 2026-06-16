package com.itg.ddlnet

import android.app.Application
import com.itg.net.Net
import com.itg.net.logging.HttpLogger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class App:Application() {

    override fun onCreate() {
        super.onCreate()

        Net.instance.ddNetConfig
            .app(this)
            .setGlobalParams("ab","bai")
            .setGlobalParams("43","af")
            .maxDownloadNum(1)
            .okHttpClient(getOkhppt())
            .useHttpLog(true)
            .url("http://47.76.59.147:8000/")
    }



    private fun  getOkhppt():OkHttpClient{
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(15, TimeUnit.SECONDS)
        builder.readTimeout(20, TimeUnit.SECONDS)
        builder.writeTimeout(35, TimeUnit.SECONDS)
        val logInterceptor = HttpLoggingInterceptor(HttpLogger())
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        builder.addNetworkInterceptor(logInterceptor)

       return builder.build()
    }
}