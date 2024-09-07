package com.itg.net.tools

import android.content.Context
import okhttp3.Cache
import java.io.File

object CacheFactory {

    fun getCache(context:Context): Cache {
        return Cache(File(context.cacheDir,"okhttp/cache"), 1024 * 1024 * 10)
    }
    fun getCache(context:Context,maxSize: Long): Cache {
        return Cache(File(context.cacheDir,"okhttp/cache"), maxSize)
    }
}