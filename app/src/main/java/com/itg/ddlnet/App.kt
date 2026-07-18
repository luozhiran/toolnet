package com.itg.ddlnet

import android.app.Application
import android.util.Log
import com.itg.net.Net
import com.itg.net.config.NetConfig
import com.itg.net.encrypt.Algorithm
import com.itg.net.encrypt.EncryptMode
import com.itg.net.monitor.IMonitorReportHandler
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.ReportMode
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultInterceptor

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Net.configure {
            application(this@App)
            baseUrl("http://47.76.59.147:8000/")
            globalParams(
                mapOf(
                    "ab" to "bai",
                    "43" to "af"
                )
            )
            maxConcurrentDownloads(1)
            enableHttpLog()
            demoFieldEncryption()
            businessInterceptor(loginExpiredInterceptor())
            monitor {
                disabled()
                    .reportMode(ReportMode.FAILURE_ONLY)
                    .reportHandler(logcatMonitorHandler())
            }
        }
    }

    private fun NetConfig.demoFieldEncryption(): NetConfig {
        return encrypt {
            algorithm(Algorithm.AES_CBC_PKCS7)
                .secretKey("0123456789abcdef0123456789abcdef")
                .iv("abcdef9876543210")
                .encryptMode(EncryptMode.OPT_IN)
                .encryptPath(
                    Regex("/api/secure-profile"),
                    listOf("phone", "idCard", "token")
                )
        }
    }

    private fun loginExpiredInterceptor(): BusinessResultInterceptor {
        return object : BusinessResultInterceptor {
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
        }
    }

    private fun logcatMonitorHandler(): IMonitorReportHandler {
        return object : IMonitorReportHandler {
            override val isAsync: Boolean get() = true

            override fun onEvent(event: MonitorEvent) {
                Log.e("Monitor", event.toJson().toString())
            }

            override fun flush() = Unit

            override fun shutdown() = Unit
        }
    }
}
