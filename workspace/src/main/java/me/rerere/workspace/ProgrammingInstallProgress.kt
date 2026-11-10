package me.rerere.workspace

enum class ProgrammingInstallStage { REPAIR, INDEX, DOWNLOAD, INSTALL, DONE }

data class ProgrammingInstallProgress(
    val stage: ProgrammingInstallStage = ProgrammingInstallStage.REPAIR,
    val fraction: Float? = null,
    val packageName: String = "",
    val message: String = "",
    val log: String = "",
    val lastProgressMillis: Long = 0L,
)

/** Consumes real APT status records. Database-reading percentages are only log text. */
class ProgrammingInstallTracker {
    private val pending = arrayOf("", "")
    private var progress = ProgrammingInstallProgress()
    private var downloadItem: String? = null
    private var downloadPercent: Float? = null

    @Synchronized
    fun accept(chunk: String, stderr: Boolean, nowMillis: Long): ProgrammingInstallProgress {
        val stream = if (stderr) 1 else 0
        val text = pending[stream] + chunk
        var start = 0
        for (index in text.indices) {
            if (text[index] == '\n' || text[index] == '\r') {
                acceptLine(text.substring(start, index), nowMillis)
                start = index + 1
            }
        }
        pending[stream] = text.substring(start).takeLast(MAX_LOG_CHARS)
        return progress
    }

    @Synchronized
    fun finish(nowMillis: Long): ProgrammingInstallProgress {
        pending.forEach { if (it.isNotBlank()) acceptLine(it, nowMillis) }
        pending.fill("")
        return progress
    }

    private fun acceptLine(raw: String, now: Long) {
        val line = raw.trim()
        if (line.isBlank()) return
        val stage = when (line) {
            "PROJETO_IA_SETUP:REPAIR" -> ProgrammingInstallStage.REPAIR
            "PROJETO_IA_SETUP:INDEX" -> ProgrammingInstallStage.INDEX
            "PROJETO_IA_SETUP:PACKAGES" -> ProgrammingInstallStage.DOWNLOAD
            "PROJETO_IA_SETUP:DONE" -> ProgrammingInstallStage.DONE
            else -> null
        }
        if (stage != null) {
            downloadItem = null
            downloadPercent = null
            progress =
                progress.copy(stage = stage, fraction = null, packageName = "", message = "", lastProgressMillis = now)
            return
        }
        val status = STATUS.matchEntire(line)
        if (status != null) {
            val (kind, item, percentage, message) = status.destructured
            val percent = percentage.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..100f } ?: return
            if (kind == "pmerror") {
                progress = progress.copy(message = message, log = appendLog(line), lastProgressMillis = now)
                return
            }
            val nextStage = if (kind == "pmstatus") ProgrammingInstallStage.INSTALL else progress.stage
            // Index downloads have unknown totals. Do not turn their estimate into a measured bar.
            val fraction = if (nextStage == ProgrammingInstallStage.INDEX) null else percent / 100f
            val packageName = if (kind == "pmstatus") item else ""
            val downloadChanged = kind == "dlstatus" && (item != downloadItem || percent != downloadPercent)
            val changed =
                nextStage != progress.stage || fraction != progress.fraction || packageName != progress.packageName ||
                    downloadChanged
            if (kind == "dlstatus") {
                downloadItem = item
                downloadPercent = percent
            }
            progress = progress.copy(
                stage = nextStage,
                fraction = fraction,
                packageName = packageName,
                message = message,
                log = if (changed) appendLog(message) else progress.log,
                lastProgressMillis = if (changed) now else progress.lastProgressMillis,
            )
            return
        }
        progress = progress.copy(
            message = line.take(300),
            log = appendLog(line),
            lastProgressMillis = if (line != progress.message) now else progress.lastProgressMillis,
        )
    }

    private fun appendLog(line: String) = (progress.log + line + "\n").takeLast(MAX_LOG_CHARS)

    companion object {
        const val MAX_LOG_CHARS = 12_000
        private val STATUS = Regex("^(dlstatus|pmstatus|pmerror):(.+?):([0-9]+(?:\\.[0-9]+)?):(.*)$")
    }
}
