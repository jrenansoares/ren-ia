package me.rerere.workspace

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ManagedEnvironmentOperationsTest {
    private class Fixture {
        var linux = false
        var tools = false
        var claude = false
        var usable = true
        var failInstall = false
        var failRefresh = false
        var truncateToolLog = false
        var downloads = 0
        val commands = mutableListOf<String>()
        val snapshots = mutableListOf<ManagedEnvironmentSnapshot>()
        var linuxReady = false
        val operations = ManagedEnvironmentOperations(
            hasLinux = { linux }, installLinux = { linux = true; downloads++ },
            execute = { command, _, _ ->
                commands += command
                when (command) {
                    ManagedEnvironment.toolsCheckCommand -> WorkspaceCommandResult(if (tools) 0 else 1, if (tools) "PROJETO_IA_ENVIRONMENT_OK\n" else "", "")
                    ProgrammingEnvironment.installCommand -> { tools = true; WorkspaceCommandResult(0, "", "", truncated = truncateToolLog) }
                    ProgrammingEnvironment.smokeTestCommand -> WorkspaceCommandResult(0, "PROJETO_IA_SMOKE_TEST_OK\n", "")
                    ManagedEnvironment.installClaudeCommand -> if (failInstall) WorkspaceCommandResult(42, "", "package failed") else { claude = true; WorkspaceCommandResult(0, "", "") }
                    ManagedEnvironment.refreshPackagesCommand -> WorkspaceCommandResult(if (failRefresh) 37 else 0, "", "")
                    ManagedEnvironment.upgradeCommand -> WorkspaceCommandResult(0, "", "")
                    else -> {
                        val updates = command == ManagedEnvironment.inspectCommand(true)
                        WorkspaceCommandResult(0, ManagedEnvironment.SNAPSHOT_PREFIX + Json.encodeToString(ManagedEnvironmentSnapshot(
                            linuxVersion = "Ubuntu 24.04.5 LTS", claudeVersion = if (claude) "2.1.123" else null,
                            claudeUsable = claude && usable, claudeError = if (claude && !usable) "cannot execute binary" else null,
                            updatesChecked = updates, linuxPackageUpdates = if (updates) 3 else null,
                            claudeCandidate = if (updates) "2.1.124" else null, claudeUpdateAvailable = updates && claude,
                        )), "")
                    }
                }
            }, onSnapshot = snapshots::add, onLinuxReady = { linuxReady = it },
        )
    }

    @Test fun preparationInstallsInSequenceAndDownloadsNeitherComponentAgain() {
        val f = Fixture()
        assertTrue(f.operations.prepare().claudeUsable)
        f.operations.prepare()
        assertEquals(1, f.downloads)
        assertEquals(1, f.commands.count { it == ProgrammingEnvironment.installCommand })
        assertEquals(1, f.commands.count { it == ManagedEnvironment.installClaudeCommand })
        assertTrue(f.commands.indexOf(ProgrammingEnvironment.installCommand) < f.commands.indexOf(ManagedEnvironment.installClaudeCommand))
    }

    @Test fun localChecksNeverDownloadOrRefreshIndexes() {
        val f = Fixture().apply { linux = true; tools = true; claude = true }
        assertTrue(f.operations.checkLocal()!!.claudeUsable)
        assertEquals(0, f.downloads)
        assertEquals(listOf(ManagedEnvironment.toolsCheckCommand, ManagedEnvironment.inspectCommand()), f.commands)
    }

    @Test fun failedClaudeInstallRetriesWithoutDownloadingLinuxOrToolsAgain() {
        val f = Fixture().apply { failInstall = true }
        assertThrows(ManagedCommandFailure::class.java) { f.operations.prepare() }
        assertTrue(f.linuxReady)
        f.failInstall = false
        assertTrue(f.operations.prepare().claudeUsable)
        assertEquals(1, f.downloads)
        assertEquals(1, f.commands.count { it == ProgrammingEnvironment.installCommand })
    }

    @Test fun installedButUnusableClaudeIsReportedWithoutRepeatedDownloads() {
        val f = Fixture().apply { linux = true; tools = true; claude = true; usable = false }
        repeat(2) { assertThrows(ManagedClaudeUnavailable::class.java) { f.operations.prepare() } }
        assertTrue(f.linuxReady)
        assertEquals("2.1.123", f.snapshots.last().claudeVersion)
        assertFalse(f.commands.contains(ManagedEnvironment.installClaudeCommand))
        assertEquals(0, f.downloads)
    }

    @Test fun checkingUpdatesOnlyRefreshesIndexesAndReadsThePlan() {
        val f = Fixture().apply { linux = true; tools = true; claude = true }
        val result = f.operations.checkUpdates()
        assertTrue(result.hasUpdates)
        assertEquals(3, result.linuxPackageUpdates)
        assertEquals("2.1.124", result.claudeCandidate)
        assertEquals(listOf(ManagedEnvironment.refreshPackagesCommand, ManagedEnvironment.inspectCommand(true)), f.commands)
    }

    @Test fun failedIndexRefreshCannotReportEverythingUpToDate() {
        val f = Fixture().apply { linux = true; failRefresh = true }
        assertThrows(ManagedCommandFailure::class.java) { f.operations.checkUpdates() }
        assertTrue(f.snapshots.isEmpty())
    }
    @Test fun successfulPackageInstallationIsNotRejectedJustBecauseItsLogWasLong() {
        val f = Fixture().apply { truncateToolLog = true }
        assertTrue(f.operations.prepare().claudeUsable)
        assertTrue(f.linuxReady)
    }

    @Test fun applyingUpdatesKeepsTheExistingLinuxAndRechecksPrograms() {
        val f = Fixture().apply { linux = true; tools = true; claude = true }
        assertTrue(f.operations.upgrade().claudeUsable)
        assertEquals(0, f.downloads)
        assertEquals(ManagedEnvironment.upgradeCommand, f.commands.first())
        assertFalse(f.commands.contains(ManagedEnvironment.installClaudeCommand))
        assertTrue(f.linuxReady)
        assertTrue(f.snapshots.last().updatesChecked)
    }

    @Test fun incompleteContradictoryOrNegativeStatusRecordsAreRejected() {
        val record = ManagedEnvironment.SNAPSHOT_PREFIX + "{\"linuxVersion\":\"Ubuntu\"}"
        val result = WorkspaceCommandResult(0, record, "")
        assertNotNull(ManagedEnvironment.snapshot(result))
        for (invalid in listOf(result.copy(exitCode = 1), result.copy(timedOut = true), result.copy(truncated = true),
            result.copy(stdout = ""), result.copy(stdout = "$record\n$record"),
            result.copy(stdout = ManagedEnvironment.SNAPSHOT_PREFIX + "{\"linuxVersion\":\"Ubuntu\",\"updatesChecked\":true,\"linuxPackageUpdates\":-1}"),
            result.copy(stdout = ManagedEnvironment.SNAPSHOT_PREFIX + "{\"linuxVersion\":\"Ubuntu\",\"claudeUsable\":true}"))) {
            assertThrows(Exception::class.java) { ManagedEnvironment.snapshot(invalid) }
        }
    }
}
