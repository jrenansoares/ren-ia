package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import br.com.projetoia.app.R
import br.com.projetoia.app.Screen
import br.com.projetoia.app.data.model.ProgrammingSetupState
import br.com.projetoia.app.data.repository.SharedLinuxRepository
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun SharedLinuxPage() {
    val repository: SharedLinuxRepository = koinInject()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current

    LaunchedEffect(repository) { repository.check() }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.linux_page_title)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.linux_page_intro), style = MaterialTheme.typography.bodyLarge)
            SharedLinuxCard(
                state = state,
                onPrepare = repository::prepare,
                onCheck = { scope.launch { repository.check() } },
                onCancel = repository::cancel,
            )
            CardGroup(title = { Text(stringResource(R.string.linux_updates_title)) }) {
                item(headlineContent = { Text(stringResource(R.string.linux_updates_description)) })
                formItem {
                    val updates = state.updates
                    if (updates == null) Text(stringResource(R.string.linux_updates_not_checked))
                    else {
                        Text(stringResource(R.string.linux_updates_packages, updates.linuxPackageUpdates ?: 0))
                        val candidate = updates.claudeCandidate
                        when {
                            candidate == null -> Text(stringResource(R.string.linux_claude_candidate_unknown))
                            updates.claudeVersion == null -> Text(stringResource(R.string.linux_claude_not_installed))
                            else -> Text(stringResource(if (updates.claudeUpdateAvailable) R.string.linux_claude_update_available else R.string.linux_claude_up_to_date, candidate))
                        }
                    }
                    TextButton(onClick = repository::checkUpdates, enabled = !state.busy && state.ready, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                        Text(stringResource(R.string.linux_check_updates))
                    }
                    if (updates?.hasUpdates == true) Button(onClick = repository::upgrade, enabled = !state.busy && state.ready, modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                        Text(stringResource(R.string.linux_apply_updates))
                    }
                }
            }
            Text(
                stringResource(R.string.linux_page_project_files),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { nav.navigate(Screen.Workspaces) }, modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                Text(stringResource(R.string.projeto_drawer_projects))
            }
        }
    }
}

@Composable
internal fun SharedLinuxCard(
    state: ProgrammingSetupState,
    onPrepare: () -> Unit,
    onCheck: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
) {
    CardGroup(title = { Text(stringResource(R.string.programming_shared_title)) }) {
        item(
            headlineContent = {
                Text(stringResource(when {
                    state.busy -> R.string.linux_page_preparing
                    state.ready && state.environment?.claudeUsable == true -> R.string.programming_shared_ready
                    state.ready -> R.string.linux_claude_pending
                    state.linuxInstalled -> R.string.programming_shared_tools_missing
                    else -> R.string.programming_shared_not_installed
                }))
            },
            supportingContent = { Text(stringResource(R.string.programming_shared_desc)) },
        )
        formItem {
            state.environment?.let { environment ->
                Text(environment.linuxVersion)
                val version = environment.claudeVersion
                if (version != null) {
                    Text(stringResource(R.string.linux_claude_version, version))
                    Text(stringResource(if (environment.claudeUsable) R.string.linux_claude_verified else R.string.linux_claude_execution_failed),
                        color = if (environment.claudeUsable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                } else Text(stringResource(R.string.linux_claude_not_installed))
            }
            Text(stringResource(R.string.linux_claude_access), style = MaterialTheme.typography.bodySmall)
            ProgrammingPreparationStatus(state)
            if (state.busy) {
                TextButton(onClick = onCancel, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.programming_cancel)) }
            } else {
                Button(
                    onClick = if (state.ready && state.environment?.claudeUsable == true) onCheck else onPrepare,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,
                ) {
                    Text(stringResource(when {
                        state.ready && state.environment?.claudeUsable == true -> R.string.programming_shared_check
                        state.ready -> R.string.linux_complete_claude
                        state.linuxInstalled -> R.string.programming_shared_prepare_tools
                        else -> R.string.programming_shared_download
                    }))
                }
            }
        }
    }
}
