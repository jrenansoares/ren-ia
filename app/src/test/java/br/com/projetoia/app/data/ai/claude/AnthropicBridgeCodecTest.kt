package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class AnthropicBridgeCodecTest {
    private fun decode(messages: String, extra: String = "") = AnthropicBridgeCodec.decode(Json.parseToJsonElement("""{"model":"projeto-free","messages":$messages$extra}""").jsonObject)

    @Test fun nativeResultsAreLinkedToTheirCallsWithoutExecutingThem() {
        val request = decode("""[
          {"role":"user","content":"Run it"},
          {"role":"assistant","content":[{"type":"text","text":"Checking"},{"type":"tool_use","id":"a","name":"Bash","input":{"command":"ls"}},{"type":"tool_use","id":"b","name":"Read","input":{"file_path":"a.txt"}}]},
          {"role":"user","content":[{"type":"tool_result","tool_use_id":"a","content":"file.txt"},{"type":"tool_result","tool_use_id":"b","is_error":true,"content":[{"type":"text","text":"Not found"}]},{"type":"text","text":"Continue"}]}
        ]""")
        val calls = request.messages.flatMap { it.getTools() }
        assertEquals(listOf("a", "b"), calls.map { it.toolCallId })
        assertEquals("file.txt", (calls[0].output.single() as UIMessagePart.Text).text)
        assertTrue(calls[1].output.filterIsInstance<UIMessagePart.Text>().joinToString { it.text }.contains("Not found"))
        assertTrue(calls.all { it.isExecuted })
        assertEquals("Continue", request.messages.last().toText())
    }

    @Test fun gatewayToolDefinitionsCannotExecute() = runBlocking {
        val request = decode("""[{"role":"user","content":"hi"}]""", """, "tools":[{"name":"Bash","description":"shell","input_schema":{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}}]""")
        assertEquals("Bash", request.tools.single().name)
        assertThrows(IllegalStateException::class.java) { runBlocking { request.tools.single().execute(Json.parseToJsonElement("{}")) } }
        Unit
    }

    @Test fun arbitraryModelNamesCannotSelectAnotherProvider() {
        val json = Json.parseToJsonElement("""{"model":"claude-paid","messages":[{"role":"user","content":"hi"}]}""").jsonObject
        assertThrows(IllegalArgumentException::class.java) { AnthropicBridgeCodec.decode(json) }
    }

    @Test fun orphanResultsAndRemoteImagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { decode("""[{"role":"user","content":[{"type":"tool_result","tool_use_id":"unknown","content":"x"}]}]""") }
        assertThrows(IllegalArgumentException::class.java) { decode("""[{"role":"user","content":[{"type":"image","source":{"type":"url","url":"https://invalid.test/private"}}]}]""") }
    }

    @Test fun inlineImagesAndSystemBlocksArePreserved() {
        val request = decode("""[{"role":"user","content":[{"type":"image","source":{"type":"base64","media_type":"image/png","data":"AQ=="}}]}]""", """, "system":[{"type":"text","text":"System","cache_control":{"type":"ephemeral"}}]""")
        assertEquals("System", request.messages.first().toText())
        assertEquals("data:image/png;base64,AQ==", (request.messages.last().parts.single() as UIMessagePart.Image).url)
    }

    @Test fun sseIncludesToolJsonUsageAndStopEvents() {
        // Include a newline and quote in arguments to verify the JSON string is framed, not interpolated.
        val safe = UIMessage.assistant("Inspecting").copy(parts = listOf(UIMessagePart.Text("Inspecting"), UIMessagePart.Tool("call_1", "Bash", """{"command":"printf 'hello\\n'"}""")))
        val response = AnthropicBridgeCodec.response(TextGenerationResult("id", "free/test", safe, usage = TokenUsage(10, 3, totalTokens = 13)))
        assertEquals("tool_use", response.string("stop_reason"))
        val sse = AnthropicBridgeCodec.sse(response)
        val frames = sse.split("\n\n").filter { it.isNotBlank() }.map { event -> Json.parseToJsonElement(event.lineSequence().first { it.startsWith("data: ") }.removePrefix("data: ")).jsonObject }
        assertEquals("message_start", frames.first().string("type"))
        assertEquals("message_stop", frames.last().string("type"))
        val args = frames.single { it.obj("delta")?.string("type") == "input_json_delta" }.obj("delta")!!.string("partial_json")!!
        assertEquals("printf 'hello\\n'", Json.parseToJsonElement(args).jsonObject.string("command"))
        assertEquals(2, frames.count { it.string("type") == "content_block_stop" })
    }

    @Test fun emptyResponsesAndUnsupportedServerToolsFailExplicitly() {
        assertThrows(IllegalArgumentException::class.java) { AnthropicBridgeCodec.response(TextGenerationResult("id", "free", UIMessage.assistant(""))) }
        assertThrows(IllegalArgumentException::class.java) { decode("""[{"role":"user","content":"hi"}]""", """, "tools":[{"type":"web_search_20250305","name":"web_search"}]""") }
    }
}
