package com.itg.ddlnet.networkscene

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultCallback
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.sendBusinessResult
import com.itg.net.flow.DownloadPhase
import com.itg.net.flow.flow
import com.itg.net.flow.flowBusinessResult
import com.itg.net.flow.flowString
import com.itg.net.retrofit.retrofit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File

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
        runFieldEncryptScene()
        runFlowGet()
        runFlowPostJson()
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
