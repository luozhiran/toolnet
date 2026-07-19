package com.itg.net.request.post.form

import com.itg.net.Net
import com.itg.net.request.post.content.PostContent
import com.itg.net.request.post.multipart.PostMul
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
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

    @Test
    fun emptyPostFormBuildsPostRequest() {
        val request = PostForm()
            .url("https://example.com/form")
            .buildCall()
            ?.request()

        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertNotNull(request.body)
    }

    @Test
    fun emptyPostContentBuildsPostRequest() {
        val request = PostContent()
            .url("https://example.com/content")
            .buildCall()
            ?.request()

        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertNotNull(request.body)
        assertEquals(0L, request.body!!.contentLength())
    }

    @Test
    fun multipartDoesNotAppendGlobalParamsToUrl() {
        Net.configure {
            clearGlobalParameters()
            globalParam("token", "global-token")
        }

        val request = PostMul()
            .url("https://example.com/upload")
            .addParam("name", "file")
            .buildCall()
            ?.request()

        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertFalse(request.url.queryParameterNames.contains("token"))
    }
}
