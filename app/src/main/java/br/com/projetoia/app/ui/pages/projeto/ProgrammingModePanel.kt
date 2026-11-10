package br.com.projetoia.app.ui.pages.projeto

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Codesandbox
import br.com.projetoia.app.R
import br.com.projetoia.app.Screen
import br.com.projetoia.app.data.ai.isProgrammingRouteAllowed
import br.com.projetoia.app.data.ai.programmingRoutes
import br.com.projetoia.app.data.ai.programmingAutoApprovalEnabled
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.model.Conversation
import br.com.projetoia.app.data.model.ProgrammingPhase
import br.com.projetoia.app.data.model.ProgrammingSetupState
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.components.ui.switchItem
import br.com.projetoia.app.ui.context.LocalNavController
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.ProgrammingInstallStage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.util.Locale

@Composable
internal fun ProgrammingPreparationStatus(state: ProgrammingSetupState) {
    var showDetails by rememberSaveable(state.selectedId) { mutableStateOf(false) }
    val now by produceState(SystemClock.elapsedRealtime(), state.busy, state.startedAtMillis) {
        value = SystemClock.elapsedRealtime()
        while (state.busy) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.smokeTestPassed) Text(stringResource(R.string.programming_test_passed), color = MaterialTheme.colorScheme.primary)
    if (state.busy) {
        val stageText = when (state.phase) {
            ProgrammingPhase.CREATING -> R.string.programming_creating
            ProgrammingPhase.INSTALLING -> when (state.progress?.stage) {
                RootfsInstallStage.VERIFYING -> R.string.programming_verifying
                RootfsInstallStage.EXTRACTING -> R.string.programming_extracting
                RootfsInstallStage.PATCHING, RootfsInstallStage.INSTALLED -> R.string.programming_patching
                else -> R.string.programming_downloading
            }
            ProgrammingPhase.TOOLS -> when (state.installProgress?.stage) {
                ProgrammingInstallStage.REPAIR -> R.string.programming_repairing
                ProgrammingInstallStage.INDEX -> R.string.programming_index
                ProgrammingInstallStage.DOWNLOAD -> R.string.programming_packages_download
                ProgrammingInstallStage.INSTALL -> R.string.programming_packages_install
                ProgrammingInstallStage.DONE -> R.string.programming_checking
                null -> R.string.programming_tools
            }
            ProgrammingPhase.TESTING -> R.string.programming_testing
            ProgrammingPhase.CLAUDE -> R.string.linux_installing_claude
            ProgrammingPhase.CHECKING_UPDATES -> R.string.linux_checking_updates
            ProgrammingPhase.UPDATING -> R.string.linux_updating
            else -> R.string.programming_checking
        }
        Text(stringResource(stageText), style = MaterialTheme.typography.titleSmall)
        if (state.phase == ProgrammingPhase.INSTALLING) {
            state.progress?.let { progress ->
                val total = progress.totalBytes
                if (progress.stage == RootfsInstallStage.DOWNLOADING || progress.stage == RootfsInstallStage.VERIFYING) {
                    if (total != null && total > 0) {
                        val fraction = (progress.bytesRead.toDouble() / total).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.programming_transfer_total, progress.bytesRead / 1_048_576.0, total / 1_048_576.0, (fraction * 100).toInt()))
                    } else {
                        Text(stringResource(R.string.programming_transfer_unknown, progress.bytesRead / 1_048_576.0))
                    }
                    val elapsed = now - state.downloadStartedAtMillis
                    if (progress.stage == RootfsInstallStage.DOWNLOADING && elapsed > 0 && progress.bytesRead > 0) {
                        Text(stringResource(R.string.programming_transfer_speed, progress.bytesRead / 1024.0 / (elapsed / 1000.0)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (progress.stage == RootfsInstallStage.EXTRACTING) {
                    Text(stringResource(R.string.programming_extracted_count, progress.entriesExtracted))
                    progress.currentEntry?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (state.phase in setOf(ProgrammingPhase.TOOLS, ProgrammingPhase.CLAUDE, ProgrammingPhase.CHECKING_UPDATES, ProgrammingPhase.UPDATING)) {
            state.installProgress?.let { progress ->
                progress.fraction?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.programming_stage_percent, (fraction * 100).toInt()))
                }
                if (progress.packageName.isNotBlank()) Text(progress.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress.message.isNotBlank()) Text(progress.message, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (state.startedAtMillis > 0 && (state.busy || state.details.isNotBlank() || state.error != null)) {
        val elapsed = ((state.finishedAtMillis ?: now) - state.startedAtMillis).coerceAtLeast(0)
        Text(stringResource(R.string.programming_elapsed, setupDuration(elapsed)), style = MaterialTheme.typography.bodySmall)
        if (state.busy) {
            val silence = (now - state.lastActivityAtMillis).coerceAtLeast(0)
            Text(stringResource(R.string.programming_last_activity, setupDuration(silence)), style = MaterialTheme.typography.bodySmall)
            if (silence >= 90_000) Text(stringResource(R.string.programming_no_activity), color = MaterialTheme.colorScheme.error)
        }
    }
    if (state.details.isNotBlank()) {
        TextButton(onClick = { showDetails = !showDetails }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
            Text(stringResource(if (showDetails) R.string.programming_hide_details else R.string.programming_show_details))
        }
        if (showDetails) {
            val scroll = rememberScrollState()
            LaunchedEffect(state.details, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
            Text(state.details, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(scroll), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun setupDuration(millis: Long): String {
    val seconds = millis / 1_000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

@Composable
fun ProgrammingModePanel(
    conversation: Conversation,
    settings: Settings,
    executing: Boolean,
    openInitially: Boolean = false,
) {
    val vm: ProgrammingVM = koinViewModel(parameters = { parametersOf(conversation.id.toString()) })
    val state by vm.state.collectAsStateWithLifecycle()
    val sharedLinux by vm.sharedLinuxState.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val active = conversation.config?.programmingMode == true
    val autoApprove = conversation.programmingAutoApprovalEnabled
    val attached = workspaces.firstOrNull { it.id == conversation.config?.workspaceId?.toString() }
    var show by rememberSaveable(conversation.id.toString()) { mutableStateOf(openInitially) }
    var name by rememberSaveable { mutableStateOf("") }
    val project = workspaces.firstOrNull { it.id == state.selectedId }
    val approvedCount = settings.programmingRoutes().size
    val availableCount = settings.providers.sumOf { provider -> provider.models.count { isProgrammingRouteAllowed(provider, it) } }

    LaunchedEffect(show) { if (show) vm.refreshSharedLinux() }
    LaunchedEffect(show, sharedLinux.ready, sharedLinux.busy, state.selectedId, project?.shellStatus) {
        if (show && !sharedLinux.busy) vm.refreshProject()
    }

    LaunchedEffect(show, workspaces.map { it.id }, conversation.config?.workspaceId) {
        if (show && state.selectedId == null) {
            (attached ?: workspaces.firstOrNull())?.let { vm.selectProject(it.id) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = active,
            onClick = { show = true },
            label = { Text(if (active && conversation.config?.programmingClaudeCode == true) "Claude Code" else stringResource(if (autoApprove) R.string.programming_title_auto else R.string.programming_title)) },
            leadingIcon = { Icon(HugeIcons.Codesandbox, null, modifier = Modifier.size(18.dp)) },
            elevation = null,
        )
        Text(
            text = attached?.name ?: stringResource(R.string.programming_engine),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    if (!show) return
    ModalBottomSheet(
        onDismissRequest = { show = false },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        tonalElevation = 0.dp,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.programming_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.programming_intro), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.programming_engine_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SharedLinuxCard(
                state = sharedLinux,
                onPrepare = vm::prepareSharedLinux,
                onCheck = vm::refreshSharedLinux,
                onCancel = vm::cancelSharedLinux,
                enabled = !state.busy && !executing,
            )

            if (workspaces.isNotEmpty()) {
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    CardGroup(title = { Text(stringResource(R.string.programming_choose_project)) }) {
                        workspaces.forEach { workspace ->
                            item(
                                onClick = if (state.busy || executing) null else { { vm.selectProject(workspace.id) } },
                                headlineContent = { Text(workspace.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { RadioButton(selected = state.selectedId == workspace.id, onClick = null) },
                            )
                        }
                    }
                }
            }
            CardGroup {
                formItem {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        enabled = !state.busy && !executing,
                        singleLine = true,
                        label = { Text(stringResource(R.string.programming_project_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.createProject(name) }, enabled = name.isNotBlank() && !state.busy && !executing, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                        Text(stringResource(R.string.programming_create))
                    }
                }
            }

            if (project != null) {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(if (state.ready) R.string.programming_ready else R.string.programming_not_ready)) },
                    supportingContent = { Text(stringResource(if (state.usingSharedLinux) R.string.programming_shared_project else R.string.programming_private_project)) },
                    )
                    formItem {
                        ProgrammingPreparationStatus(state)
                        if (state.busy) {
                            TextButton(onClick = vm::cancel, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.programming_cancel)) }
                        } else {
                            Button(onClick = vm::prepare, enabled = !executing && !sharedLinux.busy && (!state.usingSharedLinux || sharedLinux.ready), modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                                Text(stringResource(if (state.usingSharedLinux) R.string.programming_test else R.string.programming_prepare))
                            }
                            if (!state.usingSharedLinux || state.hasPrivateLinux) TextButton(
                                onClick = { vm.setSharedLinux(!state.usingSharedLinux) },
                                enabled = !executing && !sharedLinux.busy && (state.usingSharedLinux || sharedLinux.ready), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                                elevation = null,
                            ) { Text(stringResource(if (state.usingSharedLinux) R.string.programming_shared_previous else R.string.programming_shared_use)) }
                            if (state.usingSharedLinux && !sharedLinux.ready) Text(stringResource(R.string.programming_shared_required), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row {
                    TextButton(onClick = { show = false; nav.navigate(Screen.WorkspaceDetail(project.id)) }, enabled = !state.busy, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.programming_files)) }
                    TextButton(onClick = { show = false; nav.navigate(Screen.WorkspaceTerminal(project.id)) }, enabled = !state.busy && project.shellStatus == "READY", shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.programming_terminal)) }
                }
            }

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.programming_free_count, approvedCount)) },
                    supportingContent = { Text(stringResource(R.string.programming_route_policy)) },
                )
                formItem {
                    if (availableCount > 0) TextButton(
                        enabled = !state.busy && !executing,
                        onClick = { show = false; nav.navigate(Screen.ModelChecks) }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                        elevation = null,
                    ) { Text(stringResource(R.string.projeto_model_checks_open)) }
                    if (availableCount > 0) TextButton(
                        enabled = !state.busy && !executing,
                        onClick = { show = false; nav.navigate(Screen.FreeRouter) }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                        elevation = null,
                    ) { Text(stringResource(R.string.projeto_router)) }
                    if (availableCount == 0) Text(stringResource(R.string.programming_route_required), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { show = false; nav.navigate(Screen.Connections) }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.projeto_connections)) }
                }
            }
            CardGroup {
                switchItem(
                    checked = active && conversation.config?.programmingClaudeCode == true,
                    onCheckedChange = vm::setClaudeCode,
                    enabled = active && !state.busy && !executing && sharedLinux.environment?.claudeUsable == true,
                    headlineContent = { Text(stringResource(R.string.programming_claude_chat)) },
                    supportingContent = { Text(stringResource(R.string.programming_claude_chat_desc)) },
                )
                if (active && conversation.config?.programmingClaudeCode == true) switchItem(
                    checked = conversation.config.programmingConsultants,
                    onCheckedChange = vm::setConsultants,
                    enabled = active && !state.busy && !executing,
                    headlineContent = { Text(stringResource(R.string.programming_consultants)) },
                    supportingContent = { Text(stringResource(R.string.programming_consultants_desc)) },
                )
                switchItem(
                    checked = autoApprove,
                    onCheckedChange = vm::setAutoApproval,
                    enabled = active && !state.busy && !executing,
                    headlineContent = { Text(stringResource(R.string.programming_auto_approval)) },
                    supportingContent = { Text(stringResource(if (active) R.string.programming_auto_approval_desc else R.string.programming_activate_first)) },
                )
            }
            if (executing) Text(stringResource(R.string.programming_stop_first), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (autoApprove) R.string.programming_consent_auto else R.string.programming_consent), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { vm.activate { show = false } },
                enabled = project != null && state.ready && approvedCount > 0 && !state.busy && !executing,
                modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                elevation = null,
            ) { Text(stringResource(R.string.programming_start)) }
            if (active) TextButton(onClick = { vm.deactivate { show = false } }, enabled = !state.busy && !executing, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                Text(stringResource(R.string.programming_detach))
            }
        }
    }
}
