package br.com.projetoia.app.data.ai

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.*
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.*
import org.junit.Test

class FreeModelRoutingTest {
    @Test fun staleAudioAndSafetyApprovalsAreIgnoredByTheRouter() {
        val speech = Model(modelId = "canopylabs/orpheus-v1-english", chatSupported = true)
        val safety = Model(modelId = "meta-llama/llama-prompt-guard-2-22m", chatSupported = true)
        val chat = Model(modelId = "openai/gpt-oss-120b")
        val provider = ProviderSetting.OpenAI(baseUrl = "https://api.groq.com/openai/v1", apiKey = "test", models = listOf(speech, safety, chat))
        val settings = br.com.projetoia.app.data.datastore.Settings(
            providers = listOf(provider),
            freeRouter = br.com.projetoia.app.data.datastore.FreeRouterSettings(enabled = true, approvedModelIds = listOf(speech.id, safety.id, chat.id), costAcknowledgements = listOf(ModelCostAcknowledgement(provider.id, chat.id, modelCheckFingerprint(provider, chat)))),
        )
        val checked = settings.recordModelCheck(
            ModelCheck(provider.id, chat.id, modelCheckFingerprint(provider, chat), 1L, 1L, ModelCheckStatus.OK),
        )
        assertEquals(listOf(chat.id), checked.automaticRoutes("Oi").map { it.model.id })
    }

    private fun route(id: String = "example/free"): FreeRoute {
        val model = Model(modelId = id, contextLength = 8192, chatSupported = true)
        return FreeRoute(ProviderSetting.OpenAI(name = id, baseUrl = "https://example.test/v1", apiKey = "fake-test-key", models = listOf(model)), model)
    }
    private fun fake(run: suspend () -> Unit) = object : Provider<ProviderSetting> {
        override suspend fun listModels(providerSetting: ProviderSetting) = emptyList<Model>()
        override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult = error("unused")
        override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams) = flow<StreamChunk> { run(); emit(StreamChunk.TextDelta("text", "ok")) }
    }

    @Test fun rejectsPaidOpenRouterModelsEvenIfApproved() {
        val provider = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = "test")
        assertFalse(isAutomaticRouteCandidate(provider, Model(modelId = "vendor/paid")))
        assertTrue(isAutomaticRouteCandidate(provider, Model(modelId = "vendor/model:free", chatSupported = true)))
        assertTrue(isAutomaticRouteCandidate(provider, Model(modelId = "openrouter/free")))
    }

    @Test fun movesToNextModelOnQuotaFailure() = runBlocking {
        val first = route(); val second = route("other/free")
        var calls = 0
        val router = FreeRoutingProvider({ provider -> fake { calls++; if (provider.id == first.provider.id) error("HTTP 429 quota exhausted") } }, listOf(first, second), MutableStateFlow(null))
        val chunks = router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList()
        assertEquals(2, calls)
        assertEquals(second.model.id, router.activeModelId)
        assertEquals(1, chunks.size)
    }

    @Test fun exhaustedCreditDoesNotRequirePaymentOrStopOtherFreeRoutes() = runBlocking {
        val first = route(); val second = route("other/free")
        var calls = 0
        val router = FreeRoutingProvider({ provider -> fake { calls++; if (provider.id == first.provider.id) error("HTTP 402 credits exhausted") } }, listOf(first, second), MutableStateFlow(null))
        router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList()
        assertEquals(2, calls)
        assertEquals(second.model.id, router.activeModelId)
    }

    @Test fun cancellationDoesNotStartAnotherModel() = runBlocking {
        val first = route(); val second = route(); var calls = 0
        val router = FreeRoutingProvider({ fake { calls++; throw CancellationException("stopped") } }, listOf(first, second), MutableStateFlow(null))
        try { router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun partialOutputIsNeverReplayedOnAnotherModel() = runBlocking {
        val first = route(); val second = route(); var calls = 0
        val broken = object : Provider<ProviderSetting> {
            override suspend fun listModels(providerSetting: ProviderSetting) = emptyList<Model>()
            override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult = error("unused")
            override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams) = flow<StreamChunk> { calls++; emit(StreamChunk.ToolCallStart("tool", "write_file")); throw IOException("connection lost") }
        }
        val router = FreeRoutingProvider({ broken }, listOf(first, second), MutableStateFlow(null))
        try { router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList(); fail("Expected interruption") } catch (_: IOException) { }
        assertEquals(1, calls)
    }

    @Test fun doesNotSendImageContextToTextOnlyFallback() = runBlocking {
        val first = route().let { it.copy(model = it.model.copy(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))) }
        val second = route(); var calls = 0
        val router = FreeRoutingProvider({ fake { calls++; error("HTTP 429") } }, listOf(first, second), MutableStateFlow(null))
        try { router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList(); fail("Expected unavailability") } catch (_: IllegalStateException) { }
        assertEquals(1, calls)
    }

    @Test fun codingTasksPreferCodingModels() {
        assertTrue(routeScore(Model(modelId = "qwen/coder", abilities = listOf(ModelAbility.TOOL)), "Corrija o código do repositório") > routeScore(Model(modelId = "general"), "Corrija o código do repositório"))
    }

    @Test fun liveToolVerificationOverridesMissingCatalogMetadata() = runBlocking {
        val base = route()
        val verified = base.copy(toolsVerified = true)
        var calls = 0
        val router = FreeRoutingProvider(
            resolve = { fake { calls++ } },
            candidates = listOf(verified),
            status = MutableStateFlow(null),
        )
        val tool = Tool(name = "probe", description = "", execute = { emptyList() })
        router.streamText(base.provider, emptyList(), TextGenerationParams(base.model, tools = listOf(tool))).toList()
        assertEquals(1, calls)
        assertEquals(base.model.id, router.activeModelId)
    }
    @Test fun authFailureSkipsOtherModelsUsingTheSameProviderKey() = runBlocking {
        val first = route()
        val sibling = FreeRoute(first.provider, first.model.copy(id = kotlin.uuid.Uuid.random(), modelId = "sibling"))
        val fallback = route("fallback")
        val calls = mutableListOf<kotlin.uuid.Uuid>()
        val router = FreeRoutingProvider({ provider -> fake {
            calls += provider.id
            if (provider.id == first.provider.id) throw me.rerere.ai.provider.ProviderHttpException(401)
        } }, listOf(first, sibling, fallback), MutableStateFlow(null))
        router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList()
        assertEquals(listOf(first.provider.id, fallback.provider.id), calls)
    }

    @Test fun telemetryFailureCannotFailACompletedResponse() = runBlocking {
        val first = route()
        var calls = 0
        val router = FreeRoutingProvider({ fake { calls++ } }, listOf(first, route("backup")), MutableStateFlow(null),
            observe = { _, _ -> throw IOException("storage unavailable") })
        assertEquals(1, router.streamText(first.provider, emptyList(), TextGenerationParams(first.model)).toList().size)
        assertEquals(1, calls)
    }

}
