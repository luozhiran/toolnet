package com.itg.ddlnet

import android.app.Application
import com.itg.net.Net

class App:Application() {

    override fun onCreate() {
        super.onCreate()
        
        Net.instance.ddNetConfig
            .app(this)
            .setGlobalParams("ab","bai")
            .setGlobalParams("43","af")
            .maxDownloadNum(1)
            .useHttpLog(true)
            .url("http://47.76.59.147:8000/")
    }
}