package com.itg.net.tools

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object CheckTools {

    @JvmStatic
     fun checkMd5(targetMd5: String?, path: String?): Boolean {
        if (targetMd5.orEmpty().isNotBlank()) {
            val downloadFileMd5 = getMD5Three(path)
            return downloadFileMd5.equals(targetMd5, ignoreCase = true)
        }
        return false
    }

    @JvmStatic
     fun getMD5Three(path: String?): String? {
        if (path.isNullOrBlank()) return null
        try {
            val buffer = ByteArray(8192)
            var length: Int
            val md: MessageDigest = MessageDigest.getInstance("MD5")
            val f = File(path)
            FileInputStream(f).use { fis ->
                while (fis.read(buffer).also { length = it } != -1) {
                    md.update(buffer, 0, length)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (e: Exception) {
            return null
        }
    }
}
