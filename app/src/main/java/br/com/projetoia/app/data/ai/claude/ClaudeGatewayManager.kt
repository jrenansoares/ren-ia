package br.com.projetoia.app.data.ai.claude

import br.com.projetoia.app.AppScope
import br.com.projetoia.app.data.ai.FreeRoutingProvider
import br.com.projetoia.app.data.ai.RoutingObserver
import br.com.projetoia.app.data.ai.UsageRecorder
import br.com.projetoia.app.data.ai.UsageSource
import br.com.projetoia.app.data.ai.UsageTag
import br.com.projetoia.app.data.ai.isAutomaticRouteCandidate
import br.com.projetoia.app.data.ai.programmingRoutes
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.datastore.findModelById
import br.com.projetoia.app.data.datastore.getCurrentChatModel
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient

interface GatewaySession {
    val url: String
    val token: String
    fun toEnvironment(): Map<String, String>
    fun release()
}

class ClaudeGatewayManager(
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val okHttpClient: OkHttpClient,
) {
    private val mutex = Mutex()
    private var activeCount = 0
    private var activeGateway: ClaudeLocalGateway? = null
    private var activeUrl: String? = null
    private var gatewayScope: CoroutineScope? = null
    private var activeTarget: ClaudeExecutionTarget = ClaudeExecutionTarget.FreeRouterFallback()

    val isRunning: Boolean
        get() = activeGateway != null

    suspend fun acquireSession(preferredModelId: Uuid? = null): GatewaySession = mutex.withLock {
        activeCount++
        if (activeGateway == null) {
            val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob())
            gatewayScope = scope
            val initialSettings = settingsStore.awaitLoaded()
            val selectedModel = preferredModelId?.let { initialSettings.findModelById(it) }
                ?: initialSettings.getCurrentChatModel()
            val target = resolveClaudeExecutionTarget(initialSettings, selectedModel)
            activeTarget = target

            val gateway = when (target) {
                is ClaudeExecutionTarget.OpenRouterClaude -> {
                    val forwarder = AnthropicTransparentForwarder(
                        client = okHttpClient,
                        option = target.option,
                        familyOverrides = target.familyOverrides,
                        onResponseTelemetry = { modelName, turnUsage ->
                            usageRecorder.recordExternal(
                                tag = UsageTag(UsageSource.CHAT, Uuid.random(), Uuid.random()),
                                providerSetting = target.option.providerSetting,
                                modelId = target.option.model.id,
                                modelName = modelName ?: target.option.model.modelId,
                                usage = turnUsage,
                            )
                        },
                    )
                    ClaudeLocalGateway(
                        owner = scope,
                        availableModels = nativeClaudeModels(initialSettings).map { it.modelId },
                        forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
                    ) {
                        error("Encaminhamento direto OpenRouter indisponível neste terminal")
                    }
                }
                is ClaudeExecutionTarget.DirectAnthropicClaude -> {
                    val forwarder = AnthropicTransparentForwarder(
                        client = okHttpClient,
                        option = target.option,
                        familyOverrides = target.familyOverrides,
                        onResponseTelemetry = { modelName, turnUsage ->
                            usageRecorder.recordExternal(
                                tag = UsageTag(UsageSource.CHAT, Uuid.random(), Uuid.random()),
                                providerSetting = target.option.providerSetting,
                                modelId = target.option.model.id,
                                modelName = modelName ?: target.option.model.modelId,
                                usage = turnUsage,
                            )
                        },
                    )
                    ClaudeLocalGateway(
                        owner = scope,
                        availableModels = nativeClaudeModels(initialSettings).map { it.modelId },
                        forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
                    ) {
                        error("Encaminhamento direto Anthropic indisponível neste terminal")
                    }
                }
                is ClaudeExecutionTarget.FreeRouterFallback -> {
                    ClaudeLocalGateway(scope) { request ->
                        val settings = settingsStore.awaitLoaded()
                        val needsVision = request.messages.any { m ->
                            m.parts.any { it is UIMessagePart.Image } ||
                                m.getTools().any { t -> t.output.any { it is UIMessagePart.Image } }
                        }
                        val candidates = settings.programmingRoutes(
                            task = "",
                            preferredModelId = preferredModelId,
                        ).filter { !needsVision || Modality.IMAGE in it.model.inputModalities }

                        val first = candidates.firstOrNull()
                            ?: error("Nenhuma IA gratuita compatível autorizada para o Claude Code")

                        val statusFlow = MutableStateFlow<String?>(null)
                        val router = FreeRoutingProvider(
                            resolve = { setting: ProviderSetting ->
                                usageRecorder.wrap(
                                    providerManager.getProviderByType(setting),
                                    UsageTag(UsageSource.CHAT, Uuid.random(), Uuid.random()),
                                )
                            },
                            candidates = candidates,
                            status = statusFlow,
                            allowed = ::isAutomaticRouteCandidate,
                            observe = RoutingObserver(settingsStore)::record,
                        )

                        val result = router.generateText(
                            first.provider,
                            request.messages,
                            TextGenerationParams(
                                model = first.model.copy(
                                    contextLength = 0,
                                    inputModalities = if (needsVision) {
                                        listOf(Modality.TEXT, Modality.IMAGE)
                                    } else {
                                        listOf(Modality.TEXT)
                                    },
                                ),
                                tools = request.tools,
                                maxTokens = request.maxTokens,
                            ),
                        )
                        AnthropicBridgeCodec.response(result, request.requestedModel)
                    }
                }
            }
            val url = gateway.start()
            activeGateway = gateway
            activeUrl = url
        }

        val url = requireNotNull(activeUrl)
        val token = requireNotNull(activeGateway?.token)
        val sessionTarget = activeTarget

        object : GatewaySession {
            override val url: String = url
            override val token: String = token

            override fun toEnvironment(): Map<String, String> {
                val (primaryModel, sonnetModel, opusModel, haikuModel) = when (sessionTarget) {
                    is ClaudeExecutionTarget.OpenRouterClaude -> {
                        val base = sessionTarget.option.model.modelId
                        listOf(
                            base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.SONNET] ?: base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.OPUS] ?: base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.HAIKU] ?: base,
                        )
                    }
                    is ClaudeExecutionTarget.DirectAnthropicClaude -> {
                        val base = sessionTarget.option.model.modelId.removePrefix("anthropic/")
                        listOf(
                            base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.SONNET] ?: base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.OPUS] ?: base,
                            sessionTarget.familyOverrides[ClaudeModelFamily.HAIKU] ?: base,
                        )
                    }
                    is ClaudeExecutionTarget.FreeRouterFallback ->
                        listOf("projeto-free", "projeto-free", "projeto-free", "projeto-free")
                }
                return mapOf(
                    "ANTHROPIC_BASE_URL" to url,
                    "ANTHROPIC_API_KEY" to token,
                    "ANTHROPIC_AUTH_TOKEN" to token,
                    "ANTHROPIC_MODEL" to primaryModel,
                    "ANTHROPIC_DEFAULT_SONNET_MODEL" to sonnetModel,
                    "ANTHROPIC_DEFAULT_HAIKU_MODEL" to haikuModel,
                    "ANTHROPIC_DEFAULT_OPUS_MODEL" to opusModel,
                    "CLAUDE_CODE_SUBAGENT_MODEL" to haikuModel,
                    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC" to "1",
                    "DISABLE_AUTOUPDATER" to "1",
                    "DISABLE_UPDATES" to "1",
                    "DISABLE_TELEMETRY" to "1",
                    "DISABLE_ERROR_REPORTING" to "1",
                    "NO_PROXY" to "127.0.0.1,localhost",
                    "API_TIMEOUT_MS" to "180000",
                )
            }

            private var released = false

            override fun release() {
                if (released) return
                released = true
                appScope.launch {
                    mutex.withLock {
                        activeCount--
                        if (activeCount <= 0) {
                            activeCount = 0
                            activeGateway?.close()
                            activeGateway = null
                            activeUrl = null
                            gatewayScope = null
                        }
                    }
                }
            }
        }
    }
}
