package com.itg.ddlnet

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.itg.net.Net
import com.itg.net.download.callback.AbstractProgressCallback
import com.itg.net.download.data.Task
import com.itg.net.flow.DownloadPhase
import com.itg.net.flow.flow
import com.itg.net.flow.flowString
import com.itg.net.retrofit.retrofit
import com.itg.net.request.base.DdCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File

class NetworkSceneActivity : AppCompatActivity() {

    private val apiBaseUrl = "https://httpbin.org/"
    private val downloadUrl = "https://httpbin.org/bytes/4096"

    private lateinit var resultView: TextView
    private val retrofitApi by lazy {
        Net.instance.retrofit
            .baseUrl(apiBaseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create<HttpBinApi>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_scene)
        resultView = findViewById(R.id.tv_result)

        findViewById<Button>(R.id.btn_net_get).setOnClickListener { runNetGet() }
        findViewById<Button>(R.id.btn_net_post).setOnClickListener { runNetPostJson() }
        findViewById<Button>(R.id.btn_flow_get).setOnClickListener { runFlowGet() }
        findViewById<Button>(R.id.btn_flow_post).setOnClickListener { runFlowPostJson() }
        findViewById<Button>(R.id.btn_net_download).setOnClickListener { runNetDownload() }
        findViewById<Button>(R.id.btn_flow_download).setOnClickListener { runFlowDownload() }
        findViewById<Button>(R.id.btn_retrofit_suspend).setOnClickListener { runRetrofitSuspend() }
        findViewById<Button>(R.id.btn_retrofit_flow).setOnClickListener { runRetrofitFlow() }
        findViewById<Button>(R.id.btn_run_all).setOnClickListener { runAllScenes() }

        appendResult("Open a button to run one network scene, or run all scenes.")
    }

    private fun runAllScenes() {
        runNetGet()
        runNetPostJson()
        runFlowGet()
        runFlowPostJson()
        runNetDownload()
        runFlowDownload()
        runRetrofitSuspend()
        runRetrofitFlow()
    }

    private fun runNetGet() {
        appendResult("net GET: start")
        Net.instance.get()
            .url(apiBaseUrl)
            .path("get")
            .addParam("scene", "net-get")
            .noUseGlobalParams()
            .autoCancel(this)
            .send(object : DdCallback {
                override fun onFailure(er: String?) {
                    appendResult("net GET: failed ${er.orEmpty()}")
                }

                override fun onResponse(result: String?, code: Int) {
                    appendResult("net GET: code=$code body=${result.shortBody()}")
                }
            })
    }

    private fun runNetPostJson() {
        appendResult("net POST JSON: start")
        Net.instance.postJson()
            .url(apiBaseUrl)
            .path("post")
            .addParam("scene", "net-post-json")
            .addParam("time", System.currentTimeMillis())
            .noUseGlobalParams()
            .autoCancel(this)
            .send(object : DdCallback {
                override fun onFailure(er: String?) {
                    appendResult("net POST JSON: failed ${er.orEmpty()}")
                }

                override fun onResponse(result: String?, code: Int) {
                    appendResult("net POST JSON: code=$code body=${result.shortBody()}")
                }
            })
    }

    private fun runFlowGet() {
        appendResult("net-flow GET: start")
        lifecycleScope.launch {
            Net.instance.get()
                .url(apiBaseUrl)
                .path("get")
                .addParam("scene", "net-flow-get")
                .noUseGlobalParams()
                .flowString()
                .catch { e -> appendResult("net-flow GET: failed ${e.message.orEmpty()}") }
                .collect { body ->
                    appendResult("net-flow GET: body=${body.shortBody()}")
                }
        }
    }

    private fun runFlowPostJson() {
        appendResult("net-flow POST JSON: start")
        lifecycleScope.launch {
            Net.instance.postJson()
                .url(apiBaseUrl)
                .path("post")
                .addParam("scene", "net-flow-post-json")
                .addParam("time", System.currentTimeMillis())
                .noUseGlobalParams()
                .flowString()
                .catch { e -> appendResult("net-flow POST JSON: failed ${e.message.orEmpty()}") }
                .collect { body ->
                    appendResult("net-flow POST JSON: body=${body.shortBody()}")
                }
        }
    }

    private fun runNetDownload() {
        val file = File(cacheDir, "net-callback-sample.bin")
        appendResult("net download: start ${file.absolutePath}")
        Net.instance.newDownload()
            .url(downloadUrl)
            .savePath(file.absolutePath)
            .overwrite(true)
            .retryCount(1)
            .noUseGlobalParams()
            .listener(object : AbstractProgressCallback() {
                override fun onConnecting(task: Task) {
                    appendResult("net download: connecting")
                }

                override fun onProgress(task: Task, complete: Boolean) {
                    if (complete) {
                        appendResult("net download: complete ${task.path}")
                    } else {
                        appendResult("net download: ${task.downloadSize}/${task.contentLength}")
                    }
                }

                override fun onFail(error: String?, task: Task) {
                    appendResult("net download: failed ${error.orEmpty()}")
                }

                override fun onFinish(task: Task) {
                    appendResult("net download: finish")
                }
            })
            .start()
    }

    private fun runFlowDownload() {
        val file = File(cacheDir, "net-flow-sample.bin")
        appendResult("net-flow download: start ${file.absolutePath}")
        lifecycleScope.launch {
            Net.instance.newDownload()
                .url(downloadUrl)
                .savePath(file.absolutePath)
                .overwrite(true)
                .retryCount(1)
                .noUseGlobalParams()
                .flow()
                .catch { e -> appendResult("net-flow download: failed ${e.message.orEmpty()}") }
                .collect { progress ->
                    when (progress.phase) {
                        DownloadPhase.Connecting -> appendResult("net-flow download: connecting")
                        DownloadPhase.Downloading -> {
                            val task = progress.task
                            appendResult("net-flow download: ${task.downloadSize}/${task.contentLength}")
                        }
                        DownloadPhase.Complete -> appendResult("net-flow download: complete ${progress.task.path}")
                        DownloadPhase.Failed -> appendResult("net-flow download: failed")
                    }
                }
        }
    }

    private fun runRetrofitSuspend() {
        appendResult("net-retrofit suspend: start")
        lifecycleScope.launch {
            try {
                val response = retrofitApi.getResponse("net-retrofit-suspend")
                appendResult("net-retrofit suspend: code=${response.code()} body=${response.body()?.string().shortBody()}")
            } catch (e: Exception) {
                appendResult("net-retrofit suspend: failed ${e.message.orEmpty()}")
            }
        }
    }

    private fun runRetrofitFlow() {
        appendResult("net-retrofit Flow: start")
        lifecycleScope.launch {
            retrofitApi.getFlow("net-retrofit-flow")
                .catch { e -> appendResult("net-retrofit Flow: failed ${e.message.orEmpty()}") }
                .collect { body ->
                    appendResult("net-retrofit Flow: body=${body.shortBody()}")
                }
        }
    }

    private fun appendResult(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            resultView.append("${System.currentTimeMillis()}  $message\n\n")
        }
    }

    private fun String?.shortBody(maxLength: Int = 240): String {
        val value = this.orEmpty().replace("\n", " ")
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }

    override fun onDestroy() {
        super.onDestroy()
        Net.instance.cancelAll()
    }

    interface HttpBinApi {
        @GET("get")
        suspend fun getResponse(@Query("scene") scene: String): Response<ResponseBody>

        @GET("get")
        fun getFlow(@Query("scene") scene: String): Flow<String>
    }

    companion object {
        private const val TAG = "NetworkScene"
    }
}
