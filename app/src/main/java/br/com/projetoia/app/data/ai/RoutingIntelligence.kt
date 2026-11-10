package br.com.projetoia.app.data.ai

import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.datastore.SettingsStore
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

private val routingJson = Json { encodeDefaults = true; explicitNulls = false }
internal fun providerConnectionFingerprint(provider: ProviderSetting): String = MessageDigest.getInstance("SHA-256")
    .digest(routingJson.encodeToString<ProviderSetting>(provider.copyProvider(models = emptyList())).toByteArray())
    .joinToString("") { "%02x".format(it) }

@Serializable
data class ProviderCatalogState(
    val providerId: Uuid,
    val fingerprint: String,
    val attemptedAt: Long,
    val checkedAt: Long = 0,
    val status: ModelCheckStatus,
    val httpStatus: Int? = null,
    val missingModelIds: List<String> = emptyList(),
    val modelCount: Int = 0,
)

@Serializable
data class ProviderRoutingAuthorization(val providerId: Uuid, val fingerprint: String)

@Serializable
data class ModelRouteHealth(
    val providerId: Uuid,
    val modelId: Uuid,
    val fingerprint: String,
    val successes: Int = 0,
    val failures: Int = 0,
    val responseMs: Long = 0,
    val consecutiveFailures: Int = 0,
    val cooldownUntil: Long = 0,
    val lastHttpStatus: Int? = null,
    val updatedAt: Long = 0,
)

internal fun Settings.catalogState(provider: ProviderSetting): ProviderCatalogState? = freeRouter.catalogs.lastOrNull {
    it.providerId == provider.id && it.fingerprint == providerConnectionFingerprint(provider)
}
internal fun Settings.hasProviderRoutingAuthorization(provider: ProviderSetting): Boolean = freeRouter.providerAuthorizations.any {
    it.providerId == provider.id && it.fingerprint == providerConnectionFingerprint(provider)
}
internal fun Settings.catalogAllows(provider: ProviderSetting, model: Model): Boolean =
    catalogState(provider)?.let { catalog ->
        model.modelId !in catalog.missingModelIds || (modelCheck(provider, model)?.let {
            it.status == ModelCheckStatus.OK && it.checkedAt > catalog.checkedAt
        } == true)
    } != false
internal fun Settings.providerReady(provider: ProviderSetting, now: Long = System.currentTimeMillis()): Boolean = catalogState(provider)?.let {
    it.httpStatus !in setOf(401, 402) || now - it.attemptedAt >= 15 * 60_000L
} != false
internal fun Settings.routeHealth(provider: ProviderSetting, model: Model): ModelRouteHealth? = freeRouter.routeHealth.lastOrNull {
    it.providerId == provider.id && it.modelId == model.id && it.fingerprint == modelCheckFingerprint(provider, model)
}
internal fun routeLearningScore(health: ModelRouteHealth?): Int {
    if (health == null) return 0
    val reliability = (health.successes + 2.0) / (health.successes + health.failures + 3.0)
    val speed = if (health.responseMs > 0) (10_000L / health.responseMs.coerceAtLeast(500)).coerceAtMost(20).toInt() else 0
    return (reliability * 40).toInt() - 26 + speed - health.consecutiveFailures * 8
}

/** Updating capability metadata during a probe must not grant new consent or lose existing consent. */
internal fun Settings.authorizeProviderRouting(provider: ProviderSetting): Settings = copy(freeRouter = freeRouter.copy(
    providerAuthorizations = freeRouter.providerAuthorizations.filterNot { it.providerId == provider.id } +
        ProviderRoutingAuthorization(provider.id, providerConnectionFingerprint(provider)),
))

internal data class RouteObservation(val succeeded: Boolean, val durationMs: Long, val httpStatus: Int? = null, val retryAfterMs: Long? = null)
internal fun Settings.recordRouteObservation(route: FreeRoute, observation: RouteObservation, now: Long = System.currentTimeMillis()): Settings {
    val provider = providers.firstOrNull { it.id == route.provider.id } ?: return this
    val model = provider.models.firstOrNull { it.id == route.model.id } ?: return this
    val fingerprint = modelCheckFingerprint(provider, model)
    if (fingerprint != modelCheckFingerprint(route.provider, route.model)) return this
    val previous = routeHealth(provider, model) ?: ModelRouteHealth(provider.id, model.id, fingerprint)
    val delay = if (observation.succeeded) 0L else observation.retryAfterMs?.coerceIn(1_000, 86_400_000) ?: when (observation.httpStatus) {
        401, 403 -> 15 * 60_000L
        402 -> 60 * 60_000L
        404 -> 12 * 60 * 60_000L
        429 -> (60_000L * (previous.consecutiveFailures + 1)).coerceAtMost(15 * 60_000L)
        else -> 15_000L
    }
    val next = previous.copy(
        successes = (previous.successes + if (observation.succeeded) 1 else 0).coerceAtMost(10_000),
        failures = (previous.failures + if (observation.succeeded) 0 else 1).coerceAtMost(10_000),
        responseMs = if (!observation.succeeded) previous.responseMs else if (previous.responseMs == 0L) observation.durationMs.coerceAtLeast(1) else (previous.responseMs * 3 + observation.durationMs.coerceAtLeast(1)) / 4,
        consecutiveFailures = if (observation.succeeded) 0 else (previous.consecutiveFailures + 1).coerceAtMost(100),
        cooldownUntil = now + delay,
        lastHttpStatus = observation.httpStatus,
        updatedAt = now,
    )
    val catalogs = if (observation.httpStatus in setOf(401, 402)) {
        val previousCatalog = catalogState(provider)
        freeRouter.catalogs.filterNot { it.providerId == provider.id } + ProviderCatalogState(provider.id,
            providerConnectionFingerprint(provider), now, checkedAt = previousCatalog?.checkedAt ?: 0,
            status = if (observation.httpStatus == 401) ModelCheckStatus.AUTH_ERROR else ModelCheckStatus.QUOTA,
            httpStatus = observation.httpStatus, missingModelIds = previousCatalog?.missingModelIds.orEmpty(), modelCount = previousCatalog?.modelCount ?: 0)
    } else freeRouter.catalogs
    return copy(freeRouter = freeRouter.copy(catalogs = catalogs, routeHealth = freeRouter.routeHealth.filterNot { it.providerId == provider.id && it.modelId == model.id } + next))
}

class RoutingObserver(private val store: SettingsStore) {
    internal suspend fun record(route: FreeRoute, observation: RouteObservation) {
        try { store.update { it.recordRouteObservation(route, observation) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Statistics must never replay or fail an inference request. */ }
    }
}
