package br.com.projetoia.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import br.com.projetoia.app.data.ai.tools.resolveWorkspaceToolApproval
import br.com.projetoia.app.data.datastore.FreeRouterSettings
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.model.Assistant
import br.com.projetoia.app.data.model.Conversation
import br.com.projetoia.app.data.model.ConversationConfig
import br.com.projetoia.app.data.model.getAssistantOf
import br.com.projetoia.app.data.model.withAssistantUpdate
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ProgrammingModeTest {
    private val free = Model(modelId = "qwen/coder:free", chatSupported = true, abilities = listOf(ModelAbility.TOOL))
    private val provider = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = "fake-test-key", models = listOf(free))

    @Test fun onlyCanonicalFreeToolRoutesPass() {
        assertTrue(isProgrammingRouteAllowed(provider, free))
        assertTrue(isProgrammingRouteAllowed(provider, free.copy(modelId = "openrouter/free")))
        assertFalse(isProgrammingRouteAllowed(provider, free.copy(modelId = "qwen/paid")))
        // The real synthetic tool call, not catalog metadata, gates programming eligibility.
        assertTrue(isProgrammingRouteAllowed(provider, free.copy(abilities = emptyList())))
        assertFalse(isProgrammingRouteAllowed(provider.copy(apiKey = ""), free))
        assertFalse(isProgrammingRouteAllowed(provider.copy(enabled = false), free))
        assertFalse(isProgrammingRouteAllowed(provider.copy(useResponseApi = true), free))
        assertFalse(isProgrammingRouteAllowed(provider.copy(chatCompletionsPath = "/other"), free))
    }

    @Test fun customEndpointsNeedExplicitVerificationAndCostConsent() {
        for (url in listOf("https://other.test/v1", "https://api.groq.com/openai/v1")) {
            val custom = provider.copy(baseUrl = url)
            val settings = Settings(
                providers = listOf(custom),
                freeRouter = FreeRouterSettings(approvedModelIds = listOf(free.id)),
            )
            assertTrue(settings.programmingRoutes().isEmpty())
        }
    }

    @Test fun explicitApprovalsStillMatterWhenTheGlobalRouterIsOff() {
        val paid = free.copy(id = Uuid.random(), modelId = "qwen/paid")
        val p = provider.copy(models = listOf(free, paid))
        val unapproved = Settings(providers = listOf(p))
        assertTrue(unapproved.programmingRoutes().isEmpty())
        val approved = unapproved.copy(freeRouter = FreeRouterSettings(enabled = false, approvedModelIds = listOf(free.id, paid.id)))
            .recordModelCheck(ModelCheck(p.id, free.id, modelCheckFingerprint(p, free), 1L, 1L, ModelCheckStatus.OK, toolsVerified = true))
        assertEquals(listOf(free.id), approved.programmingRoutes().map { it.model.id })
    }

    @Test fun explicitlyConsentedModelsKeepCostConsentAndVerifiedToolSupport() {
        val paid = Model(modelId = "llama-3.3-70b-versatile", chatSupported = true)
        val groq = ProviderSetting.OpenAI(
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "fake-test-key",
            models = listOf(paid),
        )
        val initial = Settings(
            providers = listOf(groq),
            freeRouter = FreeRouterSettings(approvedModelIds = listOf(paid.id), costAcknowledgements = listOf(ModelCostAcknowledgement(groq.id, paid.id, modelCheckFingerprint(groq, paid)))),
        )
        val check = ModelCheck(
            providerId = groq.id,
            modelId = paid.id,
            fingerprint = modelCheckFingerprint(groq, paid),
            checkedAt = 1L,
            durationMs = 1L,
            status = ModelCheckStatus.OK,
            toolsVerified = true,
        )

        val consented = initial.recordModelCheck(check)
        val updatedProvider = consented.providers.single()
        val updatedModel = updatedProvider.models.single()
        assertTrue(ModelAbility.TOOL in updatedModel.abilities)
        assertTrue(consented.hasCostAcknowledged(updatedProvider, updatedModel))
        val withoutConsent = initial.copy(freeRouter = initial.freeRouter.copy(costAcknowledgements = emptyList())).recordModelCheck(check)
        assertTrue(withoutConsent.programmingRoutes().isEmpty())
        assertEquals(listOf(paid.id), consented.programmingRoutes().map { it.model.id })
    }

    @Test fun startingAndLeavingTheModeOnlyChangeTheCurrentConversation() {
        val assistant = Assistant(chatModelId = free.id)
        val settings = Settings(assistants = listOf(assistant), assistantId = assistant.id, providers = listOf(provider))
        val draft = Conversation(assistantId = assistant.id, messageNodes = emptyList())
        val project = Uuid.random()
        val active = draft.withProgrammingProject(settings, project)
        assertTrue(active.config!!.programmingMode)
        assertEquals(project, settings.getAssistantOf(active).workspaceId)
        assertNull(settings.assistants.single().workspaceId)
        assertNull(draft.config)
        val resumed = Json.decodeFromString<Conversation>(Json.encodeToString(active))
        assertEquals(active.config, resumed.config)
        val changed = resumed.withAssistantUpdate(settings.getAssistantOf(resumed).copy(temperature = 0.2f), settings)
        assertTrue(changed.config!!.programmingMode)
        val inactive = changed.withProgrammingProject(settings, null)
        assertFalse(inactive.config!!.programmingMode)
        assertNull(inactive.config.workspaceId)
        assertEquals(active.messageNodes, inactive.messageNodes)
    }

    @Test fun changingTheProjectClearsItsOldWorkingDirectory() {
        val assistant = Assistant(chatModelId = free.id)
        val settings = Settings(assistants = listOf(assistant), assistantId = assistant.id, providers = listOf(provider))
        val a = Uuid.random()
        val active = Conversation(assistantId = assistant.id, messageNodes = emptyList()).withProgrammingProject(settings, a).copy(workspaceCwd = "/workspace/old")
        assertEquals("/workspace/old", active.withProgrammingProject(settings, a).workspaceCwd)
        assertNull(active.withProgrammingProject(settings, Uuid.random()).workspaceCwd)
        assertNull(active.withProgrammingProject(settings, null).workspaceCwd)
    }

    @Test fun oldConversationConfigsDoNotEnableProgramming() {
        assertFalse(Json.decodeFromString<ConversationConfig>("{}").programmingMode)
    }

    @Test fun legacyProjectOverridesCannotSilentlyApproveProgrammingActions() {
        val overrides = mapOf("workspace_shell" to false, "workspace_write_file" to false, "workspace_edit_file" to false)
        for (name in overrides.keys) assertTrue(resolveWorkspaceToolApproval(name, overrides, forceApprovals = true))
        assertFalse(resolveWorkspaceToolApproval("workspace_read_file", overrides, forceApprovals = true))
        assertFalse(resolveWorkspaceToolApproval("workspace_shell", overrides))
    }

    @Test fun theDispatchBoundaryNeverCallsPaidOrUnknownRoutes() = runBlocking {
        val paid = free.copy(id = Uuid.random(), modelId = "vendor/paid")
        var calls = 0
        val fake = object : Provider<ProviderSetting> {
            override suspend fun listModels(providerSetting: ProviderSetting) = emptyList<Model>()
            override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult = error("unused")
            override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams) = flow<StreamChunk> {
                assertTrue(isProgrammingRouteAllowed(providerSetting, params.model))
                calls++
                emit(StreamChunk.TextDelta("text", "ok"))
            }
        }
        val router = FreeRoutingProvider(
            resolve = { fake },
            candidates = Settings(
                providers = listOf(provider.copy(models = listOf(free, paid))),
                freeRouter = FreeRouterSettings(approvedModelIds = listOf(free.id, paid.id)),
            ).recordModelCheck(ModelCheck(provider.id, free.id, modelCheckFingerprint(provider, free), 1L, 1L, ModelCheckStatus.OK, toolsVerified = true)).programmingRoutes(),
            status = MutableStateFlow(null),
            allowed = ::isProgrammingRouteAllowed,
        )
        assertEquals(1, router.streamText(provider, emptyList(), TextGenerationParams(free)).toList().size)
        assertEquals(1, calls)
        assertEquals(free.id, router.activeModelId)
    }

    @Test fun auxiliaryAssistantSettingsAreNotInheritedByProgramming() {
        val original = Assistant(enableMemory = true, enableWebSearch = true, enableRecentChatsReference = true, customHeaders = listOf(CustomHeader("X-Test", "fake")))
        val restricted = original.forProgramming()
        assertFalse(restricted.enableMemory)
        assertFalse(restricted.enableWebSearch)
        assertFalse(restricted.enableRecentChatsReference)
        assertTrue(restricted.localTools.isEmpty())
        assertTrue(restricted.customHeaders.isEmpty())
        assertTrue(original.enableMemory)
    }
}
