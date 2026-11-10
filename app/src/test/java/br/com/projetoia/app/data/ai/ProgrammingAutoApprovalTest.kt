package br.com.projetoia.app.data.ai

import kotlinx.serialization.json.Json
import br.com.projetoia.app.data.ai.tools.resolveWorkspaceToolApproval
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.model.Assistant
import br.com.projetoia.app.data.model.Conversation
import br.com.projetoia.app.data.model.ConversationConfig
import br.com.projetoia.app.data.model.getAssistantOf
import br.com.projetoia.app.data.model.withAssistantUpdate
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ProgrammingAutoApprovalTest {
    @Test
    fun automaticApprovalIsExplicitAndDoesNotAffectOtherToolTypes() {
        assertFalse(Json.decodeFromString<ConversationConfig>("{}").programmingAutoApprove)
        for (name in listOf("workspace_read_file", "workspace_write_file", "workspace_edit_file", "workspace_shell")) {
            assertFalse(resolveWorkspaceToolApproval(name, mapOf(name to true), forceApprovals = true,
                autoApprove = true, outsideWritableRoots = true))
        }
        assertTrue(resolveWorkspaceToolApproval("workspace_write_file", mapOf("workspace_write_file" to false),
            forceApprovals = false, autoApprove = false, outsideWritableRoots = true))
        assertTrue(resolveWorkspaceToolApproval("workspace_shell", emptyMap(), forceApprovals = true, autoApprove = false))
        assertTrue(resolveWorkspaceToolApproval("other_tool", mapOf("other_tool" to true), autoApprove = true))
    }

    @Test
    fun autoApprovalPersistsOnlyOnTheChosenConversationAndCanBeTurnedOff() {
        val assistant = Assistant()
        val settings = Settings(assistants = listOf(assistant), assistantId = assistant.id)
        val first = Conversation(assistantId = assistant.id, messageNodes = emptyList()).withProgrammingProject(settings, Uuid.random())
        val second = Conversation(assistantId = assistant.id, messageNodes = emptyList()).withProgrammingProject(settings, Uuid.random())
        assertFalse(first.programmingAutoApprovalEnabled)
        val enabled = first.withProgrammingAutoApproval(true)
        val reopened = Json.decodeFromString<Conversation>(Json.encodeToString(enabled))
        assertTrue(reopened.programmingAutoApprovalEnabled)
        assertFalse(second.programmingAutoApprovalEnabled)
        val updated = reopened.withAssistantUpdate(settings.getAssistantOf(reopened).copy(temperature = 0.1f), settings)
        assertTrue(updated.programmingAutoApprovalEnabled)
        assertFalse(updated.withProgrammingAutoApproval(false).programmingAutoApprovalEnabled)
        assertFalse(updated.withAssistantUpdate(settings.getAssistantOf(updated).copy(workspaceId = null), settings).programmingAutoApprovalEnabled)
        val exited = updated.withProgrammingProject(settings, null)
        assertFalse(exited.config!!.programmingAutoApprove)
        assertFalse(exited.withProgrammingProject(settings, Uuid.random()).programmingAutoApprovalEnabled)
    }

    @Test
    fun anInactiveOrUnboundConversationCannotEnableProjectAutoApproval() {
        for (config in listOf(ConversationConfig(programmingAutoApprove = true),
            ConversationConfig(programmingMode = true, programmingAutoApprove = true))) {
            val conversation = Conversation(assistantId = Uuid.random(), messageNodes = emptyList(), config = config)
            assertFalse(conversation.programmingAutoApprovalEnabled)
            try {
                conversation.withProgrammingAutoApproval(true)
                fail("An active project is required")
            } catch (_: IllegalStateException) { }
        }
    }
}
