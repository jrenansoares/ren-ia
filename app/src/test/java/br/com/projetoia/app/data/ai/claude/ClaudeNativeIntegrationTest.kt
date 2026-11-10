package br.com.projetoia.app.data.ai.claude

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in real CLI test. Inference is simulated; no real API credentials or paid requests. */
class ClaudeNativeIntegrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nativeBashExecutesOnceAfterManualApproval() = exercise(approve = true)
    @Test fun nativeBashCannotExecuteAfterDenial() = exercise(approve = false)
    @Test fun cancellingAtApprovalClosesTheNativeProcess() = exercise(approve = false, cancelAtApproval = true)

    private fun exercise(approve: Boolean, cancelAtApproval: Boolean = false) = runBlocking {
        val binary = System.getenv("PROJETO_CLAUDE_TEST_BIN")?.let(::File)
        assumeTrue("Set PROJETO_CLAUDE_TEST_BIN to a verified native Claude executable", binary?.isFile == true)
        withTimeout(60_000) {
            coroutineScope {
                val work = folder.newFolder("project")
                val home = folder.newFolder("home")
                val file = File(work, "result.txt")
                val calls = AtomicInteger()
                var observedResult: String? = null
                val gateway = ClaudeLocalGateway(this) { request ->
                    assertTrue(request.tools.any { it.name == "Bash" })
                    val result = if (calls.getAndIncrement() == 0) {
                        UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Tool(
                            toolCallId = "toolu_native_test", toolName = "Bash",
                            input = """{"command":"printf native-marker >> result.txt","description":"Write a local test marker"}""",
                        )))
                    } else {
                        observedResult = request.messages.flatMap { it.getTools() }.single { it.toolCallId == "toolu_native_test" }.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                        UIMessage.assistant("Teste concluído")
                    }
                    AnthropicBridgeCodec.response(TextGenerationResult("test", "test-free-model", result, usage = TokenUsage(10, 4, totalTokens = 14)))
                }
                var io: ClaudeProcessTransport? = null
                try {
                    val url = gateway.start()
                    val process = ProcessBuilder("/bin/sh", "-c", ClaudeChatRunner.bootstrapCommand)
                        .directory(work).apply {
                            environment()["PATH"] = "${binary!!.parent}:${System.getenv("PATH")}".replace("/usr/bin:/usr/bin", "/usr/bin")
                            environment()["HOME"] = home.absolutePath
                        }.start()
                    io = ClaudeProcessTransport(process)
                    io.write(buildJsonObject { put("url", url); put("token", gateway.token); put("system", "Use the provided tool. Do not repeat it.") })
                    io.write(ClaudeProtocol.initialize())
                    var initialized = false
                    var permissions = 0
                    var finished = false
                    while (true) {
                        val frame = io.read() ?: break
                        when (frame.string("type")) {
                            "control_response" -> if (frame.obj("response")?.string("request_id") == ClaudeProtocol.INIT_ID) {
                                assertEquals("success", frame.obj("response")?.string("subtype"))
                                initialized = true
                                io.write(ClaudeProtocol.user("Write the test marker using Bash."))
                            }
                            "control_request" -> {
                                val request = requireNotNull(frame.obj("request"))
                                assertEquals("can_use_tool", request.string("subtype"))
                                assertEquals("Bash", request.string("tool_name"))
                                assertFalse("The command ran before approval", file.exists())
                                if (cancelAtApproval) {
                                    io.close()
                                    assertFalse(process.isAlive)
                                    assertFalse(file.exists())
                                    return@coroutineScope
                                }
                                permissions++
                                io.write(ClaudeProtocol.decision(requireNotNull(frame.string("request_id")), requireNotNull(request.obj("input")), ClaudeDecision(approve, "Teste de recusa")))
                            }
                            "result" -> {
                                assertFalse("Native error: $frame", frame["is_error"]?.jsonPrimitive?.booleanOrNull == true)
                                assertEquals("success", frame.string("subtype"))
                                finished = true
                                break
                            }
                        }
                    }
                    assertTrue("Initialization failed: ${io.diagnostic(gateway.token, url)}", initialized)
                    assertTrue("Native turn did not finish: ${io.diagnostic(gateway.token, url)}", finished)
                    assertEquals(1, permissions)
                    assertEquals(2, calls.get())
                    assertTrue(observedResult != null)
                    if (approve) assertEquals("native-marker", file.readText()) else assertFalse(file.exists())
                } finally {
                    io?.close()
                    gateway.close()
                }
            }
        }
    }
}
