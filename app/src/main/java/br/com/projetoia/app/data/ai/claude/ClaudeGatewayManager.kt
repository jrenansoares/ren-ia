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
) {
    private val mutex = Mutex()
    private var activeCount = 0
    private var activeGateway: ClaudeLocalGateway? = null
    private var activeUrl: String? = null
    private var gatewayScope: CoroutineScope? = null

    val isRunning: Boolean
        get() = activeGateway != null

    suspend fun acquireSession(): GatewaySession = mutex.withLock {
        activeCount++
        if (activeGateway == null) {
            val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob())
            gatewayScope = scope
            val gateway = ClaudeLocalGateway(scope) { request ->
                val settings = settingsStore.awaitLoaded()
                val needsVision = request.messages.any { m ->
                    m.parts.any { it is UIMessagePart.Image } ||
                        m.getTools().any { t -> t.output.any { it is UIMessagePart.Image } }
                }
                val candidates = settings.programmingRoutes(
                    task = "",
                    preferredModelId = null,
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
            val url = gateway.start()
            activeGateway = gateway
            activeUrl = url
        }

        val url = requireNotNull(activeUrl)
        val token = requireNotNull(activeGateway?.token)

        object : GatewaySession {
            override val url: String = url
            override val token: String = token

            override fun toEnvironment(): Map<String, String> = mapOf(
                "ANTHROPIC_BASE_URL" to url,
                "ANTHROPIC_API_KEY" to token,
                "ANTHROPIC_AUTH_TOKEN" to token,
                "ANTHROPIC_MODEL" to "projeto-free",
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to "projeto-free",
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to "projeto-free",
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to "projeto-free",
                "CLAUDE_CODE_SUBAGENT_MODEL" to "projeto-free",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC" to "1",
                "DISABLE_AUTOUPDATER" to "1",
                "DISABLE_UPDATES" to "1",
                "DISABLE_TELEMETRY" to "1",
                "DISABLE_ERROR_REPORTING" to "1",
                "NO_PROXY" to "127.0.0.1,localhost",
                "API_TIMEOUT_MS" to "180000",
            )

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
