package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ClaudeApprovalsTest {
    @Test fun decisionsBelongToTheirConversationAndCannotBeReplayed() = runBlocking {
        val approvals = ClaudeApprovals()
        val a = Uuid.random(); val b = Uuid.random()
        val pending = approvals.register(a, "tool")
        assertFalse(approvals.resolve(b, "tool", true, ""))
        assertFalse(pending.isCompleted)
        assertTrue(approvals.resolve(a, "tool", false, "No"))
        assertEquals(ClaudeDecision(false, "No"), pending.await())
        assertFalse(approvals.resolve(a, "tool", true, ""))
    }

    @Test fun cancellingOneConversationDoesNotCancelAnother() {
        val approvals = ClaudeApprovals()
        val a = Uuid.random(); val b = Uuid.random()
        val first = approvals.register(a, "tool"); val second = approvals.register(b, "tool")
        approvals.cancel(a)
        assertTrue(first.isCancelled)
        assertFalse(approvals.contains(a, "tool"))
        assertFalse(second.isCancelled)
        assertTrue(approvals.contains(b, "tool"))
        approvals.cancel(b)
    }

    @Test fun aLiveApprovalCanBeClaimedOnlyOnce() = runBlocking {
        val approvals = ClaudeApprovals(); val conversation = Uuid.random()
        approvals.start(conversation)
        val pending = approvals.register(conversation, "tool")
        val claimed = approvals.claim(conversation, "tool")!!
        assertNull(approvals.claim(conversation, "tool"))
        assertTrue(approvals.isRunning(conversation))
        claimed.complete(ClaudeDecision(true))
        assertTrue(pending.await().approved)
        approvals.cancel(conversation)
        assertFalse(approvals.isRunning(conversation))
    }
}
