package me.rerere.workspace

enum class ManagedEnvironmentStage { CHECKING, LINUX, TOOLS, TESTING, CLAUDE, CHECKING_UPDATES, UPDATING }
class ManagedCommandFailure(
    val result: WorkspaceCommandResult
) : IllegalStateException("Managed command failed: ${result.exitCode}")
class ManagedClaudeUnavailable : IllegalStateException("Claude Code did not pass its execution test")

/** The app owns the shared lock and cancellation job; recipes never run in user projects. */
class ManagedEnvironmentOperations(
    private val hasLinux: () -> Boolean,
    private val installLinux: () -> Unit,
    private val execute: (String, Long, Boolean) -> WorkspaceCommandResult,
    private val onStage: (ManagedEnvironmentStage) -> Unit = {},
    private val onSnapshot: (ManagedEnvironmentSnapshot) -> Unit = {},
    private val onLinuxReady: (Boolean) -> Unit = {},
    private val onTestPassed: () -> Unit = {},
) {
    fun checkLocal(): ManagedEnvironmentSnapshot? {
        onStage(ManagedEnvironmentStage.CHECKING)
        if (!hasLinux()) {
            onLinuxReady(false)
            return null
        }
        val ready = toolsReady()
        onLinuxReady(ready)
        if (!ready) return null
        return inspect()
    }

    fun prepare(): ManagedEnvironmentSnapshot {
        if (!hasLinux()) {
            onStage(ManagedEnvironmentStage.LINUX)
            installLinux()
        }
        check(hasLinux()) { "Shared Linux was not installed" }
        onStage(ManagedEnvironmentStage.CHECKING)
        var ready = toolsReady()
        if (!ready) {
            onStage(ManagedEnvironmentStage.TOOLS)
            success(ProgrammingEnvironment.installCommand, ProgrammingEnvironment.INSTALL_TIMEOUT_MILLIS, true)
            ready = toolsReady()
        }
        onLinuxReady(ready)
        check(ready) { "Shared Linux tools are incomplete" }
        onStage(ManagedEnvironmentStage.TESTING)
        check(ProgrammingEnvironment.smokeTestPassed(success(ProgrammingEnvironment.smokeTestCommand, 60_000, true)))
        onTestPassed()
        var snapshot = inspect()
        if (snapshot.claudeVersion == null) {
            onStage(ManagedEnvironmentStage.CLAUDE)
            success(ManagedEnvironment.installClaudeCommand, ProgrammingEnvironment.INSTALL_TIMEOUT_MILLIS, true)
            snapshot = inspect()
        }
        // Execution failure never triggers another binary download or rootfs replacement.
        if (!snapshot.claudeUsable) throw ManagedClaudeUnavailable()
        return snapshot
    }

    fun checkUpdates(): ManagedEnvironmentSnapshot {
        check(hasLinux()) { "Prepare Linux first" }
        onStage(ManagedEnvironmentStage.CHECKING_UPDATES)
        success(ManagedEnvironment.refreshPackagesCommand, 10 * 60_000, true)
        return inspect(updates = true)
    }

    fun upgrade(): ManagedEnvironmentSnapshot {
        check(hasLinux()) { "Prepare Linux first" }
        onLinuxReady(false)
        onStage(ManagedEnvironmentStage.UPDATING)
        success(ManagedEnvironment.upgradeCommand, ProgrammingEnvironment.INSTALL_TIMEOUT_MILLIS, true)
        onStage(ManagedEnvironmentStage.CHECKING)
        val ready = toolsReady()
        onLinuxReady(ready)
        check(ready) { "Linux tools did not pass the check after updating" }
        val snapshot = inspect(updates = true)
        if (snapshot.claudeVersion != null && !snapshot.claudeUsable) throw ManagedClaudeUnavailable()
        return snapshot
    }

    private fun toolsReady() = ProgrammingEnvironment.isReady(
        execute(ManagedEnvironment.toolsCheckCommand, 15_000, false)
    )
    private fun inspect(updates: Boolean = false): ManagedEnvironmentSnapshot {
        val snapshot = ManagedEnvironment.snapshot(success(ManagedEnvironment.inspectCommand(updates), 90_000, false))
        onSnapshot(snapshot)
        return snapshot
    }
    private fun success(command: String, timeout: Long, observe: Boolean): WorkspaceCommandResult {
        val result = execute(command, timeout, observe)
        // Long APT logs may exceed the capture limit; the exit code still proves completion.
        // Structured inspection records are validated separately, including truncation.
        if (result.exitCode != 0 || result.timedOut) throw ManagedCommandFailure(result)
        return result
    }
}
