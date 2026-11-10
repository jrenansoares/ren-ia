package br.com.projetoia.app.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RetryPolicy
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import br.com.projetoia.app.R
import br.com.projetoia.app.data.ai.prompts.buildMemoryPrompt
import br.com.projetoia.app.data.ai.tools.ChatToolFactory
import br.com.projetoia.app.data.ai.transformers.Base64ImageToLocalFileTransformer
import br.com.projetoia.app.data.ai.transformers.DocumentAsPromptTransformer
import br.com.projetoia.app.data.ai.transformers.InputMessageTransformer
import br.com.projetoia.app.data.ai.transformers.OcrTransformer
import br.com.projetoia.app.data.ai.transformers.OutputMessageTransformer
import br.com.projetoia.app.data.ai.transformers.PlaceholderTransformer
import br.com.projetoia.app.data.ai.transformers.PromptInjectionTransformer
import br.com.projetoia.app.data.ai.transformers.RegexOutputTransformer
import br.com.projetoia.app.data.ai.transformers.TemplateTransformer
import br.com.projetoia.app.data.ai.transformers.ThinkTagTransformer
import br.com.projetoia.app.data.ai.transformers.TimeReminderTransformer
import br.com.projetoia.app.data.ai.transformers.WorkspaceReminderTransformer
import br.com.projetoia.app.data.ai.transformers.onGenerationFinish
import br.com.projetoia.app.data.ai.transformers.transformThinkTags
import br.com.projetoia.app.data.ai.transformers.transforms
import br.com.projetoia.app.data.ai.transformers.visualTransforms
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.datastore.findProvider
import br.com.projetoia.app.data.files.FileFolders
import br.com.projetoia.app.data.model.Assistant
import br.com.projetoia.app.data.model.Conversation
import br.com.projetoia.app.data.model.MemoryFile
import br.com.projetoia.app.data.model.getAssistantOf
import br.com.projetoia.app.data.model.getChatModelOf
import br.com.projetoia.app.data.repository.MemoryRepository
import br.com.projetoia.app.data.repository.WorkspaceRepository
import br.com.projetoia.app.data.repository.memoryId
import java.io.File
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 正常对话和压缩用同一份上下文组装请求，系统提示词、工具定义、参数和 sessionId 都相同，
 * 压缩请求的前缀才能和上一轮对话一致，命中提示词缓存。
 */
class ChatTurnContext(
    val settings: Settings,
    val assistant: Assistant,
    val model: Model,
    val providerSetting: ProviderSetting,
    val conversationId: Uuid,
    val conversationSystemPrompt: String?,
    val workspaceCwd: String?,
    val memories: List<MemoryFile>,
    val tools: List<Tool>,
    val programmingMode: Boolean = false,
    val programmingAutoApprove: Boolean = false,
    val claudeCode: Boolean = false,
    val programmingConsultants: Boolean = false,
)

/** 把会话变成发给模型的请求：系统提示词、上下文裁剪、输入输出转换都在这里 */
class ChatRequestFactory(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val chatToolFactory: ChatToolFactory,
    private val memoryRepository: MemoryRepository,
    templateTransformer: TemplateTransformer,
    private val workspaceRepository: WorkspaceRepository,
    private val routingObserver: RoutingObserver,
) {
    private val inputTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        templateTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
    )

    private val outputTransformers: List<OutputMessageTransformer> = listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )

    /** 解析本轮的助手、模型、记忆和工具；MCP 服务名不合法时抛出 InvalidMcpServerNamesException */
    suspend fun createContext(settings: Settings, conversation: Conversation): ChatTurnContext {
        // 模型、思考级别、搜索、工具等以会话上固定的配置为准
        val programming = conversation.config?.programmingMode == true
        val claudeCode = conversation.programmingClaudeCodeEnabled
        val autoApprove = conversation.programmingAutoApprovalEnabled
        val assistant = settings.getAssistantOf(conversation).let { if (programming) it.forProgramming() else it }
        if (programming) {
            val workspace = assistant.workspaceId?.let { workspaceRepository.getById(it.toString()) }
            check(workspace != null && !workspace.isSsh() && workspace.shellStatus == "READY") {
                context.getString(R.string.programming_environment_required)
            }
            check(me.rerere.workspace.ProgrammingEnvironment.isReady(workspaceRepository.executeCommand(
                workspace.id,
                me.rerere.workspace.ProgrammingEnvironment.checkCommand,
                timeoutMillis = 15_000L,
            ))) { context.getString(R.string.programming_environment_required) }
        }
        val task = conversation.currentMessages.lastOrNull {
            it.role == me.rerere.ai.core.MessageRole.USER
        }?.toText().orEmpty()
        val selectedModel = settings.getChatModelOf(conversation)
        val needsVision = !claudeCode && conversation.currentMessages.any { message ->
            message.parts.any { it is me.rerere.ai.ui.UIMessagePart.Image }
        }
        val needsTools =
            assistant.workspaceId != null || assistant.mcpServers.isNotEmpty() || assistant.localTools.isNotEmpty()
        val automatic = if (settings.freeRouter.enabled || programming) {
            (if (programming) settings.programmingRoutes(task, selectedModel?.id) else settings.automaticRoutes(task, selectedModel?.id)).firstOrNull {
                (!needsVision || me.rerere.ai.provider.Modality.IMAGE in it.model.inputModalities) &&
                    (!needsTools || me.rerere.ai.provider.ModelAbility.TOOL in it.model.abilities || it.toolsVerified)
            }?.model
                ?: error(
                    context.getString(
                        if (programming) R.string.programming_route_required else R.string.projeto_router_empty
                    )
                )
        } else {
            null
        }
        val model = automatic ?: selectedModel
            ?: throw IllegalStateException("No chat model selected")
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        return ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = model,
            providerSetting = provider,
            conversationId = conversation.id,
            conversationSystemPrompt = conversation.customSystemPrompt,
            workspaceCwd = conversation.workspaceCwd,
            memories = if (assistant.enableMemory) memoryRepository.getFiles(assistant.memoryId) else emptyList(),
            tools = if (claudeCode) emptyList() else chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                workspaceCwd = conversation.workspaceCwd,
                programmingMode = programming,
                programmingAutoApprove = autoApprove,
            ),
            programmingMode = programming,
            programmingAutoApprove = autoApprove,
            claudeCode = claudeCode,
            programmingConsultants = conversation.programmingConsultantsEnabled,
        )
    }

    /**
     * 本轮交给 runAgent 的请求和钩子。[messages] 的最后一条是助手消息时从它继续，否则新建一条助手消息。
     * [processingStatus] 用来显示 OCR 这类输入转换的进度。
     */
    fun createAgent(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
    ): Pair<AgentRequest, AgentHooks> {
        val usageTag = UsageTag(UsageSource.CHAT, turn.assistant.id, turn.conversationId)
        val router = if (turn.settings.freeRouter.enabled || turn.programmingMode) {
            FreeRoutingProvider(
                { setting -> usageRecorder.wrap(providerManager.getProviderByType(setting), usageTag) },
                (if (turn.programmingMode) {
                    turn.settings.programmingRoutes(
                        messages.lastOrNull { it.role == me.rerere.ai.core.MessageRole.USER }?.toText().orEmpty(),
                        turn.model.id,
                    )
                } else {
                    turn.settings.automaticRoutes(
                        messages.lastOrNull { it.role == me.rerere.ai.core.MessageRole.USER }?.toText().orEmpty(),
                        turn.model.id,
                    )
                }),
                processingStatus,
                allowed = ::isAutomaticRouteCandidate,
                observe = routingObserver::record,
            )
        } else {
            null
        }
        val request = AgentRequest(
            provider = router ?: usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                usageTag,
            ),
            providerSetting = turn.providerSetting,
            messages = messages,
            tools = turn.tools,
            stream = turn.assistant.streamOutput,
            retry = if (router != null) RetryPolicy.NONE else turn.settings.retryPolicy(),
            maxSteps = if (turn.programmingMode) 24 else 256,
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>): StepRequest {
                Log.i(TAG, "prepareStep: ${turn.model.id}")
                return buildStep(turn, messages, processingStatus)
            }

            override suspend fun previewStep(message: UIMessage, newPartsFrom: Int) =
                message.copy(modelId = router?.activeModelId ?: message.modelId).transformNewParts(newPartsFrom) {
                    it.visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun finishStep(message: UIMessage, newPartsFrom: Int) =
                message.copy(modelId = router?.activeModelId ?: message.modelId).transformNewParts(newPartsFrom) {
                    it.transforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .onGenerationFinish(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }
        }
        return request to hooks
    }

    /**
     * 生成压缩摘要。
     *
     * 请求按正常对话组装（系统提示词、工具定义、参数和 sessionId 都相同），只在末尾追加压缩指令，
     * 前缀与上一轮对话一致，可以命中提示词缓存，模型也能看到工具调用和结果。
     * 工具定义必须保留：它属于请求前缀，去掉或改动 tool_choice 都会让缓存失效。
     */
    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        val (requestMessages, params) = buildStep(turn, messages)
        // 压缩指令不经过输入转换器，模板转换器会把其中的花括号当成模板语法
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            (if (turn.programmingMode) {
                FreeRoutingProvider(
                    { setting ->
                        usageRecorder.wrap(
                            providerManager.getProviderByType(setting),
                            UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId)
                        )
                    },
                    turn.settings.programmingRoutes(preferredModelId = turn.model.id),
                    MutableStateFlow(null),
                    allowed = ::isProgrammingRouteAllowed,
                    observe = routingObserver::record,
                )
            } else {
                usageRecorder.wrap(
                    providerManager.getProviderByType(turn.providerSetting),
                    UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
                )
            }).generateText(
                providerSetting = turn.providerSetting,
                messages = requestMessages + UIMessage.user(compressPrompt),
                // 助手的输出上限是给聊天回复定的，结构化摘要写不完会被截断；max_tokens 不属于请求前缀，改了不影响缓存
                params = params.copy(maxTokens = null),
            )
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
        return summary.ifBlank { error("Failed to generate compressed summary") }
    }

    /** 一步请求实际发出的消息和参数；正常对话和压缩共用，二者的请求前缀必须一致才能命中提示词缓存 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    ): StepRequest {
        val assistant = turn.assistant
        val requestMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                        turn.conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }
                if (turn.programmingMode) {
                    appendLine()
                    append(if (turn.claudeCode) programmingSystemPrompt(turn.programmingAutoApprove)
                        .replace("Use as ferramentas workspace_*", "Use suas ferramentas nativas Read, Write, Edit, Bash, Glob e Grep")
                        .replace("não instale Claude Code ou agentes por conta própria", "não reinstale Claude Code ou agentes por conta própria")
                        else programmingSystemPrompt(turn.programmingAutoApprove))
                }

                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(turn.memories))
                }
                // 工具prompt
                turn.tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(turn.model, messages))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            val limited = messages.limitContext(assistant.contextMessageLimit)
            val attachments = if (turn.programmingMode) {
                withContext(Dispatchers.IO) {
                    try {
                        programmingVideoAttachments(limited, File(context.filesDir, FileFolders.UPLOAD), enabled = true, shellToolName = if (turn.claudeCode) "Bash" else "workspace_shell")
                    } catch (_: UnavailableProgrammingVideo) {
                        error(context.getString(R.string.chat_input_video_missing))
                    }
                }
            } else {
                limited
            }
            addAll(attachments)
        }.transforms(
            transformers = if (turn.programmingMode) inputTransformers.filterNot { it === OcrTransformer } else inputTransformers,
            context = context,
            model = turn.model,
            assistant = assistant,
            settings = turn.settings,
            processingStatus = processingStatus,
            workspaceCwd = turn.workspaceCwd,
        )
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = turn.tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(requestMessages, params)
    }

    suspend fun prepareClaudeHistory(turn: ChatTurnContext, history: List<UIMessage>, status: MutableStateFlow<String?>): List<UIMessage> {
        val upload = File(context.filesDir, FileFolders.UPLOAD).canonicalFile
        fun reference(url: String): UIMessagePart.Text {
            val file = runCatching { File(java.net.URI(url)).canonicalFile }.getOrNull()
            check(file != null && file.parentFile == upload && file.isFile && file.length() > 0) { "Anexo local indisponível" }
            return UIMessagePart.Text("Arquivo local disponível em ${kotlinx.serialization.json.Json.encodeToString("/upload/${file.name}")}. Inspecione com suas ferramentas nativas. Preserve o original e grave resultados em /workspace.")
        }
        return buildStep(turn, history, status).messages.map { message ->
            message.copy(parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Image -> reference(part.url)
                    is UIMessagePart.Audio -> reference(part.url)
                    is UIMessagePart.Video -> reference(part.url)
                    is UIMessagePart.Document -> reference(part.url)
                    is UIMessagePart.Text -> if (message.role == me.rerere.ai.core.MessageRole.SYSTEM) part.copy(text = part.text
                        .replace("workspace_read_file", "Read").replace("workspace_write_file", "Write")
                        .replace("workspace_edit_file", "Edit").replace("workspace_shell", "Bash")) else part
                    else -> part
                }
            })
        }
    }
}

/**
 * 取出压缩请求回复里的摘要正文。
 *
 * 指令要求不调用工具，模型仍可能发起调用。这时附带的文本只是"我先看看文件"之类的说明，
 * 当成摘要保存会让之后的请求丢掉全部历史，所以返回 null 交给调用方报错。
 * 压缩请求不经过输出转换器，正文开头的 <think> 思考内容也要在这里剥掉，否则会存进摘要。
 */
internal fun UIMessage.compressionSummaryOrNull(): String? {
    if (getTools().isNotEmpty()) return null
    return listOf(this)
        .transformThinkTags(now = Clock.System.now(), generationFinished = true)
        .single()
        .toText()
        .trim()
}

/**
 * 输出转换只处理这一步新生成的 part：之前步骤的内容已经处理过，再处理一遍会让正则这类改写叠加，
 * 也能让每一步开头的 <think> 都被识别出来。
 */
internal suspend fun UIMessage.transformNewParts(
    newPartsFrom: Int,
    transform: suspend (List<UIMessage>) -> List<UIMessage>,
): UIMessage {
    val transformed = transform(listOf(copy(parts = parts.drop(newPartsFrom)))).single()
    return transformed.copy(parts = parts.take(newPartsFrom) + transformed.parts)
}

/** 标题、建议、记忆整理这类后台请求的参数：只带模型自己的自定义请求头和请求体 */
internal fun backgroundTextGenerationParams(
    model: Model,
    sessionId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = sessionId.toString(),
)

internal fun Settings.retryPolicy() =
    if (networkSetting.enableAutoRetry) RetryPolicy.NETWORK else RetryPolicy.NONE
