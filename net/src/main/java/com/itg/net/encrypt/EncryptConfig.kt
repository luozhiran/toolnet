package com.itg.net.encrypt

import javax.crypto.SecretKey

/**
 * 字段加解密配置
 *
 * 通过 DSL 方式配置加密算法、密钥、IV 向量和加密规则。
 * 在 [com.itg.net.config.NetConfig.encrypt] 闭包中使用。
 */
class EncryptConfig {

    // ==================== 算法与密钥 ====================

    /** 加密算法，默认 AES/CBC/PKCS7 */
    var algorithm: Algorithm = Algorithm.AES_CBC_PKCS7

    /** AES 密钥字节数组（优先于 [secretKeyObj]） */
    var secretKey: ByteArray? = null

    /** AES SecretKey 对象 */
    var secretKeyObj: SecretKey? = null

    /** IV 向量（CBC/GCM 模式需要 16 字节） */
    var iv: ByteArray? = null

    // ==================== 开关 ====================

    /** 是否启用请求加密 */
    var requestEncryptEnabled: Boolean = true

    /** 是否启用响应解密 */
    var responseDecryptEnabled: Boolean = true

    /** 是否跳过 GET 请求（GET 通常无 body） */
    var skipGetRequest: Boolean = true

    // ==================== 加密规则 ====================

    /** 加密规则列表 */
    val rules: MutableList<EncryptRule> = mutableListOf()

    // ==================== DSL 方法 ====================

    /**
     * 设置加密算法
     */
    fun algorithm(algorithm: Algorithm): EncryptConfig {
        this.algorithm = algorithm
        return this
    }

    /**
     * 设置密钥（字节数组）
     */
    fun secretKey(key: ByteArray): EncryptConfig {
        this.secretKey = key
        return this
    }

    /**
     * 设置密钥（字符串，内部转为 UTF-8 字节后使用）
     */
    fun secretKey(keyStr: String): EncryptConfig {
        this.secretKey = keyStr.toByteArray(Charsets.UTF_8)
        return this
    }

    /**
     * 设置 SecretKey 对象
     */
    fun secretKeyObj(key: SecretKey): EncryptConfig {
        this.secretKeyObj = key
        return this
    }

    /**
     * 设置 IV 向量
     */
    fun iv(iv: ByteArray): EncryptConfig {
        this.iv = iv
        return this
    }

    /**
     * 设置 IV 向量（字符串形式）
     */
    fun iv(ivStr: String): EncryptConfig {
        this.iv = ivStr.toByteArray(Charsets.UTF_8)
        return this
    }

    /**
     * 启用/禁用请求加密
     */
    fun requestEncrypt(enabled: Boolean): EncryptConfig {
        this.requestEncryptEnabled = enabled
        return this
    }

    /**
     * 启用/禁用响应解密
     */
    fun responseDecrypt(enabled: Boolean): EncryptConfig {
        this.responseDecryptEnabled = enabled
        return this
    }

    /**
     * 是否跳过 GET 请求
     */
    fun skipGetRequest(skip: Boolean): EncryptConfig {
        this.skipGetRequest = skip
        return this
    }

    // ==================== 规则 DSL 方法 ====================

    /**
     * 按字段名精确匹配加密（双向加解密）
     */
    fun encryptField(fieldName: String): EncryptConfig {
        rules.add(EncryptRule.ByFieldName(fieldName).also { it.direction = Direction.BOTH })
        return this
    }

    /**
     * 按字段名精确匹配解密（仅响应解密）
     */
    fun decryptField(fieldName: String): EncryptConfig {
        rules.add(EncryptRule.ByFieldName(fieldName).also { it.direction = Direction.RESPONSE_ONLY })
        return this
    }

    /**
     * 按正则匹配字段（双向加解密）
     */
    fun encryptPattern(pattern: Regex): EncryptConfig {
        rules.add(EncryptRule.ByFieldPattern(pattern).also { it.direction = Direction.BOTH })
        return this
    }

    /**
     * 按请求路径 + 字段列表匹配（双向加解密）
     */
    fun encryptPath(pathPattern: Regex, fieldNames: List<String>): EncryptConfig {
        rules.add(EncryptRule.ByPath(pathPattern, fieldNames).also { it.direction = Direction.BOTH })
        return this
    }

    // ==================== 内部方法 ====================

    /**
     * 获取有效的密钥字节数组
     */
    internal fun getKeyBytes(): ByteArray? {
        return secretKey ?: secretKeyObj?.encoded
    }

    /**
     * 判断是否配置了有效的加密规则
     */
    internal fun hasValidConfig(): Boolean {
        return getKeyBytes() != null && rules.isNotEmpty()
    }
}
