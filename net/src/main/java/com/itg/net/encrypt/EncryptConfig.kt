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

    /** 加密模式，默认 OPT_OUT（全量加密，向后兼容） */
    var encryptMode: EncryptMode = EncryptMode.OPT_OUT

    /** 是否启用请求加密 */
    var requestEncryptEnabled: Boolean = true

    /** 是否启用响应解密 */
    var responseDecryptEnabled: Boolean = true

    /** 是否跳过 GET 请求（GET 通常无 body） */
    var skipGetRequest: Boolean = true

    /**
     * 单次字段加解密允许处理的最大 body 字节数。
     *
     * 字段级加解密需要把 JSON/Form body 读入内存后解析，超过该阈值会跳过处理，避免大包请求或响应造成
     * 内存峰值和 GC 抖动。设置为 0 或负数会回退到默认安全上限。
     */
    var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
        set(value) {
            field = if (value > 0L) value else DEFAULT_MAX_BODY_BYTES
        }

    // ==================== 加密规则 ====================

    /** 加密规则列表 */
    private val ruleList: MutableList<EncryptRule> = mutableListOf()

    val rules: List<EncryptRule>
        get() = synchronized(ruleList) { ruleList.toList() }

    /** 跳过路径列表（OPT_OUT 模式生效） */
    private val skipPathList: MutableList<Regex> = mutableListOf()

    val skipPaths: List<Regex>
        get() = synchronized(skipPathList) { skipPathList.toList() }

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
     * 设置加密模式
     *
     * @param mode [EncryptMode.OPT_OUT] 全量加密（默认）/ [EncryptMode.OPT_IN] 按需加密
     */
    fun encryptMode(mode: EncryptMode): EncryptConfig {
        this.encryptMode = mode
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

    /**
     * 设置字段加解密最大 body 字节数。默认 64KB，传入 0 或负数会回退到默认安全上限。
     */
    fun maxBodyBytes(maxBytes: Long): EncryptConfig {
        this.maxBodyBytes = maxBytes
        return this
    }

    // ==================== 规则 DSL 方法 ====================

    /**
     * 按字段名精确匹配加密（双向加解密）
     */
    fun encryptField(fieldName: String): EncryptConfig {
        synchronized(ruleList) {
            ruleList.add(EncryptRule.ByFieldName(fieldName).also { it.direction = Direction.BOTH })
        }
        return this
    }

    /**
     * 按字段名精确匹配解密（仅响应解密）
     */
    fun decryptField(fieldName: String): EncryptConfig {
        synchronized(ruleList) {
            ruleList.add(EncryptRule.ByFieldName(fieldName).also { it.direction = Direction.RESPONSE_ONLY })
        }
        return this
    }

    /**
     * 按正则匹配字段（双向加解密）
     */
    fun encryptPattern(pattern: Regex): EncryptConfig {
        synchronized(ruleList) {
            ruleList.add(EncryptRule.ByFieldPattern(pattern).also { it.direction = Direction.BOTH })
        }
        return this
    }

    /**
     * 按请求路径 + 字段列表匹配（双向加解密）
     */
    fun encryptPath(pathPattern: Regex, fieldNames: List<String>): EncryptConfig {
        synchronized(ruleList) {
            ruleList.add(EncryptRule.ByPath(pathPattern, fieldNames).also { it.direction = Direction.BOTH })
        }
        return this
    }

    /**
     * 添加跳过路径（OPT_OUT 模式生效，OPT_IN 模式忽略）
     *
     * 匹配该正则的请求路径将跳过加解密处理。
     * 单请求可通过 [com.itg.net.request.base.ParamsBuilder.encrypt] 覆盖此跳过。
     */
    fun skipPath(pattern: Regex): EncryptConfig {
        synchronized(skipPathList) {
            skipPathList.add(pattern)
        }
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

    /**
     * 收集所有可通过简单子串匹配的字段名（ByFieldName 和 ByPath 的字段）
     *
     * 用于加密拦截器中快速预检：在完整 JSON 解析前先检查 body 字符串中
     * 是否包含这些字段名。正则需要完整解析，保守返回空集（走完整解析路径）。
     */
    internal fun collectSimpleFieldNames(): Set<String> {
        val names = mutableSetOf<String>()
        for (rule in rules) {
            when (rule) {
                is EncryptRule.ByFieldName -> names.add(rule.fieldName)
                is EncryptRule.ByPath -> names.addAll(rule.fieldNames)
                is EncryptRule.ByFieldPattern -> { /* 正则无法预检，跳过 */ }
            }
        }
        return names
    }

    /**
     * 判断请求路径是否匹配任一 [EncryptRule.ByPath] 规则
     *
     * OPT_IN 模式下用于判断是否需要对当前请求进行加密。
     */
    internal fun matchesAnyEncryptPath(requestPath: String): Boolean {
        for (rule in rules) {
            when (rule) {
                is EncryptRule.ByFieldName,
                is EncryptRule.ByFieldPattern -> return true
                is EncryptRule.ByPath -> if (rule.pathPattern.matches(requestPath)) return true
            }
        }
        return false
    }

    /**
     * 判断请求路径是否匹配任一 [skipPaths] 规则
     *
     * OPT_OUT 模式下用于判断是否跳过当前请求。
     */
    internal fun matchesAnySkipPath(requestPath: String): Boolean {
        for (pattern in skipPaths) {
            if (pattern.matches(requestPath)) return true
        }
        return false
    }

    private companion object {
        private const val DEFAULT_MAX_BODY_BYTES = 64L * 1024L
    }
}
