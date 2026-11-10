package br.com.projetoia.app.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.rerere.ai.provider.ProviderManager
import br.com.projetoia.app.data.datastore.SettingsStore
import kotlin.uuid.Uuid

internal data class ModelCheckProgress(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val passed: Int = 0,
    val skipped: Int = 0,
    val activeModelIds: Set<Uuid> = emptySet(),
    val failedToSave: Boolean = false,
)

/** One active batch, at most three provider connections and one request per connection at a time. */
internal class ModelCheckManager(
    private val store: SettingsStore,
    private val scope: CoroutineScope,
    private val probe: ModelProbe,
    private val pauseMs: Long = 1_200,
) {
    constructor(store: SettingsStore, manager: ProviderManager, scope: CoroutineScope) :
        this(store, scope, ModelProbe({ manager.getProviderByType(it) }))

    private val mutableProgress = MutableStateFlow(ModelCheckProgress())
    val progress = mutableProgress.asStateFlow()
    private var job: Job? = null

    @Synchronized
    fun start(providerId: Uuid? = null, modelId: Uuid? = null, confirmedFreeOnly: Boolean = true) {
        if (job?.isActive == true || mutableProgress.value.running) return
        val snapshot = store.settingsFlow.value
        val routes = snapshot.providers.filter { providerId == null || it.id == providerId }.flatMap { provider ->
            provider.models.filter { model ->
                (modelId == null || model.id == modelId) && canProbeModel(provider, model) &&
                    (!confirmedFreeOnly || (
                        (isConfirmedFreeProbe(provider, model) || snapshot.hasCostAcknowledged(provider, model)) &&
                            (modelId != null || snapshot.modelCheck(provider, model)?.status != ModelCheckStatus.OK)
                        ))
            }.sortedWith(
                compareByDescending<me.rerere.ai.provider.Model> { modelStabilityScore(it) + routeScore(it, "programação código análise") }
                    .thenBy { it.modelId }
            ).map { FreeRoute(provider, it) }
        }.take(if (confirmedFreeOnly && modelId == null) MAX_BATCH_MODELS else Int.MAX_VALUE)
        if (routes.isEmpty()) return
        mutableProgress.value = ModelCheckProgress(running = true, total = routes.size)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val semaphore = Semaphore(3)
                coroutineScope {
                    routes.groupBy { it.provider.id }.values.forEach { group ->
                        launch {
                            semaphore.withPermit {
                                var blockStatus: ModelCheckStatus? = null
                                group.forEachIndexed { index, route ->
                                    currentCoroutineContext().ensureActive()
                                    val latest = store.settingsFlow.value
                                    val currentProvider = latest.providers.firstOrNull { it.id == route.provider.id }
                                    val currentModel = currentProvider?.models?.firstOrNull { it.id == route.model.id }
                                    if (currentProvider == null || currentModel == null ||
                                        modelCheckFingerprint(currentProvider, currentModel) != modelCheckFingerprint(route.provider, route.model)) {
                                        mutableProgress.update { it.copy(completed = it.completed + 1, skipped = it.skipped + 1) }
                                        return@forEachIndexed
                                    }
                                    if (index > 0 && blockStatus == null) delay(pauseMs)
                                    mutableProgress.update { it.copy(activeModelIds = it.activeModelIds + currentModel.id) }
                                    val check = if (blockStatus != null) ModelCheck(
                                        currentProvider.id,
                                        currentModel.id,
                                        modelCheckFingerprint(currentProvider, currentModel),
                                        System.currentTimeMillis(),
                                        0,
                                        if (blockStatus == ModelCheckStatus.AUTH_ERROR) ModelCheckStatus.BLOCKED_AUTH else ModelCheckStatus.BLOCKED_QUOTA,
                                    ) else probe.check(currentProvider, currentModel)
                                    currentCoroutineContext().ensureActive()
                                    store.update { current ->
                                        var updated = current.recordModelCheck(check)
                                        if (check.status == ModelCheckStatus.OK) {
                                            val p = updated.providers.firstOrNull { it.id == currentProvider.id }
                                            val m = p?.models?.firstOrNull { it.id == currentModel.id }
                                            if (p != null && m != null) {
                                                val acks = if (!isConfirmedFreeProbe(p, m) && !updated.hasCostAcknowledged(p, m) && modelId != null) {
                                                    val ack = ModelCostAcknowledgement(p.id, m.id, modelCheckFingerprint(p, m))
                                                    updated.freeRouter.costAcknowledgements.filterNot {
                                                        it.providerId == ack.providerId && it.modelId == ack.modelId
                                                    } + ack
                                                } else {
                                                    updated.freeRouter.costAcknowledgements
                                                }
                                                val shouldApprove = m.id !in updated.freeRouter.excludedModelIds || modelId != null
                                                val approved = if (shouldApprove) (updated.freeRouter.approvedModelIds + m.id).distinct() else updated.freeRouter.approvedModelIds
                                                val excluded = if (modelId != null) updated.freeRouter.excludedModelIds - m.id else updated.freeRouter.excludedModelIds
                                                val nextChatModelId = if (updated.chatModelId == br.com.projetoia.app.data.datastore.DEFAULT_AUTO_MODEL_ID) m.id else updated.chatModelId
                                                updated = updated.copy(
                                                    chatModelId = nextChatModelId,
                                                    freeRouter = updated.freeRouter.copy(
                                                        enabled = true,
                                                        approvedModelIds = approved,
                                                        excludedModelIds = excluded,
                                                        costAcknowledgements = acks,
                                                    ),
                                                )
                                            }
                                        }
                                        updated
                                    }
                                    if (check.httpStatus in setOf(401, 402)) blockStatus = check.status
                                    mutableProgress.update { it.copy(
                                        completed = it.completed + 1,
                                        passed = it.passed + if (check.status == ModelCheckStatus.OK) 1 else 0,
                                        skipped = it.skipped + if (check.status in setOf(ModelCheckStatus.BLOCKED_AUTH, ModelCheckStatus.BLOCKED_QUOTA)) 1 else 0,
                                        activeModelIds = it.activeModelIds - currentModel.id,
                                    ) }
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableProgress.update { it.copy(failedToSave = true) }
            } finally {
                mutableProgress.update { it.copy(running = false, activeModelIds = emptySet()) }
            }
        }
        job?.start()
    }

    @Synchronized
    fun cancel() { job?.cancel() }

    private companion object {
        const val MAX_BATCH_MODELS = 24
    }
}
