package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Bounded JSON-lines transport. Cancelling receive() always reaches close() and kills PRoot. */
internal class ClaudeProcessTransport(private val process: Process) : Closeable {
    private val frames = Channel<JsonObject>(8)
    private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val errors = StringBuilder()
    private val ended = CompletableDeferred<Unit>()
    private val readerThread = thread(isDaemon = true, name = "claude-json-reader") {
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = StringBuilder()
                    while (true) {
                        val char = reader.read()
                        if (char < 0 || char == 10) break
                        check(line.length < 4 * 1024 * 1024) { "Claude event exceeds the size limit" }
                        line.append(char.toChar())
                    }
                    if (line.isEmpty()) {
                        if (!process.isAlive || reader.peekEof()) break
                        continue
                    }
                    val value = Json.parseToJsonElement(line.toString()).jsonObject
                    if (frames.trySendBlocking(value).isFailure) break
                }
            }
            frames.close()
        } catch (e: Exception) {
            frames.close(e)
        } finally {
            ended.complete(Unit)
        }
    }
    private val stderrThread = thread(isDaemon = true, name = "claude-stderr-reader") {
        try {
            process.errorStream.bufferedReader().use { reader ->
                val buffer = CharArray(2048)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    synchronized(errors) {
                        errors.append(buffer, 0, n)
                        if (errors.length > 4096) errors.delete(0, errors.length - 4096)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    fun write(frame: JsonObject) = synchronized(writer) {
        writer.write(frame.toString())
        writer.newLine()
        writer.flush()
    }

    suspend fun read(): JsonObject? = frames.receiveCatching().let { result ->
        result.exceptionOrNull()?.let { throw it }
        result.getOrNull()
    }

    suspend fun awaitDecision(decision: CompletableDeferred<ClaudeDecision>): ClaudeDecision = select {
        decision.onAwait { it }
        ended.onAwait { error("Claude Code encerrou enquanto aguardava aprovação. Os arquivos foram preservados.") }
    }

    fun diagnostic(token: String, url: String): String = synchronized(errors) {
        errors.toString().replace(token, "[credencial local]").replace(url, "[conexão local]").takeLast(2000)
    }

    override fun close() {
        frames.cancel()
        process.destroyForcibly()
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        process.waitFor(500, TimeUnit.MILLISECONDS)
        readerThread.join(500)
        stderrThread.join(500)
    }

    private fun java.io.BufferedReader.peekEof(): Boolean {
        mark(1)
        val next = read()
        reset()
        return next < 0
    }
}
