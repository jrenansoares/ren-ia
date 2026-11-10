package br.com.projetoia.app.di

import br.com.projetoia.app.ui.pages.assistant.AssistantVM
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantDetailVM
import br.com.projetoia.app.ui.pages.backup.BackupVM
import br.com.projetoia.app.ui.pages.chat.ChatDrawerVM
import br.com.projetoia.app.ui.pages.chat.ChatVM
import br.com.projetoia.app.ui.pages.debug.DebugVM
import br.com.projetoia.app.ui.pages.extensions.PromptVM
import br.com.projetoia.app.ui.pages.extensions.QuickMessagesVM
import br.com.projetoia.app.ui.pages.extensions.skills.SkillDetailVM
import br.com.projetoia.app.ui.pages.extensions.skills.SkillsVM
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceDetailVM
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceSshEditorVM
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceVM
import br.com.projetoia.app.ui.pages.favorite.FavoriteVM
import br.com.projetoia.app.ui.pages.history.HistoryVM
import br.com.projetoia.app.ui.pages.imggen.ImgGenVM
import br.com.projetoia.app.ui.pages.mediacreation.MediaCreationSessionsVM
import br.com.projetoia.app.ui.pages.mediacreation.MediaCreationVM
import br.com.projetoia.app.ui.pages.memory.MemoryFileVM
import br.com.projetoia.app.ui.pages.projeto.ProgrammingVM
import br.com.projetoia.app.ui.pages.search.SearchVM
import br.com.projetoia.app.ui.pages.setting.SettingVM
import br.com.projetoia.app.ui.pages.share.handler.ShareHandlerVM
import br.com.projetoia.app.ui.pages.stats.StatsVM
import br.com.projetoia.app.ui.pages.translator.TranslatorVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            chatService = get(),
            updateChecker = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModel<ProgrammingVM> { params ->
        ProgrammingVM(
            conversationId = params.get(),
            context = get(),
            repository = get(),
            chatService = get(),
            sharedLinux = get(),
            terminalSessions = get()
        )
    }
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            memoryConsolidationScheduler = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModel<MemoryFileVM> {
        MemoryFileVM(
            memoryId = it[0],
            path = it[1],
            memoryRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::MediaCreationSessionsVM)
    viewModel<MediaCreationVM> {
        MediaCreationVM(
            id = it.get(),
            context = get(),
            settingsStore = get(),
            repository = get(),
            service = get(),
            remoteFileStore = get(),
            appScope = get(),
        )
    }
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModel<WorkspaceSshEditorVM> {
        WorkspaceSshEditorVM(
            id = it.getOrNull(),
            repository = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
}
