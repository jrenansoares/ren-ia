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

    @Test fun billingClassificationDistinguishesFreeQuotasFromPaidProviders() {
        val openRouterFree = Model(modelId = "qwen/coder:free", chatSupported = true)
        val openRouterPaid = Model(modelId = "anthropic/claude-3.5-sonnet", chatSupported = true)
        val openRouter = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = "fake", models = listOf(openRouterFree, openRouterPaid))
        assertEquals(ModelBillingTier.FREE, classifyModelBilling(openRouter, openRouterFree))
        assertEquals(ModelBillingTier.PAID_ONLY, classifyModelBilling(openRouter, openRouterPaid))

        val google = ProviderSetting.Google(apiKey = "fake", vertexAI = false)
        assertEquals(ModelBillingTier.FREE_TIER_QUOTA, classifyModelBilling(google, Model(modelId = "gemini-2.5-flash")))

        val vertex = ProviderSetting.Google(apiKey = "fake", vertexAI = true)
        assertEquals(ModelBillingTier.PAID_ONLY, classifyModelBilling(vertex, Model(modelId = "gemini-2.5-flash")))

        val groq = ConnectionService.GROQ.configure("fake")
        assertEquals(ModelBillingTier.FREE_TIER_QUOTA, classifyModelBilling(groq, Model(modelId = "llama-3.3-70b-versatile")))

        val nvidia = ConnectionService.NVIDIA.configure("fake")
        assertEquals(ModelBillingTier.FREE, classifyModelBilling(nvidia, Model(modelId = "meta/llama-3.3-70b-instruct")))

        assertTrue(billingTierPriority(ModelBillingTier.FREE) < billingTierPriority(ModelBillingTier.FREE_TIER_QUOTA))
        assertTrue(billingTierPriority(ModelBillingTier.FREE_TIER_QUOTA) < billingTierPriority(ModelBillingTier.PAID_ONLY))
    }

    @Test fun canProbeModelAllowsBuiltInToolsAndReadinessSeparatesAllThreeStates() {
        val builtInModel = Model(
            modelId = "gemini-2.5-flash",
            chatSupported = true,
            tools = setOf(BuiltInTools.Search),
        )
        val google = ProviderSetting.Google(name = "Google", apiKey = "AIza-test", models = listOf(builtInModel))
        assertTrue(canProbeModel(google, builtInModel))

        val baseSettings = br.com.projetoia.app.data.datastore.Settings(providers = listOf(google))
        // State 1: Available in catalog, not checked yet
        assertEquals(ModelReadiness.AVAILABLE, baseSettings.classifyModelReadiness(google, builtInModel))

        // State 2: Accessible (chat verified, tools not yet verified)
        val chatVerified = baseSettings.recordModelCheck(
            ModelCheck(
                providerId = google.id,
                modelId = builtInModel.id,
                fingerprint = modelCheckFingerprint(google, builtInModel),
                checkedAt = 100L,
                durationMs = 200L,
                status = ModelCheckStatus.OK,
                toolsVerified = false,
            )
        )
        assertEquals(ModelReadiness.ACCESSIBLE, chatVerified.classifyModelReadiness(google, builtInModel))

        // State 3: Tool-capable (live tool probe confirmed)
        val toolVerified = chatVerified.recordModelCheck(
            ModelCheck(
                providerId = google.id,
                modelId = builtInModel.id,
                fingerprint = modelCheckFingerprint(google, builtInModel),
                checkedAt = 200L,
                durationMs = 150L,
                status = ModelCheckStatus.OK,
                toolsVerified = true,
            )
        )
        assertEquals(ModelReadiness.TOOL_CAPABLE, toolVerified.classifyModelReadiness(google, builtInModel))
    }

    @Test fun interleaveAndCatalogMergePreserveMultiProviderAccessAndCapabilities() {
        val nvidia = ConnectionService.NVIDIA.configure("nvapi-1")
        val groq = ConnectionService.GROQ.configure("gsk-1")
        val google = ConnectionService.GOOGLE_AI_STUDIO.configure("AIza-1")

        val nModels = (1..5).map { FreeRoute(nvidia, Model(modelId = "nvidia-$it", chatSupported = true)) }
        val gModels = (1..3).map { FreeRoute(groq, Model(modelId = "groq-$it", chatSupported = true)) }
        val aModels = (1..2).map { FreeRoute(google, Model(modelId = "google-$it", chatSupported = true)) }

        val interleaved = interleaveModelRoutesByProvider(listOf(nModels, gModels, aModels), limit = 6)
        assertEquals(6, interleaved.size)
        // Round-robin first 3 slots contain 1 from each provider -> no single-provider starvation
        assertEquals(setOf(nvidia.id, groq.id, google.id), interleaved.take(3).map { it.provider.id }.toSet())

        val rawClaude = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6", chatSupported = true)
        val openRouter = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = "sk-or-test")
        val merged = br.com.projetoia.app.data.datastore.Settings(providers = listOf(openRouter))
            .mergeProviderCatalog(openRouter, listOf(rawClaude), checkedAt = 50L)
        val enrichedClaude = merged.providers.single().models.single()
        assertTrue(ModelAbility.TOOL in enrichedClaude.abilities)
    }
}
