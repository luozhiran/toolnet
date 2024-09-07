package com.itg.net

import okhttp3.Cache
import okhttp3.CacheControl
import java.util.concurrent.TimeUnit


object CacheControlFactory {
    //强制使用网络，不使用缓存
    val FORCE_NETWORK: CacheControl = CacheControl.Builder().noCache().build()

    //强制使用缓存，不使用网络
    val FORCE_CACHE: CacheControl =
        CacheControl.Builder().onlyIfCached().maxStale(Int.MAX_VALUE, TimeUnit.SECONDS).build()

    val CHECK_CACHE: CacheControl = CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()


    fun getCacheControlForSecond(cacheTime: Int = 15): CacheControl {
        return CacheControl.Builder().onlyIfCached().maxStale(cacheTime, TimeUnit.SECONDS).build()
    }

    fun getCacheControlForMILLISECONDS(cacheTime: Int = 1000): CacheControl {
        return CacheControl.Builder().onlyIfCached().maxStale(cacheTime, TimeUnit.MILLISECONDS)
            .build()
    }

    fun getCacheControl(cacheTime: Int = 1, timeUnit: TimeUnit = TimeUnit.SECONDS): CacheControl {
        return CacheControl.Builder().onlyIfCached().maxStale(cacheTime, timeUnit).build()
    }

}