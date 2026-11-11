package br.com.projetoia.app.data.ai

import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.supportsConversation
import me.rerere.ai.ui.UIMessage
import kotlinx.serialization.json.Json
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.datastore.hasConfiguredCredentials
import me.rerere.ai.registry.ModelRegistry
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

private val probeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
enum class ModelCheckStatus {
    OK, TIMEOUT, AUTH_ERROR, QUOTA, UNAVAILABLE, NETWORK, EMPTY, INVALID_REPLY, CONFIGURATION, ERROR, BLOCKED_AUTH, BLOCKED_QUOTA,
}

@Serializable
data class ModelCheck(
    val providerId: Uuid,
    val modelId: Uuid,
    val fingerprint: String,
    val checkedAt: Long,
    val durationMs: Long,
    val status: ModelCheckStatus,
    val toolsVerified: Boolean = false,
    /** HTTP status and exception type only; never persist raw provider bodies or request data. */
    val httpStatus: Int? = null,
    val failureType: String? = null,
    val toolStatus: ModelCheckStatus? = null,
    val toolHttpStatus: Int? = null,
    val toolFailureType: String? = null,
)

@Serializable
data class ModelCostAcknowledgement(
    val providerId: Uuid,
    val modelId: Uuid,
    val fingerprint: String,
)

/** Keep only a digest. A changed key, endpoint or model setting makes the result stale. */
internal fun modelCheckFingerprint(provider: ProviderSetting, model: Model): String =
    MessageDigest.getInstance("SHA-256").digest(
        probeJson.encodeToString<ProviderSetting>(provider.copyProvider(models = listOf(model))).toByteArray(),
    ).joinToString("") { "%02x".format(it) }

internal fun Settings.modelCheck(provider: ProviderSetting, model: Model): ModelCheck? =
    freeRouter.modelChecks.lastOrNull {
        it.providerId == provider.id && it.modelId == model.id &&
            it.fingerprint == modelCheckFingerprint(provider, model)
    }

internal fun Settings.hasPassedModelCheck(provider: ProviderSetting, model: Model): Boolean =
    modelCheck(provider, model)?.status == ModelCheckStatus.OK

/** Consent belongs to one exact endpoint, key and model configuration. */
internal fun Settings.hasCostAcknowledged(provider: ProviderSetting, model: Model): Boolean =
    hasProviderRoutingAuthorization(provider) || freeRouter.costAcknowledgements.any {
        it.providerId == provider.id && it.modelId == model.id && it.fingerprint == modelCheckFingerprint(provider, model)
    }

/** Unknown catalog entries may be explicitly checked. Known non-chat or overridden models stay out. */
internal fun canProbeModel(provider: ProviderSetting, model: Model): Boolean =
    provider.enabled && model.chatSupported != false && model.copy(chatSupported = true).supportsConversation() &&
        model.providerOverwrite == null && model.customBodies.isEmpty() &&
        when (provider) {
            is ProviderSetting.ApiKey -> provider.apiKey.isNotBlank()
            is ProviderSetting.Account -> provider.credential != null
        }

enum class ModelReadiness {
    AVAILABLE,
    ACCESSIBLE,
    TOOL_CAPABLE,
}

internal fun Settings.classifyModelReadiness(
    provider: ProviderSetting,
    model: Model,
    now: Long = System.currentTimeMillis(),
): ModelReadiness {
    val check = modelCheck(provider, model)
    val accessible = provider.enabled &&
        provider.hasConfiguredCredentials() &&
        check?.status == ModelCheckStatus.OK &&
        catalogAllows(provider, model) &&
        (routeHealth(provider, model)?.cooldownUntil ?: 0L) <= now
    if (!accessible) return ModelReadiness.AVAILABLE
    return if (check.toolsVerified) ModelReadiness.TOOL_CAPABLE else ModelReadiness.ACCESSIBLE
}

/**
 * Enrich a stored or discovered model with known capabilities from [ModelRegistry]
 * when catalog metadata omitted them, without overwriting explicit overrides.
 */
internal fun Model.withRegistryCapabilities(): Model {
    val knownAbilities = if (abilities.isEmpty()) ModelRegistry.MODEL_ABILITIES.getData(modelId) else emptyList()
    val knownInputs = if (inputModalities.size <= 1) ModelRegistry.MODEL_INPUT_MODALITIES.getData(modelId) else emptyList()
    val knownOutputs = if (outputModalities.size <= 1) ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(modelId) else emptyList()
    val knownContext = if (contextLength <= 0) ModelRegistry.MODEL_CONTEXT_LENGTH.getData(modelId) else null
    return copy(
        abilities = (abilities + knownAbilities).distinct(),
        inputModalities = (inputModalities + knownInputs).distinct(),
        outputModalities = (outputModalities + knownOutputs).distinct(),
        contextLength = knownContext ?: contextLength,
    )
}

enum class ModelBillingTier {
    FREE,
    FREE_TIER_QUOTA,
    PROMO_CREDITS,
    INCLUDED_BENEFIT,
    PAID_ONLY,
    UNKNOWN,
}

internal fun classifyModelBilling(provider: ProviderSetting, model: Model): ModelBillingTier {
    if (isConfirmedFreeProbe(provider, model)) return ModelBillingTier.FREE
    if (provider is ProviderSetting.Account) return ModelBillingTier.INCLUDED_BENEFIT
    val api = provider as? ProviderSetting.ApiKey ?: return ModelBillingTier.UNKNOWN
    val host = runCatching { URI(api.baseUrl).host?.lowercase(java.util.Locale.ROOT) }.getOrNull().orEmpty()
    return when {
        host == "openrouter.ai" -> {
            if (model.modelId == "openrouter/free" || model.modelId.endsWith(":free")) ModelBillingTier.FREE
            else ModelBillingTier.PAID_ONLY
        }
        host == "api.openai.com" || host == "api.anthropic.com" -> ModelBillingTier.PAID_ONLY
        provider is ProviderSetting.Google && provider.vertexAI -> ModelBillingTier.PAID_ONLY
        provider is ProviderSetting.Google && !provider.vertexAI -> ModelBillingTier.FREE_TIER_QUOTA
        host in setOf(
            "integrate.api.nvidia.com",
            "api.llm7.io",
            "apihub.agnes-ai.com",
        ) -> ModelBillingTier.FREE
        host in setOf(
            "api.groq.com",
            "api.cerebras.ai",
            "router.huggingface.co",
            "api.mistral.ai",
            "api.cohere.ai",
            "api-inference.modelscope.cn",
        ) -> ModelBillingTier.FREE_TIER_QUOTA
        host in setOf(
            "api.deepseek.com",
            "api.siliconflow.com",
            "api.together.xyz",
            "api.fireworks.ai",
        ) -> ModelBillingTier.PROMO_CREDITS
        else -> ModelBillingTier.UNKNOWN
    }
}

internal fun billingTierPriority(tier: ModelBillingTier): Int = when (tier) {
    ModelBillingTier.FREE -> 0
    ModelBillingTier.FREE_TIER_QUOTA -> 1
    ModelBillingTier.PROMO_CREDITS -> 2
    ModelBillingTier.INCLUDED_BENEFIT -> 3
    ModelBillingTier.UNKNOWN -> 4
    ModelBillingTier.PAID_ONLY -> 5
}

/** The default batch never contacts a provider whose price is unknown. */
internal fun isConfirmedFreeProbe(provider: ProviderSetting, model: Model): Boolean {
    val api = provider as? ProviderSetting.OpenAI ?: return false
    val url = runCatching { URI(api.baseUrl) }.getOrNull() ?: return false
    return canProbeModel(provider, model) && url.scheme == "https" && url.host == "openrouter.ai" &&
        url.port in listOf(-1, 443) && url.userInfo == null && url.rawQuery == null && url.rawFragment == null &&
        url.path.trimEnd('/') == "/api/v1" && !api.useResponseApi && api.chatCompletionsPath == "/chat/completions" &&
        api.customHeaders.isEmpty() && model.customHeaders.isEmpty() &&
        (model.modelId == "openrouter/free" || Regex("[A-Za-z0-9._/-]+:free").matches(model.modelId))
}

internal fun modelCheckFailure(error: Throwable): ModelCheckStatus = when (routingFailureCode(error)) {
    400, 422 -> ModelCheckStatus.CONFIGURATION
    401, 403 -> ModelCheckStatus.AUTH_ERROR
    402, 429 -> ModelCheckStatus.QUOTA
    404 -> ModelCheckStatus.UNAVAILABLE
    408, 504 -> ModelCheckStatus.TIMEOUT
    500, 502, 503 -> ModelCheckStatus.UNAVAILABLE
    else -> if (error is java.net.SocketTimeoutException) ModelCheckStatus.TIMEOUT else if (error is IOException) ModelCheckStatus.NETWORK else ModelCheckStatus.ERROR
}

/** Two short checks; tool calls are validated locally and never executed. */
internal class ModelProbe(
    private val resolve: (ProviderSetting) -> Provider<ProviderSetting>,
    private val timeoutMs: Long = 25_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val nonce: () -> String = { Uuid.random().toString() },
) {
    suspend fun check(provider: ProviderSetting, model: Model): ModelCheck {
        val started = TimeSource.Monotonic.markNow()
        fun result(
            status: ModelCheckStatus,
            tools: Boolean = false,
            httpStatus: Int? = null,
            failureType: String? = null,
            toolStatus: ModelCheckStatus? = null,
            toolHttpStatus: Int? = null,
            toolFailureType: String? = null,
        ) = ModelCheck(
            provider.id, model.id, modelCheckFingerprint(provider, model), now(),
            started.elapsedNow().inWholeMilliseconds, status, tools, httpStatus,
            failureType?.takeIf { value -> value.length in 1..80 && value.all { it.isLetterOrDigit() || it == '_' || it == '$' } },
            toolStatus, toolHttpStatus,
            toolFailureType?.takeIf { value -> value.length in 1..80 && value.all { it.isLetterOrDigit() || it == '_' || it == '$' } },
        )
        if (!canProbeModel(provider, model)) return result(ModelCheckStatus.CONFIGURATION)
        val token = nonce()
        val a = 17 + (token.hashCode().toLong().let { if (it < 0) -it else it } % 70).toInt()
        val b = 29
        val probeTool = Tool(
            name = "connection_probe",
            description = "A harmless connection check. Return the supplied token and sum. Nothing will be executed.",
            parameters = {
                InputSchema.Obj(buildJsonObject {
                    putJsonObject("token") { put("type", "string") }
                    putJsonObject("sum") { put("type", "integer") }
                }, required = listOf("token", "sum"))
            },
            execute = { error("Probe tools are never executed") },
        )
        val prompt = "Compute $a + $b. Reply with exactly '$token|SUM', replacing SUM with the integer result."
        return try {
            val providerClient = resolve(provider)
            val reply = withTimeoutOrNull(timeoutMs) {
                providerClient.generateText(
                    provider,
                    listOf(UIMessage.user(prompt)),
                    TextGenerationParams(
                        model = model,
                        maxTokens = 2048,
                        reasoningLevel = me.rerere.ai.core.ReasoningLevel.AUTO,
                        tools = emptyList(),
                        customHeaders = model.customHeaders,
                        customBody = emptyList(),
                    ),
                )
            } ?: return result(ModelCheckStatus.TIMEOUT)
            val calls = reply.message.getTools()
            val replyText = reply.message.toText()
            val hasReasoning = reply.message.parts.any {
                it is me.rerere.ai.ui.UIMessagePart.Reasoning && it.reasoning.isNotBlank()
            }
            val textValid = calls.isEmpty() && (replyText.isNotBlank() || hasReasoning)
            if (!textValid) return result(if (replyText.isBlank() && !hasReasoning) ModelCheckStatus.EMPTY else ModelCheckStatus.INVALID_REPLY)

            // Tool support is tested independently so a text-capable model remains available to normal chat.
            var toolHttpStatus: Int? = null
            var toolFailureType: String? = null
            val toolStatus = try {
                val toolReply = withTimeoutOrNull(timeoutMs) {
                    providerClient.generateText(
                        provider,
                        listOf(UIMessage.user("Call connection_probe exactly once with token '$token' and sum equal to $a + $b. Do not write text.")),
                        TextGenerationParams(
                            model = model.copy(abilities = (model.abilities + ModelAbility.TOOL).distinct()),
                            maxTokens = 2048,
                            reasoningLevel = me.rerere.ai.core.ReasoningLevel.AUTO,
                            tools = listOf(probeTool),
                            customHeaders = model.customHeaders,
                            customBody = emptyList(),
                        ),
                    )
                }
                val toolCalls = toolReply?.message?.getTools().orEmpty()
                val validTool = toolCalls.any { it.toolName == probeTool.name }
                when {
                    toolReply == null -> ModelCheckStatus.TIMEOUT
                    validTool -> ModelCheckStatus.OK
                    toolCalls.isEmpty() && toolReply.message.toText().isBlank() -> ModelCheckStatus.EMPTY
                    else -> ModelCheckStatus.INVALID_REPLY
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                toolHttpStatus = routingFailureCode(failure)
                toolFailureType = failure.javaClass.simpleName
                modelCheckFailure(failure)
            }
            result(
                ModelCheckStatus.OK,
                tools = toolStatus == ModelCheckStatus.OK,
                toolStatus = toolStatus,
                toolHttpStatus = toolHttpStatus,
                toolFailureType = toolFailureType,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            result(
                modelCheckFailure(failure),
                httpStatus = routingFailureCode(failure),
                failureType = failure.javaClass.simpleName,
            )
        }
    }
}

/** Record a result only if the original provider credentials and model configuration still match. */
internal fun Settings.recordModelCheck(check: ModelCheck): Settings {
    val provider = providers.firstOrNull { it.id == check.providerId } ?: return this
    val model = provider.models.firstOrNull { it.id == check.modelId } ?: return this
    if (modelCheckFingerprint(provider, model) != check.fingerprint) return this
    val verifiedModel = if (check.status == ModelCheckStatus.OK) model.copy(
        chatSupported = true,
        abilities = if (check.toolsVerified) (model.abilities + ModelAbility.TOOL).distinct() else model.abilities,
    ) else model
    val updatedProvider = provider.editModel(verifiedModel)
    val checkedFingerprint = modelCheckFingerprint(updatedProvider, verifiedModel)
    val costAcknowledgements = if (freeRouter.costAcknowledgements.any {
        it.providerId == provider.id && it.modelId == model.id && it.fingerprint == check.fingerprint
    }) {
        freeRouter.costAcknowledgements.filterNot { it.providerId == provider.id && it.modelId == model.id } +
            ModelCostAcknowledgement(provider.id, model.id, checkedFingerprint)
    } else {
        freeRouter.costAcknowledgements
    }
    val previousCatalog = catalogState(provider)
    val catalogs = if (check.httpStatus in setOf(401, 402)) {
        freeRouter.catalogs.filterNot { it.providerId == provider.id } + ProviderCatalogState(provider.id,
            providerConnectionFingerprint(provider), check.checkedAt, checkedAt = previousCatalog?.checkedAt ?: 0,
            status = if (check.httpStatus == 401) ModelCheckStatus.AUTH_ERROR else ModelCheckStatus.QUOTA,
            httpStatus = check.httpStatus, missingModelIds = previousCatalog?.missingModelIds.orEmpty(), modelCount = previousCatalog?.modelCount ?: 0)
    } else freeRouter.catalogs.map { catalog ->
        if (catalog.providerId == provider.id && check.status == ModelCheckStatus.OK && catalog.httpStatus in setOf(401, 402))
            catalog.copy(status = ModelCheckStatus.OK, httpStatus = null, attemptedAt = check.checkedAt)
        else catalog
    }
    return copy(
        providers = providers.map { if (it.id == provider.id) updatedProvider else it },
        freeRouter = freeRouter.copy(
            modelChecks = freeRouter.modelChecks.filterNot { it.providerId == provider.id && it.modelId == model.id } + check.copy(fingerprint = checkedFingerprint),
            costAcknowledgements = costAcknowledgements,
            catalogs = catalogs,
            routeHealth = freeRouter.routeHealth.map { health ->
                if (health.providerId == provider.id && health.modelId == model.id && health.fingerprint == check.fingerprint && check.status == ModelCheckStatus.OK)
                    health.copy(fingerprint = checkedFingerprint, cooldownUntil = 0, consecutiveFailures = 0, lastHttpStatus = null)
                else health
            },
            approvedModelIds = freeRouter.approvedModelIds,
        ),
    )
}
