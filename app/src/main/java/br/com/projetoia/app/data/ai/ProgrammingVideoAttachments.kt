package br.com.projetoia.app.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import br.com.projetoia.app.data.files.chatVideoMimeType
import java.io.File
import java.net.URI

internal class UnavailableProgrammingVideo : IllegalArgumentException()

/** Video stays in app storage. Only a tool-readable path goes to the programming model. */
internal fun programmingVideoAttachments(
    messages: List<UIMessage>,
    uploadDirectory: File,
    enabled: Boolean,
    shellToolName: String = "workspace_shell",
): List<UIMessage> {
    if (!enabled) return messages
    fun reference(url: String): UIMessagePart.Text {
        val file = runCatching { File(URI(url)).canonicalFile }.getOrNull()
            ?: throw UnavailableProgrammingVideo()
        if (file.parentFile != uploadDirectory.canonicalFile || !file.isFile || !file.canRead() || file.length() == 0L) {
            throw UnavailableProgrammingVideo()
        }
        val path = Json.encodeToString("/upload/${file.name}")
        return UIMessagePart.Text(
            "Arquivo de vídeo local disponível em $path. Use $shellToolName, por exemplo ffprobe/ffmpeg, para inspecionar e editar. " +
                "Preserve o original em /upload e grave cópias e resultados em /workspace. " +
                "Este anexo fornece o arquivo, sem análise prévia do conteúdo."
        )
    }
    fun convert(part: UIMessagePart): UIMessagePart = when (part) {
        is UIMessagePart.Video -> reference(part.url)
        is UIMessagePart.Document -> if (chatVideoMimeType(part.fileName, part.mime) != null) reference(part.url) else part
        is UIMessagePart.Tool -> part.copy(output = part.output.map(::convert))
        else -> part
    }
    return messages.map { message -> message.copy(parts = message.parts.map(::convert)) }
}
