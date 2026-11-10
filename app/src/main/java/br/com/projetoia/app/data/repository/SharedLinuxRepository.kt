package br.com.projetoia.app.data.repository

import android.app.Application
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import br.com.projetoia.app.AppScope
import br.com.projetoia.app.R
import br.com.projetoia.app.data.model.ProgrammingPhase
import br.com.projetoia.app.data.model.ProgrammingSetupState
import me.rerere.workspace.ManagedClaudeUnavailable
import me.rerere.workspace.ManagedCommandFailure
import me.rerere.workspace.ManagedEnvironmentOperations
import me.rerere.workspace.ManagedEnvironmentSnapshot
import me.rerere.workspace.ManagedEnvironmentStage
import me.rerere.workspace.ProgrammingEnvironment
import me.rerere.workspace.ProgrammingInstallTracker
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceManager

/** One installation, package manager lock and progress stream for every local project. */
class SharedLinuxRepository(
    private val manager: WorkspaceManager,
    private val installer: RootfsInstaller,
    private val workspaces: WorkspaceRepository,
    private val context: Application,
    private val appScope: AppScope,
) {
    private enum class Action { CHECK, PREPARE, CHECK_UPDATES, UPGRADE }
    private val mutex = Mutex()
    @Volatile private var setupJob: Job? = null
    private val _state = MutableStateFlow(ProgrammingSetupState(linuxInstalled = manager.hasSharedRootfs()))
    val state = _state.asStateFlow()
    fun cancel() { setupJob?.cancel() }
    suspend fun check() = runAction(Action.CHECK)
    fun prepare() { appScope.launch { runAction(Action.PREPARE) } }
    fun checkUpdates() { appScope.launch { runAction(Action.CHECK_UPDATES) } }
    fun upgrade() { appScope.launch { runAction(Action.UPGRADE) } }

    private suspend fun runAction(action: Action) {
        if (!mutex.tryLock()) return
        val job = currentCoroutineContext()[Job]
        setupJob = job
        val now = SystemClock.elapsedRealtime()
        val installed = manager.hasSharedRootfs()
        _state.update { it.copy(
            phase = ProgrammingPhase.CHECKING, linuxInstalled = installed, ready = it.ready && installed,
            environment = if (installed) it.environment else null,
            startedAtMillis = now, finishedAtMillis = null, lastActivityAtMillis = now, downloadStartedAtMillis = now,
            error = null, details = "", progress = null, installProgress = null, smokeTestPassed = false,
            updates = if (installed && action == Action.CHECK) it.updates else null,
        ) }
        try {
            runInterruptible(Dispatchers.IO) {
                val operations = ManagedEnvironmentOperations(
                    hasLinux = manager::hasSharedRootfs,
                    installLinux = {
                        installer.installShared(ProgrammingEnvironment.ROOTFS_URL, ProgrammingEnvironment.ROOTFS_SHA256) { progress ->
                            _state.update { it.copy(progress = progress, lastActivityAtMillis = SystemClock.elapsedRealtime()) }
                        }
                    },
                    execute = ::execute,
                    onStage = { stage ->
                        val phase = when (stage) {
                            ManagedEnvironmentStage.CHECKING -> ProgrammingPhase.CHECKING
                            ManagedEnvironmentStage.LINUX -> ProgrammingPhase.INSTALLING
                            ManagedEnvironmentStage.TOOLS -> ProgrammingPhase.TOOLS
                            ManagedEnvironmentStage.TESTING -> ProgrammingPhase.TESTING
                            ManagedEnvironmentStage.CLAUDE -> ProgrammingPhase.CLAUDE
                            ManagedEnvironmentStage.CHECKING_UPDATES -> ProgrammingPhase.CHECKING_UPDATES
                            ManagedEnvironmentStage.UPDATING -> ProgrammingPhase.UPDATING
                        }
                        _state.update { it.copy(phase = phase, installProgress = null, linuxInstalled = manager.hasSharedRootfs(), lastActivityAtMillis = SystemClock.elapsedRealtime()) }
                    },
                    onSnapshot = ::acceptSnapshot,
                    onLinuxReady = { ready -> _state.update { it.copy(ready = ready) } },
                    onTestPassed = { _state.update { it.copy(smokeTestPassed = true) } },
                )
                when (action) {
                    Action.CHECK -> operations.checkLocal()
                    Action.PREPARE -> operations.prepare()
                    Action.CHECK_UPDATES -> operations.checkUpdates()
                    Action.UPGRADE -> operations.upgrade()
                }
            }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(error = context.getString(R.string.programming_cancelled)) }
            throw cancelled
        } catch (error: Exception) {
            val message = when {
                error is ManagedClaudeUnavailable -> context.getString(R.string.linux_claude_execution_failed)
                error is ManagedCommandFailure && error.result.timedOut -> context.getString(R.string.programming_install_timeout)
                error is ManagedCommandFailure -> context.getString(R.string.linux_action_exit, error.result.exitCode)
                else -> context.getString(R.string.linux_action_failed)
            }
            val details = if (error is ManagedCommandFailure) error.result.stderr.ifBlank { error.result.stdout } else error.message.orEmpty()
            _state.update { it.copy(error = message, details = (it.details + "\n" + details).takeLast(ProgrammingInstallTracker.MAX_LOG_CHARS)) }
        } finally {
            try { withContext(NonCancellable) { workspaces.refreshSharedLinuxState() } }
            finally {
                _state.update { it.copy(phase = ProgrammingPhase.IDLE, linuxInstalled = manager.hasSharedRootfs(), finishedAtMillis = SystemClock.elapsedRealtime()) }
                if (setupJob === job) setupJob = null
                mutex.unlock()
            }
        }
    }

    private fun acceptSnapshot(snapshot: ManagedEnvironmentSnapshot) {
        _state.update {
            val changed = it.environment?.linuxVersion != snapshot.linuxVersion || it.environment?.claudeVersion != snapshot.claudeVersion
            it.copy(environment = snapshot,
                updates = if (snapshot.updatesChecked) snapshot else if (changed) null else it.updates,
                details = if (snapshot.claudeError != null) (it.details + "\n" + snapshot.claudeError).takeLast(ProgrammingInstallTracker.MAX_LOG_CHARS) else it.details,
            )
        }
    }

    private fun execute(command: String, timeout: Long, observe: Boolean): WorkspaceCommandResult {
        if (!observe) return manager.executeSharedCommand(command, timeout)
        val tracker = ProgrammingInstallTracker()
        val previous = _state.value.details
        val result = try {
            manager.executeSharedCommand(command, timeout) { chunk, stderr ->
                synchronized(tracker) {
                    val progress = tracker.accept(chunk, stderr, SystemClock.elapsedRealtime())
                    _state.update { it.copy(installProgress = progress,
                        details = (previous + progress.log).takeLast(ProgrammingInstallTracker.MAX_LOG_CHARS),
                        lastActivityAtMillis = progress.lastProgressMillis.takeIf { value -> value > 0 } ?: it.lastActivityAtMillis,
                    ) }
                }
            }
        } finally {
            val progress = tracker.finish(SystemClock.elapsedRealtime())
            _state.update { it.copy(installProgress = progress, details = (previous + progress.log).takeLast(ProgrammingInstallTracker.MAX_LOG_CHARS)) }
        }
        return result
    }
}
