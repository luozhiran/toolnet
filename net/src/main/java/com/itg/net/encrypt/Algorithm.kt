package com.itg.net.encrypt

/**
 * 加密算法枚举
 */
enum class Algorithm(
    val transformation: String
) {
    /** AES/CBC/PKCS7Padding（最常用） */
    AES_CBC_PKCS7("AES/CBC/PKCS7Padding"),

    /** AES/ECB/PKCS7Padding */
    AES_ECB_PKCS7("AES/ECB/PKCS7Padding"),

    /** AES/GCM/NoPadding（推荐，带认证） */
    AES_GCM_NO_PADDING("AES/GCM/NoPadding"),

    /** RSA/ECB/PKCS1Padding（非对称） */
    RSA_ECB_PKCS1("RSA/ECB/PKCS1Padding");
}
