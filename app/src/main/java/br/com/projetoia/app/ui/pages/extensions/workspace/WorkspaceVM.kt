package br.com.projetoia.app.ui.pages.extensions.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import br.com.projetoia.app.data.db.entity.WorkspaceEntity
import br.com.projetoia.app.data.repository.WorkspaceRepository
import me.rerere.workspace.ProjectArchiveProgress
import me.rerere.workspace.RootfsInstallProgress
import java.io.File

data class ProjectArchiveUiState(
    val busy: Boolean = false,
    val progress: ProjectArchiveProgress = ProjectArchiveProgress(0, 0),
    val completed: Boolean = false,
    val restoredName: String? = null,
    val error: String? = null,
)

class WorkspaceVM(
    private val repository: WorkspaceRepository,
    private val terminalSessionManager: WorkspaceTerminalSessionManager,
) : ViewModel() {
    private val _archive = MutableStateFlow(ProjectArchiveUiState())
    val archive = _archive.asStateFlow()
    private var archiveJob: Job? = null

    fun dismissArchiveResult() {
        _archive.value = ProjectArchiveUiState()
    }
    fun cancelArchive() {
        archiveJob?.cancel()
    }

    fun exportArchive(id: String, uri: Uri, resolver: ContentResolver) {
        if (_archive.value.busy) return
        archiveJob = viewModelScope.launch {
            _archive.value = ProjectArchiveUiState(busy = true)
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri) ?: error("Cannot open backup destination")
                    output.use {
                        repository.exportProjectArchive(
                            id,
                            it
                        ) { p -> _archive.update { it.copy(progress = p) } }
                    }
                }
                _archive.update { it.copy(completed = true) }
            } catch (error: Exception) {
                withContext(
                    kotlinx.coroutines.NonCancellable + Dispatchers.IO
                ) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
                if (error is CancellationException) throw error
                _archive.update { it.copy(error = error.message.orEmpty()) }
            } finally {
                _archive.update { it.copy(busy = false) }
            }
        }
    }

    fun importArchive(uri: Uri, resolver: ContentResolver, cacheDir: File) {
        if (_archive.value.busy) return
        archiveJob = viewModelScope.launch {
            _archive.value = ProjectArchiveUiState(busy = true)
            try {
                val restored = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Cannot open project backup")
                    input.use {
                        repository.importProjectArchive(
                            it,
                            cacheDir
                        ) { p -> _archive.update { it.copy(progress = p) } }
                    }
                }
                _archive.update { it.copy(completed = true, restoredName = restored.name) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _archive.update { it.copy(error = error.message.orEmpty()) }
            } finally {
                _archive.update { it.copy(busy = false) }
            }
        }
    }
    val workspaces = repository.listFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.create(name) }
        }
    }

    fun rename(workspace: WorkspaceEntity, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(workspace.id, name) }
        }
    }

    fun delete(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            terminalSessionManager.closeWorkspace(workspace.root)
            repository.delete(workspace.id)
        }
    }
}
