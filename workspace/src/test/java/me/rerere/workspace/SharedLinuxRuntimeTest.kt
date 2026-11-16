package me.rerere.workspace

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class SharedLinuxRuntimeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun rootfs(dir: File, label: String = "shared") {
        File(dir, "bin").mkdirs()
        File(dir, "bin/sh").writeText("#!/bin/sh\n")
        File(dir, "label").writeText(label)
    }

    @Test fun projectsCreatedBeforeAndAfterTheDownloadUseTheSamePersistentLinuxAndTools() {
        val base = temporary.newFolder()
        val manager = WorkspaceManager(base)
        manager.ensureWorkspace("before-download")
        assertFalse(manager.hasRootfs("before-download"))
        val tool = File(manager.sharedLinuxDir(), "usr/bin/shared-tool")
        rootfs(manager.sharedLinuxDir())
        tool.parentFile.mkdirs()
        tool.writeText("installed once")
        val reopened = WorkspaceManager(base)
        reopened.ensureWorkspace("after-download")
        for (id in listOf("before-download", "after-download")) {
            assertTrue(reopened.hasRootfs(id))
            assertTrue(reopened.usesSharedLinux(id))
            assertEquals("installed once", reopened.resolveRootfsFile(id, "/usr/bin/shared-tool").readText())
            assertFalse(reopened.privateLinuxDir(id).exists())
        }
    }

    @Test fun newProjectsReuseLinuxButKeepTheirFilesHomesAndTemporaryDirectoriesSeparate() {
        val contexts = mutableListOf<WorkspaceShellContext>()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                contexts += context
                return WorkspaceCommandResult(0, "ok", "")
            }
        }
        val manager = WorkspaceManager(temporary.newFolder(), shellRunner = runner)
        rootfs(manager.sharedLinuxDir())
        for (id in listOf("first", "second")) {
            manager.ensureWorkspace(id)
            manager.writeText(id, "video.txt", id)
            File(manager.homeDir(id), "settings").writeText(id)
            manager.executeCommand(id, "pwd")
        }
        assertEquals(manager.sharedLinuxDir(), contexts[0].linuxDir)
        assertEquals(contexts[0].linuxDir, contexts[1].linuxDir)
        assertNotEquals(contexts[0].filesDir, contexts[1].filesDir)
        assertNotEquals(contexts[0].tempDir, contexts[1].tempDir)
        assertEquals("first", manager.readText("first", "video.txt"))
        assertEquals("second", manager.readText("second", "video.txt"))
        for (id in listOf("first", "second")) {
            val context = contexts.first { it.root == id }
            val homeMount = context.bindMounts.single { it.target == "/root" }
            assertEquals(manager.homeDir(id), homeMount.source)
            assertEquals("$id", manager.resolveRootfsFile(id, "/root/settings").readText())
            assertEquals(File(manager.tempDir(id), "linux-tmp"), manager.resolveRootfsPath(id, "/tmp/test").rootDir)
            assertEquals(File(manager.tempDir(id), "linux-var-tmp"), manager.resolveRootfsPath(id, "/var/tmp/test").rootDir)
        }
    }

    @Test fun anOldProjectCanAdoptSharedLinuxAndReturnWithoutLosingItsFilesOrOldRuntime() {
        val base = temporary.newFolder()
        val manager = WorkspaceManager(base)
        manager.ensureWorkspace("legacy")
        rootfs(manager.privateLinuxDir("legacy"), "private")
        rootfs(manager.sharedLinuxDir())
        manager.writeText("legacy", "keep.txt", "original")
        assertFalse(manager.usesSharedLinux("legacy"))
        manager.useSharedLinux("legacy")
        assertEquals(manager.sharedLinuxDir(), manager.linuxDir("legacy"))
        assertEquals("private", File(manager.privateLinuxDir("legacy"), "label").readText())
        val reopened = WorkspaceManager(base)
        assertTrue(reopened.usesSharedLinux("legacy"))
        assertEquals("original", reopened.readText("legacy", "keep.txt"))
        reopened.usePrivateLinux("legacy")
        assertFalse(reopened.usesSharedLinux("legacy"))
        assertEquals("private", File(reopened.linuxDir("legacy"), "label").readText())
    }

    @Test fun deletingAProjectDoesNotDeleteSharedLinuxOrOtherProjectFiles() {
        val manager = WorkspaceManager(temporary.newFolder())
        rootfs(manager.sharedLinuxDir())
        manager.ensureWorkspace("deleted")
        manager.ensureWorkspace("kept")
        manager.writeText("kept", "keep", "safe")
        assertTrue(manager.deleteWorkspace("deleted"))
        assertTrue(manager.hasSharedRootfs())
        assertEquals("safe", manager.readText("kept", "keep"))
        assertThrows(IllegalArgumentException::class.java) { manager.deleteWorkspace(".shared-linux") }
        assertThrows(IllegalArgumentException::class.java) { manager.deleteWorkspace("..") }
    }

    @Test fun terminalAndAgentUseTheSameSharedRuntimeAndProjectMounts() {
        val manager = WorkspaceManager(temporary.newFolder(), shellRunner = ProotShellRunner(temporary.newFolder()))
        rootfs(manager.sharedLinuxDir())
        manager.ensureWorkspace("video")
        val terminal = manager.terminalLaunch("video", false)!!
        assertEquals(manager.sharedLinuxDir().absolutePath, terminal.args[4])
        assertTrue("${manager.filesDir("video").absolutePath}:/workspace" in terminal.args)
        assertTrue("${manager.homeDir("video").absolutePath}:/root" in terminal.args)
        assertTrue("${File(manager.tempDir("video"), "linux-tmp").absolutePath}:/tmp" in terminal.args)
    }

    @Test fun terminalLaunchPassesExtraEnvironmentIntoProot() {
        val manager = WorkspaceManager(temporary.newFolder(), shellRunner = ProotShellRunner(temporary.newFolder()))
        rootfs(manager.sharedLinuxDir())
        manager.ensureWorkspace("envtest")
        val terminal = manager.terminalLaunch(
            "envtest",
            false,
            extraEnv = mapOf("ANTHROPIC_BASE_URL" to "http://127.0.0.1:54321", "ANTHROPIC_MODEL" to "projeto-free"),
        )!!
        assertTrue("ANTHROPIC_BASE_URL=http://127.0.0.1:54321" in terminal.args)
        assertTrue("ANTHROPIC_MODEL=projeto-free" in terminal.args)
    }

    @Test fun temporaryCleanupPreservesTheSharedInstallerAndOldRuntimeRecoveryCopy() {
        val manager = WorkspaceManager(temporary.newFolder())
        rootfs(manager.sharedLinuxDir())
        manager.ensureWorkspace("video")
        val archive = File(manager.sharedTempDir(), "rootfs.tar.gz").apply { parentFile.mkdirs(); writeText("in progress") }
        File(manager.tempDir("video"), "discard").writeText("temp")
        manager.cleanupAllTempDirs()
        assertEquals("in progress", archive.readText())
        assertTrue(manager.hasSharedRootfs())
        assertFalse(File(manager.tempDir("video"), "discard").exists())
    }

    @Test fun interruptedPublicationRestoresThePreviousRuntimeAfterRestart() {
        val base = temporary.newFolder()
        val manager = WorkspaceManager(base)
        rootfs(manager.sharedLinuxDir())
        val backup = File(manager.sharedLinuxDir().parentFile, ".linux-previous")
        assertTrue(manager.sharedLinuxDir().renameTo(backup))
        assertTrue(WorkspaceManager(base).hasSharedRootfs())
        manager.ensureWorkspace("legacy")
        rootfs(manager.privateLinuxDir("legacy"), "legacy")
        val privateBackup = File(manager.privateLinuxDir("legacy").parentFile, ".linux-previous")
        assertTrue(manager.privateLinuxDir("legacy").renameTo(privateBackup))
        val reopened = WorkspaceManager(base)
        assertFalse(reopened.usesSharedLinux("legacy"))
        reopened.ensureWorkspace("legacy")
        assertFalse(reopened.usesSharedLinux("legacy"))
        assertEquals("legacy", File(reopened.linuxDir("legacy"), "label").readText())
    }

    @Test fun concurrentSharedInstallRequestsDownloadOnceAndRemainReusableAfterRestart() {
        val manager = WorkspaceManager(temporary.newFolder())
        val installer = RootfsInstaller(manager)
        val bytes = archive()
        val downloads = AtomicInteger()
        withServer(bytes, downloads) { url ->
            val pool = Executors.newFixedThreadPool(2)
            try {
                val jobs = pool.invokeAll(List(2) { Callable { installer.installShared(url, sha256(bytes)) } })
                jobs.forEach { it.get(5, TimeUnit.SECONDS) }
                assertEquals(1, downloads.get())
                installer.installShared(url, sha256(bytes))
                assertEquals(1, downloads.get())
                assertTrue(manager.hasSharedRootfs())
                val restarted = WorkspaceManager(manager.sharedLinuxDir().parentFile.parentFile)
                RootfsInstaller(restarted).installShared(url, sha256(bytes))
                assertEquals(1, downloads.get())
                assertTrue(restarted.hasRootfs("new-after-restart"))
                for (id in listOf("one", "two")) {
                    manager.ensureWorkspace(id)
                    assertTrue(manager.hasRootfs(id))
                    assertFalse(manager.privateLinuxDir(id).exists())
                }
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun aFailedSharedDownloadPreservesProjectFilesAndCanBeRetried() {
        val manager = WorkspaceManager(temporary.newFolder())
        manager.ensureWorkspace("video")
        manager.writeText("video", "original.mp4", "video data")
        val installer = RootfsInstaller(manager)
        val bytes = archive()
        withServer(bytes, AtomicInteger()) { url ->
            assertThrows(IllegalArgumentException::class.java) { installer.installShared(url, "0".repeat(64)) }
            assertFalse(manager.hasSharedRootfs())
            assertEquals("video data", manager.readText("video", "original.mp4"))
            assertFalse(File(manager.sharedTempDir(), "rootfs-staging").exists())
            installer.installShared(url, sha256(bytes))
            assertTrue(manager.hasRootfs("video"))
        }
    }

    @Test fun sharedSetupUsesAnIndependentWorkingDirectory() {
        var captured: WorkspaceShellContext? = null
        val manager = WorkspaceManager(temporary.newFolder(), shellRunner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                captured = context
                return WorkspaceCommandResult(0, "ok", "")
            }
        })
        manager.ensureWorkspace("video")
        manager.executeSharedCommand("check", 1000)
        assertEquals(manager.sharedLinuxDir(), captured!!.linuxDir)
        assertNotEquals(manager.filesDir("video"), captured!!.filesDir)
        assertEquals(manager.sharedTempDir(), captured!!.tempDir)
    }

    @Test fun cancellingAFirstInstallDoesNotPublishAPartialRuntimeAndRetryWorks() {
        val manager = WorkspaceManager(temporary.newFolder())
        val installer = RootfsInstaller(manager)
        val bytes = archive()
        withServer(bytes, AtomicInteger()) { url ->
            try {
                assertThrows(InterruptedException::class.java) {
                    installer.installShared(url, sha256(bytes)) { progress ->
                        if (progress.stage == RootfsInstallStage.DOWNLOADING && progress.bytesRead > 0) Thread.currentThread().interrupt()
                    }
                }
            } finally { Thread.interrupted() }
            assertFalse(manager.hasSharedRootfs())
            assertFalse(File(manager.sharedTempDir(), "rootfs.tar.gz").exists())
            installer.installShared(url, sha256(bytes))
            assertTrue(manager.hasSharedRootfs())
        }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun withServer(bytes: ByteArray, downloads: AtomicInteger, action: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rootfs.tar.gz") { exchange ->
            downloads.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { action("http://127.0.0.1:${server.address.port}/rootfs.tar.gz") } finally { server.stop(0) }
    }

    private fun archive(): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { output ->
            val content = "#!/bin/sh\n".toByteArray()
            val header = ByteArray(512)
            "bin/sh".toByteArray().copyInto(header)
            "0000755".toByteArray().copyInto(header, 100)
            content.size.toString(8).padStart(11, '0').toByteArray().copyInto(header, 124)
            header[156] = '0'.code.toByte()
            header.fill(' '.code.toByte(), 148, 156)
            header.sumOf { it.toUByte().toInt() }.toString(8).padStart(6, '0').toByteArray().copyInto(header, 148)
            output.write(header)
            output.write(content)
            output.write(ByteArray(512 - content.size))
            output.write(ByteArray(1024))
        }
        return bytes.toByteArray()
    }
}
