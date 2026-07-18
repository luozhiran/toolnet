package com.itg.ddlnet

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkConfigExample.install(this)
    }
}
