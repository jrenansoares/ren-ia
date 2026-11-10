package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class ClaudeGatewayTest {
    private val body = """{"model":"projeto-free","messages":[{"role":"user","content":"hi"}]}"""

    @Test fun credentialsAreRequiredAndTokenCountsAreExplicitlyEstimated() = runBlocking {
        withTimeout(10_000) {
            coroutineScope {
                val count = AtomicInteger()
                val gateway = ClaudeLocalGateway(this) {
                    count.incrementAndGet()
                    AnthropicBridgeCodec.response(TextGenerationResult("id", "free-test", UIMessage.assistant("ok")))
                }
                try {
                    val base = gateway.start()
                    assertEquals("127.0.0.1", URI(base).host)
                    assertEquals(401, post("$base/v1/messages", "wrong", body).first)
                    assertEquals(0, count.get())
                    val counted = post("$base/v1/messages/count_tokens", gateway.token, body)
                    assertEquals(200, counted.first)
                    assertEquals("true", counted.third["x-projeto-token-count-estimated"])
                    assertEquals(0, count.get())
                    assertEquals(200, post("$base/v1/messages", gateway.token, body).first)
                    assertEquals(1, count.get())
                } finally { gateway.close() }
            }
        }
    }

    @Test fun providerFailuresDoNotExposeSecretsOrCancelTheOwner() = runBlocking {
        withTimeout(10_000) {
            coroutineScope {
                var fail = true
                val gateway = ClaudeLocalGateway(this) {
                    if (fail) error("api-key-super-secret body=private")
                    AnthropicBridgeCodec.response(TextGenerationResult("id", "free-test", UIMessage.assistant("ok")))
                }
                try {
                    val base = gateway.start()
                    val failed = post("$base/v1/messages", gateway.token, body)
                    assertEquals(502, failed.first)
                    assertTrue(failed.second.contains("api_error"))
                    assertFalse(failed.second.contains("super-secret"))
                    assertFalse(failed.second.contains("private"))
                    fail = false
                    assertEquals(200, post("$base/v1/messages", gateway.token, body).first)
                } finally { gateway.close() }
            }
        }
    }

    private suspend fun post(url: String, token: String, body: String): Triple<Int, String, Map<String, String>> = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3000; connection.readTimeout = 3000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("x-api-key", token)
            connection.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val text = (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            Triple(code, text, connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.first() })
        } finally { connection.disconnect() }
    }
}
