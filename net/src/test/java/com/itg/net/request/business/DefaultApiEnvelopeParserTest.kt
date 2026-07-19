package com.itg.net.request.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultApiEnvelopeParserTest {

    private val parser = DefaultApiEnvelopeParser()

    @Test
    fun emptyBodyIsBusinessError() {
        val envelope = parser.parse("")

        assertFalse(envelope.success)
        assertTrue(envelope.code.orEmpty().contains("EMPTY_BODY"))
    }

    @Test
    fun invalidJsonIsBusinessError() {
        val envelope = parser.parse("<html>gateway error</html>")

        assertFalse(envelope.success)
        assertTrue(envelope.code.orEmpty().contains("INVALID_JSON"))
    }

    @Test
    fun missingCodeIsBusinessError() {
        val envelope = parser.parse("""{"data":{"name":"Ada"}}""")

        assertFalse(envelope.success)
    }
}
