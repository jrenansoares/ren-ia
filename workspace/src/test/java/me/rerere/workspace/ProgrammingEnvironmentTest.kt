package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ProgrammingEnvironmentTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun incompleteFailedOrTruncatedChecksAreNeverReady() {
        val success = WorkspaceCommandResult(0, "PROJETO_IA_ENVIRONMENT_OK\n", "")
        assertTrue(ProgrammingEnvironment.isReady(success))
        assertFalse(ProgrammingEnvironment.isReady(success.copy(exitCode = 1)))
        assertFalse(ProgrammingEnvironment.isReady(success.copy(timedOut = true)))
        assertFalse(ProgrammingEnvironment.isReady(success.copy(truncated = true)))
        assertFalse(ProgrammingEnvironment.isReady(success.copy(stdout = "prefix_PROJETO_IA_ENVIRONMENT_OK")))
        assertFalse(ProgrammingEnvironment.isReady(success.copy(stdout = "")))
    }

    @Test fun smokeRecipeActuallyEditsTestsAndCleansUpWithoutTouchingExistingFiles() {
        val project = tmp.newFolder("project with spaces")
        val existing = File(project, "existing.txt").apply { writeText("keep this") }
        val shadow = File(project, "tempfile.py").apply { writeText("raise RuntimeError('project code must not run during setup')") }
        val process = ProcessBuilder("/bin/sh", "-c", ProgrammingEnvironment.smokeTestCommand).directory(project).start()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        assertTrue("Smoke command did not finish", finished)
        val result = WorkspaceCommandResult(process.exitValue(), process.inputStream.bufferedReader().readText(), process.errorStream.bufferedReader().readText())
        assertTrue(result.stderr, ProgrammingEnvironment.smokeTestPassed(result))
        assertEquals("keep this", existing.readText())
        assertTrue(shadow.readText().startsWith("raise RuntimeError"))
        assertEquals(listOf("existing.txt", "tempfile.py"), project.listFiles()!!.map { it.name }.sorted())
    }

    @Test fun expectedArchiveDigestIsChecked() {
        val archive = tmp.newFile().apply { writeText("abc") }
        val installer = RootfsInstaller(WorkspaceManager(tmp.newFolder()))
        val progress = mutableListOf<RootfsInstallProgress>()
        installer.verifyArchiveSha256(archive, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", progress::add)
        assertEquals(RootfsInstallStage.VERIFYING, progress.last().stage)
        assertEquals(3L, progress.last().bytesRead)
        assertEquals(3L, progress.last().totalBytes)
        for (invalid in listOf("0".repeat(64), "", "not-a-hash")) {
            try {
                installer.verifyArchiveSha256(archive, invalid)
                fail("Expected digest rejection")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun failedPackageCommandPreservesTheExitCodeAndNeverSignalsCompletion() {
        val commands = tmp.newFolder("fake commands")
        File(commands, "dpkg").apply { writeText("#!/bin/sh\nexit 0\n"); setExecutable(true) }
        File(commands, "apt-get").apply { writeText("#!/bin/sh\nprintf 'E: simulated download failure\\n' >&2\nexit 42\n"); setExecutable(true) }
        val process = ProcessBuilder("/bin/sh", "-c", ProgrammingEnvironment.installCommand)
            .apply { environment()["PATH"] = commands.absolutePath + ":/usr/bin:/bin" }.start()
        val output = StringBuilder()
        val result = process.readResult(3_000, onOutput = { text, _ -> synchronized(output) { output.append(text) } })
        assertEquals(42, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(output.contains("simulated download failure"))
        assertFalse(output.contains("PROJETO_IA_SETUP:DONE"))
    }
}
