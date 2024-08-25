package com.itg.net.tools

import org.json.JSONException
import org.json.JSONObject


object JsonTools {
    /**
     * 格式化json字符串
     *
     * @param jsonStr 需要格式化的json串
     * @return 格式化后的json串
     */
    fun formatJson(jsonStr: String?): String {
        if (null == jsonStr || "" == jsonStr) return ""
        val sb = StringBuilder()
        var last = '\u0000'
        var current = '\u0000'
        var indent = 0
        for (i in 0 until jsonStr.length) {
            last = current
            current = jsonStr[i]
            when (current) {
                '{', '[' -> {
                    sb.append(current)
                    sb.append('\n')
                    indent++
                    addIndentBlank(sb, indent)
                }

                '}', ']' -> {
                    sb.append('\n')
                    indent--
                    addIndentBlank(sb, indent)
                    sb.append(current)
                }

                ',' -> {
                    sb.append(current)
                    if (last != '\\') {
                        sb.append('\n')
                        addIndentBlank(sb, indent)
                    }
                }

                else -> sb.append(current)
            }
        }
        return sb.toString()
    }

    /**
     * 添加space
     *
     * @param sb
     * @param indent
     */
    private fun addIndentBlank(sb: StringBuilder, indent: Int) {
        for (i in 0 until indent) {
            sb.append('\t')
        }
    }

    /**
     * http 请求数据返回 json 中中文字符为 unicode 编码转汉字转码
     *
     * @param theString
     * @return 转化后的结果.
     */
    fun decodeUnicode(theString: String): String {
        var aChar: Char
        val len = theString.length
        val outBuffer = StringBuffer(len)
        var x = 0
        while (x < len) {
            aChar = theString[x++]
            if (aChar == '\\') {
                aChar = theString[x++]
                if (aChar == 'u') {
                    var value = 0
                    for (i in 0..3) {
                        aChar = theString[x++]
                        value = when (aChar) {
                            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> (value shl 4) + aChar.code - '0'.code
                            'a', 'b', 'c', 'd', 'e', 'f' -> (value shl 4) + 10 + aChar.code - 'a'.code
                            'A', 'B', 'C', 'D', 'E', 'F' -> (value shl 4) + 10 + aChar.code - 'A'.code
                            else -> throw IllegalArgumentException("Malformed   \\uxxxx   encoding.")
                        }
                    }
                    outBuffer.append(value.toChar())
                } else {
                    if (aChar == 't') {
                        aChar = '\t'
                    } else if (aChar == 'r') {
                        aChar = '\r'
                    } else if (aChar == 'n') {
                        aChar = '\n'
                    } else if (aChar == 'f') {
                        aChar = '\u000C'
                    }
                    outBuffer.append(aChar)
                }
            } else {
                outBuffer.append(aChar)
            }
        }
        return outBuffer.toString()
    }

    @Throws(JSONException::class)
    fun deepMerge(source: JSONObject, target: JSONObject): JSONObject {
        val iterator = source.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (!target.has(key)) {
                target.put(key, source.opt(key))
            } else {
                val value = source.opt(key)
                if (value is JSONObject) {
                    deepMerge(value, target.getJSONObject(key))
                } else {
                    target.put(key, value)
                }
            }
        }
        return target
    }
}