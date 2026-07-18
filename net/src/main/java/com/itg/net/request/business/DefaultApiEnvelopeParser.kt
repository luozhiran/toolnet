package com.itg.net.request.business

import org.json.JSONObject

class DefaultApiEnvelopeParser(
    private val successCodes: Set<String> = setOf("0", "200", "success", "true"),
    private val codeKeys: List<String> = listOf("code", "status"),
    private val messageKeys: List<String> = listOf("message", "msg", "error"),
    private val dataKeys: List<String> = listOf("data", "result")
) : ApiEnvelopeParser {

    override fun parse(rawBody: String?): ApiEnvelope {
        if (rawBody.isNullOrBlank()) {
            return ApiEnvelope(
                code = null,
                message = null,
                dataRaw = null,
                rawBody = rawBody,
                success = true
            )
        }

        return runCatching {
            val json = JSONObject(rawBody)
            val code = firstValue(json, codeKeys)
            val message = firstValue(json, messageKeys)
            val dataRaw = firstRawValue(json, dataKeys)
            ApiEnvelope(
                code = code,
                message = message,
                dataRaw = dataRaw,
                rawBody = rawBody,
                success = code == null || successCodes.contains(code)
            )
        }.getOrElse {
            ApiEnvelope(
                code = null,
                message = null,
                dataRaw = rawBody,
                rawBody = rawBody,
                success = true
            )
        }
    }

    private fun firstValue(json: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                return json.opt(key)?.toString()
            }
        }
        return null
    }

    private fun firstRawValue(json: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                return json.opt(key)?.toString()
            }
        }
        return null
    }
}
