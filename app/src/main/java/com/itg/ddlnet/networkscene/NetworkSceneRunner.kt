package com.itg.ddlnet.networkscene

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultCallback
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.TypedBusinessResultCallback
import com.itg.net.request.business.sendBusinessResult
import com.itg.net.request.business.sendTypedBusinessResult
import com.itg.net.flow.DownloadPhase
import com.itg.net.flow.flow
import com.itg.net.flow.flowBusinessResult
import com.itg.net.flow.flowResponse
import com.itg.net.flow.flowResult
import com.itg.net.flow.flowString
import com.itg.net.flow.flowTypedBusinessResult
import com.itg.net.retrofit.retrofit
import com.itg.net.request.get.Get
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.NetResultCallback
import com.itg.net.request.result.sendResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkSceneRunner(
    private val activity: AppCompatActivity,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val logger: NetworkSceneLogger
) {
    private val apiBaseUrl = "http://192.168.31.216:3000/"
    private val downloadUrl = "${apiBaseUrl}download/static/test.html"

    private val retrofitApi by lazy {
        Net.instance.retrofit
            .baseUrl(apiBaseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create<SceneApi>()
    }

    fun appendStartupInfo() {
        logger.append("服务地址: $apiBaseUrl")
        logger.append("提示: 手机和电脑需要在同一个 Wi-Fi；请求会显示在 Web 的请求日志里。")
    }

    fun runAllScenes() {
        runNetGet()
        runNetPostJson()
        runNetPostForm()
        runNetPostContent()
        runTypedBusinessCallback()
        runFieldEncryptScene()
        runSkipEncryptScene()
        runGlobalParamsAndPathScene()
        runCacheAndMonitorExtraScene()
        runHttpErrorResult()
        runFlowGet()
        runFlowPostJson()
        runFlowResult()
        runFlowTypedBusinessResult()
        runFlowResponse()
        runNetDownload()
        runFlowDownload()
        runRetrofitSuspend()
        runRetrofitFlow()
    }

    fun runNetGet() {
        logger.append("net GET: start")
        Net.instance.get()
            .url(apiBaseUrl)
            .path("api/data")
            .addParam("scene", "net-get")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(object : BusinessResultCallback {
                override fun onSuccess(result: BusinessResult.Success) {
                    logger.append("net GET: success code=${result.httpCode} data=${result.dataRaw.shortBody()}")
                }

                override fun onBusinessError(error: BusinessResult.BusinessError) {
                    logger.append("net GET: business error code=${error.code.orEmpty()} message=${error.message.orEmpty()}")
                }

                override fun onHttpError(error: BusinessResult.HttpError) {
                    logger.append("net GET: http error code=${error.httpCode} body=${error.rawBody.shortBody()}")
                }

                override fun onNetworkError(error: BusinessResult.NetworkError) {
                    logger.append("net GET: network error ${error.error.message.orEmpty()}")
                }

                override fun onConsumed(result: BusinessResult.Consumed) {
                    logger.append("net GET: consumed ${result.reason.orEmpty()}")
                }
            })
    }

    fun runNetPostJson() {
        logger.append("net POST JSON: start")
        Net.instance.postJson()
            .url(apiBaseUrl)
            .path("api/json")
            .addParam("scene", "net-post-json")
            .addParam("time", System.currentTimeMillis())
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(object : BusinessResultCallback {
                override fun onSuccess(result: BusinessResult.Success) {
                    logger.append("net POST JSON: success code=${result.httpCode} data=${result.dataRaw.shortBody()}")
                }

                override fun onBusinessError(error: BusinessResult.BusinessError) {
                    logger.append("net POST JSON: business error code=${error.code.orEmpty()} message=${error.message.orEmpty()}")
                }

                override fun onHttpError(error: BusinessResult.HttpError) {
                    logger.append("net POST JSON: http error code=${error.httpCode} body=${error.rawBody.shortBody()}")
                }

                override fun onNetworkError(error: BusinessResult.NetworkError) {
                    logger.append("net POST JSON: network error ${error.error.message.orEmpty()}")
                }

                override fun onConsumed(result: BusinessResult.Consumed) {
                    logger.append("net POST JSON: consumed ${result.reason.orEmpty()}")
                }
            })
    }

    fun runNetPostForm() {
        logger.append("net POST Form: start")
        Net.instance.postForm()
            .url(apiBaseUrl)
            .path("api/form")
            .addParam("scene", "net-post-form")
            .addParam("client", "android")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("net POST Form"))
    }

    fun runNetPostContent() {
        logger.append("net POST raw content: start")
        Net.instance.postContent()
            .url(apiBaseUrl)
            .path("api/json")
            .addContent(
                """{"scene":"net-post-content","content":"raw json body","time":${System.currentTimeMillis()}}""",
                "application/json; charset=utf-8"
            )
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("net POST raw content"))
    }

    fun runTypedBusinessCallback() {
        logger.append("typed business callback: start")
        Net.instance.get()
            .url(apiBaseUrl)
            .path("api/data")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendTypedBusinessResult<SceneData>(object : TypedBusinessResultCallback<SceneData> {
                override fun onSuccess(result: TypedBusinessResult.Success<SceneData>) {
                    logger.append("typed business callback: success data=${result.data}")
                }

                override fun onDataConvertError(error: TypedBusinessResult.DataConvertError) {
                    logger.append("typed business callback: convert error ${error.error.message.orEmpty()}")
                }

                override fun onBusinessError(error: TypedBusinessResult.BusinessError) {
                    logger.append("typed business callback: business error code=${error.code.orEmpty()} message=${error.message.orEmpty()}")
                }

                override fun onHttpError(error: TypedBusinessResult.HttpError) {
                    logger.append("typed business callback: http error code=${error.httpCode} body=${error.rawBody.shortBody()}")
                }

                override fun onNetworkError(error: TypedBusinessResult.NetworkError) {
                    logger.append("typed business callback: network error ${error.error.error.message.orEmpty()}")
                }

                override fun onConsumed(result: TypedBusinessResult.Consumed) {
                    logger.append("typed business callback: consumed ${result.reason.orEmpty()}")
                }
            })
    }

    fun runFieldEncryptScene() {
        logger.append("field encrypt/decrypt: start")
        logger.append("field encrypt/decrypt: phone/idCard/token will be encrypted before sending")
        Net.instance.postJson()
            .url(apiBaseUrl)
            .path("api/secure-profile")
            .addParam("name", "Android Demo")
            .addParam("phone", "13800138000")
            .addParam("idCard", "110101199001011234")
            .addParam("token", "demo-token-${System.currentTimeMillis()}")
            .addParam("scene", "field-encrypt")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(object : BusinessResultCallback {
                override fun onSuccess(result: BusinessResult.Success) {
                    logger.append("field encrypt/decrypt: success code=${result.httpCode} data=${result.dataRaw.shortBody(520)}")
                }

                override fun onBusinessError(error: BusinessResult.BusinessError) {
                    logger.append("field encrypt/decrypt: business error code=${error.code.orEmpty()} message=${error.message.orEmpty()}")
                }

                override fun onHttpError(error: BusinessResult.HttpError) {
                    logger.append("field encrypt/decrypt: http error code=${error.httpCode} body=${error.rawBody.shortBody(520)}")
                }

                override fun onNetworkError(error: BusinessResult.NetworkError) {
                    logger.append("field encrypt/decrypt: network error ${error.error.message.orEmpty()}")
                }

                override fun onConsumed(result: BusinessResult.Consumed) {
                    logger.append("field encrypt/decrypt: consumed ${result.reason.orEmpty()}")
                }
            })
    }

    fun runSkipEncryptScene() {
        logger.append("skip encrypt override: start")
        logger.append("skip encrypt override: expect HTTP 400 because secure fields are sent as plain text")
        Net.instance.postJson()
            .url(apiBaseUrl)
            .path("api/secure-profile")
            .addParam("name", "Plain Demo")
            .addParam("phone", "13800138000")
            .addParam("idCard", "110101199001011234")
            .addParam("token", "plain-token")
            .skipEncrypt()
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendResult(object : NetResultCallback {
                override fun onSuccess(result: NetResult.Success) {
                    logger.append("skip encrypt override: unexpected success body=${result.body.shortBody()}")
                }

                override fun onHttpError(error: NetResult.HttpError) {
                    logger.append("skip encrypt override: expected http error code=${error.code} body=${error.body.shortBody(420)}")
                }

                override fun onNetworkError(error: NetResult.NetworkError) {
                    logger.append("skip encrypt override: network error ${error.message.orEmpty()}")
                }
            })
    }

    fun runGlobalParamsAndPathScene() {
        logger.append("global params + path/query: start")
        Net.instance.get()
            .url(apiBaseUrl)
            .path("api/user/42")
            .addParam("scene", "global-path-query")
            .addParam("name", "android")
            .autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("global params + path/query"))
    }

    fun runCacheAndMonitorExtraScene() {
        logger.append("cache + monitorExtra: start")
        val cacheControl = CacheControl.Builder()
            .maxAge(30, TimeUnit.SECONDS)
            .build()
        Net.instance.get()
            .url(apiBaseUrl)
            .path("api/data")
            .addParam("scene", "cache-monitor-extra")
            .addCacheControl(cacheControl)
            .monitorExtra("scene=cache-monitor-extra")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("cache + monitorExtra"))
    }

    fun runHttpErrorResult() {
        logger.append("sendResult HTTP error: start")
        Net.instance.get()
            .url(apiBaseUrl)
            .path("api/not-found-for-result")
            .noUseGlobalParams()
            .autoCancel(activity)
            .sendResult(object : NetResultCallback {
                override fun onSuccess(result: NetResult.Success) {
                    logger.append("sendResult HTTP error: unexpected success code=${result.code} body=${result.body.shortBody()}")
                }

                override fun onHttpError(error: NetResult.HttpError) {
                    logger.append("sendResult HTTP error: expected code=${error.code} message=${error.message.orEmpty()} body=${error.body.shortBody(420)}")
                }

                override fun onNetworkError(error: NetResult.NetworkError) {
                    logger.append("sendResult HTTP error: network error ${error.message.orEmpty()}")
                }
            })
    }

    fun runFlowGet() {
        logger.append("net-flow GET: start")
        lifecycleScope.launch {
            Net.instance.get()
                .url(apiBaseUrl)
                .path("api/echo")
                .addParam("scene", "net-flow-get")
                .addParam("client", "android")
                .noUseGlobalParams()
                .flowBusinessResult()
                .catch { e -> logger.append("net-flow GET: failed ${e.message.orEmpty()}") }
                .collect { result ->
                    when (result) {
                        is BusinessResult.Success -> logger.append("net-flow GET: success code=${result.httpCode} data=${result.dataRaw.shortBody()}")
                        is BusinessResult.BusinessError -> logger.append("net-flow GET: business error code=${result.code.orEmpty()} message=${result.message.orEmpty()}")
                        is BusinessResult.HttpError -> logger.append("net-flow GET: http error code=${result.httpCode} body=${result.rawBody.shortBody()}")
                        is BusinessResult.NetworkError -> logger.append("net-flow GET: network error ${result.error.message.orEmpty()}")
                        is BusinessResult.Consumed -> logger.append("net-flow GET: consumed ${result.reason.orEmpty()}")
                    }
                }
        }
    }

    fun runFlowPostJson() {
        logger.append("net-flow POST JSON: start")
        lifecycleScope.launch {
            Net.instance.postJson()
                .url(apiBaseUrl)
                .path("api/json")
                .addParam("scene", "net-flow-post-json")
                .addParam("time", System.currentTimeMillis())
                .noUseGlobalParams()
                .flowString()
                .catch { e -> logger.append("net-flow POST JSON: failed ${e.message.orEmpty()}") }
                .collect { body ->
                    logger.append("net-flow POST JSON: body=${body.shortBody()}")
                }
        }
    }

    fun runFlowResult() {
        logger.append("net-flow flowResult: start")
        lifecycleScope.launch {
            Net.instance.get()
                .url(apiBaseUrl)
                .path("api/not-found-flow-result")
                .noUseGlobalParams()
                .flowResult()
                .catch { e -> logger.append("net-flow flowResult: failed ${e.message.orEmpty()}") }
                .collect { result ->
                    when (result) {
                        is NetResult.Success -> logger.append("net-flow flowResult: success code=${result.code} body=${result.body.shortBody()}")
                        is NetResult.HttpError -> logger.append("net-flow flowResult: http error code=${result.code} body=${result.body.shortBody(420)}")
                        is NetResult.NetworkError -> logger.append("net-flow flowResult: network error ${result.message.orEmpty()}")
                    }
                }
        }
    }

    fun runFlowTypedBusinessResult() {
        logger.append("net-flow typed business: start")
        lifecycleScope.launch {
            val request = Net.instance.get()
            request.url(apiBaseUrl)
                .path("api/data")
                .noUseGlobalParams()
            request.flowTypedBusinessResult<SceneData, Get>()
                .catch { e -> logger.append("net-flow typed business: failed ${e.message.orEmpty()}") }
                .collect { result ->
                    when (result) {
                        is TypedBusinessResult.Success<*> -> logger.append("net-flow typed business: success data=${result.data}")
                        is TypedBusinessResult.DataConvertError -> logger.append("net-flow typed business: convert error ${result.error.message.orEmpty()}")
                        is TypedBusinessResult.BusinessError -> logger.append("net-flow typed business: business error code=${result.code.orEmpty()} message=${result.message.orEmpty()}")
                        is TypedBusinessResult.HttpError -> logger.append("net-flow typed business: http error code=${result.httpCode} body=${result.rawBody.shortBody()}")
                        is TypedBusinessResult.NetworkError -> logger.append("net-flow typed business: network error ${result.error.error.message.orEmpty()}")
                        is TypedBusinessResult.Consumed -> logger.append("net-flow typed business: consumed ${result.reason.orEmpty()}")
                    }
                }
        }
    }

    fun runFlowResponse() {
        logger.append("net-flow response converter: start")
        lifecycleScope.launch {
            Net.instance.get()
                .url(apiBaseUrl)
                .path("api/echo")
                .addParam("scene", "flow-response")
                .addParam("client", "android")
                .noUseGlobalParams()
                .flowResponse { raw -> raw?.contains("flow-response") == true }
                .catch { e -> logger.append("net-flow response converter: failed ${e.message.orEmpty()}") }
                .collect { response ->
                    logger.append("net-flow response converter: code=${response.code} ok=${response.isSuccessful} converted=${response.body} raw=${response.rawBody.shortBody()}")
                }
        }
    }

    fun runNetDownload() {
        val file = File(activity.cacheDir, "net-callback-sample.bin")
        logger.append("net download: start ${file.absolutePath}")
        Net.instance.newDownload()
            .url(downloadUrl)
            .savePath(file.absolutePath)
            .overwrite(true)
            .retryCount(1)
            .noUseGlobalParams()
            .listener(object : AbstractProgressCallback() {
                override fun onConnecting(task: Task) {
                    logger.append("net download: connecting")
                }

                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) {
                        logger.append("net download: complete ${task.path}")
                    } else {
                        logger.append("net download: ${task.downloadSize}/${task.contentLength}")
                    }
                }

                override fun onFail(error: String?, task: Task) {
                    logger.append("net download: failed ${error.orEmpty()}")
                }

                override fun onFinish(task: Task) {
                    logger.append("net download: finish")
                }
            })
            .start()
    }

    fun runFlowDownload() {
        val file = File(activity.cacheDir, "net-flow-sample.bin")
        logger.append("net-flow download: start ${file.absolutePath}")
        lifecycleScope.launch {
            Net.instance.newDownload()
                .url(downloadUrl)
                .savePath(file.absolutePath)
                .overwrite(true)
                .retryCount(1)
                .noUseGlobalParams()
                .flow()
                .catch { e -> logger.append("net-flow download: failed ${e.message.orEmpty()}") }
                .collect { progress ->
                    when (progress.phase) {
                        DownloadPhase.Connecting -> logger.append("net-flow download: connecting")
                        DownloadPhase.Downloading -> {
                            val task = progress.task
                            logger.append("net-flow download: ${task.downloadSize}/${task.contentLength}")
                        }
                        DownloadPhase.Complete -> logger.append("net-flow download: complete ${progress.task.path}")
                        DownloadPhase.Failed -> logger.append("net-flow download: failed")
                    }
                }
        }
    }

    fun runRetrofitSuspend() {
        logger.append("net-retrofit suspend: start")
        lifecycleScope.launch {
            try {
                val response = retrofitApi.getEcho("net-retrofit-suspend", "android")
                logger.append("net-retrofit suspend: code=${response.code()} body=${response.body()?.string().shortBody()}")
            } catch (e: Exception) {
                logger.append("net-retrofit suspend: failed ${e.message.orEmpty()}")
            }
        }
    }

    fun runRetrofitFlow() {
        logger.append("net-retrofit Typed Business Flow: start")
        lifecycleScope.launch {
            retrofitApi.getDataFlow()
                .catch { e -> logger.append("net-retrofit Typed Business Flow: failed ${e.message.orEmpty()}") }
                .collect { result ->
                    when (result) {
                        is TypedBusinessResult.Success -> logger.append("net-retrofit Typed Business Flow: success data=${result.data}")
                        is TypedBusinessResult.DataConvertError -> logger.append("net-retrofit Typed Business Flow: convert error ${result.error.message.orEmpty()}")
                        is TypedBusinessResult.BusinessError -> logger.append("net-retrofit Typed Business Flow: business error code=${result.code.orEmpty()} message=${result.message.orEmpty()}")
                        is TypedBusinessResult.HttpError -> logger.append("net-retrofit Typed Business Flow: http error code=${result.httpCode} body=${result.rawBody.shortBody()}")
                        is TypedBusinessResult.NetworkError -> logger.append("net-retrofit Typed Business Flow: network error ${result.error.error.error.message.orEmpty()}")
                        is TypedBusinessResult.Consumed -> logger.append("net-retrofit Typed Business Flow: consumed ${result.reason.orEmpty()}")
                    }
                }
        }
    }

    private fun logBusinessCallback(sceneName: String): BusinessResultCallback {
        return object : BusinessResultCallback {
            override fun onSuccess(result: BusinessResult.Success) {
                logger.append("$sceneName: success code=${result.httpCode} data=${result.dataRaw.shortBody()}")
            }

            override fun onBusinessError(error: BusinessResult.BusinessError) {
                logger.append("$sceneName: business error code=${error.code.orEmpty()} message=${error.message.orEmpty()}")
            }

            override fun onHttpError(error: BusinessResult.HttpError) {
                logger.append("$sceneName: http error code=${error.httpCode} body=${error.rawBody.shortBody()}")
            }

            override fun onNetworkError(error: BusinessResult.NetworkError) {
                logger.append("$sceneName: network error ${error.error.message.orEmpty()}")
            }

            override fun onConsumed(result: BusinessResult.Consumed) {
                logger.append("$sceneName: consumed ${result.reason.orEmpty()}")
            }
        }
    }

    private fun String?.shortBody(maxLength: Int = 240): String {
        val value = this.orEmpty().replace("\n", " ")
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }

    private interface SceneApi {
        @GET("api/echo")
        suspend fun getEcho(
            @Query("scene") scene: String,
            @Query("client") client: String
        ): Response<ResponseBody>

        @GET("api/data")
        fun getDataFlow(): Flow<TypedBusinessResult<SceneData>>
    }

    private data class SceneData(
        val id: Int,
        val name: String,
        val timestamp: Long
    )
}
