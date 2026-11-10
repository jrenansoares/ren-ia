package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class ClaudeProtocolTest {
    @Test fun interruptedCallsAreFinishedWithoutInventingExecutionSuccess() {
        val completed = UIMessagePart.Tool("done", "Read", "{}", output = listOf(UIMessagePart.Text("result")))
        val pending = UIMessagePart.Tool("pending", "Bash", "{}", approvalState = ToolApprovalState.Pending)
        val stopped = ClaudeProtocol.interrupted(UIMessage.assistant("").copy(parts = listOf(completed, pending)))
        assertEquals(completed, stopped.getTools().first())
        assertFalse(stopped.getTools().last().isPending)
        assertTrue(stopped.getTools().last().isExecuted)
        assertTrue(stopped.getTools().last().output.filterIsInstance<UIMessagePart.Text>().single().text.contains("antes de confirmar"))
    }

    @Test fun toolOutputsMatchCallsAndImagePayloadsStayOutOfChatHistory() {
        val original = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Tool("call", "Read", "{}")))
        val frame = Json.parseToJsonElement("""{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"call","content":[{"type":"text","text":"Read image"},{"type":"image","source":{"data":"private-base64"}}]}]}}""").jsonObject
        val message = ClaudeProtocol.toolResult(original, frame)
        assertTrue(message.getTools().single().isExecuted)
        assertFalse(message.toString().contains("private-base64"))
    }

    @Test fun aNativeExitUnblocksPendingPermissionWithoutAUserClick() = runBlocking {
        withTimeout(3000) {
            val process = ProcessBuilder("/bin/sh", "-c", "exit 0").start()
            val io = ClaudeProcessTransport(process)
            try {
                var failed = false
                try { io.awaitDecision(CompletableDeferred()) } catch (_: IllegalStateException) { failed = true }
                assertTrue(failed)
            } finally { io.close() }
        }
    }
}
