package com.itg.ddlnet

import android.app.Application
import android.util.Log
import com.itg.net.Net
import com.itg.net.logging.HttpLogger
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.ReportMode
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor
import com.itg.net.retrofit.retrofit
import okhttp3.OkHttpClient
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class App:Application() {

    override fun onCreate() {
        super.onCreate()

        Net.configure {
            application(this@App)
            globalParam("ab", "bai")
            globalParam("43", "af")
            maxConcurrentDownloads(1)
            enableHttpLog()
            baseUrl("http://47.76.59.147:8000/")
            businessInterceptor(object : BusinessResultInterceptor {
                override fun intercept(chain: BusinessResultInterceptor.Chain): BusinessResult {
                    val code = chain.envelope.code
                    if (code == "TOKEN_EXPIRED" || code == "401001") {
                        Log.w("BusinessResult", "login expired, navigate to login here")
                        return BusinessResult.Consumed(
                            reason = "login expired",
                            httpCode = chain.response.code,
                            rawBody = chain.response.body
                        )
                    }
                    return chain.proceed()
                }
            })
            monitor {
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
