package com.itg.net.util

import android.util.Log
import com.itg.net.BuildConfig

class PrintLog {

    companion object {

        @JvmField
        val open: Boolean = BuildConfig.DEBUG

        @JvmStatic
        private val TAG: String = "itgNet"

        @JvmStatic
        private val SUB_TAG: String = "itg-normal"

        @JvmStatic
        private val SUB_DOWNLOAD: String = "itg-download"

        @JvmStatic val SUB_CONTENT_START: String = "     [     "
        @JvmStatic val SUB_CONTENT_END: String = "     ]"

        @JvmStatic
        fun logr(message: String) {
            if (!open) return
            Log.i(TAG, "$SUB_TAG :$message")
        }

        @JvmStatic
        fun logd(message: String) {
            if (!open) return
            Log.i(TAG, "$SUB_DOWNLOAD :$message")
        }


        @JvmStatic
        fun logSubd(message: String) {
            if (!open) return
            Log.i(TAG, "$SUB_DOWNLOAD :${SUB_CONTENT_START}$message${SUB_CONTENT_END}")
        }
    }
}