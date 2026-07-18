package com.itg.net.request.post.form

import com.itg.net.Net
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PostFormBuilderTest {

    @After
    fun tearDown() {
        Net.configure {
            clearGlobalParameters()
        }
    }

    @Test
    fun formBodyIncludesGlobalParamsWhenLocalParamsAreEmpty() {
        Net.configure {
            clearGlobalParameters()
            globalParam("token", "global-token")
        }

        val body = PostForm().getRequestBody()

        assertNotNull(body)
        assertEquals(1, body!!.size)
        assertEquals("token", body.name(0))
        assertEquals("global-token", body.value(0))
    }

    @Test
    fun localFormParamOverridesGlobalParamWithSameKey() {
        Net.configure {
            clearGlobalParameters()
            globalParam("token", "global-token")
        }

        val body = PostForm()
            .addParam("token", "local-token")
            .getRequestBody()

        assertNotNull(body)
        assertEquals(1, body!!.size)
        assertEquals("token", body.name(0))
        assertEquals("local-token", body.value(0))
    }
}
