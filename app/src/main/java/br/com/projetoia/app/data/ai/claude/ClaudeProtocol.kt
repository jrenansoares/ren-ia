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
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock

/** SDK JSON-lines protocol, based on anthropics/claude-agent-sdk-python. */
internal object ClaudeProtocol {
    const val INIT_ID = "projeto-initialize"
    fun initialize() = buildJsonObject {
        put("type", "control_request"); put("request_id", INIT_ID)
        put("request", buildJsonObject { put("subtype", "initialize"); put("hooks", JsonNull) })
    }
    fun user(prompt: String) = buildJsonObject {
        put("type", "user"); put("message", buildJsonObject { put("role", "user"); put("content", prompt) })
    }
    fun decision(requestId: String, input: JsonObject, decision: ClaudeDecision) = buildJsonObject {
        put("type", "control_response")
        put("response", buildJsonObject {
            put("subtype", "success"); put("request_id", requestId)
            put("response", buildJsonObject {
                put("behavior", if (decision.approved) "allow" else "deny")
                if (decision.approved) put("updatedInput", input) else put("message", decision.reason.ifBlank { "Negado pelo usuário" })
            })
        })
    }

    fun appendAssistant(message: UIMessage, frame: JsonObject): UIMessage {
        val parts = frame.obj("message")?.array("content").orEmpty().mapNotNull { item ->
            val block = item.jsonObject
            when (block.string("type")) {
                "text" -> UIMessagePart.Text(block.string("text").orEmpty())
                "thinking" -> UIMessagePart.Reasoning(block.string("thinking").orEmpty())
                "tool_use" -> UIMessagePart.Tool(
                    toolCallId = requireNotNull(block.string("id")), toolName = requireNotNull(block.string("name")),
                    input = (block["input"] ?: buildJsonObject {}).toString(),
                )
                else -> null
            }
        }
        return message.copy(parts = message.parts + parts)
    }

    fun toolResult(message: UIMessage, frame: JsonObject): UIMessage {
        var updated = message
        for (item in frame.obj("message")?.array("content").orEmpty()) {
            val block = item as? JsonObject ?: continue
            if (block.string("type") != "tool_result") continue
            val id = block.string("tool_use_id") ?: continue
            val content = block["content"]
            val output = when (content) {
                is JsonPrimitive -> content.content
                is JsonArray -> content.joinToString("\n") { block ->
                    if (block.jsonObject.string("type") == "image") "[Imagem lida pela ferramenta nativa]" else block.jsonObject.string("text") ?: block.toString()
                }
                else -> "(no output)"
            }
            updated = updated.copy(parts = updated.parts.map {
                if (it is UIMessagePart.Tool && it.toolCallId == id) it.copy(
                    output = listOf(UIMessagePart.Text(output.ifBlank { "(no output)" })), finishedAt = Clock.System.now(),
                ) else it
            })
        }
        return updated
    }

    fun approval(message: UIMessage, id: String, state: ToolApprovalState): UIMessage = message.copy(parts = message.parts.map {
        if (it is UIMessagePart.Tool && it.toolCallId == id) it.copy(approvalState = state) else it
    })

    fun interrupted(message: UIMessage): UIMessage = message.copy(parts = message.parts.map {
        if (it is UIMessagePart.Tool && !it.isExecuted) it.copy(
            output = listOf(UIMessagePart.Text("Execução nativa interrompida antes de confirmar o resultado. Confira os arquivos antes de repetir o comando.")),
            approvalState = if (it.isPending) ToolApprovalState.Denied("Execução interrompida") else it.approvalState,
            finishedAt = Clock.System.now(),
        ) else it
    })
}
