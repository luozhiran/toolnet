package com.itg.net.request.business

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
            val json = JsonParser.parseString(rawBody).asJsonObject
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

    private fun firstValue(json: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val value = json.valueOrNull(key) ?: continue
            if (value.isJsonPrimitive) {
                return value.asJsonPrimitive.asString
            }
            return value.toString()
        }
        return null
    }

    private fun firstRawValue(json: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val value = json.valueOrNull(key) ?: continue
            return if (value.isJsonPrimitive) value.asJsonPrimitive.asString else value.toString()
        }
        return null
    }

    private fun JsonObject.valueOrNull(key: String): JsonElement? {
        if (!has(key)) return null
        val value = get(key)
        return if (value == null || value.isJsonNull) null else value
    }
}
