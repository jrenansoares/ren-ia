package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import br.com.projetoia.app.R
import br.com.projetoia.app.data.ai.ModelBillingTier
import br.com.projetoia.app.data.ai.ModelCheckManager
import br.com.projetoia.app.data.ai.ModelCheckStatus
import br.com.projetoia.app.data.ai.ModelCostAcknowledgement
import br.com.projetoia.app.data.ai.ModelReadiness
import br.com.projetoia.app.data.ai.authorizeProviderRouting
import br.com.projetoia.app.data.ai.canProbeModel
import br.com.projetoia.app.data.ai.catalogAllows
import br.com.projetoia.app.data.ai.classifyModelBilling
import br.com.projetoia.app.data.ai.classifyModelReadiness
import br.com.projetoia.app.data.ai.hasCostAcknowledged
import br.com.projetoia.app.data.ai.hasPassedModelCheck
import br.com.projetoia.app.data.ai.isAutomaticRouteCandidate
import br.com.projetoia.app.data.ai.isConfirmedFreeProbe
import br.com.projetoia.app.data.ai.modelCheck
import br.com.projetoia.app.data.ai.modelCheckFingerprint
import br.com.projetoia.app.data.ai.modelStabilityScore
import br.com.projetoia.app.data.ai.routeHealth
import br.com.projetoia.app.data.ai.routeScore
import br.com.projetoia.app.data.ai.claude.isOpenRouterProvider
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.datastore.hasConfiguredCredentials
import me.rerere.ai.provider.Modality
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.datastore.hasConfiguredCredentials
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.koin.compose.koinInject

@Composable
fun ModelChecksPage() {
    val store = koinInject<SettingsStore>()
    val manager = koinInject<ModelCheckManager>()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val progress by manager.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var pendingPaidCheck by remember { mutableStateOf<Pair<ProviderSetting, Model>?>(null) }
    var pendingApproveCost by remember { mutableStateOf<Pair<ProviderSetting, Model>?>(null) }
    val configuredProviders = remember(settings.providers) {
        settings.providers.filter { it.enabled && it.hasConfiguredCredentials() }
    }
    val models = remember(settings, configuredProviders, search, filter) {
        configuredProviders.flatMap { provider ->
            provider.models.map { provider to it }
        }.filter { (provider, model) ->
            search.isBlank() || provider.name.contains(search, ignoreCase = true) ||
                model.modelId.contains(search, ignoreCase = true) || model.displayName.contains(search, ignoreCase = true)
        }.filter { (provider, model) ->
            val readiness = settings.classifyModelReadiness(provider, model)
            when (filter) {
                1 -> readiness != ModelReadiness.AVAILABLE
                2 -> readiness == ModelReadiness.TOOL_CAPABLE
                3 -> Modality.IMAGE in model.inputModalities
                else -> true
            }
        }.sortedWith(
            compareBy<Pair<ProviderSetting, Model>> { (provider, model) ->
                when (settings.modelCheck(provider, model)?.status) {
                    ModelCheckStatus.OK -> 0
                    null -> 1
                    else -> 2
                }
            }
                .thenBy { it.first.name.lowercase() }
                .thenByDescending { (_, model) -> modelStabilityScore(model) + routeScore(model, "programação código análise") }
                .thenBy { it.second.displayName.ifBlank { it.second.modelId }.lowercase() }
        )
    }
    val pendingFreeModels = configuredProviders.sumOf { provider ->
        provider.models.count {
            canProbeModel(provider, it) &&
                (isConfirmedFreeProbe(provider, it) ||
                    settings.hasCostAcknowledged(provider, it) ||
                    classifyModelBilling(provider, it) in setOf(ModelBillingTier.FREE, ModelBillingTier.FREE_TIER_QUOTA)) &&
                settings.modelCheck(provider, it)?.status != ModelCheckStatus.OK
        }
    }
    val anyProbeableModels = configuredProviders.sumOf { provider ->
        provider.models.count { canProbeModel(provider, it) }
    }
    val allCount = configuredProviders.sumOf { it.models.size }
    val accessibleCount = configuredProviders.sumOf { provider ->
        provider.models.count { model -> settings.classifyModelReadiness(provider, model) != ModelReadiness.AVAILABLE }
    }
    val toolCapableCount = configuredProviders.sumOf { provider ->
        provider.models.count { model -> settings.classifyModelReadiness(provider, model) == ModelReadiness.TOOL_CAPABLE }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.projeto_model_checks_title)) }, navigationIcon = { BackButton() })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(stringResource(R.string.projeto_model_checks_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.projeto_model_checks_summary, accessibleCount, allCount)) },
                        supportingContent = { Text(stringResource(R.string.projeto_readiness_counts, allCount, accessibleCount, toolCapableCount)) },
                    )
                    item(headlineContent = { Text(stringResource(R.string.projeto_model_checks_privacy), style = MaterialTheme.typography.bodyMedium) })
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            store.update { current ->
                                var updated = current
                                current.providers.filter { it.enabled && it.hasConfiguredCredentials() }.forEach { p ->
                                    val hasFreeQuota = p.models.any { m ->
                                        classifyModelBilling(p, m) in setOf(ModelBillingTier.FREE, ModelBillingTier.FREE_TIER_QUOTA)
                                    }
                                    if (hasFreeQuota) {
                                        updated = updated.authorizeProviderRouting(p)
                                    }
                                }
                                updated.copy(freeRouter = updated.freeRouter.copy(automaticManagement = true))
                            }
                            manager.start(confirmedFreeOnly = true)
                        }
                    },
                    enabled = !progress.running && (pendingFreeModels > 0 || anyProbeableModels > 0),
                    modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,
                ) {
                    Text(stringResource(R.string.projeto_model_checks_batch))
                }
            }
            if (progress.running) {
                item {
                    CardGroup {
                        formItem {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.projeto_model_checks_progress, progress.completed, progress.total, progress.passed))
                                progress.activeModelIds.firstOrNull()?.let { activeId ->
                                    val active = models.firstOrNull { it.second.id == activeId }
                                    if (active != null) Text(stringResource(R.string.projeto_model_checks_testing, active.second.displayName.ifBlank { active.second.modelId }))
                                }
                                Button(onClick = manager::cancel, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
                            }
                        }
                    }
                }
            }
            progress.failedToSave.takeIf { it }?.let {
                item { Text(stringResource(R.string.projeto_model_checks_save_error), color = MaterialTheme.colorScheme.error) }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.projeto_model_checks_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.ren_models_all, R.string.ren_models_ready, R.string.ren_models_tools, R.string.ren_models_vision).forEachIndexed { index, label ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(stringResource(label)) }, elevation = null)
                    }
                }
            }
            items(models, key = { (provider, model) -> "${provider.id}:${model.id}" }) { (provider, model) ->
                val result = settings.modelCheck(provider, model)
                val tier = classifyModelBilling(provider, model)
                val readiness = settings.classifyModelReadiness(provider, model)
                val isFreeOrQuota = tier in setOf(ModelBillingTier.FREE, ModelBillingTier.FREE_TIER_QUOTA)
                val canCheck = canProbeModel(provider, model)
                val openRouterPaid = isOpenRouterProvider(provider) && !isConfirmedFreeProbe(provider, model)
                val canApproveForRouter = result?.status == ModelCheckStatus.OK && !openRouterPaid &&
                    (isAutomaticRouteCandidate(provider, model) || !isOpenRouterProvider(provider))
                val isApproved = model.id in settings.freeRouter.approvedModelIds
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        headlineContent = {
                            Text(model.displayName.ifBlank { model.modelId }, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(provider.name)
                                val (readinessRes, readinessColor) = when (readiness) {
                                    ModelReadiness.TOOL_CAPABLE -> R.string.projeto_readiness_tool_capable to MaterialTheme.colorScheme.primary
                                    ModelReadiness.ACCESSIBLE -> R.string.projeto_readiness_accessible to MaterialTheme.colorScheme.secondary
                                    ModelReadiness.AVAILABLE -> R.string.projeto_readiness_available to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(stringResource(readinessRes), color = readinessColor, style = MaterialTheme.typography.labelMedium)
                                if (!settings.catalogAllows(provider, model)) Text(stringResource(R.string.ren_model_absent), color = MaterialTheme.colorScheme.error)
                                val health = settings.routeHealth(provider, model)
                                if ((health?.cooldownUntil ?: 0) > System.currentTimeMillis()) Text(stringResource(R.string.ren_model_cooldown), color = MaterialTheme.colorScheme.error)
                                if (health != null) Text(stringResource(R.string.ren_model_history, health.successes, health.failures, health.responseMs), style = MaterialTheme.typography.bodySmall)
                                when {
                                    progress.running && model.id in progress.activeModelIds -> Text(stringResource(R.string.projeto_model_checks_state_running))
                                    result == null -> Text(stringResource(R.string.projeto_model_check_not_tested))
                                    else -> {
                                        Text(
                                            if (result.status == ModelCheckStatus.OK) stringResource(R.string.projeto_model_checks_chat_ok, result.durationMs)
                                            else stringResource(statusLabel(result.status)),
                                            color = if (result.status == ModelCheckStatus.OK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                        if (result.toolsVerified) Text(stringResource(R.string.projeto_model_checks_tools_ok), color = MaterialTheme.colorScheme.primary)
                                        else result.toolStatus?.let { status -> Text(stringResource(R.string.projeto_model_checks_tools_failed, stringResource(statusLabel(status))), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        result.httpStatus?.let { Text(stringResource(R.string.projeto_model_checks_http_status, it), color = MaterialTheme.colorScheme.error) }
                                        result.failureType?.let { Text(stringResource(R.string.projeto_model_checks_failure_type, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        result.toolHttpStatus?.let { Text(stringResource(R.string.projeto_model_checks_tool_http_status, it), color = MaterialTheme.colorScheme.error) }
                                        result.toolFailureType?.let { Text(stringResource(R.string.projeto_model_checks_tool_failure_type, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                                val tierText = when (tier) {
                                    ModelBillingTier.FREE -> stringResource(R.string.projeto_router_price_free)
                                    ModelBillingTier.FREE_TIER_QUOTA -> stringResource(R.string.projeto_router_price_quota)
                                    ModelBillingTier.PROMO_CREDITS -> stringResource(R.string.projeto_router_price_promo)
                                    ModelBillingTier.INCLUDED_BENEFIT -> stringResource(R.string.projeto_router_price_benefit)
                                    ModelBillingTier.PAID_ONLY -> stringResource(R.string.projeto_router_price_paid)
                                    ModelBillingTier.UNKNOWN -> if (settings.hasCostAcknowledged(provider, model)) stringResource(R.string.ren_price_authorized) else stringResource(R.string.projeto_router_price_unknown)
                                }
                                Text(tierText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (openRouterPaid) {
                                    Text(stringResource(R.string.projeto_model_direct_use_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                } else if (result != null && result.status != ModelCheckStatus.OK) {
                                    Text(stringResource(R.string.projeto_model_checks_inactive), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (result?.status == ModelCheckStatus.OK && !result.toolsVerified) {
                                    Text(stringResource(R.string.projeto_model_checks_tools_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                    )
                    formItem {
                            if (progress.running && model.id in progress.activeModelIds) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = canCheck && !progress.running,
                                        onClick = {
                                            if (isFreeOrQuota || settings.hasCostAcknowledged(provider, model)) {
                                                manager.start(provider.id, model.id, confirmedFreeOnly = false)
                                            } else {
                                                pendingPaidCheck = provider to model
                                            }
                                        }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                                        elevation = null,
                                    ) {
                                        Text(stringResource(if (result == null) R.string.projeto_model_checks_test else R.string.projeto_model_checks_retest))
                                    }
                                    if (canApproveForRouter) {
                                        TextButton(
                                            enabled = !progress.running,
                                            onClick = {
                                                val needsCostConsent = !isConfirmedFreeProbe(provider, model) &&
                                                    !isFreeOrQuota &&
                                                    tier != ModelBillingTier.INCLUDED_BENEFIT &&
                                                    !settings.hasCostAcknowledged(provider, model)
                                                if (!isApproved && needsCostConsent) {
                                                    pendingApproveCost = provider to model
                                                } else {
                                                    scope.launch {
                                                        store.update { latest ->
                                                            val ids = latest.freeRouter.approvedModelIds
                                                            val approving = model.id !in ids
                                                            latest.copy(
                                                                freeRouter = latest.freeRouter.copy(
                                                                    enabled = if (approving) true else latest.freeRouter.enabled,
                                                                    approvedModelIds = if (approving) (ids + model.id).distinct() else ids - model.id,
                                                                    excludedModelIds = if (approving) latest.freeRouter.excludedModelIds - model.id else (latest.freeRouter.excludedModelIds + model.id).distinct(),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                                            elevation = null,
                                        ) {
                                            Text(stringResource(if (isApproved) R.string.projeto_model_checks_approved else R.string.projeto_model_checks_approve))
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    pendingPaidCheck?.let { (provider, model) ->
        AlertDialog(
            onDismissRequest = { pendingPaidCheck = null },
            title = { Text(stringResource(R.string.projeto_model_checks_paid_title)) },
            text = { Text(stringResource(R.string.projeto_model_checks_paid_body, provider.name, model.displayName.ifBlank { model.modelId })) },
            confirmButton = {
                TextButton(onClick = {
                    pendingPaidCheck = null
                    manager.start(provider.id, model.id, confirmedFreeOnly = false)
                }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,) { Text(stringResource(R.string.projeto_model_checks_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaidCheck = null }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingApproveCost?.let { (provider, model) ->
        AlertDialog(
            onDismissRequest = { pendingApproveCost = null },
            title = { Text(stringResource(R.string.projeto_router_cost_title)) },
            text = { Text(stringResource(R.string.projeto_router_cost_body, provider.name, model.displayName.ifBlank { model.modelId })) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingApproveCost = null
                        scope.launch {
                            store.update { latest ->
                                val currentProvider = latest.providers.firstOrNull { it.id == provider.id } ?: return@update latest
                                val currentModel = currentProvider.models.firstOrNull { it.id == model.id } ?: return@update latest
                                if (!latest.hasPassedModelCheck(currentProvider, currentModel)) return@update latest
                                val acknowledgement = ModelCostAcknowledgement(
                                    providerId = currentProvider.id,
                                    modelId = currentModel.id,
                                    fingerprint = modelCheckFingerprint(currentProvider, currentModel),
                                )
                                latest.copy(
                                    freeRouter = latest.freeRouter.copy(
                                        enabled = true,
                                        approvedModelIds = (latest.freeRouter.approvedModelIds + currentModel.id).distinct(),
                                        excludedModelIds = latest.freeRouter.excludedModelIds - currentModel.id,
                                        costAcknowledgements = (latest.freeRouter.costAcknowledgements.filterNot {
                                            it.providerId == acknowledgement.providerId && it.modelId == acknowledgement.modelId
                                        } + acknowledgement),
                                    ),
                                )
                            }
                        }
                    },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,
                ) { Text(stringResource(R.string.projeto_router_cost_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingApproveCost = null },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun statusLabel(status: ModelCheckStatus): Int = when (status) {
    ModelCheckStatus.OK -> R.string.projeto_model_check_ok
    ModelCheckStatus.TIMEOUT -> R.string.projeto_model_check_timeout
    ModelCheckStatus.AUTH_ERROR -> R.string.projeto_model_check_auth
    ModelCheckStatus.QUOTA -> R.string.projeto_model_check_quota
    ModelCheckStatus.UNAVAILABLE -> R.string.projeto_model_check_unavailable
    ModelCheckStatus.NETWORK -> R.string.projeto_model_check_network
    ModelCheckStatus.EMPTY -> R.string.projeto_model_check_empty
    ModelCheckStatus.INVALID_REPLY -> R.string.projeto_model_check_invalid
    ModelCheckStatus.CONFIGURATION -> R.string.projeto_model_check_configuration
    ModelCheckStatus.ERROR -> R.string.projeto_model_check_error
    ModelCheckStatus.BLOCKED_AUTH -> R.string.projeto_model_check_blocked
    ModelCheckStatus.BLOCKED_QUOTA -> R.string.projeto_model_check_blocked_quota
}
