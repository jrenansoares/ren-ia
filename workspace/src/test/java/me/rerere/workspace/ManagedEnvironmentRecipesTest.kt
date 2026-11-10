package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManagedEnvironmentRecipesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun execute(command: String, version: String = "2.1.100", candidate: String = "2.1.101", failure: String = "0", probeFailure: Boolean = false): Pair<WorkspaceCommandResult, String> {
        val folder = temporary.newFolder()
        val bin = File(folder, "bin").apply { mkdir() }
        val log = File(folder, "calls")
        fun fake(name: String, body: String) {
            File(bin, name).apply { writeText("#!/usr/bin/python3\nimport os,sys\nfrom pathlib import Path\n" + body + "\n"); setExecutable(true) }
        }
        fake("dpkg-query", "v=os.environ['MANAGED_TEST_VERSION']\nif not v: sys.exit(1)\nprint('install ok installed' + ('\\n'+v if '\\n' in sys.argv[2] else ''),end='')")
        fake("claude", "print(os.environ['MANAGED_TEST_VERSION']+' (Claude Code)')\nsys.exit(int(os.environ['MANAGED_TEST_PROBE_FAILURE']))")
        fake("apt-get", "p=Path(os.environ['MANAGED_TEST_LOG']);p.write_text((p.read_text() if p.exists() else '')+'apt '+repr(sys.argv[1:])+'\\n')\nif '-s' in sys.argv:\n print('Inst libc6 [1.0] (1.1 Ubuntu)\\nInst claude-code [2.1.100] (2.1.101 Anthropic)')\nsys.exit(int(os.environ['MANAGED_TEST_FAILURE']))")
        fake("apt-cache", "print('claude-code:\\n  Candidate: '+os.environ['MANAGED_TEST_CANDIDATE'])")
        fake("dpkg", "import subprocess\nif '--compare-versions' in sys.argv: sys.exit(subprocess.run(['/usr/bin/dpkg']+sys.argv[1:]).returncode)")
        fake("curl", "Path(sys.argv[sys.argv.index('-o')+1]).write_text('untrusted test key')\nPath(os.environ['MANAGED_TEST_LOG']).write_text('key downloaded\\n')")
        fake("gpg", "print('pub:::::::::')\nprint(':'.join(['fpr']+['']*8+['BADFINGERPRINT']))")
        val process = ProcessBuilder("/bin/sh", "-c", command).directory(folder).apply {
            environment()["PATH"] = bin.absolutePath + ":/usr/bin:/bin"
            environment()["MANAGED_TEST_VERSION"] = version
            environment()["MANAGED_TEST_CANDIDATE"] = candidate
            environment()["MANAGED_TEST_FAILURE"] = failure
            environment()["MANAGED_TEST_LOG"] = log.absolutePath
            environment()["MANAGED_TEST_PROBE_FAILURE"] = if (probeFailure) "137" else "0"
        }.start()
        val result = process.readResult(15_000)
        return result to if (log.exists()) log.readText() else ""
    }

    @Test fun installedPackageSkipsAllNetworkAndInstallCommands() {
        val (result, calls) = execute(ManagedEnvironment.installClaudeCommand)
        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("", calls)
    }
    @Test fun wrongSigningKeyStopsBeforeRepositoryRegistrationOrPackageInstallation() {
        val (result, calls) = execute(ManagedEnvironment.installClaudeCommand, version = "")
        assertNotEquals(0, result.exitCode)
        assertTrue(result.stderr.contains("fingerprint mismatch"))
        assertEquals("key downloaded\n", calls)
    }
    @Test fun inspectionCountsUbuntuSeparatelyAndComparesClaudeVersionsNumerically() {
        val (result, calls) = execute(ManagedEnvironment.inspectCommand(true), version = "2.1.9", candidate = "2.1.10")
        val status = ManagedEnvironment.snapshot(result)
        assertTrue(status.claudeUsable)
        assertTrue(status.claudeUpdateAvailable)
        assertEquals(1, status.linuxPackageUpdates)
        assertTrue(calls.contains("'-s', '--with-new-pkgs', 'upgrade'"))
        assertFalse(calls.contains("update"))
    }
    @Test fun missingCandidateIsNotAnAvailableClaudeUpdate() {
        val (result, _) = execute(ManagedEnvironment.inspectCommand(true), candidate = "(none)")
        val status = ManagedEnvironment.snapshot(result)
        assertNull(status.claudeCandidate)
        assertFalse(status.claudeUpdateAvailable)
    }
    @Test fun anInstalledPackageWithAFailedExecutionProbeIsNotMarkedUsable() {
        val (result, _) = execute(ManagedEnvironment.inspectCommand(), probeFailure = true)
        val status = ManagedEnvironment.snapshot(result)
        assertEquals("2.1.100", status.claudeVersion)
        assertFalse(status.claudeUsable)
        assertNotNull(status.claudeError)
    }
    @Test fun failedDryRunDoesNotEmitASuccessfulStatusRecord() {
        val (result, _) = execute(ManagedEnvironment.inspectCommand(true), failure = "42")
        assertNotEquals(0, result.exitCode)
        assertThrows(Exception::class.java) { ManagedEnvironment.snapshot(result) }
    }
    @Test fun applyingUpdatesUsesUpgradeWithoutReplacingTheLinuxImage() {
        val (result, calls) = execute(ManagedEnvironment.upgradeCommand)
        assertEquals(0, result.exitCode)
        assertTrue(calls.contains("'update'"))
        assertTrue(calls.contains("'upgrade'"))
        assertFalse(calls.contains("'install'"))
        assertFalse(calls.contains("dist-upgrade"))
    }
}
