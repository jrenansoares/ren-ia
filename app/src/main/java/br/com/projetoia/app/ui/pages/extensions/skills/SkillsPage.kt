package br.com.projetoia.app.ui.pages.extensions.skills

import br.com.projetoia.app.ui.theme.JetbrainsMono

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.projetoia.app.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Search01
import br.com.projetoia.app.data.skills.InvalidSkillDir
import br.com.projetoia.app.data.skills.InvalidSkillReason
import br.com.projetoia.app.data.skills.SkillMetadata
import br.com.projetoia.app.data.skills.SkillValidationError
import br.com.projetoia.app.data.skills.SkillValidationException
import br.com.projetoia.app.data.skills.SkillValidator
import br.com.projetoia.app.Screen
import br.com.projetoia.app.ui.components.ai.ExtensionEmptyState
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.components.ui.CardGroupIcon
import br.com.projetoia.app.ui.components.ui.ItemAction
import br.com.projetoia.app.ui.components.ui.ItemActionMenu
import br.com.projetoia.app.ui.components.ui.Tag
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.ui.context.LocalToaster
import br.com.projetoia.app.ui.theme.CustomColors
import br.com.projetoia.app.utils.plus
import me.rerere.ui.components.ProjetoIAConfirmDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillsPage() {
    val navController = LocalNavController.current
    val vm = koinViewModel<SkillsVM>()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val invalidSkillDirs by vm.invalidSkillDirs.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<SkillMetadata?>(null) }
    var invalidDeleteTarget by remember { mutableStateOf<InvalidSkillDir?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredInvalidSkillDirs = remember(invalidSkillDirs, searchQuery) {
        invalidSkillDirs.filter { it.dirName.contains(searchQuery, ignoreCase = true) }
    }
    val filteredSkills = remember(skills, searchQuery) {
        if (searchQuery.isBlank()) {
            skills
        } else {
            skills.filter { skill ->
                skill.name.contains(searchQuery, ignoreCase = true) ||
                    skill.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.importSkillFromFile(uri) { success, message ->
            if (success) {
                toaster.show(context.getString(R.string.skills_page_import_success, message))
            } else {
                toaster.show(context.getString(R.string.skills_page_import_failed, message))
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.skills_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportSheet = true }, shape = androidx.compose.ui.graphics.RectangleShape, elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (skills.isEmpty()) {
                item {
                    ExtensionEmptyState(
                        message = stringResource(R.string.skills_page_empty_title),
                        description = stringResource(R.string.skills_page_empty_hint),
                        icon = HugeIcons.Puzzle,
                    )
                }
            }

            if (skills.isNotEmpty()) {
                item(key = "search") {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.skills_page_search_placeholder)) },
                        leadingIcon = {
                            Icon(HugeIcons.Search01, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, shapes = androidx.compose.material3.IconButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape)) {
                                    Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.skills_page_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }

            if (skills.isNotEmpty() && filteredSkills.isEmpty()) {
                item(key = "no_result") {
                    Text(
                        text = stringResource(R.string.skills_page_search_no_result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(filteredSkills, key = { it.skillDir.absolutePath }) { skill ->
                SkillCard(
                    skill = skill,
                    onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                    onDelete = { deleteTarget = skill },
                )
            }

            // 空目录、缺少 SKILL.md 等无法识别的目录不会出现在技能列表里，单独列出供清理
            if (filteredInvalidSkillDirs.isNotEmpty()) {
                item(key = "invalid_skill_dirs") {
                    InvalidSkillDirsGroup(
                        dirs = filteredInvalidSkillDirs,
                        onDelete = { invalidDeleteTarget = it },
                    )
                }
            }
        }
    }

    if (showImportSheet) {
        SkillImportSheet(
            onDismiss = { showImportSheet = false },
            onAddManually = {
                showImportSheet = false
                showAddDialog = true
            },
            onImportFromFile = {
                showImportSheet = false
                fileImportLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    )
                )
            },
            onImportFromGitHub = {
                showImportSheet = false
                showImportDialog = true
            },
        )
    }

    if (showAddDialog) {
        AddSkillDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, content ->
                vm.saveSkill(name, content) { success ->
                    showAddDialog = false
                    if (!success) {
                        toaster.show(context.getString(R.string.skills_page_save_failed))
                    }
                }
            },
        )
    }

    if (showImportDialog) {
        ImportSkillDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { repoUrl ->
                vm.importSkillFromGitHub(repoUrl) { success, message ->
                    showImportDialog = false
                    if (success) {
                        toaster.show(context.getString(R.string.skills_page_import_success, message))
                    } else {
                        toaster.show(context.getString(R.string.skills_page_import_failed, message))
                    }
                }
            },
        )
    }

    ProjetoIAConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.skills_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteSkill(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_message, deleteTarget?.name ?: ""))
    }

    ProjetoIAConfirmDialog(
        show = invalidDeleteTarget != null,
        title = stringResource(R.string.skills_page_delete_dir_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            invalidDeleteTarget?.let { vm.deleteInvalidSkillDir(it.dirName) }
            invalidDeleteTarget = null
        },
        onDismiss = { invalidDeleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_dir_message, invalidDeleteTarget?.dirName.orEmpty()))
    }
}

@Composable
private fun invalidSkillReasonText(reason: InvalidSkillReason): String = stringResource(
    when (reason) {
        InvalidSkillReason.MissingSkillMd -> R.string.skills_page_invalid_reason_missing_skill_md
        InvalidSkillReason.MissingName -> R.string.skills_page_invalid_reason_missing_name
        InvalidSkillReason.MissingDescription -> R.string.skills_page_invalid_reason_missing_description
        InvalidSkillReason.ReadFailed -> R.string.skills_page_invalid_reason_read_failed
    }
)

// 页面和 SkillsVM 的导入提示共用
@get:StringRes
internal val SkillValidationError.messageRes: Int
    get() = when (this) {
        SkillValidationError.MissingName -> R.string.skills_page_name_error
        SkillValidationError.NameHasSurroundingWhitespace -> R.string.skills_page_name_whitespace_error
        SkillValidationError.NameTooLong -> R.string.skills_page_name_too_long_error
        SkillValidationError.NameStartsWithDot -> R.string.skills_page_name_dot_error
        SkillValidationError.NameHasInvalidChars -> R.string.skills_page_name_invalid_chars_error
        SkillValidationError.MissingDescription -> R.string.skills_page_description_error
    }

internal val SkillValidationError.formatArgs: Array<Any>
    get() = if (this == SkillValidationError.NameTooLong) arrayOf(SkillValidator.MAX_NAME_LENGTH) else emptyArray()

@Composable
private fun InvalidSkillDirsGroup(
    dirs: List<InvalidSkillDir>,
    onDelete: (InvalidSkillDir) -> Unit,
) {
    CardGroup(title = { Text(stringResource(R.string.skills_page_invalid_dirs_title)) }) {
        dirs.forEach { dir ->
            item(
                leadingContent = {
                    CardGroupIcon(
                        icon = HugeIcons.Alert01,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                },
                headlineContent = {
                    Text(dir.dirName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(invalidSkillReasonText(dir.reason)) },
                trailingContent = {
                    ItemActionMenu(
                        actions = listOf(
                            ItemAction(
                                text = stringResource(R.string.delete),
                                icon = HugeIcons.Delete01,
                                destructive = true,
                                onClick = { onDelete(dir) },
                            ),
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (skill.builtin) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(HugeIcons.Puzzle)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = skill.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (skill.builtin) {
                        Tag { Text(stringResource(R.string.skills_page_builtin_tag)) }
                    }
                }
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!skill.compatibility.isNullOrBlank()) {
                    Text(
                        text = skill.compatibility,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            // 内置技能只读，不提供删除
            if (!skill.builtin) {
                ItemActionMenu(
                    actions = listOf(
                        ItemAction(
                            text = stringResource(R.string.delete),
                            icon = HugeIcons.Delete01,
                            destructive = true,
                            onClick = onDelete,
                        ),
                    )
                )
            }
        }
    }
}

@Composable
private fun SkillImportSheet(
    onDismiss: () -> Unit,
    onAddManually: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromGitHub: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        tonalElevation = 0.dp,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.skills_page_add_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            CardGroup {
                item(
                    onClick = onAddManually,
                    headlineContent = { Text(stringResource(R.string.skills_page_add_manually)) },
                    leadingContent = { CardGroupIcon(HugeIcons.Add01) },
                )
                item(
                    onClick = onImportFromFile,
                    headlineContent = { Text(stringResource(R.string.skills_page_import_from_file)) },
                    leadingContent = { CardGroupIcon(HugeIcons.FileImport) },
                )
                item(
                    onClick = onImportFromGitHub,
                    headlineContent = { Text(stringResource(R.string.skills_page_import_from_github)) },
                    leadingContent = { CardGroupIcon(HugeIcons.Download01) },
                )
            }
        }
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit,
) {
    var content by rememberSaveable { mutableStateOf("") }

    val validation = remember(content) { SkillValidator.validate(content) }
    val name = validation.getOrNull()
    val nameError = content.isNotBlank() && name == null
    val validationError = (validation.exceptionOrNull() as? SkillValidationException)?.error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_page_add_title)) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.skills_page_skill_content_label)) },
                placeholder = {
                    Text(
                        "---\nname: my-skill\ndescription: \"...\"\n---\n\n" +
                            stringResource(R.string.skills_page_skill_body_placeholder),
                        fontFamily = JetbrainsMono,
                    )
                },
                supportingText = {
                    if (nameError) Text(
                        validationError?.let { stringResource(it.messageRes, *it.formatArgs) }.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                    else if (name != null) Text(stringResource(R.string.skills_page_skill_name, name))
                    else Text(stringResource(R.string.skills_page_paste_hint))
                },
                isError = nameError,
                minLines = 8,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { name?.let { onConfirm(it, content) } },
                enabled = name != null, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                elevation = null,
            ) {
                Text(stringResource(R.string.skills_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (repoUrl: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.skills_page_import_from_github)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.skills_page_import_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.skills_page_repo_url_label)) },
                    placeholder = { Text("https://github.com/owner/repo", fontFamily = JetbrainsMono) },
                    supportingText = { Text(stringResource(R.string.skills_page_repo_url_hint)) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.skills_page_downloading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true
                    onConfirm(url)
                },
                enabled = url.isNotBlank() && !loading, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                elevation = null,
            ) {
                Text(stringResource(R.string.skills_page_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) { Text(stringResource(R.string.cancel)) }
        },
    )
}
