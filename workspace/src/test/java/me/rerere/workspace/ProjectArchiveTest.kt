package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    private val info = ProjectArchiveInfo(UUID.randomUUID().toString(), "Vídeo")

    @Test fun roundTripPreservesBinaryDotfilesEmptyFoldersAndExecutableLinks() {
        val source = folder.newFolder("source")
        File(source, ".git").mkdir()
        File(source, ".git/HEAD").writeText("ref: refs/heads/main")
        File(source, "empty").mkdir()
        File(source, "video.mp4").writeBytes(ByteArray(300_017) { (it % 251).toByte() })
        val script = File(source, "render.sh").apply { writeText("#!/bin/sh\necho ready"); setExecutable(true) }
        Files.createSymbolicLink(File(source, "run").toPath(), script.toPath().fileName)
        val archive = ByteArrayOutputStream()
        var exported = ProjectArchiveProgress(0, 0)
        ProjectArchive.export(source, info, archive) { exported = it }
        val destination = folder.newFolder("destination")
        var restored = ProjectArchiveProgress(0, 0)
        assertEquals(info, ProjectArchive.restore(ByteArrayInputStream(archive.toByteArray()), destination) { restored = it })
        assertArrayEquals(File(source, "video.mp4").readBytes(), File(destination, "video.mp4").readBytes())
        assertEquals(File(source, ".git/HEAD").readText(), File(destination, ".git/HEAD").readText())
        assertTrue(File(destination, "empty").isDirectory)
        assertTrue(File(destination, "render.sh").canExecute())
        assertTrue(Files.isSymbolicLink(File(destination, "run").toPath()))
        assertEquals("render.sh", Files.readSymbolicLink(File(destination, "run").toPath()).toString())
        assertEquals(exported, restored)
    }

    @Test fun externalLinksCannotExportPrivateFiles() {
        val source = folder.newFolder("source")
        val secret = folder.newFile("outside.txt").apply { writeText("outside") }
        Files.createSymbolicLink(File(source, "outside").toPath(), secret.toPath())
        assertThrows(IllegalArgumentException::class.java) { ProjectArchive.export(source, info, ByteArrayOutputStream()) }
        assertEquals("outside", secret.readText())
    }

    @Test fun traversalUnexpectedAndConflictingZipEntriesLeaveNoPublishedFiles() {
        for (name in listOf("files/../outside.txt", "files//absolute", "files/./x", "files/a\\b", "linux/usr/bin/app")) {
            val destination = folder.newFolder()
            val bytes = zip(listOf("files/good.txt" to "good", name to "bad"))
            assertThrows(IllegalArgumentException::class.java) { ProjectArchive.restore(ByteArrayInputStream(bytes), destination) }
            assertTrue(destination.list().isNullOrEmpty())
        }
    }

    @Test fun missingManifestFailsAndAnExistingProjectIsNeverOverwritten() {
        val destination = folder.newFolder()
        assertThrows(IllegalArgumentException::class.java) { ProjectArchive.restore(ByteArrayInputStream(zip(listOf("files/a" to "data"))), destination) }
        assertTrue(destination.list().isNullOrEmpty())
        File(destination, "original.txt").writeText("keep")
        assertThrows(IllegalArgumentException::class.java) { ProjectArchive.restore(ByteArrayInputStream(byteArrayOf()), destination) }
        assertEquals("keep", File(destination, "original.txt").readText())
    }

    @Test fun symlinkParentAndEscapingChainsAreRejectedWithoutTouchingOutside() {
        val outside = folder.newFile("outside.txt").apply { writeText("keep") }
        val manifest = """{"info":{"projectId":"${info.projectId}","name":"X"},"links":{"x":".","x/y":"../outside.txt"}}"""
        val destination = folder.newFolder()
        assertThrows(IllegalArgumentException::class.java) { ProjectArchive.restore(ByteArrayInputStream(zip(listOf("projeto-ia-project.json" to manifest))), destination) }
        assertEquals("keep", outside.readText())
        assertTrue(destination.list().isNullOrEmpty())
    }

    @Test fun cancellationStopsCopyWithoutPublishingAPartialRestore() {
        val source = folder.newFolder("source"); File(source, "file").writeBytes(ByteArray(200_000))
        val archive = ByteArrayOutputStream(); ProjectArchive.export(source, info, archive)
        val destination = folder.newFolder("destination")
        try {
            assertThrows(InterruptedException::class.java) {
                ProjectArchive.restore(ByteArrayInputStream(archive.toByteArray()), destination) { Thread.currentThread().interrupt() }
            }
        } finally { Thread.interrupted() }
        assertTrue(destination.list().isNullOrEmpty())
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray = ByteArrayOutputStream().also { buffer ->
        ZipOutputStream(buffer).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    }.toByteArray()
}
