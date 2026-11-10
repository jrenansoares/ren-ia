package br.com.projetoia.app.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.*
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class ModelProbeTest {
    @Test fun toolProbeActuallySendsToolsWhenCatalogMetadataIsMissing() = runBlocking {
        val token = "probe-test"
        val model = Model(modelId = "test-model", chatSupported = true)
        val setting = ProviderSetting.OpenAI(baseUrl = "https://example.test/v1", apiKey = "fake", models = listOf(model))
        var calls = 0
        var sum = 0
        val fake = object : Provider<ProviderSetting> {
            override suspend fun listModels(providerSetting: ProviderSetting) = listOf(model)
            override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> = error("unused")
            override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult {
                calls++
                val part = if (params.tools.isEmpty()) {
                    val operands = Regex("Compute (\\d+) \\+ (\\d+)").find(messages.single().toText())!!
                    sum = operands.groupValues[1].toInt() + operands.groupValues[2].toInt()
                    UIMessagePart.Text("$token|$sum")
                } else {
                    assertTrue(ModelAbility.TOOL in params.model.abilities)
                    assertEquals("connection_probe", params.tools.single().name)
                    UIMessagePart.Tool("test-call", "connection_probe", "{\"token\":\"$token\",\"sum\":$sum}")
                }
                return TextGenerationResult("test", model.modelId, UIMessage(role = MessageRole.ASSISTANT, parts = listOf(part)))
            }
        }
        val result = ModelProbe({ fake }, nonce = { token }).check(setting, model)
        assertEquals(2, calls)
        assertEquals(ModelCheckStatus.OK, result.status)
        assertTrue(result.toolsVerified)
        assertEquals(ModelCheckStatus.OK, result.toolStatus)
        assertFalse(ModelAbility.TOOL in model.abilities)
    }

    @Test fun reasoningModelAndFormattedTextRepliesPassProbe() = runBlocking {
        val model = Model(modelId = "deepseek-r1-distill-llama-70b", chatSupported = true, abilities = listOf(ModelAbility.REASONING))
        val setting = ProviderSetting.OpenAI(baseUrl = "https://api.groq.com/openai/v1", apiKey = "fake", models = listOf(model))
        val fake = object : Provider<ProviderSetting> {
            override suspend fun listModels(providerSetting: ProviderSetting) = listOf(model)
            override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> = error("unused")
            override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult {
                val part = if (params.tools.isEmpty()) {
                    UIMessagePart.Text("Resultado calculado com sucesso.")
                } else {
                    UIMessagePart.Tool("call-1", "connection_probe", "{\"token\":\"ok\"}")
                }
                return TextGenerationResult("test", model.modelId, UIMessage(role = MessageRole.ASSISTANT, parts = listOf(part)))
            }
        }
        val result = ModelProbe({ fake }).check(setting, model)
        assertEquals(ModelCheckStatus.OK, result.status)
        assertTrue(result.toolsVerified)
    }

    @Test fun stabilityScorePrioritizesProductionModelsOverExperimentalAcrossProviders() {
        assertTrue(modelStabilityScore(Model(modelId = "gemini-2.5-flash")) > modelStabilityScore(Model(modelId = "gemini-3.8-flash")))
        assertTrue(modelStabilityScore(Model(modelId = "gemini-2.0-flash")) > modelStabilityScore(Model(modelId = "gemini-4-argon")))
        assertTrue(modelStabilityScore(Model(modelId = "llama-3.3-70b-versatile")) > modelStabilityScore(Model(modelId = "llama-preview-exp")))
        assertTrue(modelStabilityScore(Model(modelId = "meta/llama-3.3-70b-instruct")) > modelStabilityScore(Model(modelId = "experimental-beta")))
    }
}
