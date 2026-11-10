package br.com.projetoia.app.ui.pages.extensions.workspace

import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FolderCode
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.FileExport
import br.com.projetoia.app.Screen
import br.com.projetoia.app.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import br.com.projetoia.app.R
import br.com.projetoia.app.ui.components.ai.ExtensionEmptyState
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.components.ui.CardGroupIcon
import br.com.projetoia.app.ui.components.ui.ItemAction
import br.com.projetoia.app.ui.components.ui.ItemActionMenu
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.ui.theme.CustomColors
import br.com.projetoia.app.utils.plus
import me.rerere.ui.components.ProjetoIAConfirmDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkspacePage(vm: WorkspaceVM = koinViewModel()) {
    val navController = LocalNavController.current
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    val context = LocalContext.current
    val archive by vm.archive.collectAsStateWithLifecycle()
    var exportProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportProjectId.also { exportProjectId = null }
        if (uri != null && id != null) vm.exportArchive(id, uri, context.contentResolver)
    }
    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importArchive(uri, context.contentResolver, context.cacheDir)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.workspace_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTypeSheet = true }, shape = androidx.compose.ui.graphics.RectangleShape, elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.SharedLinux) },
                        headlineContent = { Text(stringResource(R.string.linux_page_title)) },
                        supportingContent = { Text(stringResource(R.string.linux_page_intro)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Repositories) },
                        headlineContent = { Text(stringResource(R.string.projeto_repositories_title)) },
                        supportingContent = { Text(stringResource(R.string.projeto_repositories_intro)) },
                    )
                }
            }
            if (workspaces.isEmpty()) {
                item {
                    ExtensionEmptyState(
                        message = stringResource(R.string.workspace_page_empty),
                        description = stringResource(R.string.workspace_page_empty_desc),
                        icon = HugeIcons.FolderCode,
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.project_archive_import)) },
                        supportingContent = { Text(stringResource(R.string.project_archive_desc)) },
                        onClick = if (archive.busy) null else { { importArchive.launch(arrayOf("*/*")) } },
                    )
                }
            }

            items(workspaces, key = { it.id }) { workspace ->
                WorkspaceItem(
                    workspace = workspace,
                    onRename = { editTarget = workspace },
                    onDelete = { deleteTarget = workspace },
                    onOpen = { navController.navigate(Screen.WorkspaceDetail(workspace.id)) },
                    onArchive = if (!workspace.isSsh() && !archive.busy) { {
                        exportProjectId = workspace.id
                        exportArchive.launch("projeto_ia_${workspace.id.take(8)}.zip")
                    } } else null,
                )
            }
        }
    }

    if (archive.busy || archive.completed || archive.error != null) {
        AlertDialog(
            onDismissRequest = { if (!archive.busy) vm.dismissArchiveResult() },
            title = { Text(stringResource(R.string.project_archive_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (archive.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.project_archive_progress, archive.progress.files, archive.progress.bytes / 1_048_576.0))
                    } else if (archive.error != null) {
                        Text(stringResource(R.string.project_archive_error))
                        Text(archive.error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(stringResource(if (archive.restoredName != null) R.string.project_archive_restored else R.string.project_archive_saved))
                        archive.restoredName?.let { Text(it) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (archive.busy) vm.cancelArchive() else vm.dismissArchiveResult() }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(if (archive.busy) R.string.cancel else R.string.common_confirm)) } },
        )
    }
    if (showTypeSheet) {
        ModalBottomSheet(onDismissRequest = { showTypeSheet = false }, tonalElevation = 0.dp, shape = androidx.compose.ui.graphics.RectangleShape) {
            CardGroup(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                title = { Text(stringResource(R.string.workspace_page_create)) },
            ) {
                item(
                    onClick = {
                        showTypeSheet = false
                        showAddDialog = true
                    },
                    leadingContent = { CardGroupIcon(HugeIcons.FolderCode) },
                    supportingContent = { Text(stringResource(R.string.workspace_page_type_local_desc)) },
                    headlineContent = { Text(stringResource(R.string.workspace_page_type_local)) },
                )
                item(
                    onClick = {
                        showTypeSheet = false
                        navController.navigate(Screen.WorkspaceSshEditor())
                    },
                    leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
                    supportingContent = { Text(stringResource(R.string.workspace_page_type_ssh_desc)) },
                    headlineContent = { Text(stringResource(R.string.workspace_page_type_ssh)) },
                )
            }
        }
    }

    if (showAddDialog) {
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_create),
            initialName = "",
            existingNames = workspaces.map { it.name.trim() }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                vm.create(name)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { workspace ->
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_rename),
            initialName = workspace.name,
            existingNames = workspaces.filter { it.id != workspace.id }.map { it.name.trim() }.toSet(),
            onDismiss = { editTarget = null },
            onConfirm = { name ->
                vm.rename(workspace, name)
                editTarget = null
            },
        )
    }

    ProjetoIAConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.workspace_page_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.workspace_page_delete_confirm))
    }
}

@Composable
private fun WorkspaceItem(
    workspace: WorkspaceEntity,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onArchive: (() -> Unit)?,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(if (workspace.isSsh()) HugeIcons.CloudServer else HugeIcons.FolderCode)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = workspace.statusLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ItemActionMenu(
                actions = listOf(
                    *if (onArchive != null) arrayOf(ItemAction(text = stringResource(R.string.project_archive_export), icon = HugeIcons.FileExport, onClick = onArchive)) else emptyArray(),
                    ItemAction(
                        text = stringResource(R.string.common_rename),
                        icon = HugeIcons.PencilEdit01,
                        onClick = onRename,
                    ),
                    ItemAction(
                        text = stringResource(R.string.common_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@Composable
private fun EditWorkspaceDialog(
    title: String,
    initialName: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotEmpty() && trimmedName in existingNames

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_page_name)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.workspace_page_name_duplicate)) }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = name.isNotBlank() && !isDuplicate, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                elevation = null,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
