package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.projetoia.app.R
import br.com.projetoia.app.data.ai.*
import br.com.projetoia.app.data.datastore.SettingsStore
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import org.koin.compose.koinInject

@Composable
internal fun AutomaticRoutingCard() {
    val store = koinInject<SettingsStore>()
    val manager = koinInject<ProviderCatalogManager>()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val progress by manager.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val configured = settings.providers.filter { p -> p.enabled && p is ProviderSetting.ApiKey && p.apiKey.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ren_auto_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ren_auto_description), style = MaterialTheme.typography.bodySmall)
        if (progress.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.ren_auto_progress, progress.completed, progress.total, progress.tested))
            settings.providers.filter { it.id in progress.activeProviders }.forEach { Text(it.name, color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = manager::cancel) { Text(stringResource(R.string.cancel)) }
        } else {
            Button(onClick = { confirm = true }, enabled = configured.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(shape = RectangleShape, pressedShape = RectangleShape), elevation = null) {
                Text(stringResource(R.string.ren_auto_start))
            }
            if (settings.freeRouter.automaticManagement) TextButton(onClick = {
                scope.launch { manager.cancel(); store.update { it.copy(freeRouter = it.freeRouter.copy(automaticManagement = false)) } }
            }) { Text(stringResource(R.string.ren_auto_pause)) }
        }
        if (progress.failedToSave) Text(stringResource(R.string.projeto_model_checks_save_error), color = MaterialTheme.colorScheme.error)
    }
    if (confirm) {
        var selected by remember { mutableStateOf(configured.filter { settings.hasProviderRoutingAuthorization(it) }.map { it.id }.toSet()) }
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.ren_auto_title)) },
            text = { Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ren_auto_consent))
                configured.forEach { provider ->
                    if (ConnectionService.OPENROUTER.matches(provider)) Text(stringResource(R.string.ren_auto_openrouter_free))
                    else Row {
                        Checkbox(checked = provider.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + provider.id else selected - provider.id })
                        Text(provider.name)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                confirm = false
                scope.launch {
                    store.update { latest ->
                        var next = latest.copy(freeRouter = latest.freeRouter.copy(automaticManagement = true,
                            providerAuthorizations = latest.freeRouter.providerAuthorizations.filterNot { a -> configured.any { it.id == a.providerId } }))
                        latest.providers.filter { p -> p.id in selected && configured.any { old -> old.id == p.id && providerConnectionFingerprint(old) == providerConnectionFingerprint(p) } }
                            .forEach { next = next.authorizeProviderRouting(it) }
                        next
                    }
                    manager.start(force = true)
                }
            }) { Text(stringResource(R.string.ren_auto_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
