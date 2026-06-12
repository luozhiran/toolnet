package com.itg.net.okhttp.interceptors

import android.util.Log
import com.itg.net.download.data.DOWNLOAD_LOG_TAG
import com.itg.net.tools.JsonTools
import okhttp3.logging.HttpLoggingInterceptor

class HttpLogger : HttpLoggingInterceptor.Logger {
    private val messageBuffer = StringBuilder()

    override fun log(message: String) {
        try {
            var logLine = message
            // 请求或者响应开始
            if (logLine.startsWith("--> POST") || logLine.startsWith("--> GET")) {
                messageBuffer.setLength(0)
            }
            // 以{}或者[]形式的说明是响应结果的json数据，需要进行格式化
            if ((message.startsWith("{") && message.endsWith("}")) || (message.startsWith("[") && message.endsWith("]"))) {
                logLine = JsonTools.formatJson(JsonTools.decodeUnicode(message))
            }
            messageBuffer.append(logLine).append("\n")
            // 响应结束，打印整条日志
            if (message.startsWith("<-- END HTTP")) {
                Log.d(DOWNLOAD_LOG_TAG, messageBuffer.toString())
            }
        } catch (e: Exception) {
            Log.w(DOWNLOAD_LOG_TAG, "HTTP 日志格式化失败", e)
        }
    }
}
