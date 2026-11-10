package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import br.com.projetoia.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import br.com.projetoia.app.data.ai.isAutomaticRouteCandidate
import br.com.projetoia.app.data.ai.isConfirmedFreeProbe
import br.com.projetoia.app.data.ai.hasPassedModelCheck
import br.com.projetoia.app.data.ai.hasCostAcknowledged
import br.com.projetoia.app.data.ai.modelCheckFingerprint
import br.com.projetoia.app.data.ai.modelStabilityScore
import br.com.projetoia.app.data.ai.ModelCheckManager
import br.com.projetoia.app.data.ai.ModelCostAcknowledgement
import br.com.projetoia.app.data.ai.providerFallbackPriority
import br.com.projetoia.app.data.ai.routeScore
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.Screen
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.Model
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.context.LocalNavController
import org.koin.compose.koinInject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun FreeRouterPage() {
    val store = koinInject<SettingsStore>()
    val checkManager = koinInject<ModelCheckManager>()
    val checkProgress by checkManager.progress.collectAsStateWithLifecycle()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    var pendingCostAuthorization by remember { mutableStateOf<Pair<ProviderSetting, Model>?>(null) }
    val available = settings.providers.flatMap { provider ->
        provider.models.filter { isAutomaticRouteCandidate(provider, it) }.map { provider to it }
    }.sortedWith(
        compareBy<Pair<ProviderSetting, Model>> { (provider, model) ->
            when {
                model.id in settings.freeRouter.approvedModelIds && settings.hasPassedModelCheck(provider, model) -> 0
                settings.hasPassedModelCheck(provider, model) -> 1
                else -> 2
            }
        }
            .thenBy { providerFallbackPriority(it.first) }
            .thenByDescending { modelStabilityScore(it.second) + routeScore(it.second, "programação código") }
            .thenBy { it.second.displayName.ifBlank { it.second.modelId }.lowercase() }
    )
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.projeto_router)) }, navigationIcon = { BackButton() }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.projeto_router_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                TextButton(onClick = { nav.navigate(Screen.ModelChecks) }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                    Text(stringResource(R.string.projeto_model_checks_open))
                }
            }
            val approvedAndVerified = available.any { (provider, model) ->
                model.id in settings.freeRouter.approvedModelIds && settings.hasPassedModelCheck(provider, model) &&
                    (isConfirmedFreeProbe(provider, model) || settings.hasCostAcknowledged(provider, model))
            }
            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.projeto_router_enable)) },
                        supportingContent = { Text(stringResource(R.string.projeto_router_enable_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.freeRouter.enabled,
                                enabled = settings.freeRouter.enabled || approvedAndVerified,
                                onCheckedChange = { enabled ->
                                    if (!enabled || approvedAndVerified) {
                                        scope.launch { store.update { latest ->
                                            latest.copy(freeRouter = latest.freeRouter.copy(enabled = enabled))
                                        } }
                                    }
                                },
                            )
                        },
                    )
                }
            }
            item {
                Text(stringResource(R.string.projeto_router_terms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (available.isEmpty()) item {
                Text(stringResource(R.string.projeto_router_empty))
            }
            if (available.isNotEmpty()) item {
                val selectable = available.filter { (provider, model) ->
                    settings.hasPassedModelCheck(provider, model) &&
                        (isConfirmedFreeProbe(provider, model) || settings.hasCostAcknowledged(provider, model))
                }
                val allSelected = selectable.isNotEmpty() && selectable.all { (_, model) -> model.id in settings.freeRouter.approvedModelIds }
                TextButton(onClick = {
                    scope.launch { store.update { latest ->
                        val verifiedIds = latest.providers.flatMap { provider ->
                            provider.models.filter {
                                isAutomaticRouteCandidate(provider, it) && latest.hasPassedModelCheck(provider, it) &&
                                    (isConfirmedFreeProbe(provider, it) || latest.hasCostAcknowledged(provider, it))
                            }.map { it.id }
                        }
                        val clear = verifiedIds.isNotEmpty() && verifiedIds.all { it in latest.freeRouter.approvedModelIds }
                        latest.copy(freeRouter = latest.freeRouter.copy(
                            approvedModelIds = if (clear) latest.freeRouter.approvedModelIds - verifiedIds.toSet() else (latest.freeRouter.approvedModelIds + verifiedIds).distinct(),
                            excludedModelIds = if (clear) (latest.freeRouter.excludedModelIds + verifiedIds).distinct() else latest.freeRouter.excludedModelIds - verifiedIds.toSet(),
                        ))
                    } }
                }, enabled = selectable.isNotEmpty(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,) {
                    Text(stringResource(if (allSelected) R.string.projeto_router_clear else R.string.projeto_router_select_all))
                }
            }
            items(available, key = { (provider, model) -> "${provider.id}:${model.id}" }) { (provider, model) ->
                val checked = model.id in settings.freeRouter.approvedModelIds
                val verified = settings.hasPassedModelCheck(provider, model)
                val confirmedFree = isConfirmedFreeProbe(provider, model)
                val costAcknowledged = settings.hasCostAcknowledged(provider, model)
                val routeReady = verified && (confirmedFree || costAcknowledged)
                fun toggle() {
                    if (!verified) {
                        if (!checkProgress.running) {
                            checkManager.start(provider.id, model.id, confirmedFreeOnly = false)
                        }
                        return
                    }
                    if (!routeReady) {
                        if (!confirmedFree && !costAcknowledged) pendingCostAuthorization = provider to model
                        return
                    }
                    scope.launch {
                        store.update {
                            val ids = it.freeRouter.approvedModelIds
                            val approving = model.id !in ids
                            it.copy(freeRouter = it.freeRouter.copy(
                                enabled = if (approving) true else it.freeRouter.enabled,
                                approvedModelIds = if (model.id in ids) ids - model.id else ids + model.id,
                                excludedModelIds = if (model.id in ids) (it.freeRouter.excludedModelIds + model.id).distinct() else it.freeRouter.excludedModelIds - model.id,
                            ))
                        }
                    }
                }
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        onClick = { toggle() },
                        headlineContent = { Text(model.displayName.ifBlank { model.modelId }) },
                        supportingContent = {
                            Text("${provider.name} · ${if (verified) stringResource(R.string.projeto_model_check_ok) else stringResource(R.string.projeto_model_check_not_tested)} · ${stringResource(if (confirmedFree) R.string.projeto_router_price_free else if (costAcknowledged) R.string.projeto_router_price_confirmed else R.string.projeto_router_price_unknown)}")
                        },
                        trailingContent = { Checkbox(checked = checked, enabled = !checkProgress.running, onCheckedChange = { toggle() }) },
                    )
                }
            }
            item {
                Text(stringResource(R.string.projeto_router_resume), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    pendingCostAuthorization?.let { (provider, model) ->
        AlertDialog(
            onDismissRequest = { pendingCostAuthorization = null },
            title = { Text(stringResource(R.string.projeto_router_cost_title)) },
            text = { Text(stringResource(R.string.projeto_router_cost_body, provider.name, model.displayName.ifBlank { model.modelId })) },
            confirmButton = {
                TextButton(onClick = {
                    pendingCostAuthorization = null
                    scope.launch { store.update { latest ->
                        val currentProvider = latest.providers.firstOrNull { it.id == provider.id } ?: return@update latest
                        val currentModel = currentProvider.models.firstOrNull { it.id == model.id } ?: return@update latest
                        if (!latest.hasPassedModelCheck(currentProvider, currentModel)) return@update latest
                        val acknowledgement = ModelCostAcknowledgement(
                            providerId = currentProvider.id,
                            modelId = currentModel.id,
                            fingerprint = modelCheckFingerprint(currentProvider, currentModel),
                        )
                        latest.copy(freeRouter = latest.freeRouter.copy(
                            approvedModelIds = (latest.freeRouter.approvedModelIds + currentModel.id).distinct(),
                            excludedModelIds = latest.freeRouter.excludedModelIds - currentModel.id,
                            costAcknowledgements = (latest.freeRouter.costAcknowledgements.filterNot {
                                it.providerId == acknowledgement.providerId && it.modelId == acknowledgement.modelId
                            } + acknowledgement),
                        ))
                    } }
                }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,) { Text(stringResource(R.string.projeto_router_cost_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCostAuthorization = null }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
