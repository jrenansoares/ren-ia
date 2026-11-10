package br.com.projetoia.app.ui.pages.projeto

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import br.com.projetoia.app.R
import br.com.projetoia.app.data.repository.WorkspaceRepository
import br.com.projetoia.app.data.repository.SharedLinuxRepository
import br.com.projetoia.app.data.model.ProgrammingPhase
import br.com.projetoia.app.data.model.ProgrammingSetupState
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import br.com.projetoia.app.service.ChatService
import me.rerere.workspace.ProgrammingEnvironment
import me.rerere.workspace.ProgrammingInstallTracker
import me.rerere.workspace.WorkspaceShellStatus
import kotlin.uuid.Uuid

class ProgrammingVM(
    private val conversationId: String,
    private val context: Application,
    private val repository: WorkspaceRepository,
    private val chatService: ChatService,
    private val sharedLinux: SharedLinuxRepository,
    private val terminalSessions: WorkspaceTerminalSessionManager,
) : ViewModel() {
    val sharedLinuxState = sharedLinux.state
    val workspaces = repository.listFlow().map { list -> list.filterNot { it.isSsh() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _state = MutableStateFlow(ProgrammingSetupState())
    val state = _state.asStateFlow()
    private var operation: Job? = null

    fun refreshSharedLinux() { viewModelScope.launch { sharedLinux.check() } }

    fun prepareSharedLinux() = sharedLinux.prepare()

    fun cancelSharedLinux() = sharedLinux.cancel()

    fun refreshProject() {
        val id = _state.value.selectedId ?: return
        if (!_state.value.busy) run(ProgrammingPhase.CHECKING) { checkEnvironment(id) }
    }

    fun setSharedLinux(enabled: Boolean) = run(ProgrammingPhase.BINDING) {
        val id = _state.value.selectedId ?: return@run
        val project = repository.getById(id) ?: error(context.getString(R.string.programming_project_missing))
        check(!sharedLinux.state.value.busy && (!enabled || sharedLinux.state.value.ready)) { context.getString(R.string.programming_shared_required) }
        check(!terminalSessions.hasRunningSessions(project.root)) { context.getString(R.string.programming_shared_close_terminals) }
        if (enabled) repository.useSharedLinux(id) else repository.usePrivateLinux(id)
        _state.update { it.copy(ready = false, smokeTestPassed = false, usingSharedLinux = enabled) }
        checkEnvironment(id)
        check(_state.value.ready) { context.getString(R.string.programming_environment_required) }
        testEnvironment(id)
    }

    private fun run(phase: ProgrammingPhase, block: suspend () -> Unit) {
        if (_state.value.busy) return
        val now = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(phase = phase, error = null, details = "", progress = null, installProgress = null,
                startedAtMillis = now, finishedAtMillis = null, lastActivityAtMillis = now,
                downloadStartedAtMillis = now)
        }
        operation = viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(
                        ready = false,
                        smokeTestPassed = false,
                        error = context.getString(R.string.programming_cancelled)
                    )
                }
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        error = error.message ?: context.getString(R.string.programming_failed),
                        ready = false,
                        smokeTestPassed = false,
                    )
                }
            } finally {
                _state.update {
                    it.copy(
                        phase = ProgrammingPhase.IDLE,
                        finishedAtMillis = SystemClock.elapsedRealtime()
                    )
                }
            }
        }
    }

    fun selectProject(id: String) {
        if (_state.value.busy || _state.value.selectedId == id) return
        _state.update { it.copy(selectedId = id, ready = false, smokeTestPassed = false) }
        run(ProgrammingPhase.CHECKING) { checkEnvironment(id) }
    }

    fun createProject(name: String) = run(ProgrammingPhase.CREATING) {
        require(
            name.trim().isNotEmpty() && name.trim().length <= 80
        ) { context.getString(R.string.programming_name_required) }
        val project = repository.create(name.trim())
        _state.update { it.copy(selectedId = project.id, ready = false, smokeTestPassed = false) }
        checkEnvironment(project.id)
    }

    private suspend fun checkEnvironment(id: String) {
        val project = repository.getById(id)
        val ready = project != null && !project.isSsh() && project.shellStatus == WorkspaceShellStatus.READY.name &&
            ProgrammingEnvironment.isReady(
                repository.executeCommand(id, ProgrammingEnvironment.checkCommand, timeoutMillis = 15_000L)
            )
        val shared = repository.usesSharedLinux(id)
        val privateLinux = repository.hasPrivateLinux(id)
        _state.update { it.copy(ready = ready, usingSharedLinux = shared, hasPrivateLinux = privateLinux) }
    }

    fun prepare() {
        val id = _state.value.selectedId ?: return
        run(ProgrammingPhase.CHECKING) {
            val project = repository.getById(id) ?: error(context.getString(R.string.programming_project_missing))
            check(!project.isSsh())
            if (repository.usesSharedLinux(id)) {
                check(sharedLinux.state.value.ready && !sharedLinux.state.value.busy) { context.getString(R.string.programming_shared_required) }
                checkEnvironment(id)
                check(_state.value.ready) { context.getString(R.string.programming_environment_required) }
                testEnvironment(id)
                return@run
            }
            check(
                project.shellStatus != WorkspaceShellStatus.INSTALLING.name
            ) { context.getString(R.string.programming_already_installing) }
            if (project.shellStatus != WorkspaceShellStatus.READY.name) {
                _state.update {
                    it.copy(
                        phase = ProgrammingPhase.INSTALLING,
                        downloadStartedAtMillis = SystemClock.elapsedRealtime()
                    )
                }
                check(repository.installRootfs(
                    id,
                    ProgrammingEnvironment.ROOTFS_URL,
                    ProgrammingEnvironment.ROOTFS_SHA256
                ) { progress ->
                    val now = SystemClock.elapsedRealtime()
                    _state.update { it.copy(progress = progress, lastActivityAtMillis = now) }
                }) { context.getString(R.string.programming_project_missing) }
            }
            _state.update { it.copy(phase = ProgrammingPhase.CHECKING, lastActivityAtMillis = SystemClock.elapsedRealtime()) }
            checkEnvironment(id)
            if (!_state.value.ready) {
                _state.update {
                    it.copy(
                        phase = ProgrammingPhase.TOOLS,
                        lastActivityAtMillis = SystemClock.elapsedRealtime()
                    )
                }
                val tracker = ProgrammingInstallTracker()
                val installed = try { repository.executeCommand(
                    id,
                    ProgrammingEnvironment.installCommand,
                    timeoutMillis = ProgrammingEnvironment.INSTALL_TIMEOUT_MILLIS,
                    onOutput = { chunk, stderr ->
                        synchronized(tracker) {
                            val progress = tracker.accept(chunk, stderr, SystemClock.elapsedRealtime())
                            _state.update {
                                it.copy(
                                    installProgress = progress,
                                    details = progress.log,
                                    lastActivityAtMillis =
                                    progress.lastProgressMillis.takeIf { value -> value > 0 } ?: it.lastActivityAtMillis
                                )
                            }
                        }
                    },
                ) } finally {
                    val finalProgress = tracker.finish(SystemClock.elapsedRealtime())
                    _state.update { it.copy(installProgress = finalProgress, details = finalProgress.log) }
                }
                check(!installed.timedOut) { context.getString(R.string.programming_install_timeout) }
                check(
                    installed.exitCode == 0
                ) { context.getString(R.string.programming_install_exit, installed.exitCode) }
            }
            checkEnvironment(id)
            check(_state.value.ready) { context.getString(R.string.programming_environment_required) }
            testEnvironment(id)
        }
    }

    private suspend fun testEnvironment(id: String) {
        _state.update { it.copy(phase = ProgrammingPhase.TESTING, smokeTestPassed = false) }
        val result = repository.executeCommand(
            id,
            ProgrammingEnvironment.smokeTestCommand,
            timeoutMillis = 60_000L,
            onOutput = { chunk, _ ->
                _state.update {
                    it.copy(
                        details = (it.details + chunk).takeLast(ProgrammingInstallTracker.MAX_LOG_CHARS),
                        lastActivityAtMillis = SystemClock.elapsedRealtime()
                    )
                }
            }
        )
        val passed = ProgrammingEnvironment.smokeTestPassed(result)
        _state.update {
            it.copy(
                smokeTestPassed = passed,
                details = (it.details + "\n" + result.stdout + "\n" + result.stderr).trim().takeLast(
                    ProgrammingInstallTracker.MAX_LOG_CHARS
                )
            )
        }
        check(passed) { context.getString(R.string.programming_test_failed) }
    }

    fun test() {
        val id = _state.value.selectedId ?: return
        run(ProgrammingPhase.TESTING) { testEnvironment(id) }
    }

    fun activate(onActivated: () -> Unit) {
        val id = _state.value.selectedId ?: return
        run(ProgrammingPhase.BINDING) {
            checkEnvironment(id)
            check(_state.value.ready) { context.getString(R.string.programming_environment_required) }
            if (!_state.value.smokeTestPassed) testEnvironment(id)
            chatService.setProgrammingProject(Uuid.parse(conversationId), Uuid.parse(id))
            onActivated()
        }
    }

    fun deactivate(onDeactivated: () -> Unit) = run(ProgrammingPhase.BINDING) {
        chatService.setProgrammingProject(Uuid.parse(conversationId), null)
        onDeactivated()
    }

    fun cancel() {
        operation?.cancel()
    }

    fun setAutoApproval(enabled: Boolean) = run(ProgrammingPhase.BINDING) {
        chatService.setProgrammingAutoApproval(Uuid.parse(conversationId), enabled)
    }

    fun setClaudeCode(enabled: Boolean) = run(ProgrammingPhase.BINDING) {
        chatService.setProgrammingClaudeCode(Uuid.parse(conversationId), enabled)
    }

    fun setConsultants(enabled: Boolean) = run(ProgrammingPhase.BINDING) {
        chatService.setProgrammingConsultants(Uuid.parse(conversationId), enabled)
    }
}
