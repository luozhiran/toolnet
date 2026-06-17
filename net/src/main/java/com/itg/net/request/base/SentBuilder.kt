package com.itg.net.request.base

import android.os.Handler
import com.itg.net.download.data.Task
import okhttp3.Call
import okhttp3.Callback

interface SentBuilder {
    fun send(callback: DdCallback?)
    fun send(handler: Handler?, what: Int, errorWhat: Int)
    fun send(response: Callback?,task: Task?){}

    /**
     * 构建 OkHttp [Call] 但不立即执行，由调用方管理生命周期
     *
     * 供 net-flow 等扩展模块使用，允许在 callbackFlow 中自行
     * 管理 OkHttp 请求的发送和取消。
     *
     * @return 构建好的 [Call] 实例，参数非法时返回 null
     */
    fun buildCall(): Call? = null
}
