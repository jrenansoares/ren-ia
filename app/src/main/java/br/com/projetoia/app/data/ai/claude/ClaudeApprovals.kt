package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

internal data class ClaudeDecision(val approved: Boolean, val reason: String = "")

/** Live native decisions must resume stdin, never start another generation or replay a tool. */
class ClaudeApprovals {
    private data class Key(val conversation: Uuid, val tool: String)
    private val pending = ConcurrentHashMap<Key, CompletableDeferred<ClaudeDecision>>()
    private val running = ConcurrentHashMap.newKeySet<Uuid>()

    internal fun start(conversation: Uuid) {
        check(running.add(conversation)) { "Claude Code is already running" }
    }
    fun isRunning(conversation: Uuid): Boolean = conversation in running
    internal fun claim(
        conversation: Uuid,
        tool: String
    ): CompletableDeferred<ClaudeDecision>? = pending.remove(Key(conversation, tool))

    internal fun register(conversation: Uuid, tool: String): CompletableDeferred<ClaudeDecision> {
        val value = CompletableDeferred<ClaudeDecision>()
        check(pending.putIfAbsent(Key(conversation, tool), value) == null) { "Duplicate native approval" }
        return value
    }

    fun contains(conversation: Uuid, tool: String): Boolean = pending.containsKey(Key(conversation, tool))

    fun resolve(conversation: Uuid, tool: String, approved: Boolean, reason: String): Boolean =
        pending.remove(Key(conversation, tool))?.complete(ClaudeDecision(approved, reason)) == true

    internal fun cancel(conversation: Uuid) {
        running.remove(conversation)
        pending.keys.filter { it.conversation == conversation }.forEach {
            pending.remove(it)?.cancel()
        }
    }
}
