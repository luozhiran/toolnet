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
        findViewById<Button>(R.id.btn_net_get).setOnClickListener { runner.runNetGet() }
        findViewById<Button>(R.id.btn_net_post).setOnClickListener { runner.runNetPostJson() }
        findViewById<Button>(R.id.btn_flow_get).setOnClickListener { runner.runFlowGet() }
        findViewById<Button>(R.id.btn_flow_post).setOnClickListener { runner.runFlowPostJson() }
        findViewById<Button>(R.id.btn_net_download).setOnClickListener { runner.runNetDownload() }
        findViewById<Button>(R.id.btn_flow_download).setOnClickListener { runner.runFlowDownload() }
        findViewById<Button>(R.id.btn_retrofit_suspend).setOnClickListener { runner.runRetrofitSuspend() }
        findViewById<Button>(R.id.btn_retrofit_flow).setOnClickListener { runner.runRetrofitFlow() }
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
