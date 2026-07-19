package com.itg.net.request.get

import com.itg.net.Net
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test

class GetBuilderTest {

    @After
    fun tearDown() {
        Net.configure {
            baseUrl(null)
        }
    }

    @Test
    fun pathWithoutBaseUrlDoesNotBuildInvalidNullUrl() {
        Net.configure {
            baseUrl(null)
        }

        val call = Net.get()
            .path("user/profile")
            .buildCall()

        assertNull(call)
    }
}
