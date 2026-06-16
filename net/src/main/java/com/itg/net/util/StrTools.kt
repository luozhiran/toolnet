package com.itg.net.util

import okhttp3.Cookie
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


object StrTools {

    private const val EQUAL_SIGN_TAG = "="
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    @JvmStatic
    fun getCookieString(cookie: List<Cookie?>?): String? {
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
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(input.toByteArray())
            val byteArray = md5.digest()
            val hex = CharArray(byteArray.size * 2)
            byteArray.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xFF
                hex[index * 2] = HEX_CHARS[value ushr 4]
                hex[index * 2 + 1] = HEX_CHARS[value and 0x0F]
            }
            String(hex)
        } catch (e: Exception) {
            null
        }
    }

    fun extractUrlFileName(url: String?, defaultName: String?): String? {
        if (url == null || url.trim { it <= ' ' }.isEmpty()) return defaultName
        try {
            // 去掉 ? 后面的参数
            val path = url.substringBefore('?')
            // 取最后一个 / 之后的内容
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash == -1 || lastSlash == path.length - 1) {
                return defaultName
            }
            val encodedName = path.substring(lastSlash + 1)
            // URL 解码
            return URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
        } catch (e: java.lang.Exception) {
            return defaultName
        }
    }
}
