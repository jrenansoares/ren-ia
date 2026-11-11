package br.com.projetoia.app.data.ai.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.db.dao.WorkspaceDAO
import br.com.projetoia.app.data.db.entity.WorkspaceEntity
import br.com.projetoia.app.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellContext
import me.rerere.workspace.WorkspaceShellRunner
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.ssh.SshConnectionPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceToolsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun createFakeDao(initial: WorkspaceEntity): WorkspaceDAO = object : WorkspaceDAO {
        private val items = linkedMapOf(initial.id to initial)
        private val flow = MutableStateFlow(items.values.toList())

        override fun listFlow(): Flow<List<WorkspaceEntity>> = flow

        override suspend fun getById(id: String): WorkspaceEntity? = items[id]

        override suspend fun upsert(workspace: WorkspaceEntity) {
            items[workspace.id] = workspace
            flow.value = items.values.toList()
        }

        override suspend fun getAll(): List<WorkspaceEntity> = items.values.toList()

        override suspend fun updateShellStatus(id: String, shellStatus: String, updatedAt: Long): Int {
            val current = items[id] ?: return 0
            items[id] = current.copy(shellStatus = shellStatus, updatedAt = updatedAt)
            flow.value = items.values.toList()
            return 1
        }

        override suspend fun setShellCompatibilityMode(id: String, enabled: Boolean, updatedAt: Long): Int {
            val current = items[id] ?: return 0
            items[id] = current.copy(shellCompatibilityMode = enabled, updatedAt = updatedAt)
            flow.value = items.values.toList()
            return 1
        }

        override suspend fun deleteById(id: String): Int {
            val removed = items.remove(id)
            flow.value = items.values.toList()
            return if (removed != null) 1 else 0
        }
    }

    @Test
    fun writeReadEditAndShellToolsExecuteEndToEndAndReportErrorsCleanly() = runBlocking {
        val workspaceRoot = temp.newFolder("workspaces")
        val executedCommands = mutableListOf<Pair<String, String>>()
        val shellRunner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                executedCommands += context.cwd to context.command
                return when {
                    context.command.contains("python3 -c fail") -> WorkspaceCommandResult(
                        exitCode = 2,
                        stdout = "",
                        stderr = "SyntaxError: invalid syntax in sample.py",
                    )
                    else -> WorkspaceCommandResult(
                        exitCode = 0,
                        stdout = "ok:${context.cwd}",
                        stderr = "",
                    )
                }
            }
        }
        val manager = WorkspaceManager(workspaceRoot, shellRunner = shellRunner)
        val workspaceId = "ws-test-1"
        val workspaceSlug = "projeto-demo"
        manager.ensureWorkspace(workspaceSlug)
        // Mark layout rootfs ready on disk so WorkspaceRepository sees READY consistently if needed
        File(manager.filesDir(workspaceSlug)).mkdirs()

        val entity = WorkspaceEntity(
            id = workspaceId,
            name = "Projeto Demo",
            root = workspaceSlug,
            shellStatus = WorkspaceShellStatus.READY.name,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settingsStore = SettingsStore(
                PreferenceDataStoreFactory.create(scope = storeScope) { temp.newFile("ws_settings.preferences_pb") },
                storeScope,
            )
            val repo = WorkspaceRepository(
                dao = createFakeDao(entity),
                manager = manager,
                rootfsInstaller = RootfsInstaller(manager),
                settingsStore = settingsStore,
                sshPool = SshConnectionPool(),
            )
            val layout = repo.layout(workspaceId)!!
            val targetPath = "${layout.workspaceDir}/src/app.py"

            val tools = createWorkspaceTools(
                workspaceId = workspaceId,
                workspaceRepository = repo,
                cwd = layout.workspaceDir,
                forceApprovals = true,
                autoApprove = true,
            )
            val writeTool = tools.first { it.name == "workspace_write_file" }
            val readTool = tools.first { it.name == "workspace_read_file" }
            val editTool = tools.first { it.name == "workspace_edit_file" }
            val shellTool = tools.first { it.name == "workspace_shell" }

            // Auto-approve suppresses interactive prompt on writable paths
            val writeArgs = buildJsonObject {
                put("path", targetPath)
                put("text", "def greet():\n    return 'hello'\n")
            }
            assertFalse(writeTool.needsApproval(writeArgs))
            writeTool.execute(writeArgs)

            // Read created file back
            val readResult = readTool.execute(buildJsonObject { put("path", targetPath) })
            val readText = readResult.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            assertTrue(readText.contains("return 'hello'"))

            // Edit file & verify unified diff metadata + file update on disk
            val editResult = editTool.execute(buildJsonObject {
                put("path", targetPath)
                put("old_text", "return 'hello'")
                put("new_text", "return 'updated'")
            })
            val editTextPart = editResult.filterIsInstance<UIMessagePart.Text>().single()
            val editJson = Json.parseToJsonElement(editTextPart.text).jsonObject
            assertEquals(1, editJson["replacements"]?.jsonPrimitive?.intOrNull)
            assertNotNull(editTextPart.metadata["diff"])

            val updatedRead = readTool.execute(buildJsonObject { put("path", targetPath) })
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
            assertTrue(updatedRead.contains("return 'updated'"))

            // Shell success
            val okShell = shellTool.execute(buildJsonObject {
                put("command", "pytest -q")
                put("cwd", "src")
            }).filterIsInstance<UIMessagePart.Text>().single().text.let {
                Json.parseToJsonElement(it).jsonObject
            }
            assertEquals(0, okShell["exitCode"]?.jsonPrimitive?.intOrNull)
            assertEquals("ok:src", okShell["stdout"]?.jsonPrimitive?.contentOrNull)

            // Shell non-zero exit + stderr interpretation
            val failShell = shellTool.execute(buildJsonObject {
                put("command", "python3 -c fail")
            }).filterIsInstance<UIMessagePart.Text>().single().text.let {
                Json.parseToJsonElement(it).jsonObject
            }
            assertEquals(2, failShell["exitCode"]?.jsonPrimitive?.intOrNull)
            assertTrue(failShell["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("SyntaxError"))
            assertEquals(2, executedCommands.size)
        } finally {
            storeScope.cancel()
        }
    }
}
