package br.com.projetoia.app.data.ai.claude

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom

/** One ephemeral, authenticated loopback endpoint per turn. No LAN listener or provider keys. */
internal class ClaudeLocalGateway(
    private val owner: CoroutineScope,
    private val complete: suspend (BridgeRequest) -> JsonObject,
) {
    private val requests = CoroutineScope(owner.coroutineContext + SupervisorJob(owner.coroutineContext[Job]))
    val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        routing {
            get("/") {
                call.respondText(
                    buildJsonObject {
                        put("status", "ok")
                        put("service", "Ren IA Anthropic Gateway")
                    }.toString(),
                    ContentType.Application.Json
                )
            }
            get("/v1/models") {
                val provided = call.request.headers["x-api-key"]
                    ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                if (provided == null || !MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())) {
                    call.respondText(
                        error("authentication_error", "Invalid gateway credential"),
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized
                    )
                    return@get
                }
                val models = buildJsonObject {
                    put("data", buildJsonArray {
                        listOf(
                            "projeto-free",
                            "claude-3-5-sonnet-20241022",
                            "claude-3-7-sonnet-20250219",
                            "claude-3-5-haiku-20241022",
                            "claude-3-opus-20240229",
                        ).forEach { id ->
                            add(buildJsonObject {
                                put("id", id)
                                put("type", "model")
                                put("display_name", id)
                                put("created_at", 1700000000)
                            })
                        }
                    })
                    put("has_more", false)
                }
                call.respondText(models.toString(), ContentType.Application.Json)
            }
            post("/v1/messages") { handle(countOnly = false) }
            post("/v1/messages/count_tokens") { handle(countOnly = true) }
        }
    }

    suspend fun start(): String {
        server.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        return "http://127.0.0.1:$port"
    }

    fun close() {
        requests.cancel()
        server.stop(0, 500)
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handle(countOnly: Boolean) {
        val provided =
            call.request.headers["x-api-key"]
                ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        if (provided == null || !MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())) {
            call.respondText(
                error("authentication_error", "Invalid gateway credential"),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized
            )
            return
        }
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length == null || length !in 1..MAX_BODY_BYTES) {
            call.respondText(
                error("invalid_request_error", "A bounded Content-Length is required"),
                ContentType.Application.Json,
                HttpStatusCode.PayloadTooLarge
            )
            return
        }
        val rawBody = call.receiveText()
        val request = try {
            val body = Json.parseToJsonElement(rawBody).jsonObject
            AnthropicBridgeCodec.decode(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            call.respondText(
                error("invalid_request_error", e.message ?: "Unsupported gateway request; update the integration"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        } catch (e: Exception) {
            call.respondText(
                error("invalid_request_error", "Invalid JSON request"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        if (countOnly) {
            // There is no shared tokenizer across free providers. Mark the estimate explicitly;
            // the chat's usage display is populated only from actual upstream usage.
            call.response.headers.append("x-projeto-token-count-estimated", "true")
            val estimate = (rawBody.toByteArray(Charsets.UTF_8).size + 1).coerceAtLeast(1)
            call.respondText(
                buildJsonObject { put("input_tokens", estimate) }.toString(),
                ContentType.Application.Json
            )
            return
        }

        try {
            val message = requests.async { complete(request) }.await()
            if (request.stream) {
                call.respondText(AnthropicBridgeCodec.sse(message), ContentType.Text.EventStream)
            } else {
                call.respondText(message.toString(), ContentType.Application.Json)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Provider exceptions may contain request bodies or credentials. Never expose them to Linux.
            call.respondText(
                error("api_error", "Nenhuma IA gratuita compatível respondeu. Os arquivos foram preservados."),
                ContentType.Application.Json,
                HttpStatusCode.BadGateway
            )
        }
    }

    private fun error(type: String, message: String) = buildJsonObject {
        put("type", "error")
        put("error", buildJsonObject {
            put("type", type)
            put("message", message)
        })
    }.toString()

    companion object {
        private const val MAX_BODY_BYTES = 16L * 1024 * 1024
    }
}
