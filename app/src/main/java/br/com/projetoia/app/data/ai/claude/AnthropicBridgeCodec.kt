package br.com.projetoia.app.data.ai.claude

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
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.util.UUID

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())

internal data class BridgeRequest(
    val messages: List<UIMessage>,
    val tools: List<Tool>,
    val maxTokens: Int?,
    val stream: Boolean,
    val requestedModel: String = AnthropicBridgeCodec.MODEL,
)

/** Anthropic wire format only. Tool execution belongs exclusively to the native CLI. */
internal object AnthropicBridgeCodec {
    const val MODEL = "projeto-free"

    fun decode(body: JsonObject): BridgeRequest {
        val requestedModel = body.string("model")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing model")
        val choice = body.obj("tool_choice")?.string("type")
        require(choice == null || choice == "auto") { "Unsupported tool_choice" }
        val messages = mutableListOf<UIMessage>()
        body["system"]?.let { value ->
            val text = text(value)
            if (text.isNotBlank()) messages += UIMessage.system(text)
        }
        for (item in body.array("messages")) {
            val message = item.jsonObject
            val role = when (message.string("role")) {
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                else -> error("Unsupported message role")
            }
            val content = message["content"] ?: error("Missing message content")
            val blocks = if (content is JsonPrimitive) JsonArray(listOf(buildJsonObject {
                put("type", "text"); put("text", content.content)
            })) else content.jsonArray
            val ordinary = mutableListOf<UIMessagePart>()
            fun flush() {
                if (ordinary.isNotEmpty()) {
                    messages += UIMessage(role = role, parts = ordinary.toList())
                    ordinary.clear()
                }
            }
            for (element in blocks) {
                val block = element.jsonObject
                when (block.string("type")) {
                    "text" -> ordinary += UIMessagePart.Text(block.string("text").orEmpty())
                    "image" -> ordinary += image(block)
                    "tool_use" -> {
                        require(role == MessageRole.ASSISTANT)
                        ordinary += UIMessagePart.Tool(
                            toolCallId = requireNotNull(block.string("id")),
                            toolName = requireNotNull(block.string("name")),
                            input = (block["input"] ?: buildJsonObject {}).toString(),
                        )
                    }
                    "tool_result" -> {
                        require(role == MessageRole.USER)
                        flush()
                        val id = requireNotNull(block.string("tool_use_id"))
                        // Match to the original call. The OpenAI adapter emits its tool result
                        // immediately after the assistant call, preserving call IDs and order.
                        val owner = messages.indexOfLast { m -> m.getTools().any { it.toolCallId == id } }
                        val result = block["content"] ?: JsonPrimitive("(no output)")
                        val output = if (result is JsonArray) result.map { part ->
                            val p = part.jsonObject
                            when (p.string("type")) {
                                "text" -> UIMessagePart.Text(p.string("text").orEmpty())
                                "image" -> image(p)
                                else -> error("Unsupported tool result block")
                            }
                        } else listOf(UIMessagePart.Text(result.jsonPrimitive.content))
                        val prefix = if (block["is_error"]?.jsonPrimitive?.booleanOrNull == true) {
                            listOf(UIMessagePart.Text("Tool failed:"))
                        } else emptyList()
                        if (owner >= 0) {
                            messages[owner] = messages[owner].copy(parts = messages[owner].parts.map {
                                if (it is UIMessagePart.Tool && it.toolCallId == id) it.copy(output = (prefix + output).ifEmpty { listOf(UIMessagePart.Text("(no output)")) }) else it
                            })
                        } else {
                            ordinary += (prefix + output).ifEmpty { listOf(UIMessagePart.Text("Tool $id: (no output)")) }
                        }
                    }
                    "thinking", "redacted_thinking" -> Unit // Never replay signatures to another provider.
                    else -> error("Unsupported Anthropic content block")
                }
            }
            flush()
        }
        require(messages.any { it.role != MessageRole.SYSTEM }) { "Empty conversation" }
        val tools = body.array("tools").map { element ->
            val tool = element.jsonObject
            require(tool.string("type") in listOf(null, "custom")) { "Server tools are not supported" }
            val name = requireNotNull(tool.string("name"))
            require(Regex("[a-zA-Z0-9_-]{1,64}").matches(name)) { "Invalid tool name" }
            val schema = requireNotNull(tool.obj("input_schema"))
            require(schema.string("type") == "object") { "Unsupported tool schema" }
            Tool(
                name = name, description = tool.string("description").orEmpty(),
                parameters = { InputSchema.Obj(schema.obj("properties") ?: buildJsonObject {}, schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }) },
                execute = { error("Gateway must never execute a native tool") },
            )
        }
        val max = body["max_tokens"]?.jsonPrimitive?.intOrNull
        require(max == null || max > 0) { "Invalid max_tokens" }
        return BridgeRequest(
            messages = messages,
            tools = tools,
            maxTokens = max?.coerceAtMost(8192),
            stream = body["stream"]?.jsonPrimitive?.booleanOrNull == true,
            requestedModel = requestedModel,
        )
    }

    private fun image(block: JsonObject): UIMessagePart.Image {
        val source = requireNotNull(block.obj("source"))
        require(source.string("type") == "base64") { "Only inline images are supported" }
        val mime = requireNotNull(source.string("media_type"))
        require(mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif"))
        return UIMessagePart.Image("data:$mime;base64,${requireNotNull(source.string("data"))}")
    }

    private fun text(value: JsonElement): String = if (value is JsonPrimitive) value.content else value.jsonArray.joinToString("\n") {
        require(it.jsonObject.string("type") == "text") { "Unsupported system block" }
        it.jsonObject.string("text").orEmpty()
    }

    fun response(result: TextGenerationResult, requestedModel: String? = null): JsonObject {
        val blocks = buildJsonArray {
            result.message.parts.forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> if (part.text.isNotEmpty()) add(buildJsonObject { put("type", "text"); put("text", part.text) })
                    is UIMessagePart.Tool -> add(buildJsonObject {
                        put("type", "tool_use"); put("id", part.toolCallId); put("name", part.toolName)
                        val inputElement = runCatching {
                            Json.parseToJsonElement(part.input).jsonObject
                        }.getOrElse {
                            buildJsonObject { put("raw_input", part.input) }
                        }
                        put("input", inputElement)
                    })
                    is UIMessagePart.Reasoning -> Unit
                    else -> error("Unsupported provider response block")
                }
            }
        }
        require(blocks.isNotEmpty()) { "The free model returned an empty response" }
        return buildJsonObject {
            put("id", "msg_${UUID.randomUUID()}"); put("type", "message"); put("role", "assistant")
            put("model", requestedModel?.takeIf { it.isNotBlank() } ?: result.model.ifBlank { MODEL })
            put("content", blocks)
            put("stop_reason", if (result.message.getTools().isNotEmpty()) "tool_use" else if (result.finishReason in listOf("length", "max_tokens")) "max_tokens" else "end_turn")
            put("stop_sequence", JsonNull)
            put("usage", buildJsonObject { put("input_tokens", result.usage?.promptTokens ?: 0); put("output_tokens", result.usage?.completionTokens ?: 0) })
        }
    }

    /** Complete SSE framing for clients that request streaming. Upstream is one atomic response. */
    fun sse(message: JsonObject): String = buildString {
        fun event(type: String, data: JsonObject) { append("event: $type\ndata: $data\n\n") }
        event("message_start", buildJsonObject {
            put("type", "message_start"); put("message", JsonObject(message + ("content" to JsonArray(emptyList())) + ("stop_reason" to JsonNull)))
        })
        message.array("content").forEachIndexed { index, item ->
            val block = item.jsonObject
            val tool = block.string("type") == "tool_use"
            event("content_block_start", buildJsonObject {
                put("type", "content_block_start"); put("index", index)
                put("content_block", if (tool) JsonObject(block + ("input" to buildJsonObject {})) else buildJsonObject { put("type", "text"); put("text", "") })
            })
            event("content_block_delta", buildJsonObject {
                put("type", "content_block_delta"); put("index", index)
                put("delta", buildJsonObject {
                    put("type", if (tool) "input_json_delta" else "text_delta")
                    put(if (tool) "partial_json" else "text", if (tool) block["input"].toString() else block.string("text").orEmpty())
                })
            })
            event("content_block_stop", buildJsonObject { put("type", "content_block_stop"); put("index", index) })
        }
        event("message_delta", buildJsonObject {
            put("type", "message_delta"); put("delta", buildJsonObject { put("stop_reason", message["stop_reason"]!!); put("stop_sequence", JsonNull) }); put("usage", message["usage"]!!)
        })
        event("message_stop", buildJsonObject { put("type", "message_stop") })
    }
}
