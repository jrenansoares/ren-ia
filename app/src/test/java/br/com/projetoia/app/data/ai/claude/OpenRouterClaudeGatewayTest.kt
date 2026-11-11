package br.com.projetoia.app.data.ai.claude

import br.com.projetoia.app.data.datastore.Settings
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class OpenRouterClaudeGatewayTest {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient()

    @Test
    fun normalizesOpenRouterUrlsAndClassifiesClaudeFamilies() {
        assertEquals("https://openrouter.ai/api", openRouterAnthropicBaseUrl("https://openrouter.ai/api/v1"))
        assertEquals("https://openrouter.ai/api", openRouterAnthropicBaseUrl("https://openrouter.ai/api/v1/"))
        assertEquals("https://openrouter.ai/api", openRouterAnthropicBaseUrl("https://openrouter.ai/api"))
        assertEquals(
            "https://openrouter.ai/api/v1/messages",
            openRouterAnthropicMessagesUrl("https://openrouter.ai/api/v1"),
        )

        assertEquals(ClaudeModelFamily.SONNET, classifyOpenRouterClaudeFamily("anthropic/claude-sonnet-4.6"))
        assertEquals(ClaudeModelFamily.OPUS, classifyOpenRouterClaudeFamily("anthropic/claude-opus-4.6"))
        assertEquals(ClaudeModelFamily.HAIKU, classifyOpenRouterClaudeFamily("anthropic/claude-haiku-4.5"))
        assertEquals(ClaudeModelFamily.FABLE, classifyOpenRouterClaudeFamily("anthropic/claude-fable-5.2"))
        assertNull(classifyOpenRouterClaudeFamily("anthropic/claude-sonnet-4.6:batch"))
        assertNull(classifyOpenRouterClaudeFamily("openai/gpt-4o"))
    }

    @Test
    fun targetResolutionSelectsOpenRouterClaudeAndPreservesGroqAndGeminiFallback() {
        val sonnet = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val groqModel = Model(modelId = "llama-3.3-70b-versatile", displayName = "Llama 3.3")
        val geminiModel = Model(modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash")

        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(sonnet),
        )
        val groq = ProviderSetting.OpenAI(
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "gsk-test-key",
            models = listOf(groqModel),
        )
        val google = ProviderSetting.Google(
            name = "Google",
            apiKey = "AIza-test-key",
            models = listOf(geminiModel),
        )
        val settings = Settings(providers = listOf(openRouter, groq, google))

        val sonnetTarget = resolveClaudeExecutionTarget(settings, sonnet)
        assertTrue(sonnetTarget is ClaudeExecutionTarget.OpenRouterClaude)
        val openRouterTarget = sonnetTarget as ClaudeExecutionTarget.OpenRouterClaude
        assertEquals("anthropic/claude-sonnet-4.6", openRouterTarget.option.model.modelId)
        assertEquals("https://openrouter.ai/api/v1/messages", openRouterTarget.option.messagesUrl)

        assertTrue(resolveClaudeExecutionTarget(settings, groqModel) is ClaudeExecutionTarget.FreeRouterFallback)
        assertTrue(resolveClaudeExecutionTarget(settings, geminiModel) is ClaudeExecutionTarget.FreeRouterFallback)
    }

    @Test
    fun stage1AndStage2GatewayForwardsSonnetStreamingToolsAndTelemetrySafely() = runBlocking {
        var capturedAuth: String? = null
        var capturedTitle: String? = null
        var capturedBeta: String? = null
        var capturedBody: String? = null
        var leakedGatewayCred = false
        var countTokensCalledUpstream = false

        val upstreamServer = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/api/v1/messages") {
                    capturedAuth = call.request.header("Authorization")
                    capturedTitle = call.request.header("X-Title")
                    capturedBeta = call.request.header("anthropic-beta")
                    leakedGatewayCred = call.request.header("x-api-key") != null
                    capturedBody = call.receiveText()

                    call.respondOutputStream(ContentType.Text.EventStream, HttpStatusCode.OK) {
                        val writer = bufferedWriter(StandardCharsets.UTF_8)
                        val events = listOf(
                            """event: message_start""",
                            """data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"anthropic/claude-sonnet-4.6","usage":{"input_tokens":120,"cache_read_input_tokens":30}}}""",
                            "",
                            """event: content_block_start""",
                            """data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"Read","input":{}}}""",
                            "",
                            """event: content_block_delta""",
                            """data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"file_path\":\"README.md\"}"}}""",
                            "",
                            """event: content_block_stop""",
                            """data: {"type":"content_block_stop","index":0}""",
                            "",
                            """event: message_delta""",
                            """data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":45}}""",
                            "",
                            """event: message_stop""",
                            """data: {"type":"message_stop"}""",
                            "",
                        )
                        events.forEach { line ->
                            writer.write(line)
                            writer.write("\n")
                            writer.flush()
                        }
                    }
                }
                post("/api/v1/messages/count_tokens") {
                    countTokensCalledUpstream = true
                    call.respondText("Not Found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
        }.start(wait = false)

        val upstreamPort = upstreamServer.engine.resolvedConnectors().single().port
        val sonnetModel = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val openRouterProvider = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(sonnetModel),
        )
        val option = OpenRouterClaudeOption(
            providerSetting = openRouterProvider,
            model = sonnetModel,
            family = ClaudeModelFamily.SONNET,
            anthropicBaseUrl = "http://127.0.0.1:$upstreamPort/api",
            messagesUrl = "http://127.0.0.1:$upstreamPort/api/v1/messages",
            apiKey = "sk-or-test-key",
        )

        var telemetryModel: String? = null
        var telemetryUsage: TokenUsage? = null
        val forwarder = AnthropicTransparentForwarder(
            client = httpClient,
            option = option,
            familyOverrides = mapOf(ClaudeModelFamily.SONNET to sonnetModel.modelId),
            onResponseTelemetry = { model, usage ->
                telemetryModel = model
                telemetryUsage = usage
            },
        )
        val gateway = ClaudeLocalGateway(
            owner = this,
            availableModels = listOf(sonnetModel.modelId),
            forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
        ) {
            error("Must not invoke bridge fallback")
        }

        try {
            val gatewayUrl = gateway.start()

            // Verify unauthenticated request is rejected
            val unauthorizedReq = Request.Builder()
                .url("$gatewayUrl/v1/messages")
                .header("x-api-key", "wrong-token")
                .post("{\"model\":\"projeto-free\",\"messages\":[]}".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(unauthorizedReq).execute().use { resp ->
                assertEquals(401, resp.code)
            }

            // Verify count_tokens stays local (never hits OpenRouter 404)
            val countReq = Request.Builder()
                .url("$gatewayUrl/v1/messages/count_tokens")
                .header("x-api-key", gateway.token)
                .post("{\"model\":\"anthropic/claude-sonnet-4.6\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(countReq).execute().use { resp ->
                assertEquals(200, resp.code)
                assertEquals("true", resp.header("x-projeto-token-count-estimated"))
            }
            assertFalse(countTokensCalledUpstream)

            // Verify SSE streaming + tools + thinking + cache_control transparency
            val payload = """
                {
                  "model": "projeto-free",
                  "max_tokens": 2048,
                  "stream": true,
                  "thinking": {"type": "enabled", "budget_tokens": 1024},
                  "system": [{"type": "text", "text": "Be precise", "cache_control": {"type": "ephemeral"}}],
                  "tools": [{"name": "Read", "description": "Read a file", "input_schema": {"type": "object"}}],
                  "tool_choice": {"type": "auto"},
                  "messages": [
                    {"role": "user", "content": "Inspect README.md"},
                    {"role": "assistant", "content": [{"type": "tool_use", "id": "prev_1", "name": "Glob", "input": {"pattern": "*"}}]},
                    {"role": "user", "content": [{"type": "tool_result", "tool_use_id": "prev_1", "content": "README.md"}]}
                  ]
                }
            """.trimIndent()
            val messagesReq = Request.Builder()
                .url("$gatewayUrl/v1/messages")
                .header("x-api-key", gateway.token)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "prompt-caching-2024-07-31")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            val responseText = httpClient.newCall(messagesReq).execute().use { resp ->
                assertEquals(200, resp.code)
                assertTrue(resp.header("Content-Type").orEmpty().contains("text/event-stream"))
                resp.body.string()
            }

            assertFalse(leakedGatewayCred)
            assertEquals("Bearer sk-or-test-key", capturedAuth)
            assertEquals("Projeto IA", capturedTitle)
            assertEquals("prompt-caching-2024-07-31", capturedBeta)

            val forwardedJson = Json.parseToJsonElement(requireNotNull(capturedBody)).jsonObject
            assertEquals("anthropic/claude-sonnet-4.6", forwardedJson["model"]?.jsonPrimitive?.content)
            assertNotNull(forwardedJson["thinking"])
            assertNotNull(forwardedJson["tools"])
            assertNotNull(forwardedJson["tool_choice"])
            assertEquals(3, forwardedJson["messages"]?.jsonArray?.size)

            assertTrue(responseText.contains("content_block_delta"))
            assertTrue(responseText.contains("tool_use"))
            assertTrue(responseText.contains("README.md"))

            assertEquals("anthropic/claude-sonnet-4.6", telemetryModel)
            assertEquals(120, telemetryUsage?.promptTokens)
            assertEquals(45, telemetryUsage?.completionTokens)
            assertEquals(30, telemetryUsage?.cachedTokens)
        } finally {
            gateway.close()
            upstreamServer.stop(0, 500)
        }
    }

    @Test
    fun stage3EnablesAllOpenRouterClaudeFamiliesAndPromotesClaudeForMultiAgent() {
        val sonnet = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val opus = Model(modelId = "anthropic/claude-opus-4.6", displayName = "Claude Opus 4.6")
        val haiku = Model(modelId = "anthropic/claude-haiku-4.5", displayName = "Claude Haiku 4.5")
        val fable = Model(modelId = "anthropic/claude-fable-5.2", displayName = "Claude Fable 5.2")
        val batch = Model(modelId = "anthropic/claude-sonnet-4.6:batch", displayName = "Batch")
        val gemini = Model(modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash")

        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(sonnet, opus, haiku, fable, batch),
        )
        val google = ProviderSetting.Google(
            name = "Google",
            apiKey = "AIza-test-key",
            models = listOf(gemini),
        )
        val settings = Settings(providers = listOf(openRouter, google))

        val options = openRouterClaudeOptions(settings)
        assertEquals(
            listOf(
                "anthropic/claude-sonnet-4.6",
                "anthropic/claude-opus-4.6",
                "anthropic/claude-haiku-4.5",
                "anthropic/claude-fable-5.2",
            ),
            options.map { it.model.modelId },
        )

        val opusTarget = resolveClaudeExecutionTarget(settings, opus) as ClaudeExecutionTarget.OpenRouterClaude
        assertEquals("anthropic/claude-opus-4.6", opusTarget.option.model.modelId)
        assertEquals("anthropic/claude-sonnet-4.6", opusTarget.familyOverrides[ClaudeModelFamily.SONNET])
        assertEquals("anthropic/claude-haiku-4.5", opusTarget.familyOverrides[ClaudeModelFamily.HAIKU])

        val haikuTarget = resolveClaudeExecutionTarget(settings, haiku) as ClaudeExecutionTarget.OpenRouterClaude
        assertEquals("anthropic/claude-haiku-4.5", haikuTarget.option.model.modelId)

        val fableTarget = resolveClaudeExecutionTarget(settings, fable) as ClaudeExecutionTarget.OpenRouterClaude
        assertEquals("anthropic/claude-fable-5.2", fableTarget.option.model.modelId)

        // Direct non-consultant Gemini selection stays on FreeRouterFallback; multi-agent mode reserves Claude for execution.
        assertTrue(
            resolveClaudeExecutionTarget(
                settings,
                gemini,
                preferOpenRouterClaudeWhenConsultantsActive = false,
            ) is ClaudeExecutionTarget.FreeRouterFallback,
        )
        val promotedTarget = resolveClaudeExecutionTarget(
            settings,
            gemini,
            preferOpenRouterClaudeWhenConsultantsActive = true,
        )
        assertTrue(promotedTarget is ClaudeExecutionTarget.OpenRouterClaude)
        assertEquals(
            "anthropic/claude-sonnet-4.6",
            (promotedTarget as ClaudeExecutionTarget.OpenRouterClaude).option.model.modelId,
        )
    }

    @Test
    fun errorResponseRedactsApiKeyOnRateLimit() = runBlocking {
        val secretKey = "sk-or-super-secret-value"
        val upstreamServer = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/api/v1/messages") {
                    call.respondText(
                        """{"error":{"message":"Rate limit exceeded for key $secretKey"}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.TooManyRequests,
                    )
                }
            }
        }.start(wait = false)

        val port = upstreamServer.engine.resolvedConnectors().single().port
        val model = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val option = OpenRouterClaudeOption(
            providerSetting = ProviderSetting.OpenAI(
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = secretKey,
                models = listOf(model),
            ),
            model = model,
            family = ClaudeModelFamily.SONNET,
            anthropicBaseUrl = "http://127.0.0.1:$port/api",
            messagesUrl = "http://127.0.0.1:$port/api/v1/messages",
            apiKey = secretKey,
        )
        val forwarder = AnthropicTransparentForwarder(httpClient, option)
        val gateway = ClaudeLocalGateway(
            owner = this,
            availableModels = listOf(model.modelId),
            forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
        ) { error("unused") }

        try {
            val url = gateway.start()
            val req = Request.Builder()
                .url("$url/v1/messages")
                .header("x-api-key", gateway.token)
                .post("{\"model\":\"anthropic/claude-sonnet-4.6\",\"messages\":[]}".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                assertEquals(429, resp.code)
                val body = resp.body.string()
                assertFalse(body.contains(secretKey))
                assertTrue(body.contains("[REDACTED]"))
                val json = Json.parseToJsonElement(body).jsonObject
                assertEquals("error", json["type"]?.jsonPrimitive?.contentOrNull)
                val err = json["error"]?.jsonObject
                assertEquals("rate_limit_error", err?.get("type")?.jsonPrimitive?.contentOrNull)
                assertTrue(err?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty().contains("OpenRouter"))
            }
        } finally {
            gateway.close()
            upstreamServer.stop(0, 500)
        }
    }

    @Test
    fun errorNormalizationMapsCreditAndAuthErrorsAndRedactsTokenPatterns() = runBlocking {
        val secretKey = "sk-or-v1-secret-token-value-1234"
        var nextStatus = HttpStatusCode.PaymentRequired
        var nextBody = """{"error":{"message":"Insufficient credits for Bearer sk-or-v1-anothersecretkey","code":402}}"""
        val upstreamServer = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/api/v1/messages") {
                    call.respondText(nextBody, ContentType.Application.Json, nextStatus)
                }
            }
        }.start(wait = false)

        val port = upstreamServer.engine.resolvedConnectors().single().port
        val model = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val option = OpenRouterClaudeOption(
            providerSetting = ProviderSetting.OpenAI(
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = secretKey,
                models = listOf(model),
            ),
            model = model,
            family = ClaudeModelFamily.SONNET,
            anthropicBaseUrl = "http://127.0.0.1:$port/api",
            messagesUrl = "http://127.0.0.1:$port/api/v1/messages",
            apiKey = secretKey,
        )
        val forwarder = AnthropicTransparentForwarder(httpClient, option)
        val gateway = ClaudeLocalGateway(
            owner = this,
            availableModels = listOf(model.modelId),
            forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
        ) { error("unused") }

        try {
            val url = gateway.start()
            fun send(): Pair<Int, String> {
                val req = Request.Builder()
                    .url("$url/v1/messages")
                    .header("x-api-key", gateway.token)
                    .post("{\"model\":\"anthropic/claude-sonnet-4.6\",\"messages\":[]}".toRequestBody(jsonMediaType))
                    .build()
                return httpClient.newCall(req).execute().use { it.code to it.body.string() }
            }

            val (code402, body402) = send()
            assertEquals(402, code402)
            assertFalse(body402.contains("anothersecretkey"))
            val json402 = Json.parseToJsonElement(body402).jsonObject
            assertEquals("error", json402["type"]?.jsonPrimitive?.contentOrNull)
            assertEquals("permission_error", json402["error"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
            assertTrue(json402["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty().contains("Insufficient credits"))

            nextStatus = HttpStatusCode.Unauthorized
            nextBody = """{"error":{"message":"User not found"}}"""
            val (code401, body401) = send()
            assertEquals(401, code401)
            val json401 = Json.parseToJsonElement(body401).jsonObject
            assertEquals("authentication_error", json401["error"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
            assertTrue(json401["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty().contains("OpenRouter API key"))
        } finally {
            gateway.close()
            upstreamServer.stop(0, 500)
        }
    }

    @Test
    fun promotedClaudeModelIdForTogglePromotesNonClaudeAndPreservesClaudeSelection() {
        val opus = Model(modelId = "anthropic/claude-opus-4.6", displayName = "Claude Opus 4.6")
        val sonnet = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val freeRouter = Model(modelId = "openrouter/free", displayName = "OpenRouter Free")
        val gemini = Model(modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash")

        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(opus, sonnet, freeRouter),
        )
        val google = ProviderSetting.Google(
            name = "Google",
            apiKey = "AIza-test",
            models = listOf(gemini),
        )
        val settings = Settings(providers = listOf(openRouter, google))

        // Non-Claude models (free router or Gemini) promote to Sonnet by default
        assertEquals(sonnet.id, promotedClaudeModelIdForToggle(settings, freeRouter, freeRouter.id))
        assertEquals(sonnet.id, promotedClaudeModelIdForToggle(settings, gemini, gemini.id))

        // Already-selected Claude Opus stays Opus instead of getting overwritten
        assertEquals(opus.id, promotedClaudeModelIdForToggle(settings, opus, opus.id))

        // Without OpenRouter key/Claude models, preserves fallback
        val noClaudeSettings = Settings(providers = listOf(google))
        assertEquals(gemini.id, promotedClaudeModelIdForToggle(noClaudeSettings, gemini, gemini.id))
    }
}
