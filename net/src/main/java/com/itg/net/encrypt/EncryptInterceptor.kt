package com.itg.net.encrypt

import com.itg.net.util.PrintLog
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 字段级加解密拦截器
 *
 * 在 OkHttp 拦截器层对 JSON/Form 请求体中的指定字段进行加密，
 * 对响应体中的指定字段进行解密。一次配置，所有模块（net / net-flow / net-retrofit）生效。
 *
 * ## 优先级链（由高到低）
 * 1. 请求级 .encrypt() / .skipEncrypt() —— 最高优先级
 * 2. GET 请求跳过 / 非文本 body 跳过
 * 3. 全局 EncryptMode（OPT_IN / OPT_OUT）
 * 4. 字段规则匹配
 */
class EncryptInterceptor(
    private val config: EncryptConfig
) : Interceptor {

    companion object {
        private const val TAG = "EncryptInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (config.requestEncryptEnabled && config.hasValidConfig()) {
            if (shouldProcessRequest(request)) {
                request = encryptRequest(request)
            }
        }

        val response = chain.proceed(request)

        if (config.responseDecryptEnabled && config.hasValidConfig()) {
            if (shouldProcessResponse(response)) {
                return decryptResponse(response)
            }
        }

        return response
    }

    // ==================== 优先级判断 ====================

    /**
     * 判断是否需要对请求执行加密
     *
     * 优先级链：
     * 1. 请求级强制加密 → true
     * 2. 请求级强制跳过 → false
     * 3. GET 跳过 → false
     * 4. 非文本 body 跳过 → false
     * 5. 全局模式判断
     */
    private fun shouldProcessRequest(request: Request): Boolean {
        // 1. 最高优先级：请求级强制加密（无视全局跳过）
        val marker = request.tag(EncryptMarker::class.java)
        if (marker?.value == EncryptMarker.ENCRYPT) return true

        // 2. 最高优先级：请求级强制跳过（无视全局加密）
        if (marker?.value == EncryptMarker.SKIP) return false

        // 3. GET 跳过
        if (config.skipGetRequest && request.method.equals("GET", ignoreCase = true)) return false

        // 4. 非文本 body 跳过
        val contentType = request.body?.contentType() ?: return false
        if (!isTextBody(contentType)) return false

        // 5. 全局模式判断
        return when (config.encryptMode) {
            EncryptMode.OPT_IN -> config.matchesAnyEncryptPath(request.url.encodedPath)
            EncryptMode.OPT_OUT -> !config.matchesAnySkipPath(request.url.encodedPath)
        }
    }

    /**
     * 判断是否需要对响应执行解密，逻辑与请求对称
     */
    private fun shouldProcessResponse(response: Response): Boolean {
        val request = response.request

        // 1. 请求级强制加密
        val marker = request.tag(EncryptMarker::class.java)
        if (marker?.value == EncryptMarker.ENCRYPT) return true

        // 2. 请求级强制跳过
        if (marker?.value == EncryptMarker.SKIP) return false

        // 3. 非文本 body 跳过
        val contentType = response.body?.contentType() ?: return false
        if (!isTextBody(contentType)) return false

        // 4. 全局模式判断
        return when (config.encryptMode) {
            EncryptMode.OPT_IN -> config.matchesAnyEncryptPath(request.url.encodedPath)
            EncryptMode.OPT_OUT -> !config.matchesAnySkipPath(request.url.encodedPath)
        }
    }

    private fun isTextBody(contentType: MediaType): Boolean {
        return contentType.subtype.contains("json", ignoreCase = true) ||
                contentType.subtype.contains("x-www-form-urlencoded", ignoreCase = true)
    }

    // ==================== 请求加密 ====================

    private fun encryptRequest(request: Request): Request {
        val body = request.body ?: return request
        val contentType = body.contentType() ?: return request
        if (exceedsBodyLimit(body)) return request

        val bodyString = body.readString()
        if (bodyString.isBlank()) return request
        if (exceedsBodyLimit(bodyString)) return request

        val encryptedBody = when {
            contentType.subtype.contains("json", ignoreCase = true) ->
                encryptJson(bodyString, request.url.encodedPath)
            contentType.subtype.contains("x-www-form-urlencoded", ignoreCase = true) ->
                encryptForm(bodyString, request.url.encodedPath)
            else -> null
        }

        if (encryptedBody == null) return request

        PrintLog.logr("$TAG: request fields encrypted for ${request.url.encodedPath}")
        return request.newBuilder()
            .method(request.method, encryptedBody.toRequestBody(contentType))
            .build()
    }

    private fun encryptJson(bodyString: String, requestPath: String): String? {
        if (!hasRuleMatch(bodyString, requestPath, forEncrypt = true)) return null

        return try {
            when {
                bodyString.trimStart().startsWith("[") -> {
                    val array = JSONArray(bodyString)
                    val modified = encryptJsonArray(array, requestPath)
                    if (modified) array.toString() else null
                }
                else -> {
                    val obj = JSONObject(bodyString)
                    val modified = encryptJsonObject(obj, requestPath)
                    if (modified) obj.toString() else null
                }
            }
        } catch (e: Exception) {
            PrintLog.logr("$TAG: encrypt json failed, skip. ${e.message}")
            null
        }
    }

    private fun encryptJsonObject(obj: JSONObject, requestPath: String): Boolean {
        var modified = false
        val key = config.getKeyBytes() ?: return false

        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val fieldName = iterator.next()
            val value = obj.opt(fieldName) ?: continue

            when {
                value is JSONObject -> {
                    if (encryptJsonObject(value, requestPath)) modified = true
                }
                value is JSONArray -> {
                    if (encryptJsonArray(value, requestPath)) modified = true
                }
                shouldEncrypt(fieldName, requestPath) -> {
                    obj.put(fieldName, EncryptUtil.encrypt(
                        value.toString(), key, config.algorithm, config.iv
                    ))
                    modified = true
                }
            }
        }
        return modified
    }

    private fun encryptJsonArray(array: JSONArray, requestPath: String): Boolean {
        var modified = false
        for (i in 0 until array.length()) {
            when (val element = array.opt(i)) {
                is JSONObject -> { if (encryptJsonObject(element, requestPath)) modified = true }
                is JSONArray -> { if (encryptJsonArray(element, requestPath)) modified = true }
            }
        }
        return modified
    }

    private fun encryptForm(bodyString: String, requestPath: String): String? {
        return try {
            val key = config.getKeyBytes() ?: return null
            val sb = StringBuilder()
            var modified = false

            for (pair in bodyString.split("&")) {
                if (sb.isNotEmpty()) sb.append("&")
                val eqIndex = pair.indexOf("=")
                if (eqIndex == -1) { sb.append(pair); continue }

                val encodedName = pair.substring(0, eqIndex)
                val name = encodedName.formDecode()
                val value = pair.substring(eqIndex + 1).formDecode()

                if (shouldEncrypt(name, requestPath)) {
                    val encryptedValue = EncryptUtil.encrypt(value, key, config.algorithm, config.iv)
                    sb.append(encodedName).append("=")
                        .append(encryptedValue.formEncode())
                    modified = true
                } else {
                    sb.append(pair)
                }
            }
            if (modified) sb.toString() else null
        } catch (e: Exception) {
            PrintLog.logr("$TAG: encrypt form failed, skip. ${e.message}")
            null
        }
    }

    // ==================== 响应解密 ====================

    private fun decryptResponse(response: Response): Response {
        val body = response.body ?: return response
        val contentType = body.contentType() ?: return response
        if (exceedsBodyLimit(body)) return response

        val bodyString = body.string()
        if (bodyString.isBlank()) {
            return response.withBody(bodyString, contentType)
        }
        if (exceedsBodyLimit(bodyString)) {
            return response.withBody(bodyString, contentType)
        }

        val decryptedBody = when {
            contentType.subtype.contains("json", ignoreCase = true) ->
                decryptJson(bodyString, response.request.url.encodedPath)
            contentType.subtype.contains("x-www-form-urlencoded", ignoreCase = true) ->
                decryptForm(bodyString, response.request.url.encodedPath)
            else -> null
        }

        if (decryptedBody == null) {
            return response.withBody(bodyString, contentType)
        }

        PrintLog.logr("$TAG: response fields decrypted for ${response.request.url.encodedPath}")
        return response.newBuilder()
            .body(decryptedBody.toResponseBody(contentType))
            .build()
    }

    private fun decryptJson(bodyString: String, requestPath: String): String? {
        if (!hasRuleMatch(bodyString, requestPath, forEncrypt = false)) return null

        return try {
            when {
                bodyString.trimStart().startsWith("[") -> {
                    val array = JSONArray(bodyString)
                    val modified = decryptJsonArray(array, requestPath)
                    if (modified) array.toString() else null
                }
                else -> {
                    val obj = JSONObject(bodyString)
                    val modified = decryptJsonObject(obj, requestPath)
                    if (modified) obj.toString() else null
                }
            }
        } catch (e: Exception) {
            PrintLog.logr("$TAG: decrypt json failed, skip. ${e.message}")
            null
        }
    }

    private fun decryptJsonObject(obj: JSONObject, requestPath: String): Boolean {
        var modified = false
        val key = config.getKeyBytes() ?: return false

        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val fieldName = iterator.next()
            val value = obj.opt(fieldName) ?: continue

            when {
                value is JSONObject -> {
                    if (decryptJsonObject(value, requestPath)) modified = true
                }
                value is JSONArray -> {
                    if (decryptJsonArray(value, requestPath)) modified = true
                }
                shouldDecrypt(fieldName, requestPath) -> {
                    obj.put(fieldName, EncryptUtil.decrypt(
                        value.toString(), key, config.algorithm, config.iv
                    ))
                    modified = true
                }
            }
        }
        return modified
    }

    private fun decryptJsonArray(array: JSONArray, requestPath: String): Boolean {
        var modified = false
        for (i in 0 until array.length()) {
            when (val element = array.opt(i)) {
                is JSONObject -> { if (decryptJsonObject(element, requestPath)) modified = true }
                is JSONArray -> { if (decryptJsonArray(element, requestPath)) modified = true }
            }
        }
        return modified
    }

    private fun decryptForm(bodyString: String, requestPath: String): String? {
        return try {
            val key = config.getKeyBytes() ?: return null
            val sb = StringBuilder()
            var modified = false

            for (pair in bodyString.split("&")) {
                if (sb.isNotEmpty()) sb.append("&")
                val eqIndex = pair.indexOf("=")
                if (eqIndex == -1) { sb.append(pair); continue }

                val encodedName = pair.substring(0, eqIndex)
                val name = encodedName.formDecode()
                val value = pair.substring(eqIndex + 1).formDecode()

                if (shouldDecrypt(name, requestPath)) {
                    val decryptedValue = EncryptUtil.decrypt(value, key, config.algorithm, config.iv)
                    sb.append(encodedName).append("=")
                        .append(decryptedValue.formEncode())
                    modified = true
                } else {
                    sb.append(pair)
                }
            }
            if (modified) sb.toString() else null
        } catch (e: Exception) {
            PrintLog.logr("$TAG: decrypt form failed, skip. ${e.message}")
            null
        }
    }

    // ==================== 字段规则匹配 ====================

    private fun shouldEncrypt(fieldName: String, requestPath: String): Boolean {
        for (rule in config.rules) {
            if (rule.direction == Direction.RESPONSE_ONLY) continue
            if (ruleMatches(rule, fieldName, requestPath)) return true
        }
        return false
    }

    private fun shouldDecrypt(fieldName: String, requestPath: String): Boolean {
        for (rule in config.rules) {
            if (rule.direction == Direction.REQUEST_ONLY) continue
            if (ruleMatches(rule, fieldName, requestPath)) return true
        }
        return false
    }

    private fun ruleMatches(rule: EncryptRule, fieldName: String, requestPath: String): Boolean {
        return when (rule) {
            is EncryptRule.ByFieldName -> fieldName == rule.fieldName
            is EncryptRule.ByFieldPattern -> rule.pattern.matches(fieldName)
            is EncryptRule.ByPath ->
                rule.pathPattern.matches(requestPath) && fieldName in rule.fieldNames
        }
    }

    // ==================== 快速预检 ====================

    /**
     * 快速预检：判断 body 字符串中是否可能包含需要处理的字段
     *
     * 对 ByFieldName 和 ByPath 规则，使用 O(n) 子串匹配，
     * 避免无匹配时昂贵的 JSONObject 解析。正则规则无法预检，保守返回 true。
     */
    private fun hasRuleMatch(bodyString: String, requestPath: String, forEncrypt: Boolean): Boolean {
        val simpleNames = config.collectSimpleFieldNames()
        if (simpleNames.isEmpty()) return true

        for (rule in config.rules) {
            if (forEncrypt && rule.direction == Direction.RESPONSE_ONLY) continue
            if (!forEncrypt && rule.direction == Direction.REQUEST_ONLY) continue

            when (rule) {
                is EncryptRule.ByFieldName -> {
                    if (bodyString.contains("\"${rule.fieldName}\"")) return true
                }
                is EncryptRule.ByPath -> {
                    if (!rule.pathPattern.matches(requestPath)) continue
                    for (field in rule.fieldNames) {
                        if (bodyString.contains("\"$field\"")) return true
                    }
                }
                is EncryptRule.ByFieldPattern -> return true
            }
        }
        return false
    }

    private fun exceedsBodyLimit(body: RequestBody): Boolean {
        val limit = config.maxBodyBytes
        if (limit <= 0L) return false
        val length = try {
            body.contentLength()
        } catch (_: Exception) {
            -1L
        }
        return length > limit
    }

    private fun exceedsBodyLimit(body: ResponseBody): Boolean {
        val limit = config.maxBodyBytes
        if (limit <= 0L) return false
        val length = body.contentLength()
        return length > limit
    }

    private fun exceedsBodyLimit(bodyString: String): Boolean {
        val limit = config.maxBodyBytes
        return limit > 0L && bodyString.toByteArray(StandardCharsets.UTF_8).size > limit
    }
}

/**
 * 读取 RequestBody 内容为字符串
 */
private fun Response.withBody(bodyString: String, contentType: MediaType): Response {
    return newBuilder()
        .body(bodyString.toResponseBody(contentType))
        .build()
}

private fun String.formDecode(): String {
    return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}

private fun String.formEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

private fun RequestBody.readString(): String {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readUtf8()
}
