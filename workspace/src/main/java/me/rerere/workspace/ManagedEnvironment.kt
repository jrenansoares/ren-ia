package me.rerere.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ManagedEnvironment {
    const val CLAUDE_KEY_FINGERPRINT = "31DDDE24DDFAB679F42D7BD2BAA929FF1A7ECACE"
    const val SNAPSHOT_PREFIX = "PROJETO_IA_MANAGED_ENV="
    val toolsCheckCommand =
        ProgrammingEnvironment.checkCommand + "\ncommand -v curl >/dev/null\ncommand -v gpg >/dev/null"
    val installClaudeCommand: String by lazy { recipe("install-claude.sh") }
    val refreshPackagesCommand: String by lazy { recipe("refresh-packages.sh") }
    val upgradeCommand: String by lazy { recipe("upgrade.sh") }

    fun inspectCommand(updates: Boolean = false): String =
        "python3 -I -S -${if (updates) " --updates" else ""} <<'PROJETO_IA_MANAGED_PY'\n" + recipe("inspect.py") +
            "\nPROJETO_IA_MANAGED_PY\n"

    fun snapshot(result: WorkspaceCommandResult): ManagedEnvironmentSnapshot {
        require(result.exitCode == 0 && !result.timedOut && !result.truncated) { "Incomplete environment inspection" }
        val record = result.stdout.lineSequence().filter { it.startsWith(SNAPSHOT_PREFIX) }.singleOrNull()
            ?: error("Environment inspection did not return a status record")
        val value = Json.decodeFromString<ManagedEnvironmentSnapshot>(record.removePrefix(SNAPSHOT_PREFIX))
        require(value.linuxVersion.isNotBlank())
        require(!value.claudeUsable || (value.claudeVersion != null && value.claudeError == null))
        require(!value.updatesChecked || (value.linuxPackageUpdates != null && value.linuxPackageUpdates >= 0))
        require(
            !value.claudeUpdateAvailable ||
                (value.updatesChecked && value.claudeCandidate != null && value.claudeVersion != null)
        )
        return value
    }

    private fun recipe(name: String): String = requireNotNull(
        ManagedEnvironment::class.java.getResourceAsStream("/projetoia-managed-environment/$name")
    ) { "Missing managed environment recipe: $name" }.bufferedReader().use { it.readText() }
}

@Serializable
data class ManagedEnvironmentSnapshot(
    val linuxVersion: String,
    val claudeVersion: String? = null,
    val claudeUsable: Boolean = false,
    val claudeError: String? = null,
    val updatesChecked: Boolean = false,
    val linuxPackageUpdates: Int? = null,
    val claudeCandidate: String? = null,
    val claudeUpdateAvailable: Boolean = false,
) {
    val hasUpdates: Boolean get() = (linuxPackageUpdates ?: 0) > 0 || claudeUpdateAvailable
}
