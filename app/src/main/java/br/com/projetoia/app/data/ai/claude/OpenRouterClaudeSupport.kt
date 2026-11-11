package br.com.projetoia.app.data.ai.claude

import br.com.projetoia.app.data.datastore.Settings
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import java.net.URI
import java.util.Locale
import kotlin.uuid.Uuid

internal enum class ClaudeModelFamily {
    SONNET,
    OPUS,
    HAIKU,
    FABLE,
}

/**
 * Famílias Claude habilitadas no roteamento explícito via OpenRouter:
 * Sonnet, Opus, Haiku e Fable (Estágio 3 após validação do gateway, ferramentas e sessão).
 */
internal val STAGE_ENABLED_CLAUDE_FAMILIES: Set<ClaudeModelFamily> = setOf(
    ClaudeModelFamily.SONNET,
    ClaudeModelFamily.OPUS,
    ClaudeModelFamily.HAIKU,
    ClaudeModelFamily.FABLE,
)

internal data class OpenRouterClaudeOption(
    val providerSetting: ProviderSetting.OpenAI,
    val model: Model,
    val family: ClaudeModelFamily,
    val anthropicBaseUrl: String,
    val messagesUrl: String,
    val apiKey: String,
)

internal data class DirectAnthropicClaudeOption(
    val providerSetting: ProviderSetting.Claude,
    val model: Model,
    val family: ClaudeModelFamily,
    val messagesUrl: String,
    val apiKey: String,
)

internal sealed interface ClaudeExecutionTarget {
    data class OpenRouterClaude(
        val option: OpenRouterClaudeOption,
        val familyOverrides: Map<ClaudeModelFamily, String>,
    ) : ClaudeExecutionTarget

    data class DirectAnthropicClaude(
        val option: DirectAnthropicClaudeOption,
        val familyOverrides: Map<ClaudeModelFamily, String>,
    ) : ClaudeExecutionTarget

    data class FreeRouterFallback(
        val preferredModelId: Uuid? = null,
    ) : ClaudeExecutionTarget
}

internal fun isOpenRouterProvider(providerSetting: ProviderSetting): Boolean {
    if (providerSetting !is ProviderSetting.OpenAI) return false
    val host = runCatching {
        URI(providerSetting.baseUrl.trim()).host?.lowercase(Locale.ROOT)
    }.getOrNull() ?: return false
    return host == "openrouter.ai" || host.endsWith(".openrouter.ai")
}

internal fun classifyOpenRouterClaudeFamily(modelId: String): ClaudeModelFamily? {
    val normalized = modelId.trim().lowercase(Locale.ROOT)
    if (!normalized.startsWith("anthropic/claude")) return null
    if (normalized.contains(":batch")) return null
    return when {
        normalized.contains("sonnet") -> ClaudeModelFamily.SONNET
        normalized.contains("opus") -> ClaudeModelFamily.OPUS
        normalized.contains("haiku") -> ClaudeModelFamily.HAIKU
        normalized.contains("fable") -> ClaudeModelFamily.FABLE
        else -> null
    }
}

internal fun isOpenRouterClaudeModelId(
    modelId: String,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): Boolean {
    val family = classifyOpenRouterClaudeFamily(modelId) ?: return false
    return family in enabledFamilies
}

internal fun openRouterAnthropicBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "https://openrouter.ai/api"
    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val path = parsed?.path?.trimEnd('/').orEmpty()
    if (parsed != null && parsed.scheme != null && parsed.authority != null && path.isEmpty()) {
        return "${parsed.scheme}://${parsed.authority}/api"
    }
    return trimmed.removeSuffix("/v1").trimEnd('/')
}

internal fun openRouterAnthropicMessagesUrl(baseUrl: String): String {
    return "${openRouterAnthropicBaseUrl(baseUrl)}/v1/messages"
}

internal fun firstConfiguredApiKey(rawKey: String): String =
    rawKey.split(',').asSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

internal fun openRouterApiKey(providerSetting: ProviderSetting.OpenAI): String {
    return firstConfiguredApiKey(providerSetting.apiKey)
}

internal fun classifyAnthropicClaudeFamily(modelId: String): ClaudeModelFamily? {
    val normalized = modelId.trim().lowercase(Locale.ROOT)
    if (normalized.contains(":batch")) return null
    val base = normalized.removePrefix("anthropic/")
    if (!base.startsWith("claude")) return null
    return when {
        base.contains("sonnet") -> ClaudeModelFamily.SONNET
        base.contains("opus") -> ClaudeModelFamily.OPUS
        base.contains("haiku") -> ClaudeModelFamily.HAIKU
        base.contains("fable") -> ClaudeModelFamily.FABLE
        else -> null
    }
}

internal fun anthropicMessagesUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "https://api.anthropic.com/v1/messages"
    if (trimmed.endsWith("/messages")) return trimmed
    if (trimmed.endsWith("/v1")) return "$trimmed/messages"
    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val path = parsed?.path?.trimEnd('/').orEmpty()
    return if (parsed != null && parsed.scheme != null && parsed.authority != null && path.isEmpty()) {
        "${parsed.scheme}://${parsed.authority}/v1/messages"
    } else {
        "$trimmed/v1/messages"
    }
}

internal fun directAnthropicClaudeOptions(
    settings: Settings,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): List<DirectAnthropicClaudeOption> {
    val options = mutableListOf<DirectAnthropicClaudeOption>()
    for (provider in settings.providers) {
        if (provider !is ProviderSetting.Claude || !provider.enabled) continue
        val key = firstConfiguredApiKey(provider.apiKey)
        if (key.isBlank()) continue
        val messagesUrl = anthropicMessagesUrl(provider.baseUrl)
        for (model in provider.models) {
            if (model.type != ModelType.CHAT) continue
            val family = classifyAnthropicClaudeFamily(model.modelId) ?: continue
            if (family !in enabledFamilies) continue
            options += DirectAnthropicClaudeOption(
                providerSetting = provider,
                model = model,
                family = family,
                messagesUrl = messagesUrl,
                apiKey = key,
            )
        }
    }
    return options
}

internal fun findDirectAnthropicClaudeOption(
    settings: Settings,
    model: Model?,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): DirectAnthropicClaudeOption? {
    if (model == null) return null
    val options = directAnthropicClaudeOptions(settings, enabledFamilies)
    return options.firstOrNull { it.model.id == model.id }
        ?: options.firstOrNull { it.model.modelId.equals(model.modelId, ignoreCase = true) }
}

internal fun openRouterClaudeOptions(
    settings: Settings,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): List<OpenRouterClaudeOption> {
    val options = mutableListOf<OpenRouterClaudeOption>()
    for (provider in settings.providers) {
        if (provider !is ProviderSetting.OpenAI) continue
        if (!provider.enabled || !isOpenRouterProvider(provider)) continue
        val key = openRouterApiKey(provider)
        if (key.isBlank()) continue
        val baseUrl = openRouterAnthropicBaseUrl(provider.baseUrl)
        val messagesUrl = openRouterAnthropicMessagesUrl(provider.baseUrl)
        for (model in provider.models) {
            if (model.type != ModelType.CHAT) continue
            val family = classifyOpenRouterClaudeFamily(model.modelId) ?: continue
            if (family !in enabledFamilies) continue
            options += OpenRouterClaudeOption(
                providerSetting = provider,
                model = model,
                family = family,
                anthropicBaseUrl = baseUrl,
                messagesUrl = messagesUrl,
                apiKey = key,
            )
        }
    }
    return options
}

internal fun findOpenRouterClaudeOption(
    settings: Settings,
    model: Model?,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): OpenRouterClaudeOption? {
    if (model == null) return null
    val options = openRouterClaudeOptions(settings, enabledFamilies)
    return options.firstOrNull { it.model.id == model.id }
        ?: options.firstOrNull { it.model.modelId.equals(model.modelId, ignoreCase = true) }
}

internal fun nativeClaudeModels(
    settings: Settings,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): List<Model> = buildList {
    directAnthropicClaudeOptions(settings, enabledFamilies).forEach { add(it.model) }
    openRouterClaudeOptions(settings, enabledFamilies).forEach { add(it.model) }
}

internal fun hasNativeClaudeOption(
    settings: Settings,
    model: Model?,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): Boolean = findDirectAnthropicClaudeOption(settings, model, enabledFamilies) != null ||
    findOpenRouterClaudeOption(settings, model, enabledFamilies) != null

internal fun resolveClaudeExecutionTarget(
    settings: Settings,
    preferredModel: Model?,
    preferOpenRouterClaudeWhenConsultantsActive: Boolean = false,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): ClaudeExecutionTarget {
    val directOptions = directAnthropicClaudeOptions(settings, enabledFamilies)
    val openRouterOptions = openRouterClaudeOptions(settings, enabledFamilies)
    val matchedDirect = preferredModel?.let { target ->
        directOptions.firstOrNull { it.model.id == target.id }
    }
    if (matchedDirect != null) {
        return ClaudeExecutionTarget.DirectAnthropicClaude(
            option = matchedDirect,
            familyOverrides = buildDirectFamilyOverrides(matchedDirect, directOptions),
        )
    }
    val matchedOpenRouter = preferredModel?.let { target ->
        openRouterOptions.firstOrNull { it.model.id == target.id }
    }
    if (matchedOpenRouter != null) {
        return ClaudeExecutionTarget.OpenRouterClaude(
            option = matchedOpenRouter,
            familyOverrides = buildFamilyOverrides(matchedOpenRouter, openRouterOptions),
        )
    }
    if (preferOpenRouterClaudeWhenConsultantsActive) {
        val defaultDirect = directOptions.firstOrNull { it.family == ClaudeModelFamily.SONNET } ?: directOptions.firstOrNull()
        if (defaultDirect != null) {
            return ClaudeExecutionTarget.DirectAnthropicClaude(
                option = defaultDirect,
                familyOverrides = buildDirectFamilyOverrides(defaultDirect, directOptions),
            )
        }
        val defaultClaude = openRouterOptions.firstOrNull { it.family == ClaudeModelFamily.SONNET } ?: openRouterOptions.firstOrNull()
        if (defaultClaude != null) {
            return ClaudeExecutionTarget.OpenRouterClaude(
                option = defaultClaude,
                familyOverrides = buildFamilyOverrides(defaultClaude, openRouterOptions),
            )
        }
    }
    return ClaudeExecutionTarget.FreeRouterFallback(preferredModelId = preferredModel?.id)
}

internal fun preferredDefaultOpenRouterClaudeOption(
    settings: Settings,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): OpenRouterClaudeOption? {
    val options = openRouterClaudeOptions(settings, enabledFamilies)
    return options.firstOrNull { it.family == ClaudeModelFamily.SONNET } ?: options.firstOrNull()
}

internal fun preferredDefaultNativeClaudeModel(
    settings: Settings,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): Model? {
    val direct = directAnthropicClaudeOptions(settings, enabledFamilies)
    val preferredDirect = direct.firstOrNull { it.family == ClaudeModelFamily.SONNET } ?: direct.firstOrNull()
    if (preferredDirect != null) return preferredDirect.model
    return preferredDefaultOpenRouterClaudeOption(settings, enabledFamilies)?.model
}

/**
 * Resolve qual modelId deve ficar ativo na conversa ao habilitar o Claude Code:
 * preserva se já for um Claude válido (direto Anthropic ou via OpenRouter); caso contrário
 * promove automaticamente para o Claude padrão (preferindo Sonnet) para evitar fallback ao projeto-free.
 */
internal fun promotedClaudeModelIdForToggle(
    settings: Settings,
    currentModel: Model?,
    fallbackModelId: Uuid?,
    enabledFamilies: Set<ClaudeModelFamily> = STAGE_ENABLED_CLAUDE_FAMILIES,
): Uuid? {
    if (hasNativeClaudeOption(settings, currentModel, enabledFamilies)) {
        return fallbackModelId
    }
    return preferredDefaultNativeClaudeModel(settings, enabledFamilies)?.id ?: fallbackModelId
}

private fun buildDirectFamilyOverrides(
    selected: DirectAnthropicClaudeOption,
    options: List<DirectAnthropicClaudeOption>,
): Map<ClaudeModelFamily, String> {
    val sameProvider = options.filter { it.providerSetting.id == selected.providerSetting.id }
    val pool = sameProvider.ifEmpty { options }
    return buildMap {
        put(selected.family, selected.model.modelId.removePrefix("anthropic/"))
        for (family in listOf(ClaudeModelFamily.SONNET, ClaudeModelFamily.OPUS, ClaudeModelFamily.HAIKU)) {
            if (containsKey(family)) continue
            val candidate = (pool.firstOrNull { it.family == family }?.model?.modelId ?: selected.model.modelId)
                .removePrefix("anthropic/")
            put(family, candidate)
        }
    }
}

private fun buildFamilyOverrides(
    selected: OpenRouterClaudeOption,
    options: List<OpenRouterClaudeOption>,
): Map<ClaudeModelFamily, String> {
    val sameProvider = options.filter { it.providerSetting.id == selected.providerSetting.id }
    val pool = sameProvider.ifEmpty { options }
    return buildMap {
        put(selected.family, selected.model.modelId)
        for (family in listOf(ClaudeModelFamily.SONNET, ClaudeModelFamily.OPUS, ClaudeModelFamily.HAIKU)) {
            if (containsKey(family)) continue
            val candidate = pool.firstOrNull { it.family == family }?.model?.modelId ?: selected.model.modelId
            put(family, candidate)
        }
    }
}
