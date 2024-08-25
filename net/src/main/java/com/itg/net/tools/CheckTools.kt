package com.itg.net.tools

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object CheckTools {

    @JvmStatic
     fun checkMd5(targetMd5: String?, path: String?): Boolean {
        if (targetMd5.orEmpty().isNotBlank()) {
            val downloadFileMd5 = getMD5Three(path)
            return downloadFileMd5 == targetMd5
        }
        return false
    }

    @JvmStatic
     fun getMD5Three(path: String?): String? {
        var bi: BigInteger? = null
        try {
            val buffer = ByteArray(8192)
            var len = 0
            val md: MessageDigest = MessageDigest.getInstance("MD5")
            val f = File(path)
            val fis = FileInputStream(f)
            while (fis.read(buffer).also { len = it } != -1) {
                md.update(buffer, 0, len)
            }
            fis.close()
            val b: ByteArray = md.digest()
            bi = BigInteger(1, b)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bi?.toString(16)
    }
}