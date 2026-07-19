package com.itg.net.encrypt

/**
 * Supported field encryption algorithms.
 */
enum class Algorithm(
    val transformation: String
) {
    /** AES/CBC/PKCS7Padding. */
    AES_CBC_PKCS7("AES/CBC/PKCS7Padding"),

    /** AES/ECB/PKCS7Padding. */
    AES_ECB_PKCS7("AES/ECB/PKCS7Padding"),

    /** AES/GCM/NoPadding with authentication. */
    AES_GCM_NO_PADDING("AES/GCM/NoPadding");
}
