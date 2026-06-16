package com.itg.net

import com.itg.net.config.NetConfig
import com.itg.net.client.OkHttpManager
import com.itg.net.request.create
import com.itg.net.request.get.Get
import com.itg.net.request.post.multipart.PostMul
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.post.content.PostContent
import com.itg.net.request.post.file.PostFile
import com.itg.net.request.post.form.PostForm
import com.itg.net.request.post.json.PostJson
import com.itg.net.util.PrintLog


const val MEDIA_JSON = "application/json; charset=utf-8"

const val MEDIA_OCTET_STREAM = "application/octet-stream"

//榛樿骞挎挱
const val BROAD_ACTION = "com.yqtec.install.broadcast"

enum class ModeType{PostFile,PostForm,PostJson,PostMul,Get,PostResume,PostContent}
class Net {

    companion object {

        @JvmStatic
        val instance: Net by lazy { Net() }
    }

    val ddNetConfig: NetConfig by lazy { NetConfig() }
    val okhttpManager: OkHttpManager by lazy { OkHttpManager(ddNetConfig) }
    val download: Download by lazy { Download.instance }

    fun configure(block: NetConfig.() -> Unit): Net {
        ddNetConfig.block()
        return this
    }

    fun builder(type: ModeType): ParamsBuilder {
        PrintLog.logr("创建 ${type.name} 类型")
        return create(type)
    }

    fun get() = builder(ModeType.Get) as Get

    fun postMultipart() = builder(ModeType.PostMul) as PostMul

    fun postFile() = builder(ModeType.PostFile) as PostFile

    fun postForm() = builder(ModeType.PostForm) as PostForm

    fun postJson() = builder(ModeType.PostJson) as PostJson

    fun postContent() = builder(ModeType.PostContent) as PostContent

    fun newDownload() = download.taskBuilder()


    fun cancelAll() {
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            it.cancel()
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            it.cancel()
        }
    }

    fun cancelTag(tag: Any?) {
        if (tag == null) return
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
            }
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
            }
        }
    }

    fun cancel(tag: Any?) = cancelTag(tag)


    fun cancelFirstTag(tag: Any?): Boolean {
        if (tag == null) return false
        okhttpManager.okHttpClient.dispatcher.queuedCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
                return true
            }
        }
        okhttpManager.okHttpClient.dispatcher.runningCalls().forEach {
            if (tag == it.request().tag()) {
                it.cancel()
                return true
            }
        }
        return false
    }

}
