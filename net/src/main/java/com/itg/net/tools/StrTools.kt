package com.itg.net.tools

import okhttp3.Cookie
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


object StrTools {

   private const val EQUAL_SIGN_TAG = "="

    @JvmStatic
    fun getCookieString(cookie: List<Cookie?>?):String?{
        if (cookie.isNullOrEmpty()) return null
        val cookieHeader = StringBuilder()
        cookie.forEachIndexed { index, value ->
            if (index > 0) {
                cookieHeader.append("; ")
            }
            if (value != null) {
                cookieHeader.append(value.name).append(EQUAL_SIGN_TAG).append(value.value)
            }
        }
        return cookieHeader.toString()
    }

    @JvmStatic
    fun getMd5(input: String): String? {
        try {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(input.toByteArray())
            val byteArray = md5.digest()
            val sb = StringBuilder()
            for (b in byteArray) {
                // 一个byte格式化成两位的16进制，不足两位高位补零
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }
}