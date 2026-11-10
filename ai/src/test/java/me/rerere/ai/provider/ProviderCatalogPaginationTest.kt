package me.rerere.ai.provider

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ProviderCatalogPaginationTest {
    @Test fun googleFollowsAllPagesBeforeReturningTheCatalog() = runBlocking {
        val urls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            urls += chain.request().url.toString()
            val body = if (urls.size == 1) """{"models":[{"name":"models/first","displayName":"First","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"page2"}"""
                else """{"models":[{"name":"models/second","displayName":"Second","supportedGenerationMethods":["generateContent"]}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val models = GoogleProvider(client).listModels(ProviderSetting.Google(apiKey = "fake"))
        assertEquals(listOf("first", "second"), models.map { it.modelId })
        assertTrue(urls.last().contains("pageToken=page2"))
    }

    @Test fun anthropicFollowsTheLastIdCursor() = runBlocking {
        val cursors = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            cursors += chain.request().url.queryParameter("after_id")
            val body = if (cursors.size == 1) """{"data":[{"id":"first"}],"has_more":true,"last_id":"first"}"""
                else """{"data":[{"id":"second"}],"has_more":false}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val models = ClaudeProvider(client).listModels(ProviderSetting.Claude(apiKey = "fake"))
        assertEquals(listOf("first", "second"), models.map { it.modelId })
        assertEquals(listOf(null, "first"), cursors)
    }

    @Test fun repeatedPagesFailWithoutPublishingAnIncompleteCatalog() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"models":[],"nextPageToken":"same"}""".toResponseBody()).build()
        }.build()
        try { GoogleProvider(client).listModels(ProviderSetting.Google(apiKey = "fake")); fail("Expected incomplete catalog rejection") }
        catch (_: IllegalStateException) { assertEquals(2, calls) }
    }
}
