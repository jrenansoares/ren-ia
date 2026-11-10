package br.com.projetoia.app.di

import kotlinx.serialization.json.Json
import br.com.projetoia.app.AppScope
import br.com.projetoia.app.data.ai.ChatRequestFactory
import br.com.projetoia.app.data.ai.ProviderCatalogManager
import br.com.projetoia.app.data.ai.RoutingObserver
import br.com.projetoia.app.data.ai.ModelCheckManager
import br.com.projetoia.app.data.ai.claude.ClaudeApprovals
import br.com.projetoia.app.data.ai.claude.ClaudeChatRunner
import br.com.projetoia.app.data.ai.claude.ClaudeGatewayManager
import br.com.projetoia.app.data.ai.tools.ChatToolFactory
import br.com.projetoia.app.data.ai.tools.local.LocalTools
import br.com.projetoia.app.data.event.AppEventBus
import br.com.projetoia.app.service.ChatFloatingTracker
import br.com.projetoia.app.service.ChatNotificationManager
import br.com.projetoia.app.service.ChatService
import br.com.projetoia.app.service.MediaCreationService
import br.com.projetoia.app.ui.floating.ChatFloatingWindows
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import br.com.projetoia.app.utils.EmojiData
import br.com.projetoia.app.utils.EmojiUtils
import br.com.projetoia.app.utils.JsonInstant
import br.com.projetoia.app.utils.SoundEffectPlayer
import br.com.projetoia.app.utils.UpdateChecker
import br.com.projetoia.app.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        ClaudeGatewayManager(get(), get(), get(), get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get(), get(), get(), get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    // ChatService 只在后台才用到，延迟创建
    single {
        ChatFloatingTracker(
            appScope = get(),
            lazyChatService = lazy { get() },
            settingsStore = get(),
        )
    }

    // 同上，进程启动即开始监听前后台切换
    single(createdAtStart = true) {
        ChatFloatingWindows(
            context = get(),
            appScope = get(),
            tracker = get(),
            lazyChatService = lazy { get() },
        )
    }

    single {
        ChatToolFactory(
            context = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatRequestFactory(
            context = get(),
            providerManager = get(),
            usageRecorder = get(),
            chatToolFactory = get(),
            memoryRepository = get(),
            templateTransformer = get(),
            workspaceRepository = get(),
            routingObserver = get(),
        )
    }

    single { RoutingObserver(get()) }
    single { ProviderCatalogManager(get(), get(), get<AppScope>()) }
    single { ClaudeApprovals() }
    single {
        ModelCheckManager(
            store = get(),
            manager = get(),
            scope = get<AppScope>(),
        )
    }
    single { ClaudeChatRunner(get(), get(), get(), get(), get()) }
    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            requestFactory = get(),
            claudeChatRunner = get(),
            claudeApprovals = get(),
            memoryConsolidationScheduler = get(),
            translationHandler = get(),
            metaGenerator = get(),
            mcpManager = get(),
            filesManager = get(),
            folderRepository = get(),
            workspaceRepository = get(),
        )
    }

    single {
        MediaCreationService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            repository = get(),
            manager = get(),
            remoteFileStore = get(),
            okHttpClient = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
