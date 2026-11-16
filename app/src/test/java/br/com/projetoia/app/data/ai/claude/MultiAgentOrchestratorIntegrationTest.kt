package br.com.projetoia.app.data.ai.claude

import br.com.projetoia.app.data.ai.ChatTurnContext
import br.com.projetoia.app.data.ai.FreeRoute
import br.com.projetoia.app.data.ai.ModelCheck
import br.com.projetoia.app.data.ai.ModelCheckStatus
import br.com.projetoia.app.data.ai.ModelCostAcknowledgement
import br.com.projetoia.app.data.ai.modelCheckFingerprint
import br.com.projetoia.app.data.ai.recordModelCheck
import br.com.projetoia.app.data.datastore.FreeRouterSettings
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.model.Assistant
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.workspace.WorkspaceCommandResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MultiAgentOrchestratorIntegrationTest {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient()

    @Test
    fun sanitizesSecretsAndIgnoresCredentialFiles() {
        assertTrue(isSensitiveOrIgnoredPath(".env"))
        assertTrue(isSensitiveOrIgnoredPath(".env.production"))
        assertTrue(isSensitiveOrIgnoredPath("config/secrets.json"))
        assertTrue(isSensitiveOrIgnoredPath("certs/release.keystore"))
        assertTrue(isSensitiveOrIgnoredPath(".git/config"))
        assertFalse(isSensitiveOrIgnoredPath("src/main/kotlin/App.kt"))

        val raw = """
            API_KEY=sk-ant-api03-verysecretvalue1234
            OPENROUTER=sk-or-v1-secret987654321
            GOOGLE=AIzaSyDummyKey1234567890abcd
            Authorization: Bearer super_secret_token_here
            https://alice:mypassword@github.com/org/private-repo.git
        """.trimIndent()
        val scrubbed = sanitizeSensitiveText(raw)
        assertFalse(scrubbed.contains("verysecretvalue1234"))
        assertFalse(scrubbed.contains("secret987654321"))
        assertFalse(scrubbed.contains("DummyKey1234567890abcd"))
        assertFalse(scrubbed.contains("super_secret_token_here"))
        assertFalse(scrubbed.contains("mypassword"))
        assertTrue(scrubbed.contains("[REDACTED"))
    }

    @Test
    fun simpleDirectTaskBypassesMultiAgentConsultants() {
        val assistant = Assistant(workspaceId = Uuid.random())
        val claude = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(claude),
        )
        val turn = ChatTurnContext(
            settings = Settings(providers = listOf(openRouter)),
            assistant = assistant,
            model = claude,
            providerSetting = openRouter,
            conversationId = Uuid.random(),
            conversationSystemPrompt = null,
            workspaceCwd = null,
            memories = emptyList(),
            tools = emptyList(),
            programmingMode = true,
            claudeCode = true,
            programmingConsultants = true,
        )
        val turnWithoutConsultants = ChatTurnContext(
            settings = Settings(providers = listOf(openRouter)),
            assistant = assistant,
            model = claude,
            providerSetting = openRouter,
            conversationId = turn.conversationId,
            conversationSystemPrompt = null,
            workspaceCwd = null,
            memories = emptyList(),
            tools = emptyList(),
            programmingMode = true,
            claudeCode = true,
            programmingConsultants = false,
        )

        assertFalse(shouldOrchestrateTask("oi", turn))
        assertFalse(shouldOrchestrateTask("git status", turn))
        assertTrue(
            shouldOrchestrateTask(
                "Implemente a validação de tokens no arquivo AuthManager.kt e execute os testes unitários.",
                turn,
            ),
        )
        assertFalse(
            shouldOrchestrateTask(
                "Implemente a validação completa com testes",
                turnWithoutConsultants,
            ),
        )
    }

    @Test
    fun selectsDiverseNonClaudeConsultantsPreferringGeminiAndGptOss() {
        val claudeSonnet = Model(modelId = "anthropic/claude-sonnet-4.6", displayName = "Claude Sonnet 4.6")
        val gemini = Model(modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash", chatSupported = true)
        val gptOss = Model(modelId = "openai/gpt-oss-120b", displayName = "GPT-OSS 120B", chatSupported = true)
        val llama = Model(modelId = "llama-3.3-70b-versatile", displayName = "Llama 3.3 70B", chatSupported = true)

        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test",
            models = listOf(claudeSonnet),
        )
        val google = ProviderSetting.Google(
            name = "Google",
            apiKey = "AIza-test",
            models = listOf(gemini),
        )
        val groq = ProviderSetting.OpenAI(
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "gsk-test",
            models = listOf(gptOss, llama),
        )

        var settings = Settings(
            providers = listOf(openRouter, google, groq),
            freeRouter = FreeRouterSettings(
                enabled = true,
                approvedModelIds = listOf(gemini.id, gptOss.id, llama.id, claudeSonnet.id),
                costAcknowledgements = listOf(
                    ModelCostAcknowledgement(google.id, gemini.id, modelCheckFingerprint(google, gemini)),
                    ModelCostAcknowledgement(groq.id, gptOss.id, modelCheckFingerprint(groq, gptOss)),
                    ModelCostAcknowledgement(groq.id, llama.id, modelCheckFingerprint(groq, llama)),
                ),
            ),
        )
        settings = settings
            .recordModelCheck(ModelCheck(google.id, gemini.id, modelCheckFingerprint(google, gemini), 1L, 1L, ModelCheckStatus.OK))
            .recordModelCheck(ModelCheck(groq.id, gptOss.id, modelCheckFingerprint(groq, gptOss), 1L, 1L, ModelCheckStatus.OK))
            .recordModelCheck(ModelCheck(groq.id, llama.id, modelCheckFingerprint(groq, llama), 1L, 1L, ModelCheckStatus.OK))

        val routes = selectConsultantRoutes(settings, "Implemente teste de integração para login")
        assertTrue(routes.none { it.model.modelId.contains("claude") })
        val families = routes.map { classifyConsultantFamily(it.model.modelId, it.provider.name) }
        assertTrue("gemini" in families)
        assertTrue("gpt-oss" in families)
    }

    @Test
    fun endToEndMultiAgentFlowPlansWithConsultantsExecutesWithClaudeAndValidatesTests() = runBlocking {
        val workspaceId = Uuid.random()
        val claudeOpus = Model(modelId = "anthropic/claude-opus-4.6", displayName = "Claude Opus 4.6")
        val gemini = Model(modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash", chatSupported = true)
        val gptOss = Model(modelId = "openai/gpt-oss-120b", displayName = "GPT-OSS 120B", chatSupported = true)

        val openRouter = ProviderSetting.OpenAI(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-test-key",
            models = listOf(claudeOpus),
        )
        val google = ProviderSetting.Google(
            name = "Google",
            apiKey = "AIza-test-key",
            models = listOf(gemini),
        )
        val groq = ProviderSetting.OpenAI(
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "gsk-test-key",
            models = listOf(gptOss),
        )

        val settings = Settings(
            providers = listOf(openRouter, google, groq),
            freeRouter = FreeRouterSettings(
                enabled = true,
                approvedModelIds = listOf(gemini.id, gptOss.id),
                costAcknowledgements = listOf(
                    ModelCostAcknowledgement(google.id, gemini.id, modelCheckFingerprint(google, gemini)),
                    ModelCostAcknowledgement(groq.id, gptOss.id, modelCheckFingerprint(groq, gptOss)),
                ),
            ),
        )
            .recordModelCheck(ModelCheck(google.id, gemini.id, modelCheckFingerprint(google, gemini), 1L, 1L, ModelCheckStatus.OK))
            .recordModelCheck(ModelCheck(groq.id, gptOss.id, modelCheckFingerprint(groq, gptOss), 1L, 1L, ModelCheckStatus.OK))

        val assistant = Assistant(workspaceId = workspaceId)
        val turn = ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = claudeOpus,
            providerSetting = openRouter,
            conversationId = Uuid.random(),
            conversationSystemPrompt = null,
            workspaceCwd = "/workspace/demo-repo",
            memories = emptyList(),
            tools = emptyList(),
            programmingMode = true,
            programmingAutoApprove = false,
            claudeCode = true,
            programmingConsultants = true,
        )

        val inspection = object : WorkspaceInspectionAdapter {
            override suspend fun collectContext(
                workspaceId: String,
                workspaceCwd: String?,
                task: String,
            ) = RepositoryContextBundle(
                workspaceId = workspaceId,
                workspaceCwd = workspaceCwd,
                branch = "feature/auth-fix",
                gitStatus = "M src/AuthService.kt",
                files = listOf("src/AuthService.kt", "tests/AuthServiceTest.kt", "README.md"),
                excerpts = listOf(
                    RepositoryFileExcerpt(
                        path = "src/AuthService.kt",
                        content = sanitizeSensitiveText("fun verify(token: String) = token.isNotBlank() // TOKEN=sk-ant-1234567890"),
                    ),
                ),
                repositories = listOf(
                    RepositoryReference(
                        name = "demo-repo",
                        path = "/workspace/demo-repo",
                        branch = "feature/auth-fix",
                        remoteUrl = sanitizeUrlCredentials("https://alice:pw@github.com/org/demo-repo.git"),
                    ),
                ),
            )

            override suspend fun collectPostExecutionDiff(workspaceId: String, workspaceCwd: String?) =
                listOf("src/AuthService.kt", "tests/AuthServiceTest.kt") to "2 files changed, 18 insertions(+)"

            override suspend fun runAutoApprovedTests(workspaceId: String, workspaceCwd: String?, command: String): WorkspaceCommandResult? =
                null
        }

        val planningModelsCalled = mutableListOf<String>()
        val validationPromptsSeen = mutableListOf<String>()
        val invoker = ConsultantInvoker { route: FreeRoute, messages: List<UIMessage>, _ ->
            val promptText = messages.last().toText()
            if (promptText.contains("Consolide em JSON")) {
                """
                {
                  "summary": "Corrigir validação de token em AuthService e cobrir com testes.",
                  "targetFiles": ["src/AuthService.kt", "tests/AuthServiceTest.kt"],
                  "actions": ["Atualizar função verifyToken", "Executar testes em AuthServiceTest"],
                  "acceptanceCriteria": ["AuthServiceTest passando sem regressões"],
                  "risks": ["Verificar assinatura real de verify em AuthService.kt"]
                }
                """.trimIndent()
            } else if (promptText.contains("Executor:") && promptText.contains("Saída dos testes:")) {
                validationPromptsSeen += promptText
                """
                {
                  "approved": true,
                  "summary": "Alterações e testes em AuthServiceTest validados com ajuste coerente do plano.",
                  "findings": ["Assinatura verify() ajustada corretamente após inspeção real."]
                }
                """.trimIndent()
            } else {
                planningModelsCalled += route.model.modelId
                assertFalse("Secrets must never reach consultant prompt", promptText.contains("sk-ant-1234567890"))
                assertFalse("URL password must never reach consultant prompt", promptText.contains("alice:pw"))
                delay(15)
                """
                {
                  "summary": "Proposta de ${route.model.displayName}",
                  "targetFiles": ["src/AuthService.kt"],
                  "actions": ["Validar formato e expiração do token"],
                  "acceptanceCriteria": ["pytest/junit passa"],
                  "risks": ["Conferir assinatura real"]
                }
                """.trimIndent()
            }
        }

        val orchestrator = ClaudeMultiAgentOrchestrator(inspection, invoker)
        val task = "Corrija a validação em AuthService.kt considerando a assinatura real e rode os testes."
        val stagesSeen = mutableListOf<OrchestratorStage>()
        val prepared = orchestrator.prepareExecution(turn, task, claudeOpus.modelId) { stage, _ ->
            stagesSeen += stage
        }
        assertNotNull(prepared)
        val (repoContext, plan) = prepared!!
        assertTrue(OrchestratorStage.ANALYSIS in stagesSeen)
        assertTrue(OrchestratorStage.PLANNING in stagesSeen)
        assertTrue("gemini-2.5-flash" in planningModelsCalled)
        assertTrue("openai/gpt-oss-120b" in planningModelsCalled)

        // Stage 3 Execution via OpenRouter Anthropic transparent gateway (Claude Opus)
        val directive = ClaudeMultiAgentOrchestrator.buildExecutorPlanDirective(plan, repoContext)
        assertTrue(directive.contains("demo-repo"))
        assertTrue(directive.contains("[AJUSTE NO PLANO]"))

        val upstreamServer = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/api/v1/messages") {
                    val body = call.receiveText()
                    assertTrue(body.contains("anthropic/claude-opus-4.6"))
                    call.respondText(
                        """{"id":"msg_opus_1","type":"message","role":"assistant","model":"anthropic/claude-opus-4.6","content":[{"type":"text","text":"OK"}],"stop_reason":"end_turn","usage":{"input_tokens":80,"output_tokens":32}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }
            }
        }.start(wait = false)

        val port = upstreamServer.engine.resolvedConnectors().single().port
        val opusOption = OpenRouterClaudeOption(
            providerSetting = openRouter,
            model = claudeOpus,
            family = ClaudeModelFamily.OPUS,
            anthropicBaseUrl = "http://127.0.0.1:$port/api",
            messagesUrl = "http://127.0.0.1:$port/api/v1/messages",
            apiKey = "sk-or-test-key",
        )
        var respondedModel: String? = null
        val forwarder = AnthropicTransparentForwarder(
            client = httpClient,
            option = opusOption,
            onResponseTelemetry = { model, _ -> respondedModel = model },
        )
        val gateway = ClaudeLocalGateway(
            owner = this,
            availableModels = listOf(claudeOpus.modelId),
            forwardRaw = { call, rawBody -> forwarder.forward(call, rawBody) },
        ) { error("unused") }

        try {
            val url = gateway.start()
            val req = Request.Builder()
                .url("$url/v1/messages")
                .header("x-api-key", gateway.token)
                .post("""{"model":"anthropic/claude-opus-4.6","messages":[{"role":"user","content":"Execute plan"}]}""".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                assertEquals(200, resp.code)
            }
            assertEquals("anthropic/claude-opus-4.6", respondedModel)
        } finally {
            gateway.close()
            upstreamServer.stop(0, 500)
        }

        // Simulate Claude Code executor result message with Edit tool, Bash tests, and plan adjustment note
        val executedMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool("tool_edit_1", "Edit", """{"file_path":"src/AuthService.kt"}"""),
                UIMessagePart.Tool(
                    "tool_bash_1",
                    "Bash",
                    """{"command":"./gradlew testDebugUnitTest"}""",
                    output = listOf(UIMessagePart.Text("2 tests passed, 0 failed")),
                ),
                UIMessagePart.Text(
                    "[AJUSTE NO PLANO] A função existente chama-se verify() e não verifyToken(), mantendo retrocompatibilidade.\nValidação atualizada e testes aprovados.",
                ),
            ),
        )

        val report = orchestrator.finalizeAndValidate(
            turn = turn,
            task = task,
            context = repoContext,
            plan = plan,
            executorProvider = "OpenRouter",
            executorModel = respondedModel!!,
            executedMessage = executedMessage,
            preExecutionFiles = parseGitStatusPaths(repoContext.gitStatus),
        )

        assertEquals("anthropic/claude-opus-4.6", report.evidence.executorModel)
        assertEquals("OpenRouter", report.evidence.executorProvider)
        assertTrue("src/AuthService.kt" in report.evidence.modifiedFiles)
        assertTrue("tests/AuthServiceTest.kt" in report.evidence.modifiedFiles)
        assertEquals(1, report.evidence.planAdjustments.size)
        assertTrue(report.evidence.testCommands.contains("./gradlew testDebugUnitTest"))
        assertTrue(validationPromptsSeen.isNotEmpty())
        assertTrue(report.reviews.isNotEmpty() && report.reviews.all { it.approved })
        assertEquals(MultiAgentFinalStatus.APPROVED_WITH_NOTES, report.finalStatus)

        val rendered = ClaudeMultiAgentOrchestrator.formatReportMarkdown(report)
        assertTrue(rendered.contains("anthropic/claude-opus-4.6"))
        assertTrue(rendered.contains("Gemini 2.5 Flash"))
        assertTrue(rendered.contains("GPT-OSS 120B"))
        assertTrue(rendered.contains("src/AuthService.kt"))
        assertTrue(rendered.contains("2 tests passed, 0 failed"))
        assertTrue(rendered.contains(MultiAgentFinalStatus.APPROVED_WITH_NOTES.label))
    }
}
