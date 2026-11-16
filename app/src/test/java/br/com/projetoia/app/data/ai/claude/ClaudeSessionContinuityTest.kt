package br.com.projetoia.app.data.ai.claude

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSessionContinuityTest {
    @Test
    fun freshConversationHasNoCompletedAssistantTurn() {
        val history = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("Create hello.txt"),
        )
        assertFalse(hasCompletedAssistantTurn(history))
        assertEquals(listOf("Create hello.txt"), tailSinceLastCompletedAssistant(history).map { it.toText() })
    }

    @Test
    fun resumedTurnSendsOnlyMessagesAfterLastCompletedAssistantTurn() {
        val history = listOf(
            UIMessage.user("Turn 1 prompt"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool("tool_1", "Write", """{"path":"a.txt"}"""),
                    UIMessagePart.Text("Created a.txt"),
                ),
                finishedAt = LocalDateTime(2026, 11, 10, 12, 0),
            ),
            UIMessage.user("Turn 2 prompt"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                finishedAt = null,
            ),
        )
        assertTrue(hasCompletedAssistantTurn(history))
        val tail = tailSinceLastCompletedAssistant(history)
        assertEquals(1, tail.size)
        assertEquals("Turn 2 prompt", tail.single().toText())
    }

    @Test
    fun completedAssistantWithoutTrailingUserMessageProducesEmptyTail() {
        val history = listOf(
            UIMessage.user("Turn 1"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("Answer 1")),
            ),
        )
        assertTrue(hasCompletedAssistantTurn(history))
        assertTrue(tailSinceLastCompletedAssistant(history).isEmpty())
    }
}
