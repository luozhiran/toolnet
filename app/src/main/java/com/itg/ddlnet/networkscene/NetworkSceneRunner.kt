package com.itg.ddlnet.networkscene

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.ModeType
import com.itg.net.request.base.DdCallback
import com.itg.net.request.business.BusinessResult
import com.itg.net.request.business.BusinessResultCallback
import com.itg.net.request.business.TypedBusinessResult
import com.itg.net.request.business.TypedBusinessResultCallback
import com.itg.net.request.business.sendBusinessResult
import com.itg.net.request.business.sendTypedBusinessResult
import com.itg.net.flow.DownloadPhase
import com.itg.net.flow.NetResponse
import com.itg.net.flow.converter.GsonNetConverter
import com.itg.net.flow.flow
import com.itg.net.flow.flowDownload
import com.itg.net.flow.flowGet
import com.itg.net.flow.flowGetResponse
import com.itg.net.flow.flowBusinessResult
import com.itg.net.flow.flowPostJson
import com.itg.net.flow.flowPostJsonResponse
import com.itg.net.flow.flowResponse
import com.itg.net.flow.flowResult
import com.itg.net.flow.flowString
import com.itg.net.flow.flowTypedBusinessResult
import com.itg.net.request.result.NetResult
import com.itg.net.request.result.NetResultCallback
import com.itg.net.request.result.sendResult
import com.itg.net.retrofit.retrofit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.IOException
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
        runResponseTooLarge()
        runInterceptorError()
        runDdCallbackBasic()
        runHandlerCallback()
        runBuildCall()
        runOkHttpCallback()
        runPostFileUpload()
        runPostResumeUpload()
        runFlowString()
        runFlowGetConvenience()
        runFlowPostJsonConvenience()
        runFlowGetResponse()
        runFlowPostJsonResponse()
        runFlowDownloadConvenience()
        runNetDownloadBindActivity()
        runNetDownloadMd5()
        runNetDownloadMonitorExtra()
        runNetDownloadSkipMonitor()
        runGlobalDownloadListener()
        runCancelByTag()
        runCancelFirstTag()
        runCancelAll()
        runRetrofitSuspend()
        runRetrofitNetResponse()
        runRetrofitNetResult()
        runRetrofitFlow()
        runFlushMonitor()
        runCustomInterceptor()
        runEncryptOptIn()
        runFlowGet()
        runFlowPostJson()
        runFlowResult()
        runFlowTypedBusinessResult()
        runFlowResponse()
        runNetDownload()
        runFlowDownload()
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
                        is BusinessResult.ResponseTooLarge -> logger.append("net-flow GET: response too large ${result.error.message}")
                        is BusinessResult.NetworkError -> logger.append("net-flow GET: network error ${result.error.message.orEmpty()}")
                        is BusinessResult.InterceptorError -> logger.append("net-flow GET: interceptor error ${result.error.message.orEmpty()}")
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
                        is NetResult.ResponseTooLarge -> logger.append("net-flow flowResult: response too large ${result.message}")
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
            request.flowTypedBusinessResult<SceneData>()
                .catch { e -> logger.append("net-flow typed business: failed ${e.message.orEmpty()}") }
                .collect { result ->
                    when (result) {
                        is TypedBusinessResult.Success<*> -> logger.append("net-flow typed business: success data=${result.data}")
                        is TypedBusinessResult.DataConvertError -> logger.append("net-flow typed business: convert error ${result.error.message.orEmpty()}")
                        is TypedBusinessResult.BusinessError -> logger.append("net-flow typed business: business error code=${result.code.orEmpty()} message=${result.message.orEmpty()}")
                        is TypedBusinessResult.HttpError -> logger.append("net-flow typed business: http error code=${result.httpCode} body=${result.rawBody.shortBody()}")
                        is TypedBusinessResult.ResponseTooLarge -> logger.append("net-flow typed business: response too large ${result.error.error.message}")
                        is TypedBusinessResult.NetworkError -> logger.append("net-flow typed business: network error ${result.error.error.message.orEmpty()}")
                        is TypedBusinessResult.InterceptorError -> logger.append("net-flow typed business: interceptor error ${result.error.error.message.orEmpty()}")
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
                        is TypedBusinessResult.ResponseTooLarge -> logger.append("net-retrofit Typed Business Flow: response too large ${result.error.error.message}")
                        is TypedBusinessResult.NetworkError -> logger.append("net-retrofit Typed Business Flow: network error ${result.error.error.error.message.orEmpty()}")
                        is TypedBusinessResult.InterceptorError -> logger.append("net-retrofit Typed Business Flow: interceptor error ${result.error.error.message.orEmpty()}")
                        is TypedBusinessResult.Consumed -> logger.append("net-retrofit Typed Business Flow: consumed ${result.reason.orEmpty()}")
                    }
                }
        }
    }

    // ==================== New scenes: callback variants ====================

    fun runDdCallbackBasic() {
        logger.append("DdCallback basic: start")
        Net.instance.get()
            .url(apiBaseUrl).path("api/echo")
            .addParam("scene", "dd-callback-basic").noUseGlobalParams()
            .autoCancel(activity)
            .send(object : DdCallback {
                override fun onFailure(er: String?) { logger.append("DdCallback basic: failure ${er.orEmpty()}") }
                override fun onResponse(result: String?, code: Int) { logger.append("DdCallback basic: code=$code body=${result.shortBody()}") }
            })
    }

    fun runHandlerCallback() {
        logger.append("Handler callback: start")
        val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
            when (msg.what) {
                1 -> { logger.append("Handler callback: success body=${(msg.obj as? String).shortBody()}"); true }
                2 -> { logger.append("Handler callback: error ${msg.obj}"); true }
                else -> false
            }
        }
        Net.instance.get()
            .url(apiBaseUrl).path("api/echo")
            .addParam("scene", "handler-callback").noUseGlobalParams()
            .autoCancel(activity)
            .send(handler, 1, 2)
    }

    fun runBuildCall() {
        logger.append("buildCall: start")
        val call = Net.instance.get()
            .url(apiBaseUrl).path("api/echo")
            .addParam("scene", "build-call").noUseGlobalParams()
            .buildCall()
        if (call != null) {
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) { logger.append("buildCall: failure ${e.message}") }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    logger.append("buildCall: code=${response.code} body=${response.body?.string().shortBody()}")
                    response.close()
                }
            })
        } else { logger.append("buildCall: call is null (invalid url)") }
    }

    fun runOkHttpCallback() {
        logger.append("OkHttp raw callback: start")
        Net.instance.get()
            .url(apiBaseUrl).path("api/echo")
            .addParam("scene", "okhttp-callback").noUseGlobalParams()
            .send(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) { logger.append("OkHttp callback: failure ${e.message}") }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    logger.append("OkHttp callback: code=${response.code} body=${response.body?.string().shortBody()}")
                    response.close()
                }
            }, task = null)
    }

    // ==================== POST file upload ====================

    fun runPostFileUpload() {
        val file = File(activity.cacheDir, "upload-sample.txt").also { it.writeText("Net upload test ${System.currentTimeMillis()}") }
        logger.append("PostFile upload: start file=${file.name} size=${file.length()}")
        val request = Net.instance.postFile()
        request.url(apiBaseUrl).path("api/form")
        request.addFile(file)
        request.noUseGlobalParams().autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("PostFile upload"))
    }

    fun runPostResumeUpload() {
        val file = File(activity.cacheDir, "resume-upload.bin").also { it.writeText("X".repeat(1024 * 100)) }
        logger.append("PostResumeFile: start size=${file.length()} offset=1024")
        val request = Net.instance.postResumeFile()
        request.url(apiBaseUrl).path("api/form")
        request.addFile(file)
        request.addResumeFileOffset(1024L)
        request.noUseGlobalParams().autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("PostResumeFile"))
    }

    // ==================== Error handling: ResponseTooLarge & InterceptorError ====================

    fun runResponseTooLarge() {
        logger.append("ResponseTooLarge: start")
        Net.instance.get()
            .url(apiBaseUrl).path("api/data")
            .addParam("scene", "response-too-large").noUseGlobalParams().autoCancel(activity)
            .sendResult(object : NetResultCallback {
                override fun onSuccess(result: NetResult.Success) { logger.append("ResponseTooLarge: success code=${result.code}") }
                override fun onHttpError(error: NetResult.HttpError) { logger.append("ResponseTooLarge: http error code=${error.code}") }
                override fun onResponseTooLarge(error: NetResult.ResponseTooLarge) { logger.append("ResponseTooLarge: len=${error.contentLength} max=${error.maxBytes}") }
                override fun onNetworkError(error: NetResult.NetworkError) { logger.append("ResponseTooLarge: network error ${error.message}") }
            })
    }

    fun runInterceptorError() {
        logger.append("InterceptorError: start")
        Net.instance.get()
            .url(apiBaseUrl).path("api/echo")
            .addParam("scene", "interceptor-error").noUseGlobalParams().autoCancel(activity)
            .sendBusinessResult(object : BusinessResultCallback {
                override fun onSuccess(result: BusinessResult.Success) { logger.append("InterceptorError: success") }
                override fun onBusinessError(error: BusinessResult.BusinessError) { logger.append("InterceptorError: business error") }
                override fun onHttpError(error: BusinessResult.HttpError) { logger.append("InterceptorError: http error") }
                override fun onNetworkError(error: BusinessResult.NetworkError) { logger.append("InterceptorError: network error") }
                override fun onInterceptorError(error: BusinessResult.InterceptorError) { logger.append("InterceptorError: caught index=${error.index} err=${error.error.message}") }
                override fun onResponseTooLarge(error: BusinessResult.ResponseTooLarge) { logger.append("InterceptorError: too large") }
                override fun onConsumed(result: BusinessResult.Consumed) { logger.append("InterceptorError: consumed") }
            })
    }

    // ==================== Flow convenience methods ====================

    fun runFlowString() {
        logger.append("net-flow flowString: start")
        lifecycleScope.launch {
            Net.instance.get()
                .url(apiBaseUrl).path("api/echo").addParam("scene", "flow-string").noUseGlobalParams()
                .flowString()
                .catch { e -> logger.append("flowString: failed ${e.message.orEmpty()}") }
                .collect { body -> logger.append("flowString: body=${body.shortBody()}") }
        }
    }

    fun runFlowGetConvenience() {
        logger.append("flowGet convenience: start")
        lifecycleScope.launch {
            Net.instance.flowGet { url(apiBaseUrl).path("api/echo").addParam("scene", "flow-get-convenience").noUseGlobalParams() }
                .catch { e -> logger.append("flowGet: failed ${e.message.orEmpty()}") }
                .collect { body -> logger.append("flowGet: body=${body.shortBody()}") }
        }
    }

    fun runFlowPostJsonConvenience() {
        logger.append("flowPostJson convenience: start")
        lifecycleScope.launch {
            Net.instance.flowPostJson { url(apiBaseUrl).path("api/json").addParam("scene", "flow-post-json-convenience").addParam("time", System.currentTimeMillis()).noUseGlobalParams() }
                .catch { e -> logger.append("flowPostJson: failed ${e.message.orEmpty()}") }
                .collect { body -> logger.append("flowPostJson: body=${body.shortBody()}") }
        }
    }

    fun runFlowGetResponse() {
        logger.append("flowGetResponse (deserialize): start")
        lifecycleScope.launch {
            Net.instance.flowGetResponse(GsonNetConverter<SceneData>(type = SceneData::class.java)) {
                url(apiBaseUrl).path("api/data").addParam("scene", "flow-get-response").noUseGlobalParams()
            }
                .catch { e -> logger.append("flowGetResponse: failed ${e.message.orEmpty()}") }
                .collect { response -> logger.append("flowGetResponse: code=${response.code} ok=${response.isSuccessful} body=${response.body}") }
        }
    }

    fun runFlowPostJsonResponse() {
        logger.append("flowPostJsonResponse (deserialize): start")
        lifecycleScope.launch {
            Net.instance.flowPostJsonResponse(GsonNetConverter<SceneData>(type = SceneData::class.java)) {
                url(apiBaseUrl).path("api/json").addParam("scene", "flow-post-json-response").addParam("time", System.currentTimeMillis()).noUseGlobalParams()
            }
                .catch { e -> logger.append("flowPostJsonResponse: failed ${e.message.orEmpty()}") }
                .collect { response -> logger.append("flowPostJsonResponse: code=${response.code} ok=${response.isSuccessful} body=${response.body}") }
        }
    }

    fun runFlowDownloadConvenience() {
        val file = File(activity.cacheDir, "flow-convenience-dl.bin")
        logger.append("flowDownload convenience: start ${file.absolutePath}")
        lifecycleScope.launch {
            Net.instance.flowDownload { url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1).noUseGlobalParams() }
                .catch { e -> logger.append("flowDownload: failed ${e.message.orEmpty()}") }
                .collect { progress ->
                    when (progress.phase) {
                        DownloadPhase.Connecting -> logger.append("flowDownload: connecting")
                        DownloadPhase.Downloading -> logger.append("flowDownload: ${progress.task.downloadSize}/${progress.task.contentLength}")
                        DownloadPhase.Complete -> logger.append("flowDownload: complete")
                        DownloadPhase.Failed -> logger.append("flowDownload: failed")
                    }
                }
        }
    }

    // ==================== Download variants ====================

    fun runNetDownloadBindActivity() {
        val file = File(activity.cacheDir, "bind-activity-dl.bin")
        logger.append("download bindActivity: start")
        Net.instance.newDownload().url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1)
            .bindActivity(activity).noUseGlobalParams()
            .listener(object : AbstractProgressCallback() {
                override fun onConnecting(task: Task) { logger.append("bindActivity dl: connecting") }
                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) logger.append("bindActivity dl: complete") else logger.append("bindActivity dl: ${task.downloadSize}/${task.contentLength}")
                }
                override fun onFail(error: String?, task: Task) { logger.append("bindActivity dl: failed ${error.orEmpty()}") }
                override fun onFinish(task: Task) { logger.append("bindActivity dl: finish") }
            }).start()
    }

    fun runNetDownloadMd5() {
        val file = File(activity.cacheDir, "md5-check-dl.bin")
        logger.append("download with MD5: start")
        Net.instance.newDownload().url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1)
            .noUseGlobalParams()
            .listener(object : AbstractProgressCallback() {
                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) logger.append("md5 dl: complete (md5=${task.md5})") else logger.append("md5 dl: ${task.downloadSize}/${task.contentLength}")
                }
                override fun onFail(error: String?, task: Task) { logger.append("md5 dl: failed ${error.orEmpty()}") }
            }).start()
    }

    fun runNetDownloadMonitorExtra() {
        val file = File(activity.cacheDir, "monitor-dl.bin")
        logger.append("download monitor extra: start")
        Net.instance.newDownload().url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1)
            .noUseGlobalParams().monitor().monitorExtra("scene=download-monitor;version=1.0")
            .listener(object : AbstractProgressCallback() {
                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) logger.append("monitor dl: complete") else logger.append("monitor dl: ${task.downloadSize}/${task.contentLength}")
                }
            }).start()
    }

    fun runNetDownloadSkipMonitor() {
        val file = File(activity.cacheDir, "skip-monitor-dl.bin")
        logger.append("download skipMonitor: start")
        Net.instance.newDownload().url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1)
            .noUseGlobalParams().skipMonitor()
            .listener(object : AbstractProgressCallback() {
                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) logger.append("skipMonitor dl: complete") else logger.append("skipMonitor dl: ${task.downloadSize}/${task.contentLength}")
                }
            }).start()
    }

    fun runGlobalDownloadListener() {
        val file = File(activity.cacheDir, "global-listener-dl.bin")
        logger.append("global download listener: start")
        val global = object : AbstractProgressCallback() {
            override fun onConnecting(task: Task) { logger.append("global: connecting ${task.url}") }
            override fun onProgress(task: Task, complete: Boolean) { if (complete) logger.append("global: complete ${task.url}") }
            override fun onFinish(task: Task) {
                Net.instance.removeGlobalDownloadListener(this)
                logger.append("global: removed after finish")
            }
        }
        Net.instance.addGlobalDownloadListener(global)
        Net.instance.newDownload().url(downloadUrl).savePath(file.absolutePath).overwrite(true).retryCount(1).noUseGlobalParams().start()
    }

    // ==================== Cancel variants ====================

    fun runCancelByTag() {
        val tag = "cancel-tag-${System.currentTimeMillis()}"
        logger.append("cancel by tag: tag=$tag")
        Net.instance.get().url(apiBaseUrl).path("api/echo").addParam("scene", "cancel-by-tag").addTag(tag).noUseGlobalParams()
            .sendBusinessResult(object : BusinessResultCallback {
                override fun onSuccess(result: BusinessResult.Success) { logger.append("cancel by tag: success (not cancelled)") }
                override fun onBusinessError(error: BusinessResult.BusinessError) {}
                override fun onHttpError(error: BusinessResult.HttpError) {}
                override fun onNetworkError(error: BusinessResult.NetworkError) { logger.append("cancel by tag: cancelled") }
                override fun onConsumed(result: BusinessResult.Consumed) {}
            })
        Net.instance.cancel(tag)
    }

    fun runCancelFirstTag() {
        val tag = "cancel-first-${System.currentTimeMillis()}"
        logger.append("cancelFirstTag: tag=$tag (3 requests, cancels first)")
        repeat(3) { i ->
            Net.instance.get().url(apiBaseUrl).path("api/echo").addParam("scene", "cancel-first-$i").addTag(tag).noUseGlobalParams().autoCancel(activity)
                .sendBusinessResult(logBusinessCallback("cancelFirstTag #$i"))
        }
        logger.append("cancelFirstTag: found=${Net.instance.cancelFirstTag(tag)}")
    }

    fun runCancelAll() {
        logger.append("cancelAll: start")
        repeat(2) { i -> Net.instance.get().url(apiBaseUrl).path("api/echo").addParam("scene", "cancel-all-$i").noUseGlobalParams().sendBusinessResult(logBusinessCallback("cancelAll #$i")) }
        Net.instance.cancelAll()
        logger.append("cancelAll: done")
    }

    // ==================== Retrofit: NetResponse, NetResult ====================

    fun runRetrofitNetResponse() {
        logger.append("retrofit NetResponse: start")
        lifecycleScope.launch {
            try {
                val r = retrofitApi.getEcho("retrofit-net-response", "android")
                logger.append("retrofit NetResponse: code=${r.code()} ok=${r.isSuccessful} body=${r.body()?.string().shortBody()}")
            } catch (e: Exception) { logger.append("retrofit NetResponse: failed ${e.message.orEmpty()}") }
        }
    }

    fun runRetrofitNetResult() {
        logger.append("retrofit NetResult Flow: start")
        lifecycleScope.launch {
            retrofitApi.getNetResult("retrofit-net-result")
                .catch { e -> logger.append("retrofit NetResult: failed ${e.message.orEmpty()}") }
                .collect { r ->
                    when (r) {
                        is NetResult.Success -> logger.append("retrofit NetResult: success code=${r.code} body=${r.body.shortBody()}")
                        is NetResult.HttpError -> logger.append("retrofit NetResult: http error code=${r.code}")
                        is NetResult.NetworkError -> logger.append("retrofit NetResult: network error ${r.message}")
                        is NetResult.ResponseTooLarge -> logger.append("retrofit NetResult: too large")
                    }
                }
        }
    }

    // ==================== Advanced ====================

    fun runFlushMonitor() {
        logger.append("flushMonitor: flushing buffered events")
        Net.instance.flushMonitor()
        logger.append("flushMonitor: done")
    }

    fun runCustomInterceptor() {
        logger.append("custom interceptor: adding X-Demo header")
        Net.instance.get().url(apiBaseUrl).path("api/echo").addParam("scene", "custom-interceptor")
            .addHeader("X-Demo", "interceptor-test").noUseGlobalParams().autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("custom interceptor"))
    }

    fun runEncryptOptIn() {
        logger.append("encrypt OPT_IN mode: encryptPath /api/json only")
        Net.instance.postJson().url(apiBaseUrl).path("api/json")
            .addParam("scene", "encrypt-optin").addParam("phone", "13800138000").addParam("name", "demo")
            .noUseGlobalParams().autoCancel(activity)
            .sendBusinessResult(logBusinessCallback("encrypt OPT_IN"))
    }

    // ==================== Helpers ====================

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
        ): Response<okhttp3.ResponseBody>

        @GET("api/data")
        fun getDataFlow(): kotlinx.coroutines.flow.Flow<TypedBusinessResult<SceneData>>

        @GET("api/data")
        fun getNetResult(
            @Query("scene") scene: String
        ): kotlinx.coroutines.flow.Flow<NetResult>
    }

    private data class SceneData(
        val id: Int,
        val name: String,
        val timestamp: Long
    )
}
