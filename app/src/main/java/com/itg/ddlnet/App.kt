package com.itg.ddlnet

import android.app.Application
import com.itg.net.Net
import com.itg.net.logging.HttpLogger
import com.itg.net.retrofit.NetRetrofit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
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

        val retrofit = NetRetrofit.builder()
            .baseUrl("https://api.example.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())  // 支持 String 响应
            .build()
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