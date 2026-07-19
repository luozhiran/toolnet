package com.itg.net.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlToolsTest {

    @Test
    fun cutOffStrToMapPreservesReservedDelimitersInValues() {
        val params = StringBuilder()

        UrlTools.appendUrlParamsToStr(params, "q", "price$50#tag")

        val parsed = UrlTools.cutOffStrToMap(params.toString())

        assertEquals("price$50#tag", parsed?.get("q"))
    }

    @Test
    fun mapKeyValueToStrAppendPreservesReservedDelimiters() {
        val encoded = UrlTools.mapKeyValueToStrAppend(
            mapOf("token" to "a\$b#c"),
            null
        )

        val parsed = UrlTools.cutOffStrToMap(encoded)

        assertEquals("a\$b#c", parsed?.get("token"))
    }
}
