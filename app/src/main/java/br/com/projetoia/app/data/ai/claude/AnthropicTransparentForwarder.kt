package br.com.projetoia.app.data.ai.claude

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.TokenUsage
import me.rerere.common.http.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val BLOCKED_CUSTOM_HEADERS = setOf(
    "authorization",
    "x-api-key",
    "host",
    "content-length",
    "content-type",
    "connection",
    "accept-encoding",
)

internal class AnthropicTransparentForwarder private constructor(
    private val client: OkHttpClient,
    private val messagesUrl: String,
    private val apiKey: String,
    private val providerSetting: ProviderSetting,
    private val primaryModel: Model,
    private val useApiKeyHeader: Boolean,
    private val providerLabel: String,
    private val familyOverrides: Map<ClaudeModelFamily, String> = emptyMap(),
    private val onResponseTelemetry: (respondedModel: String?, usage: TokenUsage) -> Unit = { _, _ -> },
) {
    constructor(
        client: OkHttpClient,
        option: OpenRouterClaudeOption,
        familyOverrides: Map<ClaudeModelFamily, String> = emptyMap(),
        onResponseTelemetry: (respondedModel: String?, usage: TokenUsage) -> Unit = { _, _ -> },
    ) : this(
        client = client,
        messagesUrl = option.messagesUrl,
        apiKey = option.apiKey,
        providerSetting = option.providerSetting,
        primaryModel = option.model,
        useApiKeyHeader = false,
        providerLabel = "OpenRouter",
        familyOverrides = familyOverrides,
        onResponseTelemetry = onResponseTelemetry,
    )

    constructor(
        client: OkHttpClient,
        option: DirectAnthropicClaudeOption,
        familyOverrides: Map<ClaudeModelFamily, String> = emptyMap(),
        onResponseTelemetry: (respondedModel: String?, usage: TokenUsage) -> Unit = { _, _ -> },
    ) : this(
        client = client,
        messagesUrl = option.messagesUrl,
        apiKey = option.apiKey,
        providerSetting = option.providerSetting,
        primaryModel = option.model,
        useApiKeyHeader = true,
        providerLabel = "Anthropic",
        familyOverrides = familyOverrides,
        onResponseTelemetry = onResponseTelemetry,
    )

    internal fun resolveUpstreamModel(requestedModel: String): String {
        val raw = requestedModel.trim()
        val normalizedInput = if (useApiKeyHeader && raw.startsWith("anthropic/", ignoreCase = true)) {
            raw.substringAfter('/')
        } else {
            raw
        }
        val primaryId = if (useApiKeyHeader && primaryModel.modelId.startsWith("anthropic/", ignoreCase = true)) {
            primaryModel.modelId.substringAfter('/')
        } else {
            primaryModel.modelId
        }
        if (normalizedInput.isEmpty() || normalizedInput.equals("projeto-free", ignoreCase = true)) {
            return primaryId
        }
        if (!useApiKeyHeader && normalizedInput.startsWith("anthropic/", ignoreCase = true)) {
            return normalizedInput
        }
        if (useApiKeyHeader && normalizedInput.startsWith("claude-", ignoreCase = true)) {
            return normalizedInput
        }
        val lower = normalizedInput.lowercase(Locale.ROOT)
        val mapped = when {
            lower.contains("opus") -> familyOverrides[ClaudeModelFamily.OPUS] ?: primaryId
            lower.contains("haiku") -> familyOverrides[ClaudeModelFamily.HAIKU] ?: primaryId
            lower.contains("fable") -> familyOverrides[ClaudeModelFamily.FABLE] ?: primaryId
            lower.contains("sonnet") -> familyOverrides[ClaudeModelFamily.SONNET] ?: primaryId
            else -> primaryId
        }
        return if (useApiKeyHeader && mapped.startsWith("anthropic/", ignoreCase = true)) {
            mapped.substringAfter('/')
        } else {
            mapped
        }
    }

    suspend fun forward(call: ApplicationCall, rawBody: String) {
        val parsed = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
        if (parsed == null) {
            call.respondText(
                buildAnthropicErrorJson("invalid_request_error", "Malformed Anthropic JSON payload."),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return
        }

        val requestedModel = parsed["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val resolvedModel = resolveUpstreamModel(requestedModel)
        val outgoingBody = if (requestedModel == resolvedModel) {
            rawBody
        } else {
            JsonObject(parsed.toMutableMap().apply {
                put("model", JsonPrimitive(resolvedModel))
            }).toString()
        }
        val streamRequested = parsed["stream"]?.jsonPrimitive?.booleanOrNull == true

        val requestBuilder = Request.Builder()
            .url(messagesUrl)
            .post(outgoingBody.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (useApiKeyHeader) {
                    header("x-api-key", apiKey)
                } else {
                    header("Authorization", "Bearer $apiKey")
                    header("X-Title", "Projeto IA")
                }
            }
            .header(
                "anthropic-version",
                call.request.headers["anthropic-version"]?.takeIf { it.isNotBlank() } ?: "2023-06-01",
            )
            .header(
                "Accept",
                call.request.headers["Accept"]?.takeIf { it.isNotBlank() }
                    ?: if (streamRequested) "text/event-stream" else "application/json",
            )

        call.request.headers["anthropic-beta"]?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("anthropic-beta", it)
        }
        call.request.headers["anthropic-dangerous-direct-browser-access"]?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("anthropic-dangerous-direct-browser-access", it)
        }

        for (header in providerSetting.customHeaders + primaryModel.customHeaders) {
            val name = header.name.trim()
            if (name.isBlank() || name.lowercase(Locale.ROOT) in BLOCKED_CUSTOM_HEADERS) continue
            requestBuilder.header(name, header.value)
        }

        val response = try {
            client.newCall(requestBuilder.build()).await()
        } catch (t: Throwable) {
            val safeMessage = redactSensitiveSecrets(t.message ?: "Upstream connection failed", apiKey)
            call.respondText(
                buildAnthropicErrorJson("api_error", safeMessage),
                ContentType.Application.Json,
                HttpStatusCode.BadGateway,
            )
            return
        }

        response.use { upstream ->
            val contentType = upstream.header("Content-Type").orEmpty()
            val isSse = contentType.contains("text/event-stream", ignoreCase = true)

            if (!upstream.isSuccessful) {
                val rawError = upstream.body.string().take(32_768)
                val sanitized = redactSensitiveSecrets(rawError, apiKey).ifBlank {
                    buildAnthropicErrorJson("api_error", "Upstream HTTP ${upstream.code}")
                }
                val payload = normalizeErrorPayload(sanitized, upstream.code, providerLabel)
                val status = runCatching { HttpStatusCode.fromValue(upstream.code) }
                    .getOrDefault(HttpStatusCode.BadGateway)
                call.respondText(payload, ContentType.Application.Json, status)
                return
            }

            if (isSse) {
                var respondedModel: String? = null
                var inputTokens = 0
                var outputTokens = 0
                var cachedTokens = 0

                try {
                    call.respondOutputStream(ContentType.Text.EventStream, HttpStatusCode.OK) {
                        val reader = BufferedReader(
                            InputStreamReader(upstream.body.byteStream(), StandardCharsets.UTF_8)
                        )
                        val writer = bufferedWriter(StandardCharsets.UTF_8)
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val current = line!!
                            if (current.startsWith("data:")) {
                                val dataPayload = current.removePrefix("data:").trim()
                                if (dataPayload.isNotEmpty() && dataPayload != "[DONE]") {
                                    inspectSseEvent(dataPayload)?.let { telemetry ->
                                        telemetry.model?.let { respondedModel = it }
                                        if (telemetry.inputTokens > 0) inputTokens = telemetry.inputTokens
                                        if (telemetry.outputTokens > 0) outputTokens = telemetry.outputTokens
                                        if (telemetry.cachedTokens > 0) cachedTokens = telemetry.cachedTokens
                                    }
                                }
                            }
                            writer.write(current)
                            writer.write("\n")
                            if (current.isEmpty() || current.startsWith("data:")) {
                                writer.flush()
                            }
                        }
                        writer.flush()
                    }
                } finally {
                    val total = inputTokens + outputTokens
                    onResponseTelemetry(
                        respondedModel ?: resolvedModel,
                        TokenUsage(
                            promptTokens = inputTokens,
                            completionTokens = outputTokens,
                            cachedTokens = cachedTokens,
                            totalTokens = total,
                        ),
                    )
                }
            } else {
                val responseText = upstream.body.string()
                val telemetry = inspectResponseJson(responseText)
                val usage = TokenUsage(
                    promptTokens = telemetry?.inputTokens ?: 0,
                    completionTokens = telemetry?.outputTokens ?: 0,
                    cachedTokens = telemetry?.cachedTokens ?: 0,
                    totalTokens = (telemetry?.inputTokens ?: 0) + (telemetry?.outputTokens ?: 0),
                )
                onResponseTelemetry(telemetry?.model ?: resolvedModel, usage)
                val status = runCatching { HttpStatusCode.fromValue(upstream.code) }
                    .getOrDefault(HttpStatusCode.OK)
                call.respondText(responseText, ContentType.Application.Json, status)
            }
        }
    }
}

private data class TelemetrySnapshot(
    val model: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
)

private fun inspectSseEvent(jsonText: String): TelemetrySnapshot? {
    val obj = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
    val type = obj["type"]?.jsonPrimitive?.contentOrNull
    return when (type) {
        "message_start" -> {
            val message = obj["message"]?.jsonObject ?: return null
            val model = message["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val usage = message["usage"]?.jsonObject
            TelemetrySnapshot(
                model = model,
                inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                cachedTokens = (usage?.get("cache_read_input_tokens")?.jsonPrimitive?.intOrNull ?: 0),
            )
        }
        "message_delta" -> {
            val usage = obj["usage"]?.jsonObject ?: return null
            TelemetrySnapshot(
                inputTokens = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                cachedTokens = usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        else -> null
    }
}

private fun inspectResponseJson(jsonText: String): TelemetrySnapshot? {
    val obj = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
    val model = obj["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val usage = obj["usage"]?.jsonObject
    return TelemetrySnapshot(
        model = model,
        inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
        outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
        cachedTokens = usage?.get("cache_read_input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
    )
}

private val REDACTED_BEARER_REGEX = Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-+/=]{8,}""")
private val REDACTED_API_KEY_REGEX = Regex("""\b(?:sk-or-v1-|sk-ant-|AIza|gsk_|nvapi-|xai-|hf_)[A-Za-z0-9._\-]{8,}""")

internal fun redactSensitiveSecrets(text: String, vararg secrets: String): String {
    var sanitized = text
    for (secret in secrets) {
        if (secret.isNotBlank()) {
            sanitized = sanitized.replace(secret, "[REDACTED]")
        }
    }
    sanitized = REDACTED_BEARER_REGEX.replace(sanitized, "Bearer [REDACTED]")
    sanitized = REDACTED_API_KEY_REGEX.replace(sanitized, "[REDACTED]")
    return sanitized
}

private fun anthropicErrorTypeForStatus(statusCode: Int): String = when (statusCode) {
    401 -> "authentication_error"
    402, 403 -> "permission_error"
    429 -> "rate_limit_error"
    400, 404, 422 -> "invalid_request_error"
    502, 503, 529 -> "overloaded_error"
    else -> "api_error"
}

private fun providerHintForStatus(statusCode: Int, message: String, providerLabel: String): String {
    val clean = message.trim().ifBlank { "HTTP $statusCode" }
    val hint = when (statusCode) {
        401 -> "Check your $providerLabel API key in Settings."
        402 -> "Insufficient credits on $providerLabel account."
        404 -> "Claude model unavailable or not found on $providerLabel."
        429 -> "$providerLabel rate limit reached; retry shortly."
        else -> null
    } ?: return clean
    return if (clean.contains(providerLabel, ignoreCase = true)) clean else "$clean ($hint)"
}

private fun normalizeErrorPayload(body: String, statusCode: Int, providerLabel: String): String {
    val defaultType = anthropicErrorTypeForStatus(statusCode)
    val parsed = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val errorElement = parsed?.get("error")
    if (parsed != null && errorElement != null) {
        val normalizedError = when (errorElement) {
            is JsonObject -> {
                val existingType = errorElement["type"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val rawMsg = errorElement["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val enrichedMsg = providerHintForStatus(statusCode, rawMsg, providerLabel)
                JsonObject(errorElement.toMutableMap().apply {
                    if (existingType == null) put("type", JsonPrimitive(defaultType))
                    put("message", JsonPrimitive(enrichedMsg))
                })
            }
            else -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive(defaultType),
                    "message" to JsonPrimitive(providerHintForStatus(statusCode, errorElement.toString(), providerLabel)),
                )
            )
        }
        return JsonObject(parsed.toMutableMap().apply {
            put("type", JsonPrimitive("error"))
            put("error", normalizedError)
        }).toString()
    }
    return buildAnthropicErrorJson(defaultType, providerHintForStatus(statusCode, body, providerLabel))
}

private fun buildAnthropicErrorJson(type: String, message: String): String {
    return JsonObject(
        mapOf(
            "type" to JsonPrimitive("error"),
            "error" to JsonObject(
                mapOf(
                    "type" to JsonPrimitive(type),
                    "message" to JsonPrimitive(message),
                )
            ),
        )
    ).toString()
}
