package br.com.projetoia.app.data.files

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class ChatAttachmentFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun mp4FromFilesIsVideoEvenWhenAndroidReturnsAGenericOrWrongMime() {
        for (mime in listOf(null, "application/octet-stream", "text/plain", "video/mp4")) {
            assertEquals("video/mp4", chatVideoMimeType("SaveClip.App_AQMcVCk_JA0_arquivo.MP4", mime))
        }
        assertEquals("video/quicktime", chatVideoMimeType("camera.mov", "application/octet-stream"))
        assertEquals("video/webm", chatVideoMimeType("file", " VIDEO/WEBM; codecs=vp9 "))
    }

    @Test fun regularCodeAndUnknownBinaryFilesDoNotBecomeVideos() {
        assertNull(chatVideoMimeType("component.ts", "video/mp2t"))
        assertNull(chatVideoMimeType("component.tsx", "application/octet-stream"))
        assertNull(chatVideoMimeType("notes.txt", "text/plain"))
        assertNull(chatVideoMimeType("archive.unknown", "application/octet-stream"))
    }

    @Test fun unnamedMp4CanBeRecognizedByHeaderWithoutMistakingHeifOrAudioForVideo() {
        val mp4 = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray()
        assertEquals("video/mp4", chatVideoMimeType("download", "application/octet-stream", mp4))
        assertEquals("video/mp4", chatVideoMimeType("clip.MP4  ", null))
        assertNull(chatVideoMimeType("photo", null, byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray()))
        assertNull(chatVideoMimeType("sound.m4a", "audio/mp4", mp4))
        assertNull(chatVideoMimeType("file", null, byteArrayOf(1, 2, 3)))
    }

    @Test fun aLargeFileIsCopiedWithoutTheTextDocumentSizeLimitAndReportsActualBytes() {
        val bytes = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
        val file = File(temporary.root, "video.mp4")
        val progress = mutableListOf<Long>()
        copyChatAttachment(ByteArrayInputStream(bytes), file, onProgress = progress::add)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(0L, progress.first())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(listOf(file), temporary.root.listFiles()!!.toList())
    }

    @Test fun aReadFailureLeavesNoPartialAttachmentAndRetryCanFinish() {
        val file = File(temporary.root, "video.mp4")
        val failing = object : InputStream() {
            var reads = 0
            override fun read(): Int = if (reads++ < 16) 1 else throw IOException("provider read failed")
        }
        assertThrows(IOException::class.java) { copyChatAttachment(failing, file) }
        assertFalse(file.exists())
        assertTrue(temporary.root.listFiles()!!.isEmpty())
        copyChatAttachment(ByteArrayInputStream(byteArrayOf(1, 2, 3)), file)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test fun cancellingACopyClosesTheSourceAndDoesNotPublishAPartialFile() {
        val file = File(temporary.root, "video.mp4")
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(256 * 1024)) {
            override fun close() { closed = true; super.close() }
        }
        assertThrows(CancellationException::class.java) {
            copyChatAttachment(source, file) { if (it > 0) throw CancellationException("cancelled") }
        }
        assertTrue(closed)
        assertFalse(file.exists())
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun anExistingFileIsPreservedAndItsRejectedInputIsClosed() {
        val file = temporary.newFile("keep.mp4").apply { writeText("original") }
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun close() { closed = true; super.close() }
        }
        assertThrows(IllegalStateException::class.java) { copyChatAttachment(source, file) }
        assertEquals("original", file.readText())
        assertTrue(closed)
    }

    @Test fun partialDataCanStayOutsideTheDirectoryExposedToTheProject() {
        val uploads = temporary.newFolder("uploads")
        val staging = temporary.newFolder("staging")
        val target = File(uploads, "video.mp4")
        val bytes = ByteArray(128 * 1024) { 42 }
        copyChatAttachment(ByteArrayInputStream(bytes), target, staging) {
            assertTrue(uploads.listFiles()!!.isEmpty())
        }
        assertArrayEquals(bytes, target.readBytes())
        assertTrue(staging.listFiles()!!.isEmpty())
    }
}
