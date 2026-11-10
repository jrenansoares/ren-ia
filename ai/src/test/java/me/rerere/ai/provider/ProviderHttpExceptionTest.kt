package me.rerere.ai.provider

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ProviderHttpExceptionTest {
    private fun response(retryAfter: String) = Response.Builder()
        .request(Request.Builder().url("https://example.test").build())
        .protocol(Protocol.HTTP_1_1).code(429).message("Limited")
        .header("Retry-After", retryAfter).body("private provider body".toResponseBody()).build()

    @Test fun retryAfterIsStructuredAndBodiesAreNotRetained() {
        val error = response("120").providerHttpException()
        assertEquals(429, error.statusCode)
        assertEquals(120_000L, error.retryAfterMs)
        assertFalse(error.message!!.contains("private"))
    }
    @Test fun absurdRetryValuesAreBounded() {
        assertEquals(86_400_000L, response("999999999").providerHttpException().retryAfterMs)
        assertNull(response("invalid").providerHttpException().retryAfterMs)
    }
}
