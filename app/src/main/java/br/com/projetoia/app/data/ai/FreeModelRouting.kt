package br.com.projetoia.app.data.ai

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderHttpException
import kotlin.time.TimeSource
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.supportsConversation
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import br.com.projetoia.app.data.datastore.Settings
import kotlin.uuid.Uuid

internal data class FreeRoute(
    val provider: ProviderSetting,
    val model: Model,
    /** Live probe can override missing or stale catalog metadata. */
    val toolsVerified: Boolean = false,
)

/** Explicit user-approved models only; OpenRouter additionally requires a free model ID. */
internal fun isAutomaticRouteCandidate(provider: ProviderSetting, model: Model): Boolean {
    if (!canProbeModel(provider, model)) return false
    val api = provider as? ProviderSetting.ApiKey ?: return provider is ProviderSetting.Account
    val host = runCatching { java.net.URI(api.baseUrl).host?.lowercase(java.util.Locale.ROOT) }.getOrNull()
    return host != "openrouter.ai" || isConfirmedFreeProbe(provider, model)
}

/** User-requested provider order for failover; a selected model always stays first. */
internal fun providerFallbackPriority(provider: ProviderSetting): Int {
    if (provider is ProviderSetting.Google && !provider.vertexAI) return 0
    val host = (provider as? ProviderSetting.ApiKey)?.let {
        runCatching { java.net.URI(it.baseUrl).host?.lowercase(java.util.Locale.ROOT) }.getOrNull()
    }
    return when (host) {
        "api.deepseek.com" -> 1
        "integrate.api.nvidia.com" -> 2
        "api.groq.com" -> 3
        "openrouter.ai" -> 4
        else -> 5
    }
}

internal fun modelStabilityScore(model: Model): Int {
    val id = model.modelId.lowercase(java.util.Locale.ROOT)
    var score = 0
    when {
        id == "gemini-2.5-flash" || id.endsWith("/gemini-2.5-flash") -> score += 90
        id == "gemini-2.5-flash-lite" || id.endsWith("/gemini-2.5-flash-lite") -> score += 85
        id == "gemini-2.0-flash" || id.endsWith("/gemini-2.0-flash") -> score += 82
        id == "gemini-2.0-flash-lite" || id.endsWith("/gemini-2.0-flash-lite") -> score += 78
        id == "gemini-flash-latest" || id == "gemini-flash-lite-latest" -> score += 75
        id == "gemini-1.5-flash" || id == "gemini-1.5-flash-latest" -> score += 72
        id == "gemini-2.5-pro" || id.endsWith("/gemini-2.5-pro") -> score += 68
        id == "llama-3.3-70b-versatile" || id.endsWith("/llama-3.3-70b-instruct") || id.contains("llama-3.3-70b") -> score += 88
        id == "llama-3.1-8b-instant" || id.endsWith("/llama-3.1-8b-instruct") || id.contains("llama-3.1-8b") -> score += 80
        id.contains("llama-4-scout") || id.contains("llama-4-maverick") -> score += 78
        id.contains("qwen-qwq-32b") || id.contains("qwen3-32b") || id.contains("qwen2.5-coder-32b") || id.contains("qwen2.5-72b") -> score += 76
        id == "deepseek-chat" || id.endsWith("/deepseek-v3") || id.contains("deepseek-r1-distill-llama-70b") -> score += 84
        id == "deepseek-reasoner" || id.endsWith("/deepseek-r1") -> score += 74
        id.contains("nemotron-ultra") || id.contains("nemotron-super") || id.contains("llama-3.1-nemotron-70b") -> score += 75
        id.contains("gemma2-9b-it") || id.contains("gemma-3-27b-it") || id.contains("gemma-2-27b-it") -> score += 70
        id.contains("mistral-small-latest") || id.contains("codestral-latest") || id.contains("mistral-large-latest") -> score += 75
        id.contains("command-r-plus") || id.contains("command-r") || id.contains("command-a") -> score += 72
        id.contains("claude-3-7-sonnet") || id.contains("claude-3-5-sonnet") || id.contains("claude-3-5-haiku") || id.contains("claude-sonnet-4") -> score += 80
        id == "gpt-4o-mini" || id == "gpt-4o" || id == "gpt-4.1-mini" || id == "gpt-4.1" -> score += 80
        id == "openrouter/free" -> score += 85
        id.endsWith(":free") -> score += 45
    }
    if (listOf("argon", "3.8", "experimental", "-exp", "preview", "customtools", "thinking-exp", "learnlm", "-001", "beta").any { it in id }) {
        score -= 60
    }
    return score
}

internal fun routeScore(model: Model, task: String): Int {
    val name = (model.modelId + " " + model.displayName).lowercase(java.util.Locale.ROOT)
    val query = task.lowercase(java.util.Locale.ROOT)
    var score = 0
    if (listOf("código", "codigo", "program", "github", "reposit", "script", "bug").any { it in query }) {
        if (listOf("coder", "code", "coding").any { it in name }) score += 20
        if (ModelAbility.TOOL in model.abilities) score += 10
    }
    if (listOf("planej", "analise", "análise", "racioc", "matem").any { it in query } && ModelAbility.REASONING in model.abilities) score += 10
    return score
}

internal fun routeProfileScore(
    model: Model,
    profile: br.com.projetoia.app.data.datastore.RoutingProfile,
    task: String,
): Int {
    val id = model.modelId.lowercase(java.util.Locale.ROOT)
    val name = (model.modelId + " " + model.displayName).lowercase(java.util.Locale.ROOT)
    var score = modelStabilityScore(model)

    when (profile) {
        br.com.projetoia.app.data.datastore.RoutingProfile.AUTO -> {
            score += routeScore(model, task)
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.CODING -> {
            if (ModelAbility.TOOL in model.abilities) score += 30
            if (listOf("coder", "code", "coding", "codestral", "dev").any { it in name }) score += 40
            if (listOf("llama-3.3-70b", "deepseek-chat", "deepseek-coder", "qwen2.5-coder", "qwen-qwq", "gemini-2.5-flash", "claude-3-5-sonnet", "claude-sonnet-4").any { it in id }) score += 45
            if (model.contextLength >= 32_000) score += 15
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.REASONING -> {
            if (ModelAbility.REASONING in model.abilities) score += 60
            if (listOf("reasoner", "r1", "qwq", "thinking", "reasoning", "nemotron-ultra", "o1", "o3").any { it in id }) score += 50
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.VISION -> {
            if (Modality.IMAGE in model.inputModalities) score += 60
            if (listOf("vision", "vl", "flash", "4o", "sonnet").any { it in id }) score += 30
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.VIDEO -> {
            if (Modality.VIDEO in model.inputModalities || model.contextLength >= 500_000) score += 70
            if (listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-2.5-pro").any { it in id }) score += 50
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.FAST -> {
            if (listOf("8b", "lite", "fast", "instant", "small", "mini", "haiku").any { it in id }) score += 60
            if (listOf("70b", "405b", "reasoner", "r1", "large").any { it in id }) score -= 30
        }
        br.com.projetoia.app.data.datastore.RoutingProfile.CUSTOM -> {
            // Preserves stability score, ordering by customPriorityModelIds in comparator
        }
    }
    return score
}

internal fun Settings.automaticRoutes(
    task: String,
    preferredModelId: Uuid? = null,
    profile: br.com.projetoia.app.data.datastore.RoutingProfile = freeRouter.activeProfile,
): List<FreeRoute> = providers.flatMap { provider ->
    provider.models.filter {
        it.id in freeRouter.approvedModelIds && it.id !in freeRouter.excludedModelIds &&
            isAutomaticRouteCandidate(provider, it) &&
            hasPassedModelCheck(provider, it) && catalogAllows(provider, it) && providerReady(provider) &&
            (routeHealth(provider, it)?.cooldownUntil ?: 0) <= System.currentTimeMillis() &&
            (isConfirmedFreeProbe(provider, it) || hasCostAcknowledged(provider, it)) &&
            (profile != br.com.projetoia.app.data.datastore.RoutingProfile.VISION || Modality.IMAGE in it.inputModalities) &&
            (profile != br.com.projetoia.app.data.datastore.RoutingProfile.VIDEO || (Modality.VIDEO in it.inputModalities || it.contextLength >= 200_000)) &&
            (profile != br.com.projetoia.app.data.datastore.RoutingProfile.CODING || ModelAbility.TOOL in it.abilities || modelCheck(provider, it)?.toolsVerified == true)
    }
        .map { model -> FreeRoute(provider, model, modelCheck(provider, model)?.toolsVerified == true) }
}.sortedWith(
    compareBy<FreeRoute> { if (it.model.id == preferredModelId) 0 else 1 }
        .thenBy {
            if (profile == br.com.projetoia.app.data.datastore.RoutingProfile.CUSTOM && it.model.id in freeRouter.customPriorityModelIds) {
                freeRouter.customPriorityModelIds.indexOf(it.model.id)
            } else {
                providerFallbackPriority(it.provider)
            }
        }
        .thenByDescending {
            routeProfileScore(it.model, profile, task) + routeLearningScore(routeHealth(it.provider, it.model))
        }
        .thenBy { freeRouter.approvedModelIds.indexOf(it.model.id) },
)

internal fun routingFailureCode(error: Throwable): Int? =
    (error as? ProviderHttpException)?.statusCode ?: error.cause?.let { routingFailureCode(it) } ?: Regex("(?<![0-9])(401|402|403|404|408|429|500|502|503|504)(?![0-9])")
        .find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

/** Bounded failover before any streaming output; never replays partially emitted tool calls. */
internal class FreeRoutingProvider(
    private val resolve: (ProviderSetting) -> Provider<ProviderSetting>,
    private val candidates: List<FreeRoute>,
    private val status: MutableStateFlow<String?>,
    private val allowed: (ProviderSetting, Model) -> Boolean = ::isAutomaticRouteCandidate,
    private val observe: suspend (FreeRoute, RouteObservation) -> Unit = { _, _ -> },
) : Provider<ProviderSetting> {
    var activeModelId: Uuid? = null
        private set
    private var preferred: FreeRoute? = null
    private val blockedProviders = mutableSetOf<Uuid>()

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    override suspend fun listModels(providerSetting: ProviderSetting): List<Model> = candidates.map { it.model }

    private fun routes(params: TextGenerationParams): List<FreeRoute> {
        val now = System.currentTimeMillis()
        return candidates.filter {
            allowed(it.provider, it.model) &&
                (cooldownUntil[cooldownKey(it)] ?: 0) <= now &&
                (params.tools.isEmpty() || ModelAbility.TOOL in it.model.abilities || it.toolsVerified) &&
                it.model.inputModalities.containsAll(params.model.inputModalities) &&
                it.model.contextLength >= params.model.contextLength
        }.sortedBy { if (it.model.id == preferred?.model?.id) 0 else 1 }.take(6)
    }

    private fun cooldownKey(route: FreeRoute) = "${route.provider.id}:${route.model.id}:${providerConnectionFingerprint(route.provider)}"

    private fun failed(route: FreeRoute, error: Throwable): Boolean {
        if (error is CancellationException) throw error
        val code = routingFailureCode(error)
        if (error !is IOException && code == null) return false
        val delay = (error as? ProviderHttpException)?.retryAfterMs?.coerceIn(1_000L, 86_400_000L) ?: when (code) {
            401, 402, 403 -> 15 * 60_000L
            404 -> 12 * 60 * 60_000L
            429 -> 60_000L
            else -> 15_000L
        }
        cooldownUntil[cooldownKey(route)] = System.currentTimeMillis() + delay
        if (code == 401 || code == 402) blockedProviders += route.provider.id
        return true
    }

    private suspend fun report(route: FreeRoute, observation: RouteObservation) {
        try { observe(route, observation) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Statistics cannot fail or replay a completed inference request. */ }
    }

    private fun requestModel(route: FreeRoute): Model = if (route.toolsVerified) {
        route.model.copy(abilities = (route.model.abilities + ModelAbility.TOOL).distinct())
    } else route.model

    private fun select(route: FreeRoute) {
        activeModelId = route.model.id
        preferred = route
        status.value = "Usando ${route.provider.name} · ${route.model.displayName.ifBlank { route.model.modelId }}"
    }

    override suspend fun generateText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult {
        var last: Throwable? = null
        for (route in routes(params)) {
            if (route.provider.id in blockedProviders) continue
            select(route)
            val started = TimeSource.Monotonic.markNow()
            try {
                val result = resolve(route.provider).generateText(route.provider, messages, params.copy(model = requestModel(route), customHeaders = route.model.customHeaders, customBody = route.model.customBodies))
                report(route, RouteObservation(true, started.elapsedNow().inWholeMilliseconds))
                return result
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                report(route, RouteObservation(false, started.elapsedNow().inWholeMilliseconds, routingFailureCode(error), (error as? ProviderHttpException)?.retryAfterMs))
                if (!failed(route, error)) throw error
                last = error
            }
        }
        throw IllegalStateException("Nenhuma IA gratuita compatível está disponível. O trabalho foi preservado; tente novamente mais tarde.", last)
    }

    override suspend fun streamText(providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> = flow {
        var last: Throwable? = null
        for (route in routes(params)) {
            if (route.provider.id in blockedProviders) continue
            select(route)
            var emitted = false
            var firstOutputMs: Long? = null
            val started = TimeSource.Monotonic.markNow()
            try {
                resolve(route.provider).streamText(route.provider, messages, params.copy(model = requestModel(route), customHeaders = route.model.customHeaders, customBody = route.model.customBodies)).collect { chunk ->
                    emitted = true
                    if (firstOutputMs == null) firstOutputMs = started.elapsedNow().inWholeMilliseconds
                    emit(chunk)
                }
                report(route, RouteObservation(true, firstOutputMs ?: started.elapsedNow().inWholeMilliseconds))
                return@flow
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                report(route, RouteObservation(false, started.elapsedNow().inWholeMilliseconds, routingFailureCode(error), (error as? ProviderHttpException)?.retryAfterMs))
                if (emitted || !failed(route, error)) throw error
                last = error
            }
        }
        throw IllegalStateException("Nenhuma IA gratuita compatível está disponível. O trabalho foi preservado; tente novamente mais tarde.", last)
    }
}
