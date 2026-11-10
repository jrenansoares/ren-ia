package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.OpenRouterReasoningMetadata
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatCompletionsHistoryCompatibilityTest {
    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

    private fun buildRequest(
        baseUrl: String,
        assistant: UIMessage = reply(),
        includeHistoryReasoning: Boolean = true,
        stream: Boolean = true,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return method.invoke(
            api,
            listOf(UIMessage.user("Oi"), assistant, UIMessage.user("Com quem falo?")),
            TextGenerationParams(model = Model(modelId = "openai/gpt-oss-120b")),
            ProviderSetting.OpenAI(baseUrl = baseUrl, includeHistoryReasoning = includeHistoryReasoning),
            stream,
        ) as JsonObject
    }

    private fun reply(reasoning: UIMessagePart.Reasoning = UIMessagePart.Reasoning(reasoning = "thinking")) =
        UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(reasoning, UIMessagePart.Text("Oi! Como posso ajudar você hoje?")),
        )

    @Test
    fun `second OpenRouter turn keeps answer but never sends raw reasoning content`() {
        for (stream in listOf(true, false)) {
            val messages = buildRequest("https://openrouter.ai/api/v1", stream = stream)["messages"]!!.jsonArray
            assertEquals(3, messages.size)
            val assistant = messages[1].jsonObject
            assertEquals("Oi! Como posso ajudar você hoje?", assistant["content"]!!.jsonPrimitive.content)
            assertEquals("Com quem falo?", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
            assertFalse(assistant.containsKey("reasoning_content"))
        }
    }

    @Test
    fun `second Groq turn omits reasoning from other providers too`() {
        val reasoning = UIMessagePart.Reasoning(
            reasoning = "thinking",
            metadata = OpenRouterReasoningMetadata(buildJsonArray {
                add(buildJsonObject {
                    put("type", "reasoning.text")
                    put("text", "thinking")
                })
            }).toMetadata(),
        )
        val assistant = buildRequest("https://api.groq.com/openai/v1", reply(reasoning))["messages"]!!
            .jsonArray[1].jsonObject
        assertFalse(assistant.containsKey("reasoning_content"))
        assertFalse(assistant.containsKey("reasoning_details"))
        assertEquals("Oi! Como posso ajudar você hoje?", assistant["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenRouter structured reasoning and tool results survive continuation`() {
        val details = buildJsonArray {
            add(buildJsonObject {
                put("type", "reasoning.text")
                put("text", "thinking")
                put("index", 0)
            })
        }
        val assistant = reply(UIMessagePart.Reasoning(
            reasoning = "thinking",
            metadata = OpenRouterReasoningMetadata(details).toMetadata(),
        )).let { message ->
            message.copy(parts = message.parts + UIMessagePart.Tool(
                toolCallId = "call_1",
                toolName = "read_file",
                input = "{}",
                output = listOf(UIMessagePart.Text("file contents")),
            ))
        }
        val messages = buildRequest("https://openrouter.ai/api/v1", assistant)["messages"]!!.jsonArray
        assertEquals(details, messages[1].jsonObject["reasoning_details"])
        assertFalse(messages[1].jsonObject.containsKey("reasoning_content"))
        assertEquals(
            "call_1",
            messages[1].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        )
        assertEquals("tool", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("file contents", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DeepSeek and Moonshot retain their native reasoning field`() {
        for (host in listOf("api.deepseek.com", "api.moonshot.cn")) {
            val assistant = buildRequest("https://$host/v1")["messages"]!!.jsonArray[1].jsonObject
            assertEquals("thinking", assistant["reasoning_content"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `disabled history reasoning is respected`() {
        val assistant = buildRequest("https://api.deepseek.com/v1", includeHistoryReasoning = false)["messages"]!!
            .jsonArray[1].jsonObject
        assertFalse(assistant.containsKey("reasoning_content"))
        assertFalse(assistant.containsKey("reasoning_details"))
    }

    @Test
    fun `OpenRouter omits reasoning only history without creating empty assistant messages`() {
        val messages = buildRequest("https://openrouter.ai/api/v1", UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Reasoning(reasoning = "thinking")),
        ))["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
    }
}
