package br.com.projetoia.app.data.ai

import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.datastore.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

internal const val CATALOG_INTERVAL_MS = 12 * 60 * 60_000L
internal const val CHECK_VALIDITY_MS = 7 * 24 * 60 * 60_000L

internal fun Settings.mergeProviderCatalog(provider: ProviderSetting, catalog: List<Model>, now: Long): Settings {
    val current = providers.firstOrNull { it.id == provider.id } ?: return this
    if (providerConnectionFingerprint(current) != providerConnectionFingerprint(provider)) return this
    // Empty successful catalogs are recorded, not mistaken for inference access.
    val byName = catalog.distinctBy { it.modelId }.associateBy { it.modelId }
    val oldIds = current.models.mapTo(HashSet()) { it.modelId }
    val discovered = conversationalCatalog(catalog)
    val updatedModels = current.models.map { old ->
        val fresh = byName[old.modelId]
        // Preserve verified capabilities and UUIDs. Explicit provider deactivation still wins.
        if (fresh == null) old else old.copy(
            chatSupported = if (fresh.chatSupported == false) false else old.chatSupported ?: fresh.chatSupported,
            abilities = (old.abilities + fresh.abilities).distinct(),
        )
    } + discovered.filter { it.modelId !in oldIds }
    val missing = current.models.filter { it.modelId !in byName && it.modelId != "openrouter/free" }.map { it.modelId }
    val state = ProviderCatalogState(current.id, providerConnectionFingerprint(current), now, now, ModelCheckStatus.OK,
        missingModelIds = missing, modelCount = discovered.size)
    return copy(providers = providers.map { if (it.id == current.id) current.copyProvider(models = updatedModels) else it },
        freeRouter = freeRouter.copy(catalogs = freeRouter.catalogs.filterNot { it.providerId == current.id } + state))
}

internal fun Settings.recordCatalogFailure(provider: ProviderSetting, error: Throwable, now: Long): Settings {
    val current = providers.firstOrNull { it.id == provider.id } ?: return this
    if (providerConnectionFingerprint(current) != providerConnectionFingerprint(provider)) return this
    val previous = catalogState(current)
    val state = ProviderCatalogState(current.id, providerConnectionFingerprint(current), now,
        checkedAt = previous?.checkedAt ?: 0, status = modelCheckFailure(error), httpStatus = routingFailureCode(error),
        missingModelIds = previous?.missingModelIds.orEmpty(), modelCount = previous?.modelCount ?: 0)
    return copy(freeRouter = freeRouter.copy(catalogs = freeRouter.catalogs.filterNot { it.providerId == current.id } + state))
}

internal fun Settings.automaticProbeCandidates(provider: ProviderSetting, force: Boolean, now: Long, limit: Int = 4): List<Model> {
    if (!freeRouter.automaticManagement) return emptyList()
    return provider.models.filter { model ->
            model.id !in freeRouter.excludedModelIds && canProbeModel(provider, model) && catalogAllows(provider, model) &&
                (isConfirmedFreeProbe(provider, model) || hasCostAcknowledged(provider, model)) &&
                (routeHealth(provider, model)?.cooldownUntil ?: 0) <= now &&
                (force || modelCheck(provider, model)?.let { it.status == ModelCheckStatus.OK && now - it.checkedAt < CHECK_VALIDITY_MS } != true)
        }.sortedWith(compareByDescending<Model> { routeScore(it, "programação código análise") + modelStabilityScore(it) + routeLearningScore(routeHealth(provider, it)) }
            .thenBy { it.modelId })
        .take(limit)
}

internal data class AutomaticSelectionProgress(val running: Boolean = false, val completed: Int = 0, val total: Int = 0, val activeProviders: Set<Uuid> = emptySet(), val tested: Int = 0, val failedToSave: Boolean = false)

/** Provider metadata is checked at most twice daily; inference probes need explicit/free consent. */
internal class ProviderCatalogManager(
    private val store: SettingsStore,
    private val providers: ProviderManager,
    private val scope: CoroutineScope,
) {
    private val mutableProgress = MutableStateFlow(AutomaticSelectionProgress())
    val progress = mutableProgress.asStateFlow()
    private var job: Job? = null
    private var pendingForce = false
    private var pendingProviderId: Uuid? = null

    @Synchronized fun start(force: Boolean = false, providerId: Uuid? = null) {
        if (job?.isCompleted == false) {
            if (force) {
                pendingProviderId = if (!pendingForce || pendingProviderId == providerId) providerId else null
                pendingForce = true
            }
            return
        }
        mutableProgress.value = AutomaticSelectionProgress(running = true)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val settings = store.awaitLoaded()
                val now = System.currentTimeMillis()
                val configured = settings.providers.filter { provider ->
                    (providerId == null || provider.id == providerId) && provider.enabled && provider is ProviderSetting.ApiKey && provider.apiKey.isNotBlank()
                }.filter { provider ->
                    val previous = settings.catalogState(provider)
                    val interval = if (previous?.status == ModelCheckStatus.OK) CATALOG_INTERVAL_MS else 15 * 60_000L
                    force || previous == null || now - previous.attemptedAt >= interval
                }
                mutableProgress.update { it.copy(total = configured.size) }
                val semaphore = Semaphore(2)
                coroutineScope {
                    configured.forEach { provider -> launch {
                        semaphore.withPermit {
                            mutableProgress.update { it.copy(activeProviders = it.activeProviders + provider.id) }
                            try { kotlinx.coroutines.withTimeoutOrNull(75_000) { refresh(provider, force) } }
                            finally { mutableProgress.update { it.copy(completed = it.completed + 1, activeProviders = it.activeProviders - provider.id) } }
                        }
                    } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableProgress.update { it.copy(failedToSave = true) } }
            finally {
                synchronized(this@ProviderCatalogManager) {
                    mutableProgress.update { it.copy(running = false, activeProviders = emptySet()) }
                    job = null
                    if (pendingForce) {
                        val nextProvider = pendingProviderId
                        pendingForce = false
                        pendingProviderId = null
                        start(force = true, providerId = nextProvider)
                    }
                }
            }
        }
        job?.start()
    }

    @Synchronized fun cancel() { pendingForce = false; pendingProviderId = null; job?.cancel() }

    private suspend fun refresh(original: ProviderSetting, force: Boolean) {
        val listed: List<Model>
        try {
            listed = withTimeout(20_000) { providers.getProviderByType(original).listModels(original) }
        } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) {
            store.update { it.recordCatalogFailure(original, java.net.SocketTimeoutException(), System.currentTimeMillis()) }
            return
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            store.update { it.recordCatalogFailure(original, failure, System.currentTimeMillis()) }
            return
        }
        store.update { it.mergeProviderCatalog(original, listed, System.currentTimeMillis()) }
        val settings = store.awaitLoaded()
        val provider = settings.providers.firstOrNull { it.id == original.id } ?: return
        if (providerConnectionFingerprint(provider) != providerConnectionFingerprint(original) || !settings.freeRouter.automaticManagement) return
        val now = System.currentTimeMillis()
        val candidates = settings.automaticProbeCandidates(provider, force, now, limit = 6)
        // Up to six candidates / 75 seconds per provider, stopping after two successes or account-level auth/billing errors.
        var passed = 0
        for (model in candidates.take(6)) {
            val latest = store.awaitLoaded()
            val currentProvider = latest.providers.firstOrNull { it.id == provider.id } ?: return
            val currentModel = currentProvider.models.firstOrNull { it.id == model.id } ?: continue
            if (!latest.freeRouter.automaticManagement || modelCheckFingerprint(currentProvider, currentModel) != modelCheckFingerprint(provider, model)) return
            if (!isConfirmedFreeProbe(currentProvider, currentModel) && !latest.hasCostAcknowledged(currentProvider, currentModel)) continue
            val check = ModelProbe({ providers.getProviderByType(it) }).check(currentProvider, currentModel)
            store.update { current ->
                var updated = current.recordModelCheck(check)
                val p = updated.providers.firstOrNull { it.id == currentProvider.id }
                val m = p?.models?.firstOrNull { it.id == currentModel.id }
                if (p != null && m != null && updated.modelCheck(p, m)?.checkedAt == check.checkedAt) {
                    updated = updated.recordRouteObservation(FreeRoute(p, m), RouteObservation(check.status == ModelCheckStatus.OK, check.durationMs, check.httpStatus))
                }
                if (p != null && m != null && m.id !in updated.freeRouter.excludedModelIds && updated.freeRouter.automaticManagement && updated.modelCheck(p, m)?.status == ModelCheckStatus.OK &&
                    (isConfirmedFreeProbe(p, m) || updated.hasCostAcknowledged(p, m))) {
                    val nextChatModelId = if (updated.chatModelId == br.com.projetoia.app.data.datastore.DEFAULT_AUTO_MODEL_ID) m.id else updated.chatModelId
                    updated = updated.copy(
                        chatModelId = nextChatModelId,
                        freeRouter = updated.freeRouter.copy(
                            enabled = true,
                            approvedModelIds = (updated.freeRouter.approvedModelIds + m.id).distinct(),
                        ),
                    )
                }
                updated
            }
            mutableProgress.update { it.copy(tested = it.tested + 1) }
            if (check.status == ModelCheckStatus.OK) passed++
            if (passed >= 2 || check.httpStatus in setOf(401, 402)) break
        }
        // Previously checked models are reused without sending another prompt.
        store.update { current ->
            val p = current.providers.firstOrNull { it.id == provider.id } ?: return@update current
            if (!current.freeRouter.automaticManagement) return@update current
            val ready = p.models.filter { it.id !in current.freeRouter.excludedModelIds && current.catalogAllows(p, it) && current.hasPassedModelCheck(p, it) &&
                (isConfirmedFreeProbe(p, it) || current.hasCostAcknowledged(p, it)) }.map { it.id }
            val nextChatModelId = if (current.chatModelId == br.com.projetoia.app.data.datastore.DEFAULT_AUTO_MODEL_ID && ready.isNotEmpty()) ready.first() else current.chatModelId
            current.copy(
                chatModelId = nextChatModelId,
                freeRouter = current.freeRouter.copy(
                    enabled = current.freeRouter.enabled || ready.isNotEmpty(),
                    approvedModelIds = (current.freeRouter.approvedModelIds + ready).distinct(),
                ),
            )
        }
    }
}
