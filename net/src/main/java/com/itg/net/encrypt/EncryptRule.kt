package com.itg.net.encrypt

/**
 * 加密规则密封类
 *
 * 支持三种匹配方式：按字段名精确匹配、按正则匹配、按请求路径+字段组合匹配。
 */
sealed class EncryptRule {

    /** 加密方向 */
    var direction: Direction = Direction.BOTH

    /**
     * 按字段名精确匹配
     *
     * @param fieldName JSON 字段名，如 "phone"、"password"
     */
    data class ByFieldName(
        val fieldName: String
    ) : EncryptRule()

    /**
     * 按字段名正则匹配
     *
     * @param pattern 正则表达式，如 Regex(".*[Ss]ecret.*")
     */
    data class ByFieldPattern(
        val pattern: Regex
    ) : EncryptRule()

    /**
     * 按请求路径 + 字段列表组合匹配
     *
     * @param pathPattern 请求路径正则，如 Regex("/user/.*")
     * @param fieldNames 该路径下需要加密的字段列表
     */
    data class ByPath(
        val pathPattern: Regex,
        val fieldNames: List<String>
    ) : EncryptRule()
}

/**
 * 加密方向
 */
enum class Direction {
    /** 仅请求加密 */
    REQUEST_ONLY,

    /** 仅响应解密 */
    RESPONSE_ONLY,

    /** 双向加解密 */
    BOTH
}
