package br.com.projetoia.app.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ProgrammingVideoAttachmentsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun message(vararg parts: UIMessagePart) = UIMessage.user("edit").copy(parts = parts.toList())

    @Test fun programmingSendsAToolPathAndKeepsTheOriginalVideoAndMessage() {
        val uploads = temporary.newFolder()
        val video = File(uploads, "video clip.mp4").apply { writeText("PRIVATE_VIDEO_BYTES".repeat(2048)) }
        val original = message(UIMessagePart.Video(video.toURI().toString()))
        val converted = programmingVideoAttachments(listOf(original), uploads, true).single()
        val text = converted.parts.single() as UIMessagePart.Text
        assertEquals(original.id, converted.id)
        assertTrue(text.text.contains("/upload/video clip.mp4"))
        assertTrue(text.text.contains("/workspace"))
        assertTrue(text.text.length < 600)
        assertFalse(Json.encodeToString(converted).contains("PRIVATE_VIDEO_BYTES"))
        assertFalse(converted.parts.any { it is UIMessagePart.Video })
        assertTrue(original.parts.single() is UIMessagePart.Video)
        assertTrue(video.exists())
    }

    @Test fun videoDocumentsAndNestedToolVideosAlsoBecomeLocalReferences() {
        val uploads = temporary.newFolder()
        val video = File(uploads, "media.mp4").apply { writeText("video") }
        val original = message(
            UIMessagePart.Document(video.toURI().toString(), "clip.MP4", "application/octet-stream"),
            UIMessagePart.Tool(toolCallId = "call", toolName = "media", input = "{}", output = listOf(UIMessagePart.Video(video.toURI().toString()))),
        )
        val parts = programmingVideoAttachments(listOf(original), uploads, true).single().parts
        assertTrue(parts[0] is UIMessagePart.Text)
        assertTrue((parts[1] as UIMessagePart.Tool).output.single() is UIMessagePart.Text)
    }

    @Test fun nativeVideoOutsideProgrammingIsLeftUnchanged() {
        val original = listOf(message(UIMessagePart.Video("https://example.com/video.mp4")))
        assertSame(original, programmingVideoAttachments(original, File(temporary.root, "missing"), false))
    }

    @Test fun inaccessibleOrUntrustedVideoPathsAreRejectedInsteadOfPretendingTheFileWasRead() {
        val uploads = temporary.newFolder()
        val outside = temporary.newFile("outside.mp4").apply { writeText("private") }
        val empty = File(uploads, "empty.mp4").apply { createNewFile() }
        val urls = listOf(
            "https://example.com/video.mp4", outside.toURI().toString(),
            File(uploads, "missing.mp4").toURI().toString(), empty.toURI().toString(), uploads.toURI().toString(),
        )
        for (url in urls) assertThrows(UnavailableProgrammingVideo::class.java) {
            programmingVideoAttachments(listOf(message(UIMessagePart.Video(url))), uploads, true)
        }
    }

    @Test fun aSymlinkCannotExposeAFileOutsideTheUploadDirectory() {
        val uploads = temporary.newFolder()
        val outside = temporary.newFile("private.mp4").apply { writeText("private") }
        val link = File(uploads, "link.mp4")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(UnavailableProgrammingVideo::class.java) {
            programmingVideoAttachments(listOf(message(UIMessagePart.Video(link.toURI().toString()))), uploads, true)
        }
    }

    @Test fun textImagesAndOrdinaryDocumentsKeepTheirOriginalParts() {
        val original = message(
            UIMessagePart.Text("question"), UIMessagePart.Image("image.png"),
            UIMessagePart.Document("code.ts", "code.ts", "video/mp2t"),
            UIMessagePart.Document("document.pdf", "document.pdf", "application/pdf"),
        )
        val converted = programmingVideoAttachments(listOf(original), temporary.root, true).single()
        assertEquals(original, converted)
    }
}
