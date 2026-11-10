package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsConversation
import br.com.projetoia.app.R
import br.com.projetoia.app.Screen
import br.com.projetoia.app.data.ai.*
import java.text.DateFormat
import java.util.Date
import br.com.projetoia.app.data.ai.ConnectionService
import br.com.projetoia.app.data.ai.conversationalCatalog
import br.com.projetoia.app.data.ai.routingFailureCode
import br.com.projetoia.app.data.ai.saveConnection
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.components.ui.AutoAIIcon
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.utils.openUrl
import org.koin.compose.koinInject

@Composable
fun ConnectionsPage() {
    val store = koinInject<SettingsStore>()
    val manager = koinInject<ProviderManager>()
    val catalogManager = koinInject<ProviderCatalogManager>()
    val catalogProgress by catalogManager.progress.collectAsStateWithLifecycle()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ConnectionService?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.projeto_connections)) }, navigationIcon = { BackButton() })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(stringResource(R.string.projeto_connections_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                    Text(stringResource(R.string.projeto_connection_add_button))
                }
            }
            item {
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        onClick = { nav.navigate(Screen.ModelChecks) },
                        headlineContent = { Text(stringResource(R.string.projeto_model_checks_open)) },
                        supportingContent = { Text(stringResource(R.string.projeto_model_checks_intro)) },
                    )
                }
            }
            item { AutomaticRoutingCard() }
            notice?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.primary) } }
            items(ConnectionService.entries.filter { service -> settings.providers.any { service.matches(it) && (it as? ProviderSetting.ApiKey)?.apiKey?.isNotBlank() == true } }) { service ->
                val connection = settings.providers.firstOrNull { service.matches(it) } as? ProviderSetting.ApiKey
                val configured = !connection?.apiKey.isNullOrBlank()
                val count = connection?.models?.count { settings.hasPassedModelCheck(connection, it) && settings.catalogAllows(connection, it) && (settings.routeHealth(connection, it)?.cooldownUntil ?: 0) <= System.currentTimeMillis() } ?: 0
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        onClick = { selected = service },
                        leadingContent = { AutoAIIcon(service.title, modifier = Modifier.size(36.dp)) },
                        headlineContent = { Text(service.title, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (configured) stringResource(R.string.ren_key_models, count, connection?.models?.size ?: 0) else stringResource(R.string.projeto_connection_add))
                                connection?.let { provider ->
                                    val state = settings.catalogState(provider)
                                    Text(stringResource(when {
                                        state?.status == ModelCheckStatus.AUTH_ERROR -> R.string.ren_key_auth_failed
                                        provider.models.any { settings.hasPassedModelCheck(provider, it) } -> R.string.ren_key_inference_ok
                                        state?.status == ModelCheckStatus.OK -> R.string.ren_key_catalog_ok
                                        state?.status == ModelCheckStatus.QUOTA -> R.string.ren_key_limited
                                        state == null -> R.string.ren_key_not_checked
                                        else -> R.string.ren_key_check_failed
                                    }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    /* Catalog success alone never proves that a model can infer. */
                                    if (state != null) Text(stringResource(R.string.ren_key_checked_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.attemptedAt))), style = MaterialTheme.typography.bodySmall)
                                    state?.httpStatus?.let { Text(stringResource(R.string.projeto_model_checks_http_status, it)) }
                                }
                            }
                        },
                        trailingContent = { Text(stringResource(if (configured) R.string.projeto_connection_edit else R.string.projeto_connection_connect), color = MaterialTheme.colorScheme.primary) },
                    )
                    formItem {
                        TextButton(enabled = !catalogProgress.running, onClick = { connection?.let { catalogManager.start(force = true, providerId = it.id) } }) {
                            Text(stringResource(R.string.ren_key_check))
                        }
                    }
                }
            }
            items(settings.providers.filter { p -> p is ProviderSetting.ApiKey && p.apiKey.isNotBlank() && ConnectionService.entries.none { it.matches(p) } }, key = { it.id.toString() }) { provider ->
                CardGroup {
                    item(onClick = { nav.navigate(Screen.SettingProvider) }, headlineContent = { Text(provider.name) },
                        supportingContent = {
                            val state = settings.catalogState(provider)
                            Text(stringResource(if (state?.status == ModelCheckStatus.OK) R.string.ren_key_catalog_ok else R.string.ren_key_not_checked))
                        })
                    formItem { TextButton(enabled = !catalogProgress.running, onClick = { catalogManager.start(force = true, providerId = provider.id) }) { Text(stringResource(R.string.ren_key_check)) } }
                }
            }
            item {
                CardGroup {
                    item(onClick = { nav.navigate(Screen.FreeRouter) }, headlineContent = { Text(stringResource(R.string.projeto_router)) }, supportingContent = { Text(stringResource(R.string.projeto_router_desc)) })
                    item(onClick = { nav.navigate(Screen.SettingProvider) }, headlineContent = { Text(stringResource(R.string.projeto_connections_advanced)) }, supportingContent = { Text(stringResource(R.string.projeto_connections_advanced_desc)) })
                }
            }
        }
    }
    if (choosing) {
        var search by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { choosing = false }, tonalElevation = 0.dp, shape = androidx.compose.ui.graphics.RectangleShape) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(R.string.projeto_connection_add_button), style = MaterialTheme.typography.headlineSmall) }
                item { OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text(stringResource(R.string.projeto_connection_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                items(ConnectionService.entries.filter { it.title.contains(search, ignoreCase = true) }) { service ->
                    CardGroup {
                        item(
                            onClick = { choosing = false; selected = service },
                            leadingContent = { AutoAIIcon(service.title, modifier = Modifier.size(32.dp)) },
                            headlineContent = { Text(service.title) },
                            supportingContent = { Text(stringResource(R.string.projeto_connection_catalog_note)) },
                        )
                    }
                }
            }
        }
    }
    selected?.let { service ->
        val existing = settings.providers.firstOrNull { service.matches(it) } as? ProviderSetting.ApiKey
        var key by remember(service) { mutableStateOf(existing?.apiKey.orEmpty()) }
        var busy by remember(service) { mutableStateOf(false) }
        var error by remember(service) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text(service.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.projeto_connection_help))
                    OutlinedTextField(
                        value = key, onValueChange = { key = it; error = null }, enabled = !busy,
                        label = { Text(stringResource(R.string.projeto_connection_key)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { context.openUrl(service.keyUrl) }, enabled = !busy, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.projeto_connection_get_key)) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) CircularProgressIndicator()
                }
            },
            confirmButton = {
                Button(enabled = !busy && key.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val configured = service.configure(key, existing)
                            val models = manager.getProviderByType(configured).listModels(configured)
                            val compatible = conversationalCatalog(models)
                            // The built-in OpenRouter free endpoint is not always returned in /models.
                            val candidates = (compatible + configured.models.filter { it.modelId == "openrouter/free" }).distinctBy { it.modelId }
                            store.update { latest ->
                                val updated = latest.saveConnection(service, key, models + configured.models.filter { model -> model.modelId == "openrouter/free" })
                                val saved = updated.providers.first { service.matches(it) }
                                updated.mergeProviderCatalog(saved, models, System.currentTimeMillis())
                            }
                            if (store.settingsFlow.value.freeRouter.automaticManagement) catalogManager.start(force = true, providerId = store.settingsFlow.value.providers.firstOrNull { service.matches(it) }?.id)
                            notice = if (candidates.isEmpty()) resources.getString(R.string.projeto_connection_no_models) else resources.getString(R.string.projeto_connection_success, candidates.size)
                            selected = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = resources.getString(when {
                                failure is IllegalArgumentException -> R.string.projeto_connection_invalid_key
                                routingFailureCode(failure) in listOf(401, 403) -> R.string.projeto_connection_auth_error
                                routingFailureCode(failure) == 429 -> R.string.projeto_connection_quota_error
                                else -> R.string.projeto_connection_network_error
                            })
                        } finally {
                            busy = false
                        }
                    }
                }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,) { Text(stringResource(R.string.projeto_connection_test_save)) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !busy, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
