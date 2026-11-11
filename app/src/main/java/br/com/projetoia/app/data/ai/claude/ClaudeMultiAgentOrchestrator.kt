package br.com.projetoia.app.data.ai.claude

import br.com.projetoia.app.data.ai.ChatTurnContext
import br.com.projetoia.app.data.ai.FreeRoute
import br.com.projetoia.app.data.ai.ModelCheckStatus
import br.com.projetoia.app.data.ai.UsageRecorder
import br.com.projetoia.app.data.ai.UsageSource
import br.com.projetoia.app.data.ai.UsageTag
import br.com.projetoia.app.data.ai.automaticRoutes
import br.com.projetoia.app.data.ai.modelCheck
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceStorageArea
import java.net.URI
import java.util.Locale
import kotlin.uuid.Uuid

internal enum class OrchestratorStage {
    ANALYSIS,
    PLANNING,
    EXECUTION,
    VALIDATION,
}

internal data class RepositoryReference(
    val name: String,
    val path: String,
    val branch: String?,
    val remoteUrl: String?,
)

internal data class RepositoryFileExcerpt(
    val path: String,
    val content: String,
)

internal data class RepositoryContextBundle(
    val workspaceId: String,
    val workspaceCwd: String?,
    val branch: String? = null,
    val gitStatus: String = "",
    val files: List<String> = emptyList(),
    val excerpts: List<RepositoryFileExcerpt> = emptyList(),
    val repositories: List<RepositoryReference> = emptyList(),
)

internal data class ConsultantProposal(
    val route: FreeRoute,
    val providerName: String,
    val modelId: String,
    val displayName: String,
    val rawText: String,
    val summary: String,
    val targetFiles: List<String>,
    val actions: List<String>,
    val acceptanceCriteria: List<String>,
    val risks: List<String>,
)

internal data class StructuredPlan(
    val summary: String,
    val targetFiles: List<String>,
    val actions: List<String>,
    val acceptanceCriteria: List<String>,
    val risks: List<String>,
    val consultantLabels: List<String>,
    val consultantRouteKeys: List<String>,
)

internal data class ExecutionEvidence(
    val executorProvider: String,
    val executorModel: String,
    val modifiedFiles: List<String>,
    val testCommands: List<String>,
    val testOutput: String,
    val planAdjustments: List<String>,
    val gitDiffStat: String,
    val assistantSummary: String,
)

internal data class ValidationReview(
    val reviewerProvider: String,
    val reviewerModel: String,
    val approved: Boolean,
    val summary: String,
    val findings: List<String>,
)

internal enum class MultiAgentFinalStatus(val label: String) {
    APPROVED("APROVADO"),
    APPROVED_WITH_NOTES("APROVADO COM RESSALVAS"),
    NEEDS_REVISION("REQUER AJUSTES"),
    EXECUTED("EXECUTADO"),
}

internal data class MultiAgentTurnReport(
    val plan: StructuredPlan,
    val evidence: ExecutionEvidence,
    val reviews: List<ValidationReview>,
    val finalStatus: MultiAgentFinalStatus,
)

internal fun interface ConsultantInvoker {
    suspend fun complete(route: FreeRoute, messages: List<UIMessage>, maxTokens: Int): String?
}

internal interface WorkspaceInspectionAdapter {
    suspend fun collectContext(
        workspaceId: String,
        workspaceCwd: String?,
        task: String,
    ): RepositoryContextBundle

    suspend fun collectPostExecutionDiff(
        workspaceId: String,
        workspaceCwd: String?,
    ): Pair<List<String>, String>

    suspend fun runAutoApprovedTests(
        workspaceId: String,
        workspaceCwd: String?,
        command: String,
    ): WorkspaceCommandResult?
}

internal class RepositoryWorkspaceInspection(
    private val workspaceRepository: WorkspaceRepository,
) : WorkspaceInspectionAdapter {
    override suspend fun collectContext(
        workspaceId: String,
        workspaceCwd: String?,
        task: String,
    ): RepositoryContextBundle {
        val discoveredFiles = mutableListOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add("" to 0)

        while (queue.isNotEmpty() && discoveredFiles.size < MAX_LISTED_FILES) {
            val (dir, depth) = queue.removeFirst()
            val entries = runCatching {
                workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, dir)
            }.getOrDefault(emptyList())

            for (entry in entries) {
                val relPath = entry.path.trimStart('/')
                if (isSensitiveOrIgnoredPath(relPath)) continue
                if (entry.isDirectory) {
                    if (depth < MAX_SCAN_DEPTH) {
                        queue.addLast(relPath to (depth + 1))
                    }
                } else {
                    discoveredFiles += relPath
                    if (discoveredFiles.size >= MAX_LISTED_FILES) break
                }
            }
        }

        val selectedPaths = selectRelevantFiles(discoveredFiles, task)
        val excerpts = mutableListOf<RepositoryFileExcerpt>()
        var totalChars = 0
        for (path in selectedPaths) {
            if (totalChars >= MAX_TOTAL_EXCERPT_CHARS) break
            val raw = runCatching {
                workspaceRepository.readTextForPreview(workspaceId, WorkspaceStorageArea.FILES, path)
            }.getOrNull() ?: continue
            val clean = sanitizeSensitiveText(raw).take(MAX_FILE_EXCERPT_CHARS)
            if (clean.isBlank()) continue
            excerpts += RepositoryFileExcerpt(path = path, content = clean)
            totalChars += clean.length
        }

        val repos = runCatching { workspaceRepository.discoverGitRepositories() }
            .getOrDefault(emptyList())
            .filter { it.workspaceId == workspaceId || it.name.isNotBlank() }
            .take(8)
            .map { repo ->
                RepositoryReference(
                    name = repo.name,
                    path = repo.path,
                    branch = repo.branch,
                    remoteUrl = sanitizeUrlCredentials(repo.remoteUrl),
                )
            }

        val cwd = workspaceCwd?.trim().orEmpty()
        val branchResult = runCatching {
            workspaceRepository.executeCommand(
                id = workspaceId,
                command = "git rev-parse --abbrev-ref HEAD 2>/dev/null || true",
                cwd = cwd,
                timeoutMillis = 8_000L,
            )
        }.getOrNull()
        val statusResult = runCatching {
            workspaceRepository.executeCommand(
                id = workspaceId,
                command = "git status --short 2>/dev/null | head -n 40 || true",
                cwd = cwd,
                timeoutMillis = 8_000L,
            )
        }.getOrNull()

        return RepositoryContextBundle(
            workspaceId = workspaceId,
            workspaceCwd = workspaceCwd,
            branch = branchResult?.stdout?.trim()?.takeIf { it.isNotBlank() },
            gitStatus = sanitizeSensitiveText(statusResult?.stdout?.trim().orEmpty()),
            files = discoveredFiles,
            excerpts = excerpts,
            repositories = repos,
        )
    }

    override suspend fun collectPostExecutionDiff(
        workspaceId: String,
        workspaceCwd: String?,
    ): Pair<List<String>, String> {
        val cwd = workspaceCwd?.trim().orEmpty()
        val statusOut = runCatching {
            workspaceRepository.executeCommand(
                id = workspaceId,
                command = "git status --short 2>/dev/null || true",
                cwd = cwd,
                timeoutMillis = 10_000L,
            ).stdout
        }.getOrDefault("")
        val diffStatOut = runCatching {
            workspaceRepository.executeCommand(
                id = workspaceId,
                command = "git diff --stat 2>/dev/null | head -n 40 || true",
                cwd = cwd,
                timeoutMillis = 10_000L,
            ).stdout
        }.getOrDefault("")

        val changed = parseGitStatusPaths(statusOut)
        return changed to sanitizeSensitiveText(diffStatOut.trim())
    }

    override suspend fun runAutoApprovedTests(
        workspaceId: String,
        workspaceCwd: String?,
        command: String,
    ): WorkspaceCommandResult? {
        return runCatching {
            workspaceRepository.executeCommand(
                id = workspaceId,
                command = command,
                cwd = workspaceCwd?.trim().orEmpty(),
                timeoutMillis = 60_000L,
            )
        }.getOrNull()
    }
}

internal class ClaudeMultiAgentOrchestrator(
    private val inspection: WorkspaceInspectionAdapter,
    private val invoker: ConsultantInvoker,
) {
    suspend fun prepareExecution(
        turn: ChatTurnContext,
        task: String,
        executorModelId: String,
        onStage: (OrchestratorStage, String) -> Unit = { _, _ -> },
    ): Pair<RepositoryContextBundle, StructuredPlan>? {
        if (!shouldOrchestrateTask(task, turn)) return null
        val workspaceId = turn.assistant.workspaceId?.toString() ?: return null

        onStage(OrchestratorStage.ANALYSIS, "Analisando repositório e contexto seguro")
        val context = inspection.collectContext(workspaceId, turn.workspaceCwd, task)

        val candidates = selectConsultantRoutes(
            settings = turn.settings,
            task = task,
            excludeModelIds = setOf(turn.model.id),
            excludeRawModelIds = setOf(executorModelId),
            limit = MAX_PARALLEL_CONSULTANTS,
        )
        if (candidates.isEmpty()) return null

        onStage(
            OrchestratorStage.PLANNING,
            "Consultando ${candidates.joinToString(", ") { it.model.displayName.ifBlank { it.model.modelId } }} em paralelo",
        )
        val proposals = collectProposalsInParallel(candidates, task, context)
        if (proposals.isEmpty()) return null

        onStage(OrchestratorStage.PLANNING, "Consolidando plano técnico estruturado")
        val plan = consolidateProposals(proposals, task, context)
        return context to plan
    }

    suspend fun collectProposalsInParallel(
        candidates: List<FreeRoute>,
        task: String,
        context: RepositoryContextBundle,
    ): List<ConsultantProposal> = coroutineScope {
        val contextPrompt = formatContextForConsultants(task, context)
        candidates.map { route ->
            async {
                try {
                    val response = withTimeoutOrNull(25_000L) {
                        invoker.complete(
                            route,
                            listOf(
                                UIMessage.system(CONSULTANT_PLANNING_SYSTEM_PROMPT),
                                UIMessage.user(contextPrompt),
                            ),
                            1024,
                        )
                    }?.trim().orEmpty()
                    if (response.isBlank()) null else parseConsultantProposal(route, response)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun consolidateProposals(
        proposals: List<ConsultantProposal>,
        task: String,
        context: RepositoryContextBundle,
    ): StructuredPlan {
        if (proposals.size == 1) {
            val only = proposals.single()
            return StructuredPlan(
                summary = only.summary.ifBlank { task.take(240) },
                targetFiles = only.targetFiles.distinct(),
                actions = only.actions.ifEmpty { listOf("Implementar alterações solicitadas com validação de arquivos reais.") },
                acceptanceCriteria = only.acceptanceCriteria.ifEmpty { listOf("Executar testes relevantes do projeto sem regressões.") },
                risks = only.risks,
                consultantLabels = listOf("${only.providerName}: ${only.displayName}"),
                consultantRouteKeys = listOf("${only.route.provider.id}:${only.route.model.modelId}"),
            )
        }

        val lead = proposals.first().route
        val synthesisInput = buildString {
            appendLine("Tarefa do usuário: ${task.take(2_000)}")
            appendLine("Arquivos mapeados: ${context.files.take(40).joinToString(", ")}")
            appendLine()
            proposals.forEachIndexed { idx, p ->
                appendLine("=== Proposta ${idx + 1}: ${p.providerName} / ${p.displayName} (${p.modelId}) ===")
                appendLine(p.rawText.take(3_000))
                appendLine()
            }
            appendLine("Consolide em JSON único com chaves: summary, targetFiles, actions, acceptanceCriteria, risks.")
        }

        val consolidatedJson = runCatching {
            withTimeoutOrNull(20_000L) {
                invoker.complete(
                    lead,
                    listOf(
                        UIMessage.system(PLAN_CONSOLIDATION_SYSTEM_PROMPT),
                        UIMessage.user(synthesisInput),
                    ),
                    1024,
                )
            }
        }.getOrNull()

        val parsed = consolidatedJson?.let { parseStructuredPlanJson(it) }
        val labels = proposals.map { "${it.providerName}: ${it.displayName}" }
        val keys = proposals.map { "${it.route.provider.id}:${it.route.model.modelId}" }

        return if (parsed != null) {
            parsed.copy(
                consultantLabels = labels,
                consultantRouteKeys = keys,
            )
        } else {
            StructuredPlan(
                summary = proposals.firstNotNullOfOrNull { it.summary.takeIf(String::isNotBlank) } ?: task.take(240),
                targetFiles = proposals.flatMap { it.targetFiles }.distinct().take(15),
                actions = proposals.flatMap { it.actions }.distinct().take(12)
                    .ifEmpty { listOf("Inspecionar arquivos-alvo e aplicar alterações necessárias.") },
                acceptanceCriteria = proposals.flatMap { it.acceptanceCriteria }.distinct().take(10)
                    .ifEmpty { listOf("Validar compilação e testes automatizados aplicáveis.") },
                risks = proposals.flatMap { it.risks }.distinct().take(8),
                consultantLabels = labels,
                consultantRouteKeys = keys,
            )
        }
    }

    suspend fun finalizeAndValidate(
        turn: ChatTurnContext,
        task: String,
        context: RepositoryContextBundle,
        plan: StructuredPlan,
        executorProvider: String,
        executorModel: String,
        executedMessage: UIMessage,
        preExecutionFiles: List<String>,
        onStage: (OrchestratorStage, String) -> Unit = { _, _ -> },
    ): MultiAgentTurnReport {
        onStage(OrchestratorStage.VALIDATION, "Coletando evidências de arquivos e testes")
        val workspaceId = context.workspaceId

        val toolModifiedFiles = extractModifiedFilesFromTools(executedMessage)
        val (bashTestCommands, bashTestOutput) = extractTestExecutionFromTools(executedMessage)

        val (postGitFiles, diffStat) = inspection.collectPostExecutionDiff(workspaceId, context.workspaceCwd)
        val gitDeltaFiles = postGitFiles.filterNot { it in preExecutionFiles && it !in toolModifiedFiles }
        val modifiedFiles = (toolModifiedFiles + gitDeltaFiles).distinct()

        var combinedTestCommands = bashTestCommands
        var combinedTestOutput = sanitizeSensitiveText(bashTestOutput)

        if (combinedTestCommands.isEmpty() && turn.programmingAutoApprove && modifiedFiles.isNotEmpty()) {
            val autoCommand = detectSafeVerificationCommand(context.files, modifiedFiles)
            if (autoCommand != null) {
                onStage(OrchestratorStage.VALIDATION, "Executando verificação automática: $autoCommand")
                val cmdResult = inspection.runAutoApprovedTests(workspaceId, context.workspaceCwd, autoCommand)
                if (cmdResult != null) {
                    combinedTestCommands = listOf(autoCommand)
                    combinedTestOutput = sanitizeSensitiveText(
                        buildString {
                            appendLine("$ $autoCommand (exit=${cmdResult.exitCode})")
                            if (cmdResult.stdout.isNotBlank()) appendLine(cmdResult.stdout.take(4_000))
                            if (cmdResult.stderr.isNotBlank()) appendLine(cmdResult.stderr.take(2_000))
                        }.trim(),
                    )
                }
            }
        }

        val assistantText = executedMessage.toText()
        val discrepancies = extractPlanDiscrepancies(assistantText)

        val evidence = ExecutionEvidence(
            executorProvider = executorProvider,
            executorModel = executorModel,
            modifiedFiles = modifiedFiles,
            testCommands = combinedTestCommands,
            testOutput = combinedTestOutput,
            planAdjustments = discrepancies,
            gitDiffStat = diffStat,
            assistantSummary = sanitizeSensitiveText(assistantText.take(4_000)),
        )

        val reviewerCandidates = selectIndependentReviewers(
            settings = turn.settings,
            task = task,
            executorModelId = executorModel,
            excludeModelUuid = turn.model.id,
            plannerRouteKeys = plan.consultantRouteKeys,
        )

        val reviews = if (reviewerCandidates.isNotEmpty()) {
            onStage(
                OrchestratorStage.VALIDATION,
                "Revisão independente por ${reviewerCandidates.joinToString(", ") { it.model.displayName.ifBlank { it.model.modelId } }}",
            )
            collectValidationReviews(reviewerCandidates, task, plan, evidence)
        } else {
            emptyList()
        }

        val finalStatus = computeFinalStatus(evidence, reviews)
        return MultiAgentTurnReport(
            plan = plan,
            evidence = evidence,
            reviews = reviews,
            finalStatus = finalStatus,
        )
    }

    private suspend fun collectValidationReviews(
        reviewers: List<FreeRoute>,
        task: String,
        plan: StructuredPlan,
        evidence: ExecutionEvidence,
    ): List<ValidationReview> = coroutineScope {
        val reviewPrompt = formatValidationPrompt(task, plan, evidence)
        reviewers.map { route ->
            async {
                try {
                    val response = withTimeoutOrNull(20_000L) {
                        invoker.complete(
                            route,
                            listOf(
                                UIMessage.system(CONSULTANT_VALIDATION_SYSTEM_PROMPT),
                                UIMessage.user(reviewPrompt),
                            ),
                            768,
                        )
                    }?.trim().orEmpty()
                    if (response.isBlank()) null else parseValidationReview(route, response)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        const val MAX_PARALLEL_CONSULTANTS = 3

        fun buildExecutorPlanDirective(
            plan: StructuredPlan,
            context: RepositoryContextBundle,
        ): String = buildString {
            appendLine("[PLANO TÉCNICO CONSOLIDADO — MULTIAGENTE]")
            appendLine("Consultores (somente leitura): ${plan.consultantLabels.joinToString(", ")}")
            appendLine("Resumo: ${plan.summary}")
            if (plan.targetFiles.isNotEmpty()) {
                appendLine("Arquivos sugeridos: ${plan.targetFiles.joinToString(", ")}")
            }
            if (plan.actions.isNotEmpty()) {
                appendLine("Ações:")
                plan.actions.forEachIndexed { idx, action -> appendLine("${idx + 1}. $action") }
            }
            if (plan.acceptanceCriteria.isNotEmpty()) {
                appendLine("Critérios de aceitação:")
                plan.acceptanceCriteria.forEach { criterion -> appendLine("- $criterion") }
            }
            if (plan.risks.isNotEmpty()) {
                appendLine("Riscos identificados:")
                plan.risks.forEach { risk -> appendLine("- $risk") }
            }
            if (context.repositories.isNotEmpty()) {
                appendLine("Repositórios autorizados no ambiente:")
                context.repositories.forEach { repo ->
                    appendLine("- ${repo.name} (${repo.path}${repo.branch?.let { ", branch: $it" }.orEmpty()})")
                }
            }
            appendLine()
            appendLine("Regras obrigatórias do executor Claude:")
            appendLine("1. Apenas você (Claude Code) pode modificar arquivos, executar comandos ou operações Git usando suas ferramentas locais.")
            appendLine("2. O plano acima é consultivo. Inspecione os arquivos reais antes de editar; se encontrar incompatibilidades, arquivos ausentes ou abordagem melhor no código real, questione e corrija o plano documentando cada ajuste com o prefixo '[AJUSTE NO PLANO] <motivo>'.")
            appendLine("3. Execute os testes aplicáveis aos critérios de aceitação usando Bash (respeitando aprovações configuradas) e relate os comandos e resultados reais.")
        }

        fun formatReportMarkdown(report: MultiAgentTurnReport): String = buildString {
            appendLine()
            appendLine("---")
            appendLine("### Orquestração Multiagente — ${report.finalStatus.label}")
            appendLine("- **Executor:** `${report.evidence.executorProvider}` · `${report.evidence.executorModel}`")
            if (report.plan.consultantLabels.isNotEmpty()) {
                appendLine("- **Consultores:** ${report.plan.consultantLabels.joinToString(", ")}")
            }
            appendLine("- **Plano:** ${report.plan.summary}")
            if (report.plan.targetFiles.isNotEmpty()) {
                appendLine("- **Arquivos-alvo do plano:** ${report.plan.targetFiles.joinToString(", ") { "`$it`" }}")
            }
            if (report.evidence.planAdjustments.isNotEmpty()) {
                appendLine("- **Ajustes ao plano pelo executor:**")
                report.evidence.planAdjustments.forEach { appendLine("  - $it") }
            }
            if (report.evidence.modifiedFiles.isNotEmpty()) {
                appendLine("- **Arquivos modificados:** ${report.evidence.modifiedFiles.joinToString(", ") { "`$it`" }}")
            } else {
                appendLine("- **Arquivos modificados:** Nenhum arquivo alterado registrado.")
            }
            if (report.evidence.testCommands.isNotEmpty()) {
                appendLine("- **Testes executados:** ${report.evidence.testCommands.joinToString(", ") { "`$it`" }}")
                if (report.evidence.testOutput.isNotBlank()) {
                    appendLine("  ```text")
                    appendLine(report.evidence.testOutput.take(1_200))
                    appendLine("  ```")
                }
            } else {
                appendLine("- **Testes executados:** Nenhum comando de teste automatizado foi executado neste turno.")
            }
            if (report.reviews.isNotEmpty()) {
                appendLine("- **Validação independente:**")
                report.reviews.forEach { rev ->
                    val badge = if (rev.approved) "APROVADO" else "AJUSTES RECOMENDADOS"
                    appendLine("  - **${rev.reviewerProvider} · ${rev.reviewerModel}** [$badge]: ${rev.summary}")
                    rev.findings.forEach { finding -> appendLine("    - $finding") }
                }
            }
            appendLine("- **Status final:** **${report.finalStatus.label}**")
        }
    }
}

internal fun createProviderConsultantInvoker(
    providerManager: ProviderManager,
    usageRecorder: UsageRecorder,
    assistantId: Uuid,
    conversationId: Uuid,
): ConsultantInvoker = ConsultantInvoker { route, messages, maxTokens ->
    val provider = usageRecorder.wrap(
        providerManager.getProviderByType(route.provider),
        UsageTag(UsageSource.CHAT, assistantId, conversationId),
    )
    val result = provider.generateText(
        route.provider,
        messages,
        TextGenerationParams(
            model = route.model,
            maxTokens = maxTokens,
            tools = emptyList(),
            customHeaders = route.model.customHeaders,
        ),
    )
    result.message.toText()
}

internal fun shouldOrchestrateTask(task: String, turn: ChatTurnContext): Boolean {
    if (!turn.programmingConsultants) return false
    return !isSimpleDirectTask(task)
}

internal fun isSimpleDirectTask(task: String): Boolean {
    val trimmed = task.trim()
    if (trimmed.isEmpty()) return true
    val lower = trimmed.lowercase(Locale.ROOT)
    val words = lower.split(Regex("""\s+""")).filter { it.isNotBlank() }

    val engineeringKeywords = listOf(
        "implement", "implementar", "crie", "criar", "corrig", "fix", "bug",
        "refator", "refactor", "arquitet", "planej", "plan", "teste", "test",
        "multiagente", "reposit", "endpoint", "api", "arquivo", "file", "classe",
        "função", "function", "erro", "error", "build", "compile", "commit",
    )
    if (engineeringKeywords.any { lower.contains(it) }) return false
    if (Regex("""\b\S+\.(kt|java|py|ts|tsx|js|json|yml|yaml|xml|md|gradle)\b""").containsMatchIn(lower)) {
        return false
    }
    if (words.size >= 10 || lower.length >= 80) return false

    return true
}

internal fun selectConsultantRoutes(
    settings: Settings,
    task: String,
    excludeModelIds: Set<Uuid> = emptySet(),
    excludeRawModelIds: Set<String> = emptySet(),
    limit: Int = ClaudeMultiAgentOrchestrator.MAX_PARALLEL_CONSULTANTS,
): List<FreeRoute> {
    val pool = settings.automaticRoutes(task, null).filter { route ->
        val check = settings.modelCheck(route.provider, route.model)
        val okOrTrusted = check == null || check.status == ModelCheckStatus.OK
        val rawId = route.model.modelId.lowercase(Locale.ROOT)
        val notClaude = classifyOpenRouterClaudeFamily(rawId) == null && !rawId.contains("claude")
        val notExcluded = route.model.id !in excludeModelIds &&
            excludeRawModelIds.none { it.equals(route.model.modelId, ignoreCase = true) }
        okOrTrusted && notClaude && notExcluded
    }
    if (pool.isEmpty()) return emptyList()

    val chosen = mutableListOf<FreeRoute>()
    val seenFamilies = mutableSetOf<String>()
    val seenKeys = mutableSetOf<String>()

    // Pass 1: maximize diversity across model families (Gemini, GPT-OSS, Llama, Qwen, DeepSeek, Mistral...)
    for (route in pool) {
        if (chosen.size >= limit) break
        val key = "${route.provider.id}:${route.model.modelId}"
        val family = classifyConsultantFamily(route.model.modelId, route.provider.name)
        if (key !in seenKeys && family !in seenFamilies) {
            chosen += route
            seenKeys += key
            seenFamilies += family
        }
    }

    // Pass 2: fill remaining slots with distinct provider/model combinations
    for (route in pool) {
        if (chosen.size >= limit) break
        val key = "${route.provider.id}:${route.model.modelId}"
        if (key !in seenKeys) {
            chosen += route
            seenKeys += key
        }
    }
    return chosen
}

internal fun selectIndependentReviewers(
    settings: Settings,
    task: String,
    executorModelId: String,
    excludeModelUuid: Uuid,
    plannerRouteKeys: List<String>,
    limit: Int = 2,
): List<FreeRoute> {
    val allEligible = selectConsultantRoutes(
        settings = settings,
        task = task,
        excludeModelIds = setOf(excludeModelUuid),
        excludeRawModelIds = setOf(executorModelId),
        limit = 6,
    )
    if (allEligible.isEmpty()) return emptyList()

    val primaryPlannerKey = plannerRouteKeys.firstOrNull()
    val independentFirst = allEligible.sortedBy { route ->
        val key = "${route.provider.id}:${route.model.modelId}"
        when {
            key !in plannerRouteKeys -> 0
            key != primaryPlannerKey -> 1
            else -> 2
        }
    }
    return independentFirst.take(limit)
}

internal fun classifyConsultantFamily(modelId: String, providerName: String): String {
    val text = "$providerName $modelId".lowercase(Locale.ROOT)
    return when {
        "gemini" in text -> "gemini"
        "gpt-oss" in text || "openai/" in text || "gpt-" in text -> "gpt-oss"
        "qwen" in text -> "qwen"
        "deepseek" in text -> "deepseek"
        "llama" in text -> "llama"
        "mistral" in text -> "mistral"
        else -> providerName.lowercase(Locale.ROOT).ifBlank { text }
    }
}

internal fun isSensitiveOrIgnoredPath(rawPath: String): Boolean {
    val normalized = rawPath.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
    if (normalized.isEmpty()) return false
    val segments = normalized.split('/')
    if (segments.any { it in IGNORED_DIR_NAMES }) return true
    val fileName = segments.last()
    if (fileName.startsWith(".env")) return true
    if (SENSITIVE_FILE_EXTENSIONS.any { fileName.endsWith(it) }) return true
    if (SENSITIVE_FILE_KEYWORDS.any { fileName.contains(it) }) return true
    return false
}

internal fun sanitizeSensitiveText(input: String): String {
    if (input.isEmpty()) return input
    var out = input
    out = Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----""")
        .replace(out, "[REDACTED_PRIVATE_KEY]")
    out = Regex("""\b(sk-ant-[A-Za-z0-9_-]{8,}|sk-or-[A-Za-z0-9_-]{8,}|sk-[A-Za-z0-9_-]{16,}|AIza[0-9A-Za-z_-]{16,}|gsk_[A-Za-z0-9_-]{12,}|ghp_[A-Za-z0-9]{12,}|github_pat_[A-Za-z0-9_]{12,}|xox[baprs]-[A-Za-z0-9-]{10,})\b""")
        .replace(out, "[REDACTED_SECRET]")
    out = Regex("""(?i)\b(authorization\s*[:=]\s*bearer\s+|bearer\s+)[^\s"']+""")
        .replace(out) { match -> "${match.groupValues[1]}[REDACTED]" }
    out = Regex("""(?i)\b(api[_-]?key|access[_-]?token|auth[_-]?token|secret|password)\s*[:=]\s*("[^"]+"|'[^']+'|\S+)""")
        .replace(out) { match -> "${match.groupValues[1]}=[REDACTED]" }
    out = Regex("""(https?://)([^:@/\s]+):([^@/\s]+)@""")
        .replace(out) { match -> "${match.groupValues[1]}[REDACTED]@" }
    return out
}

internal fun sanitizeUrlCredentials(url: String?): String? {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return sanitizeSensitiveText(trimmed)
    return if (parsed.userInfo != null) {
        runCatching {
            URI(parsed.scheme, null, parsed.host, parsed.port, parsed.path, parsed.query, parsed.fragment).toString()
        }.getOrElse { sanitizeSensitiveText(trimmed) }
    } else {
        sanitizeSensitiveText(trimmed)
    }
}

private fun selectRelevantFiles(files: List<String>, task: String): List<String> {
    val taskTokens = task.lowercase(Locale.ROOT)
        .split(Regex("""[^a-z0-9_.-]+"""))
        .filter { it.length >= 3 }
        .toSet()

    val manifestNames = setOf(
        "readme.md", "package.json", "build.gradle.kts", "settings.gradle.kts",
        "pyproject.toml", "requirements.txt", "cargo.toml", "pom.xml",
    )

    return files.sortedWith(
        compareByDescending<String> { path ->
            val lower = path.lowercase(Locale.ROOT)
            var score = 0
            if (lower in manifestNames || lower.substringAfterLast('/') in manifestNames) score += 50
            for (token in taskTokens) {
                if (lower.contains(token)) score += 30
            }
            score
        }.thenBy { it.length },
    ).take(6)
}

private fun formatContextForConsultants(task: String, context: RepositoryContextBundle): String = buildString {
    appendLine("Tarefa solicitada pelo usuário:")
    appendLine(task.take(3_000))
    appendLine()
    context.branch?.let { appendLine("Branch atual: $it") }
    if (context.gitStatus.isNotBlank()) {
        appendLine("Git status:\n${context.gitStatus}")
    }
    if (context.repositories.isNotEmpty()) {
        appendLine("Repositórios autorizados:")
        context.repositories.forEach { repo ->
            appendLine("- ${repo.name} (${repo.path}, branch=${repo.branch ?: "?"}, remote=${repo.remoteUrl ?: "local"})")
        }
    }
    if (context.files.isNotEmpty()) {
        appendLine("Árvore resumida do workspace:")
        context.files.take(60).forEach { appendLine("- $it") }
    }
    if (context.excerpts.isNotEmpty()) {
        appendLine()
        appendLine("Trechos relevantes sanitizados:")
        context.excerpts.forEach { excerpt ->
            appendLine("--- ${excerpt.path} ---")
            appendLine(excerpt.content)
        }
    }
}

private fun formatValidationPrompt(
    task: String,
    plan: StructuredPlan,
    evidence: ExecutionEvidence,
): String = buildString {
    appendLine("Tarefa original: ${task.take(2_000)}")
    appendLine("Plano consolidado: ${plan.summary}")
    if (plan.acceptanceCriteria.isNotEmpty()) {
        appendLine("Critérios de aceitação:")
        plan.acceptanceCriteria.forEach { appendLine("- $it") }
    }
    appendLine()
    appendLine("Executor: ${evidence.executorProvider} / ${evidence.executorModel}")
    appendLine("Arquivos modificados: ${evidence.modifiedFiles.ifEmpty { listOf("Nenhum") }.joinToString(", ")}")
    if (evidence.planAdjustments.isNotEmpty()) {
        appendLine("Ajustes ao plano relatados pelo executor:")
        evidence.planAdjustments.forEach { appendLine("- $it") }
    }
    if (evidence.gitDiffStat.isNotBlank()) {
        appendLine("Git diff stat:\n${evidence.gitDiffStat}")
    }
    if (evidence.testCommands.isNotEmpty()) {
        appendLine("Comandos de teste: ${evidence.testCommands.joinToString(", ")}")
    }
    if (evidence.testOutput.isNotBlank()) {
        appendLine("Saída dos testes:\n${evidence.testOutput.take(3_000)}")
    }
    if (evidence.assistantSummary.isNotBlank()) {
        appendLine("Resumo entregue pelo executor:\n${evidence.assistantSummary.take(2_000)}")
    }
    appendLine()
    appendLine("""Responda em JSON: {"approved": true|false, "summary": "...", "findings": ["..."]}""")
}

private fun parseConsultantProposal(route: FreeRoute, rawText: String): ConsultantProposal {
    val providerName = route.provider.name.ifBlank { "Provedor" }
    val modelId = route.model.modelId
    val displayName = route.model.displayName.ifBlank { modelId }
    val json = extractJsonObject(rawText)
    if (json != null) {
        return ConsultantProposal(
            route = route,
            providerName = providerName,
            modelId = modelId,
            displayName = displayName,
            rawText = rawText,
            summary = json.stringField("summary").ifBlank { rawText.lineSequence().firstOrNull().orEmpty() },
            targetFiles = json.stringList("targetFiles"),
            actions = json.stringList("actions"),
            acceptanceCriteria = json.stringList("acceptanceCriteria"),
            risks = json.stringList("risks"),
        )
    }
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val bullets = lines.filter { it.startsWith("-") || it.startsWith("*") || Regex("""^\d+\.""").containsMatchIn(it) }
        .map { it.replaceFirst(Regex("""^([-*]|\d+\.)\s*"""), "") }
    return ConsultantProposal(
        route = route,
        providerName = providerName,
        modelId = modelId,
        displayName = displayName,
        rawText = rawText,
        summary = lines.firstOrNull().orEmpty().take(240),
        targetFiles = emptyList(),
        actions = bullets.take(8),
        acceptanceCriteria = emptyList(),
        risks = emptyList(),
    )
}

private fun parseStructuredPlanJson(rawText: String): StructuredPlan? {
    val json = extractJsonObject(rawText) ?: return null
    val summary = json.stringField("summary")
    val actions = json.stringList("actions")
    if (summary.isBlank() && actions.isEmpty()) return null
    return StructuredPlan(
        summary = summary.ifBlank { actions.firstOrNull().orEmpty() },
        targetFiles = json.stringList("targetFiles"),
        actions = actions,
        acceptanceCriteria = json.stringList("acceptanceCriteria"),
        risks = json.stringList("risks"),
        consultantLabels = emptyList(),
        consultantRouteKeys = emptyList(),
    )
}

private fun parseValidationReview(route: FreeRoute, rawText: String): ValidationReview {
    val providerName = route.provider.name.ifBlank { "Provedor" }
    val modelDisplay = route.model.displayName.ifBlank { route.model.modelId }
    val json = extractJsonObject(rawText)
    if (json != null) {
        val approved = json["approved"]?.jsonPrimitive?.booleanOrNull ?: !rawText.contains("reprovad", ignoreCase = true)
        return ValidationReview(
            reviewerProvider = providerName,
            reviewerModel = modelDisplay,
            approved = approved,
            summary = json.stringField("summary").ifBlank { rawText.take(300) },
            findings = json.stringList("findings"),
        )
    }
    val lower = rawText.lowercase(Locale.ROOT)
    val rejected = "reprovad" in lower || "falh" in lower || "rejected" in lower || "not approved" in lower
    return ValidationReview(
        reviewerProvider = providerName,
        reviewerModel = modelDisplay,
        approved = !rejected,
        summary = rawText.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(300),
        findings = emptyList(),
    )
}

private fun extractJsonObject(rawText: String): JsonObject? {
    val trimmed = rawText.trim()
    val candidates = buildList {
        add(trimmed)
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""").find(trimmed)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) add(fenced)
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) add(trimmed.substring(start, end + 1))
    }
    for (candidate in candidates) {
        val obj = runCatching { Json.parseToJsonElement(candidate).jsonObject }.getOrNull()
        if (obj != null) return obj
    }
    return null
}

private fun JsonObject.stringField(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun JsonObject.stringList(key: String): List<String> {
    val array: JsonArray = runCatching { this[key]?.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { el: JsonElement ->
        runCatching { el.jsonPrimitive.contentOrNull?.trim() }.getOrNull()
    }.filter { it.isNotBlank() }
}

internal fun extractModifiedFilesFromTools(message: UIMessage): List<String> {
    val modified = mutableListOf<String>()
    for (tool in message.getTools()) {
        if (tool.toolName !in setOf("Write", "Edit", "MultiEdit", "write_file", "edit_file")) continue
        val inputObj = runCatching { Json.parseToJsonElement(tool.input).jsonObject }.getOrNull() ?: continue
        val path = inputObj.stringField("file_path").ifBlank { inputObj.stringField("path") }
        if (path.isNotBlank()) modified += path
    }
    return modified.distinct()
}

internal fun extractTestExecutionFromTools(message: UIMessage): Pair<List<String>, String> {
    val commands = mutableListOf<String>()
    val outputs = mutableListOf<String>()
    for (tool in message.getTools()) {
        if (tool.toolName !in setOf("Bash", "workspace_shell")) continue
        val inputObj = runCatching { Json.parseToJsonElement(tool.input).jsonObject }.getOrNull()
        val cmd = inputObj?.stringField("command").orEmpty()
        val lower = cmd.lowercase(Locale.ROOT)
        val looksLikeTest = listOf(
            "test", "pytest", "unittest", "gradlew", "vitest", "jest",
            "cargo test", "go test", "ctest", "rspec", "phpunit",
        ).any { it in lower }
        if (looksLikeTest && cmd.isNotBlank()) {
            commands += cmd
            val outText = tool.output.filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .trim()
            if (outText.isNotBlank()) {
                outputs += "$ $cmd\n${outText.take(1_500)}"
            }
        }
    }
    return commands.distinct() to outputs.joinToString("\n\n")
}

internal fun extractPlanDiscrepancies(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.lines()
        .map { it.trim() }
        .filter { line ->
            line.contains("[AJUSTE NO PLANO]", ignoreCase = true) ||
                line.contains("[PLAN ADJUSTMENT]", ignoreCase = true)
        }
        .map { line ->
            line.replace(Regex("""^[-*\s]*\[(AJUSTE NO PLANO|PLAN ADJUSTMENT)\]\s*""", RegexOption.IGNORE_CASE), "").trim()
        }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun parseGitStatusPaths(statusStdout: String): List<String> {
    if (statusStdout.isBlank()) return emptyList()
    return statusStdout.lines()
        .map { it.trimEnd() }
        .filter { it.length > 3 }
        .map { line ->
            val raw = line.substring(3).trim()
            raw.substringAfter(" -> ", raw)
        }
        .filter { it.isNotBlank() && !isSensitiveOrIgnoredPath(it) }
        .distinct()
}

private fun detectSafeVerificationCommand(
    workspaceFiles: List<String>,
    modifiedFiles: List<String>,
): String? {
    val all = (workspaceFiles + modifiedFiles).map { it.lowercase(Locale.ROOT) }
    return when {
        all.any { it.endsWith("pytest.ini") || it.endsWith("pyproject.toml") || it.startsWith("tests/") } &&
            modifiedFiles.any { it.endsWith(".py") } -> "pytest -q"
        all.any { it.endsWith("package.json") } &&
            modifiedFiles.any { it.endsWith(".js") || it.endsWith(".ts") || it.endsWith(".tsx") } -> "npm test -- --watchAll=false"
        all.any { it == "gradlew" || it.endsWith("/gradlew") } -> "./gradlew test"
        else -> null
    }
}

private fun computeFinalStatus(
    evidence: ExecutionEvidence,
    reviews: List<ValidationReview>,
): MultiAgentFinalStatus {
    if (reviews.isNotEmpty()) {
        val allApproved = reviews.all { it.approved }
        if (!allApproved) return MultiAgentFinalStatus.NEEDS_REVISION
        return if (evidence.testCommands.isNotEmpty() && evidence.planAdjustments.isEmpty()) {
            MultiAgentFinalStatus.APPROVED
        } else {
            MultiAgentFinalStatus.APPROVED_WITH_NOTES
        }
    }
    return MultiAgentFinalStatus.EXECUTED
}

private const val MAX_SCAN_DEPTH = 2
private const val MAX_LISTED_FILES = 120
private const val MAX_FILE_EXCERPT_CHARS = 4_000
private const val MAX_TOTAL_EXCERPT_CHARS = 18_000

private val IGNORED_DIR_NAMES = setOf(
    ".git", ".gradle", "build", "node_modules", "__pycache__", ".venv", "venv",
    "dist", "target", ".idea", ".cache",
)

private val SENSITIVE_FILE_EXTENSIONS = setOf(
    ".pem", ".key", ".jks", ".p12", ".keystore", ".pfx", ".crt",
)

private val SENSITIVE_FILE_KEYWORDS = setOf(
    "secret", "credential", "apikey", "api_key", "id_rsa", "id_ed25519", "local.properties",
)

private const val CONSULTANT_PLANNING_SYSTEM_PROMPT = """
You are a read-only software architecture & planning consultant.
You MUST NOT attempt to modify files or execute commands; only analyze the sanitized repository context and task.
Respond strictly as concise JSON with keys:
- "summary": concise technical approach
- "targetFiles": array of probable file paths to inspect/edit
- "actions": ordered array of implementation steps
- "acceptanceCriteria": array of testable verification criteria
- "risks": array of key edge cases or assumptions Claude should verify in real files
"""

private const val PLAN_CONSOLIDATION_SYSTEM_PROMPT = """
Consolidate the consultant proposals into a single actionable technical plan JSON object.
Deduplicate files and steps, preserve concrete risks, and reply with JSON keys:
"summary", "targetFiles", "actions", "acceptanceCriteria", "risks".
"""

private const val CONSULTANT_VALIDATION_SYSTEM_PROMPT = """
You are an independent read-only code reviewer validating changes already executed by Claude Code.
Evaluate whether modified files and test outputs satisfy the task and acceptance criteria.
Reply strictly in JSON with keys:
- "approved": boolean
- "summary": concise review verdict
- "findings": array of short concrete observations or missing checks
"""
