package com.itg.ddlnet

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.itg.ddlnet.networkscene.NetworkSceneLogger
import com.itg.ddlnet.networkscene.NetworkSceneRunner
import com.itg.net.Net

class NetworkSceneActivity : AppCompatActivity() {

    private lateinit var logger: NetworkSceneLogger
    private lateinit var runner: NetworkSceneRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_scene)

        logger = NetworkSceneLogger(this, TAG)
        runner = NetworkSceneRunner(this, lifecycleScope, logger)

        bindSceneButtons()
        runner.appendStartupInfo()
    }

    private fun bindSceneButtons() {
        // Callback variants
        findViewById<Button>(R.id.btn_dd_callback).setOnClickListener { runner.runDdCallbackBasic() }
        findViewById<Button>(R.id.btn_handler_callback).setOnClickListener { runner.runHandlerCallback() }
        findViewById<Button>(R.id.btn_build_call).setOnClickListener { runner.runBuildCall() }
        findViewById<Button>(R.id.btn_okhttp_callback).setOnClickListener { runner.runOkHttpCallback() }
        // Core requests
        findViewById<Button>(R.id.btn_net_get).setOnClickListener { runner.runNetGet() }
        findViewById<Button>(R.id.btn_net_post).setOnClickListener { runner.runNetPostJson() }
        findViewById<Button>(R.id.btn_net_form).setOnClickListener { runner.runNetPostForm() }
        findViewById<Button>(R.id.btn_net_content).setOnClickListener { runner.runNetPostContent() }
        findViewById<Button>(R.id.btn_typed_business).setOnClickListener { runner.runTypedBusinessCallback() }
        // Encrypt
        findViewById<Button>(R.id.btn_encrypt_scene).setOnClickListener { runner.runFieldEncryptScene() }
        findViewById<Button>(R.id.btn_skip_encrypt).setOnClickListener { runner.runSkipEncryptScene() }
        findViewById<Button>(R.id.btn_encrypt_optin).setOnClickListener { runner.runEncryptOptIn() }
        // Advanced & errors
        findViewById<Button>(R.id.btn_global_path).setOnClickListener { runner.runGlobalParamsAndPathScene() }
        findViewById<Button>(R.id.btn_cache_monitor).setOnClickListener { runner.runCacheAndMonitorExtraScene() }
        findViewById<Button>(R.id.btn_http_error).setOnClickListener { runner.runHttpErrorResult() }
        findViewById<Button>(R.id.btn_response_too_large).setOnClickListener { runner.runResponseTooLarge() }
        findViewById<Button>(R.id.btn_interceptor_error).setOnClickListener { runner.runInterceptorError() }
        // File upload
        findViewById<Button>(R.id.btn_post_file).setOnClickListener { runner.runPostFileUpload() }
        findViewById<Button>(R.id.btn_post_resume).setOnClickListener { runner.runPostResumeUpload() }
        // Flow
        findViewById<Button>(R.id.btn_flow_string).setOnClickListener { runner.runFlowString() }
        findViewById<Button>(R.id.btn_flow_get_conv).setOnClickListener { runner.runFlowGetConvenience() }
        findViewById<Button>(R.id.btn_flow_post_conv).setOnClickListener { runner.runFlowPostJsonConvenience() }
        findViewById<Button>(R.id.btn_flow_get).setOnClickListener { runner.runFlowGet() }
        findViewById<Button>(R.id.btn_flow_post).setOnClickListener { runner.runFlowPostJson() }
        findViewById<Button>(R.id.btn_flow_result).setOnClickListener { runner.runFlowResult() }
        findViewById<Button>(R.id.btn_flow_typed).setOnClickListener { runner.runFlowTypedBusinessResult() }
        findViewById<Button>(R.id.btn_flow_response).setOnClickListener { runner.runFlowResponse() }
        findViewById<Button>(R.id.btn_flow_get_resp).setOnClickListener { runner.runFlowGetResponse() }
        findViewById<Button>(R.id.btn_flow_post_resp).setOnClickListener { runner.runFlowPostJsonResponse() }
        // Downloads
        findViewById<Button>(R.id.btn_net_download).setOnClickListener { runner.runNetDownload() }
        findViewById<Button>(R.id.btn_net_dl_bind).setOnClickListener { runner.runNetDownloadBindActivity() }
        findViewById<Button>(R.id.btn_net_dl_md5).setOnClickListener { runner.runNetDownloadMd5() }
        findViewById<Button>(R.id.btn_net_dl_monitor).setOnClickListener { runner.runNetDownloadMonitorExtra() }
        findViewById<Button>(R.id.btn_net_dl_skip_monitor).setOnClickListener { runner.runNetDownloadSkipMonitor() }
        findViewById<Button>(R.id.btn_global_dl_listener).setOnClickListener { runner.runGlobalDownloadListener() }
        findViewById<Button>(R.id.btn_flow_download).setOnClickListener { runner.runFlowDownload() }
        findViewById<Button>(R.id.btn_flow_dl_conv).setOnClickListener { runner.runFlowDownloadConvenience() }
        // Cancel
        findViewById<Button>(R.id.btn_cancel_tag).setOnClickListener { runner.runCancelByTag() }
        findViewById<Button>(R.id.btn_cancel_first).setOnClickListener { runner.runCancelFirstTag() }
        findViewById<Button>(R.id.btn_cancel_all).setOnClickListener { runner.runCancelAll() }
        // Retrofit
        findViewById<Button>(R.id.btn_retrofit_suspend).setOnClickListener { runner.runRetrofitSuspend() }
        findViewById<Button>(R.id.btn_retrofit_response).setOnClickListener { runner.runRetrofitNetResponse() }
        findViewById<Button>(R.id.btn_retrofit_net_result).setOnClickListener { runner.runRetrofitNetResult() }
        findViewById<Button>(R.id.btn_retrofit_flow).setOnClickListener { runner.runRetrofitFlow() }
        // Advanced
        findViewById<Button>(R.id.btn_flush_monitor).setOnClickListener { runner.runFlushMonitor() }
        findViewById<Button>(R.id.btn_custom_interceptor).setOnClickListener { runner.runCustomInterceptor() }
        // Run all
        findViewById<Button>(R.id.btn_run_all).setOnClickListener { runner.runAllScenes() }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!logger.closeIfOpen()) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Net.instance.cancelAll()
    }

    companion object {
        private const val TAG = "NetworkScene"
    }
}
