package com.itg.net.encrypt

import com.itg.net.util.PrintLog
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 字段级加解密拦截器
 *
 * 在 OkHttp 拦截器层对 JSON/Form 请求体中的指定字段进行加密，
 * 对响应体中的指定字段进行解密。一次配置，所有模块（net / net-flow / net-retrofit）生效。
 *
 * ## 工作原理
 * - 请求拦截：读取 body → 匹配字段 → 加密 → 构造新 body
 * - 响应拦截：读取 body → 匹配字段 → 解密 → 构造新 body
 * - 对非 JSON/Form body（如文件上传）自动跳过
 */
class EncryptInterceptor(
    private val config: EncryptConfig
) : Interceptor {

    companion object {
        private const val TAG = "EncryptInterceptor"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // ========== 请求加密 ==========
        if (config.requestEncryptEnabled && config.hasValidConfig()) {
            request = encryptRequest(request)
        }

        // ========== 执行请求 ==========
        val response = chain.proceed(request)

        // ========== 响应解密 ==========
        if (config.responseDecryptEnabled && config.hasValidConfig()) {
            return decryptResponse(response)
        }

        return response
    }

    // ==================== 请求加密 ====================

    private fun encryptRequest(request: Request): Request {
        // GET 请求通常无 body，按配置跳过
        if (config.skipGetRequest && request.method.equals("GET", ignoreCase = true)) {
            return request
        }

        val body = request.body ?: return request
        val contentType = body.contentType() ?: return request

        val bodyString = body.readString()
        if (bodyString.isBlank()) return request

        val encryptedBody = when {
            contentType.subtype.contains("json", ignoreCase = true) ->
                encryptJson(bodyString, request.url.encodedPath)
            contentType.subtype.contains("x-www-form-urlencoded", ignoreCase = true) ->
                encryptForm(bodyString, request.url.encodedPath)
            else -> {
                PrintLog.logr("$TAG: skip non-text body, contentType=$contentType")
                return request
            }
        }

        if (encryptedBody == null) return request

        PrintLog.logr("$TAG: request fields encrypted for ${request.url.encodedPath}")
        return request.newBuilder()
            .method(request.method, encryptedBody.toRequestBody(contentType))
            .build()
    }

    private fun encryptJson(bodyString: String, requestPath: String): String? {
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
                // 嵌套 JSONObject
                value is JSONObject -> {
                    val childModified = encryptJsonObject(value, requestPath)
                    if (childModified) modified = true
                }
                // 嵌套 JSONArray
                value is JSONArray -> {
                    val arrModified = encryptJsonArray(value, requestPath)
                    if (arrModified) modified = true
                }
                // 基本类型字段
                shouldEncrypt(fieldName, requestPath) -> {
                    val ciphertext = EncryptUtil.encrypt(
                        value.toString(), key, config.algorithm, config.iv
                    )
                    obj.put(fieldName, ciphertext)
                    modified = true
                }
            }
        }
        return modified
    }

    private fun encryptJsonArray(array: JSONArray, requestPath: String): Boolean {
        var modified = false
        for (i in 0 until array.length()) {
            val element = array.opt(i) ?: continue
            when (element) {
                is JSONObject -> {
                    val objModified = encryptJsonObject(element, requestPath)
                    if (objModified) modified = true
                }
                is JSONArray -> {
                    val arrModified = encryptJsonArray(element, requestPath)
                    if (arrModified) modified = true
                }
            }
        }
        return modified
    }

    private fun encryptForm(bodyString: String, requestPath: String): String? {
        return try {
            val key = config.getKeyBytes() ?: return null
            val sb = StringBuilder()
            var modified = false

            val pairs = bodyString.split("&")
            for (pair in pairs) {
                if (sb.isNotEmpty()) sb.append("&")
                val eqIndex = pair.indexOf("=")
                if (eqIndex == -1) {
                    sb.append(pair)
                    continue
                }
                val paramName = pair.substring(0, eqIndex)
                val paramValue = pair.substring(eqIndex + 1)

                if (shouldEncrypt(paramName, requestPath)) {
                    val ciphertext = EncryptUtil.encrypt(
                        paramValue, key, config.algorithm, config.iv
                    )
                    sb.append(paramName).append("=").append(ciphertext)
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

        val bodyString = body.string()
        if (bodyString.isBlank()) return response

        val decryptedBody = when {
            contentType.subtype.contains("json", ignoreCase = true) ->
                decryptJson(bodyString, response.request.url.encodedPath)
            contentType.subtype.contains("x-www-form-urlencoded", ignoreCase = true) ->
                decryptForm(bodyString, response.request.url.encodedPath)
            else -> null
        }

        if (decryptedBody == null) return response

        PrintLog.logr("$TAG: response fields decrypted for ${response.request.url.encodedPath}")
        return response.newBuilder()
            .body(decryptedBody.toResponseBody(contentType))
            .build()
    }

    private fun decryptJson(bodyString: String, requestPath: String): String? {
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
                    val childModified = decryptJsonObject(value, requestPath)
                    if (childModified) modified = true
                }
                value is JSONArray -> {
                    val arrModified = decryptJsonArray(value, requestPath)
                    if (arrModified) modified = true
                }
                shouldDecrypt(fieldName, requestPath) -> {
                    val plaintext = EncryptUtil.decrypt(
                        value.toString(), key, config.algorithm, config.iv
                    )
                    obj.put(fieldName, plaintext)
                    modified = true
                }
            }
        }
        return modified
    }

    private fun decryptJsonArray(array: JSONArray, requestPath: String): Boolean {
        var modified = false
        for (i in 0 until array.length()) {
            val element = array.opt(i) ?: continue
            when (element) {
                is JSONObject -> {
                    val objModified = decryptJsonObject(element, requestPath)
                    if (objModified) modified = true
                }
                is JSONArray -> {
                    val arrModified = decryptJsonArray(element, requestPath)
                    if (arrModified) modified = true
                }
            }
        }
        return modified
    }

    private fun decryptForm(bodyString: String, requestPath: String): String? {
        return try {
            val key = config.getKeyBytes() ?: return null
            val sb = StringBuilder()
            var modified = false

            val pairs = bodyString.split("&")
            for (pair in pairs) {
                if (sb.isNotEmpty()) sb.append("&")
                val eqIndex = pair.indexOf("=")
                if (eqIndex == -1) {
                    sb.append(pair)
                    continue
                }
                val paramName = pair.substring(0, eqIndex)
                val paramValue = pair.substring(eqIndex + 1)

                if (shouldDecrypt(paramName, requestPath)) {
                    val plaintext = EncryptUtil.decrypt(
                        paramValue, key, config.algorithm, config.iv
                    )
                    sb.append(paramName).append("=").append(plaintext)
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

    // ==================== 规则匹配 ====================

    /**
     * 判断请求路径 + 字段名是否匹配加密规则
     */
    private fun shouldEncrypt(fieldName: String, requestPath: String): Boolean {
        for (rule in config.rules) {
            if (rule.direction == Direction.RESPONSE_ONLY) continue

            val matched = when (rule) {
                is EncryptRule.ByFieldName -> fieldName == rule.fieldName
                is EncryptRule.ByFieldPattern -> rule.pattern.matches(fieldName)
                is EncryptRule.ByPath ->
                    rule.pathPattern.matches(requestPath) && fieldName in rule.fieldNames
            }
            if (matched) return true
        }
        return false
    }

    /**
     * 判断请求路径 + 字段名是否匹配解密规则
     */
    private fun shouldDecrypt(fieldName: String, requestPath: String): Boolean {
        for (rule in config.rules) {
            if (rule.direction == Direction.REQUEST_ONLY) continue

            val matched = when (rule) {
                is EncryptRule.ByFieldName -> fieldName == rule.fieldName
                is EncryptRule.ByFieldPattern -> rule.pattern.matches(fieldName)
                is EncryptRule.ByPath ->
                    rule.pathPattern.matches(requestPath) && fieldName in rule.fieldNames
            }
            if (matched) return true
        }
        return false
    }
}

/**
 * 读取 RequestBody 内容为字符串（不消费原始流）
 */
private fun RequestBody.readString(): String {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readUtf8()
}
