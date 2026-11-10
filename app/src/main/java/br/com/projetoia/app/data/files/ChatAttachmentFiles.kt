package br.com.projetoia.app.data.files

import java.io.File
import java.io.InputStream
import java.util.Locale

private val videoExtensions = mapOf(
    "mp4" to "video/mp4", "m4v" to "video/x-m4v", "mov" to "video/quicktime",
    "webm" to "video/webm", "mkv" to "video/x-matroska", "avi" to "video/x-msvideo",
    "3gp" to "video/3gpp", "3g2" to "video/3gpp2", "mpeg" to "video/mpeg",
    "mpg" to "video/mpeg",
)

/** Android providers can report generic MIME types for an MP4 selected through Files. */
internal fun chatVideoMimeType(fileName: String, mime: String?, header: ByteArray = byteArrayOf()): String? {
    val extension = fileName.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension in setOf("ts", "tsx")) return null // Android can label TypeScript as video/mp2t.
    val normalized = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    normalized?.takeIf { it.startsWith("video/") }?.let { return it }
    videoExtensions[extension]?.let { return it }
    if (normalized?.startsWith("audio/") == true ||
        extension in setOf("m4a", "m4b", "heic", "heif", "avif")) {
        return null
    }
    // Some document providers lose both MIME and extension. Inspect only a small local header.
    if (header.size >= 12 && header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") {
        val brand = header.copyOfRange(8, 12).toString(Charsets.US_ASCII)
        if (brand in
            setOf("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ", "qt  ")) {
            return if (brand ==
                "qt  ") {
                "video/quicktime"
            } else {
                "video/mp4"
            }
        }
    }
    if (header.size >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "AVI ") {
        return "video/x-msvideo"
    }
    return null
}

/** Stream into a private temporary file; never publish partial data or overwrite an existing file. */
internal fun copyChatAttachment(
    input: InputStream,
    target: File,
    stagingDirectory: File = target.parentFile,
    onProgress: ((Long) -> Unit)? = null,
) = input.use { source ->
    check(!target.exists()) { "Attachment destination already exists" }
    target.parentFile.mkdirs()
    stagingDirectory.mkdirs()
    val temporary = File.createTempFile(".attachment-", ".part", stagingDirectory)
    try {
        var received = 0L
        onProgress?.invoke(received)
        temporary.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Attachment import cancelled")
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
                received += count
                onProgress?.invoke(received)
            }
        }
        onProgress?.invoke(received)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Attachment import cancelled")
        check(!target.exists() && temporary.renameTo(target)) { "Unable to save attachment" }
    } finally {
        temporary.delete()
    }
}
