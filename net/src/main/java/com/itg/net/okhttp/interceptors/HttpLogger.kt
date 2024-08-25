package com.itg.net.okhttp.interceptors

import com.itg.net.tools.JsonTools
import com.orhanobut.logger.Logger
import okhttp3.logging.HttpLoggingInterceptor
import java.lang.Exception

class HttpLogger : HttpLoggingInterceptor.Logger {
    private val mMessage = StringBuilder()

    override fun log(message: String) {
        try {
            var tempMessage = message
            // 请求或者响应开始
            if (tempMessage.startsWith("--> POST")) {
                mMessage.setLength(0)
            } else if (tempMessage.startsWith("--> GET")) {
                mMessage.setLength(0)
            }
            // 以{}或者[]形式的说明是响应结果的json数据，需要进行格式化
            if ((message.startsWith("{") && message.endsWith("}")) || (message.startsWith("[") && message.endsWith("]"))) {
                tempMessage = JsonTools.formatJson(JsonTools.decodeUnicode(message));
            }
            mMessage.append(tempMessage).append("\n")
            // 响应结束，打印整条日志
            if (message.startsWith("<-- END HTTP")) {
                Logger.d(mMessage.toString())
            }
        }catch (e:Exception){

        }

    }
}