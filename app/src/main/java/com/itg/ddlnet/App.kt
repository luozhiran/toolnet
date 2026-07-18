package com.itg.ddlnet

import android.app.Application
import android.util.Log
import com.itg.net.Net
import com.itg.net.logging.HttpLogger
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.ReportMode
import com.itg.net.retrofit.retrofit
import okhttp3.OkHttpClient
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
            .useHttpLog(true)
            .url("http://47.76.59.147:8000/")
            .monitor {
                enabled = false
                reportMode= ReportMode.FAILURE_ONLY
                reportHandler = object : IMonitorReportHandler {
                    override val isAsync: Boolean get() = true
                    override fun onEvent(event: MonitorEvent) {
                        Log.e("Monitor", event.toJson().toString())
                    }

                    override fun flush() {

                    }

                    override fun shutdown() {

                    }

                }
            }

        val retrofit = Net.instance.retrofit
            .baseUrl("https://api.example.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }



    private fun  getOkhppt():OkHttpClient{
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(15, TimeUnit.SECONDS)
        builder.readTimeout(20, TimeUnit.SECONDS)
        builder.writeTimeout(35, TimeUnit.SECONDS)
        builder.addNetworkInterceptor(HttpLogger())

       return builder.build()
    }
}
