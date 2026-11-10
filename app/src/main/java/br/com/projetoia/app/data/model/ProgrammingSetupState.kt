package br.com.projetoia.app.data.model

import me.rerere.workspace.ProgrammingInstallProgress
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.ManagedEnvironmentSnapshot

enum class ProgrammingPhase { IDLE, CREATING, CHECKING, INSTALLING, TOOLS, TESTING, BINDING, CLAUDE, CHECKING_UPDATES, UPDATING }

data class ProgrammingSetupState(
    val selectedId: String? = null,
    val phase: ProgrammingPhase = ProgrammingPhase.IDLE,
    val progress: RootfsInstallProgress? = null,
    val ready: Boolean = false,
    val smokeTestPassed: Boolean = false,
    val error: String? = null,
    val details: String = "",
    val installProgress: ProgrammingInstallProgress? = null,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long? = null,
    val lastActivityAtMillis: Long = 0L,
    val downloadStartedAtMillis: Long = 0L,
    val linuxInstalled: Boolean = false,
    val usingSharedLinux: Boolean = false,
    val hasPrivateLinux: Boolean = false,
    val environment: ManagedEnvironmentSnapshot? = null,
    val updates: ManagedEnvironmentSnapshot? = null,
) {
    val busy: Boolean get() = phase != ProgrammingPhase.IDLE
}
