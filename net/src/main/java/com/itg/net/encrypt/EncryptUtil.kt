package com.itg.net.encrypt

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加解密工具类
 *
 * 提供 AES/RSA 加解密、Base64 编解码、JSON 字段级加解密等基础操作。
 * 内部使用 ThreadLocal 缓存 [Cipher] 实例，避免重复 JCA Provider 查找开销。
 */
object EncryptUtil {

    private const val AES_ALGORITHM = "AES"

    /**
     * 线程级 Cipher 缓存
     *
     * 每个线程持有一组 transformation → Cipher 的映射。
     * Cipher 实例本身非线程安全，通过 ThreadLocal 隔离。
     * [Cipher.init] 开销远小于 [Cipher.getInstance]，因此只缓存实例，每次重新 init。
     */
    private val cipherCache = object : ThreadLocal<MutableMap<String, Cipher>>() {
        override fun initialValue(): MutableMap<String, Cipher> = mutableMapOf()
    }

    /**
     * 使用指定算法和密钥加密明文
     *
     * @param plaintext 明文字符串
     * @param key 密钥字节数组
     * @param algorithm 加密算法
     * @param iv IV 向量（CBC/GCM 模式需要）
     * @return Base64 编码的密文
     */
    fun encrypt(plaintext: String, key: ByteArray, algorithm: Algorithm, iv: ByteArray? = null): String {
        val cipher = getCachedCipher(algorithm)
        initCipher(cipher, Cipher.ENCRYPT_MODE, key, algorithm, iv)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 使用指定算法和密钥解密密文
     *
     * @param ciphertext Base64 编码的密文
     * @param key 密钥字节数组
     * @param algorithm 加密算法
     * @param iv IV 向量（CBC/GCM 模式需要）
     * @return 明文字符串
     */
    fun decrypt(ciphertext: String, key: ByteArray, algorithm: Algorithm, iv: ByteArray? = null): String {
        val cipher = getCachedCipher(algorithm)
        initCipher(cipher, Cipher.DECRYPT_MODE, key, algorithm, iv)
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * 生成随机 AES 密钥
     *
     * @param keySize 密钥长度（128 / 192 / 256 位），默认 256
     */
    fun generateAesKey(keySize: Int = 256): SecretKey {
        val keygen = javax.crypto.KeyGenerator.getInstance(AES_ALGORITHM)
        keygen.init(keySize, SecureRandom())
        return keygen.generateKey()
    }

    /**
     * 从字符串构造 AES 密钥（自动补位或截断到 16/24/32 字节）
     */
    fun generateAesKeyFromString(keyStr: String, keySize: Int = 256): SecretKey {
        val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
        val targetLen = keySize / 8
        val padded = keyBytes.copyOf(targetLen)
        return SecretKeySpec(padded, AES_ALGORITHM)
    }

    /**
     * 生成随机 IV 向量（16 字节）
     */
    fun generateIv(): ByteArray {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        return iv
    }

    // ==================== 内部方法 ====================

    /** 从缓存获取或创建 Cipher 实例 */
    private fun getCachedCipher(algorithm: Algorithm): Cipher {
        val map = cipherCache.get() ?: mutableMapOf<String, Cipher>().also { cipherCache.set(it) }
        return map.getOrPut(algorithm.transformation) {
            Cipher.getInstance(algorithm.transformation)
        }
    }

    /** 初始化 Cipher（轻量操作） */
    private fun initCipher(
        cipher: Cipher,
        mode: Int,
        key: ByteArray,
        algorithm: Algorithm,
        iv: ByteArray?
    ) {
        val secretKey = SecretKeySpec(key, AES_ALGORITHM)
        if (iv != null && algorithm != Algorithm.AES_ECB_PKCS7) {
            cipher.init(mode, secretKey, IvParameterSpec(iv))
        } else {
            cipher.init(mode, secretKey)
        }
    }
}
