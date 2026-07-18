package com.itg.net.request.business

data class ApiEnvelope(
    val code: String?,
    val message: String?,
    val dataRaw: String?,
    val rawBody: String?,
    val success: Boolean
)
