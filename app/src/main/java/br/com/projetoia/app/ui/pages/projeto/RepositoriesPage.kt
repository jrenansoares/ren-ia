package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import br.com.projetoia.app.R
import br.com.projetoia.app.data.repository.InstalledRepositoryInfo
import br.com.projetoia.app.data.repository.WorkspaceRepository
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.Screen
import org.koin.compose.koinInject
import me.rerere.workspace.WorkspaceShellStatus

@Composable
fun RepositoriesPage(repository: WorkspaceRepository = koinInject()) {
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    val workspacesFlow = remember(repository) { repository.listFlow() }
    val workspaces by workspacesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val readyProjects = remember(workspaces) {
        workspaces.filter { !it.isSsh() && it.shellStatus == WorkspaceShellStatus.READY.name }
    }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val selectedProject = readyProjects.firstOrNull { it.id == selectedProjectId } ?: readyProjects.firstOrNull()
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var repositoryUrl by remember { mutableStateOf("") }
    var directoryName by remember { mutableStateOf("") }
    var directoryNameCustomized by remember { mutableStateOf(false) }
    var repositories by remember { mutableStateOf(emptyList<InstalledRepositoryInfo>()) }
    var scanning by remember { mutableStateOf(true) }
    var cloning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<Int?>(null) }
    var errorDetail by remember { mutableStateOf("") }
    val outputFlow = remember { MutableStateFlow("") }
    val gitOutput by outputFlow.collectAsStateWithLifecycle()

    suspend fun refreshRepositories() {
        scanning = true
        try {
            repositories = repository.discoverGitRepositories()
        } catch (failure: Exception) {
            errorDetail = failure.message.orEmpty()
        } finally {
            scanning = false
        }
    }

    LaunchedEffect(repository) { refreshRepositories() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projeto_repositories_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = { scope.launch { refreshRepositories() } }, enabled = !scanning && !cloning, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                        Text(stringResource(R.string.projeto_repositories_scan))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(stringResource(R.string.projeto_repositories_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.projeto_repositories_install)) }) {
                    formItem {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box {
                                TextButton(onClick = { projectMenuExpanded = true }, enabled = !cloning, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                                    Text(selectedProject?.name ?: stringResource(R.string.projeto_repositories_project))
                                }
                                DropdownMenu(
                                    expanded = projectMenuExpanded,
                                    onDismissRequest = { projectMenuExpanded = false },
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    shape = androidx.compose.ui.graphics.RectangleShape,
                                ) {
                                    readyProjects.forEach { project ->
                                        DropdownMenuItem(
                                            text = { Text(project.name) },
                                            onClick = {
                                                selectedProjectId = project.id
                                                projectMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = repositoryUrl,
                                onValueChange = { value ->
                                    repositoryUrl = value
                                    if (!directoryNameCustomized) {
                                        directoryName = repositoryFolderFromUrl(value)
                                    }
                                },
                                label = { Text(stringResource(R.string.projeto_repositories_url)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = directoryName,
                                onValueChange = {
                                    directoryName = it
                                    directoryNameCustomized = it.isNotBlank()
                                },
                                label = { Text(stringResource(R.string.projeto_repositories_folder)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.projeto_repositories_linux_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (readyProjects.isEmpty()) {
                                TextButton(onClick = { nav.navigate(Screen.SharedLinux) }, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                                    Text(stringResource(R.string.linux_page_manage))
                                }
                            }
                            Button(
                                onClick = {
                                    val target = selectedProject ?: return@Button
                                    scope.launch {
                                        cloning = true
                                        resultMessage = null
                                        errorDetail = ""
                                        outputFlow.value = ""
                                        try {
                                            val result = repository.installGitRepository(
                                                id = target.id,
                                                repositoryUrl = repositoryUrl,
                                                directoryName = directoryName,
                                                onOutput = { chunk, _ ->
                                                    outputFlow.update { (it + chunk).takeLast(8_000) }
                                                },
                                            )
                                            if (result.exitCode != 0 || result.timedOut) {
                                                errorDetail = result.stderr.ifBlank { result.stdout }.takeLast(8_000)
                                                resultMessage = R.string.projeto_repositories_clone_failed
                                            } else {
                                                resultMessage = R.string.projeto_repositories_clone_success
                                                repositoryUrl = ""
                                                directoryName = ""
                                                directoryNameCustomized = false
                                                refreshRepositories()
                                            }
                                        } catch (failure: Exception) {
                                            errorDetail = failure.message.orEmpty()
                                            resultMessage = R.string.projeto_repositories_clone_failed
                                        } finally {
                                            cloning = false
                                        }
                                    }
                                },
                                enabled = !cloning && selectedProject != null && repositoryUrl.isNotBlank() && directoryName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                                elevation = null,
                            ) {
                                if (cloning) {
                                    CircularProgressIndicator()
                                } else {
                                    Text(stringResource(R.string.projeto_repositories_install_action))
                                }
                            }
                            if (cloning) Text(stringResource(R.string.projeto_repositories_cloning))
                            resultMessage?.let { message ->
                                Text(
                                    stringResource(message),
                                    color = if (message == R.string.projeto_repositories_clone_failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (errorDetail.isNotBlank()) {
                                Text(errorDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (gitOutput.isNotBlank()) {
                                Text(stringResource(R.string.projeto_repositories_check_output), style = MaterialTheme.typography.labelLarge)
                                Text(gitOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    if (scanning) stringResource(R.string.projeto_repositories_scanning)
                    else stringResource(R.string.projeto_repositories_count, repositories.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!scanning && repositories.isEmpty()) {
                item { Text(stringResource(R.string.projeto_repositories_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(repositories, key = { "${it.workspaceId}:${it.path}" }) { repo ->
                RepositoryCard(repo)
            }
        }
    }
}

@Composable
private fun RepositoryCard(repository: InstalledRepositoryInfo) {
    CardGroup(modifier = Modifier.fillMaxWidth()) {
        item(
            headlineContent = { Text(repository.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${repository.workspaceName} · ${repository.path}")
                    repository.remoteUrl?.let { Text(stringResource(R.string.projeto_repositories_origin, it)) }
                    repository.branch?.let { Text(stringResource(R.string.projeto_repositories_branch, it)) }
                }
            },
        )
    }
}

private fun repositoryFolderFromUrl(url: String): String = url.trim().trimEnd('/')
    .substringAfterLast('/')
    .removeSuffix(".git")
    .takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) && it != "." && it != ".." }
    .orEmpty()
