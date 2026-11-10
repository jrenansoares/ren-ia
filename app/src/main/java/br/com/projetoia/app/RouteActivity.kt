package br.com.projetoia.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.projetoia.app.ui.modifier.ImmediateVisibility as MaterialAnimatedVisibility
import br.com.projetoia.app.ui.modifier.ImmediateVisibility as AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import me.rerere.ui.toast.Toaster
import me.rerere.ui.toast.rememberToasterState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.db.DatabaseMigrationTracker
import br.com.projetoia.app.data.db.MigrationState
import br.com.projetoia.app.data.event.AppEvent
import br.com.projetoia.app.data.event.AppEventBus
import br.com.projetoia.app.data.repository.MediaCreationRepository
import br.com.projetoia.app.service.MediaCreationForegroundService
import br.com.projetoia.app.ui.activity.SafeModeActivity
import br.com.projetoia.app.ui.components.ui.TTSController
import br.com.projetoia.app.ui.context.LocalASRState
import br.com.projetoia.app.ui.context.LocalNavController
import br.com.projetoia.app.ui.context.LocalSettings
import br.com.projetoia.app.ui.context.LocalSharedTransitionScope
import br.com.projetoia.app.ui.context.LocalTTSState
import br.com.projetoia.app.ui.context.LocalToaster
import br.com.projetoia.app.ui.context.Navigator
import br.com.projetoia.app.ui.hooks.readBooleanPreference
import br.com.projetoia.app.ui.hooks.readStringPreference
import br.com.projetoia.app.ui.hooks.rememberCustomAsrState
import br.com.projetoia.app.ui.hooks.rememberCustomTtsState
import br.com.projetoia.app.ui.pages.assistant.AssistantPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantBasicPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantDetailPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantExtensionsPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantLocalToolPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantMcpPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantMemoryPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantPromptPage
import br.com.projetoia.app.ui.pages.assistant.detail.AssistantRequestPage
import br.com.projetoia.app.ui.pages.backup.BackupPage
import br.com.projetoia.app.ui.pages.chat.ChatPage
import br.com.projetoia.app.ui.pages.debug.DebugPage
import br.com.projetoia.app.ui.pages.extensions.ExtensionsPage
import br.com.projetoia.app.ui.pages.extensions.PromptPage
import br.com.projetoia.app.ui.pages.extensions.QuickMessagesPage
import br.com.projetoia.app.ui.pages.extensions.skills.SkillDetailPage
import br.com.projetoia.app.ui.pages.memory.MemoryFilePage
import br.com.projetoia.app.ui.pages.extensions.skills.SkillsPage
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspacePage
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceDetailPage
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceSshEditorPage
import br.com.projetoia.app.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.workspace.WorkspaceStorageArea
import br.com.projetoia.app.ui.pages.favorite.FavoritePage
import br.com.projetoia.app.ui.pages.history.HistoryPage
import br.com.projetoia.app.ui.pages.imggen.ImageGenPage
import br.com.projetoia.app.ui.pages.log.LogPage
import br.com.projetoia.app.ui.pages.mediacreation.MediaCreationPage
import br.com.projetoia.app.ui.pages.mediacreation.MediaCreationSessionsPage
import br.com.projetoia.app.ui.pages.search.SearchPage
import br.com.projetoia.app.ui.pages.setting.SettingAboutPage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesPage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesThemePage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesNotificationPage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesGeneralPage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesNetworkPage
import br.com.projetoia.app.ui.pages.setting.SettingPreferencesUIPage
import br.com.projetoia.app.ui.pages.setting.SettingThemePage
import br.com.projetoia.app.ui.pages.setting.SettingFilesPage
import br.com.projetoia.app.ui.pages.setting.SettingMcpDetailPage
import br.com.projetoia.app.ui.pages.setting.SettingMcpPage
import br.com.projetoia.app.ui.pages.setting.SettingModelPage
import br.com.projetoia.app.ui.pages.setting.SettingPage
import br.com.projetoia.app.ui.pages.setting.SettingProviderDetailPage
import br.com.projetoia.app.ui.pages.setting.SettingProviderPage
import br.com.projetoia.app.ui.pages.setting.SettingSearchDetailPage
import br.com.projetoia.app.ui.pages.setting.SettingSearchPage
import br.com.projetoia.app.ui.pages.setting.SettingMediaPage
import br.com.projetoia.app.ui.pages.setting.SettingMediaProviderDetailPage
import br.com.projetoia.app.ui.pages.setting.SettingSpeechPage
import br.com.projetoia.app.ui.pages.setting.SettingWebPage
import br.com.projetoia.app.ui.pages.share.handler.ShareHandlerPage
import br.com.projetoia.app.ui.pages.stats.StatsPage
import br.com.projetoia.app.ui.pages.translator.TranslatorPage
import br.com.projetoia.app.ui.pages.webview.WebViewPage
import br.com.projetoia.app.ui.theme.ProjetoIATheme
import br.com.projetoia.app.utils.CrashHandler
import br.com.projetoia.app.utils.openUsageAccessSettings
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"
private const val ACTION_TRANSLATE = "br.com.projetoia.app.action.TRANSLATE"
private const val ACTION_MEDIA_CREATION = "br.com.projetoia.app.action.MEDIA_CREATION"
private const val EXTRA_CONVERSATION_ID = "conversationId"

/** 打开指定会话的聊天页 */
fun conversationIntent(context: Context, conversationId: String): Intent =
    Intent(context, RouteActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_CONVERSATION_ID, conversationId)
    }

class RouteActivity : ComponentActivity() {
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private val mediaCreationRepository by inject<MediaCreationRepository>()
    private var navStack: MutableList<NavKey>? = null
    private val pendingIntents = ArrayDeque<Intent>()

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            ProjetoIATheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(
                                    callFactory = { okHttpClient },
                                    cacheStrategy = { CacheControlCacheStrategy() },
                                )
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes()
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val backStack = navStack ?: run {
            // Compose 尚未创建导航栈，待就绪后处理。
            pendingIntents.addLast(intent)
            return
        }
        val destination = when (intent.action) {
            ACTION_TRANSLATE -> Screen.Translator
            ACTION_MEDIA_CREATION -> {
                openNewMediaCreation(backStack)
                null
            }
            Intent.ACTION_SEND -> Screen.ShareHandler(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                streamUri = intent.getStringExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> mediaCreationDestination(intent)
                ?: intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { Screen.Chat(it) }
        }
        if (destination != null && backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    // 快捷方式没有指定会话，打开一个空会话
    private fun openNewMediaCreation(backStack: MutableList<NavKey>) {
        lifecycleScope.launch {
            val destination = Screen.MediaCreation(mediaCreationRepository.newSession().id.toString())
            if (backStack.lastOrNull() != destination) backStack.add(destination)
        }
    }

    // 媒体创作的通知带着会话 ID，点击后直接打开那个会话
    private fun mediaCreationDestination(intent: Intent): Screen? {
        val sessionId = intent.getStringExtra(MediaCreationForegroundService.EXTRA_MEDIA_SESSION_ID) ?: return null
        return runCatching { Uuid.parse(sessionId) }.getOrNull()?.let { Screen.MediaCreation(it.toString()) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.OpenUsageAccessSettings -> this@RouteActivity.openUsageAccessSettings()
                    is AppEvent.ChatGenerationUpdate -> Unit // 由 ChatNotificationManager 消费
                    is AppEvent.ChatGenerationEnded -> Unit // 由 ChatNotificationManager 消费
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        val startScreen = Screen.Chat(
            id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                readStringPreference(
                    "lastConversationId",
                    Uuid.random().toString()
                ) ?: Uuid.random().toString()
            }
        )

        val backStack = rememberNavBackStack(Screen.ProjetoHome)
        SideEffect {
            navStack = backStack
            while (pendingIntents.isNotEmpty()) {
                handleIntent(pendingIntents.removeFirst())
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        // 同一帧内连续两次返回时 NavDisplay 的返回状态还没更新，会多回调一次，不加判断会把栈弹空
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                        popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                        predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                        entryProvider = entryProvider {
                            entry<Screen.ProjetoHome> { br.com.projetoia.app.ui.pages.projeto.ProjetoHomePage() }
                            entry<Screen.SharedLinux> { br.com.projetoia.app.ui.pages.projeto.SharedLinuxPage() }
                            entry<Screen.FreeRouter> { br.com.projetoia.app.ui.pages.projeto.FreeRouterPage() }
                            entry<Screen.Connections> { br.com.projetoia.app.ui.pages.projeto.ConnectionsPage() }
                            entry<Screen.ModelChecks> { br.com.projetoia.app.ui.pages.projeto.ModelChecksPage() }
                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None }
                                    + NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) },
                                    programmingSetup = key.programmingSetup,
                                )
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.History> {
                                HistoryPage()
                            }

                            entry<Screen.Favorite> {
                                FavoritePage()
                            }

                            entry<Screen.Assistant> {
                                AssistantPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                AssistantDetailPage(key.id)
                            }

                            entry<Screen.AssistantBasic> { key ->
                                AssistantBasicPage(key.id)
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                AssistantPromptPage(key.id)
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.MemoryFile> { key ->
                                MemoryFilePage(memoryId = key.memoryId, path = key.path)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }

                            entry<Screen.Translator> {
                                TranslatorPage()
                            }

                            entry<Screen.Setting> {
                                SettingPage()
                            }

                            entry<Screen.Backup> {
                                BackupPage()
                            }

                            entry<Screen.ImageGen> {
                                ImageGenPage()
                            }

                            entry<Screen.MediaCreationSessions> {
                                MediaCreationSessionsPage()
                            }

                            // 从会话列表展开（容器变换），页面本身只做淡入淡出，避免和平移打架
                            entry<Screen.MediaCreation>(
                                metadata = NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None }
                                    + NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }
                                    + NavDisplay.predictivePopTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                MediaCreationPage(key.id)
                            }

                            entry<Screen.WebView> { key ->
                                WebViewPage(key.url, key.contentId)
                            }

                            entry<Screen.SettingTheme> {
                                SettingThemePage()
                            }

                            entry<Screen.SettingPreferences> {
                                SettingPreferencesPage()
                            }

                            entry<Screen.SettingPreferencesTheme> {
                                SettingPreferencesThemePage()
                            }

                            entry<Screen.SettingPreferencesNotification> {
                                SettingPreferencesNotificationPage()
                            }

                            entry<Screen.SettingPreferencesGeneral> {
                                SettingPreferencesGeneralPage()
                            }

                            entry<Screen.SettingPreferencesUI> {
                                SettingPreferencesUIPage()
                            }

                            entry<Screen.SettingPreferencesNetwork> {
                                SettingPreferencesNetworkPage()
                            }

                            entry<Screen.SettingProvider> {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> {
                                SettingModelPage()
                            }

                            entry<Screen.SettingAbout> {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch> {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMedia> {
                                SettingMediaPage()
                            }

                            entry<Screen.SettingMediaProviderDetail> { key ->
                                SettingMediaProviderDetailPage(id = Uuid.parse(key.providerId))
                            }

                            entry<Screen.SettingMcp> {
                                SettingMcpPage()
                            }

                            entry<Screen.SettingMcpDetail> { key ->
                                SettingMcpDetailPage(id = Uuid.parse(key.serverId))
                            }


                            entry<Screen.SettingFiles> {
                                SettingFilesPage()
                            }

                            entry<Screen.SettingWeb> {
                                SettingWebPage()
                            }

                            entry<Screen.Debug> {
                                DebugPage()
                            }

                            entry<Screen.Log> {
                                LogPage()
                            }

                            entry<Screen.Extensions> {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages> {
                                QuickMessagesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.Workspaces> {
                                WorkspacePage()
                            }

                            entry<Screen.Repositories> {
                                br.com.projetoia.app.ui.pages.projeto.RepositoriesPage()
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceDetailPage(key.id)
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.WorkspaceSshEditor> { key ->
                                WorkspaceSshEditorPage(key.id)
                            }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceFileEditorPage(
                                    id = key.id,
                                    area = WorkspaceStorageArea.valueOf(key.area),
                                    path = key.path,
                                )
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.Stats> {
                                StatsPage()
                            }
                        }
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[开发模式]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Toaster(
                        state = toastState,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data object ProjetoHome : Screen
    @Serializable
    data object SharedLinux : Screen
    @Serializable
    data object FreeRouter : Screen

    @Serializable
    data object Connections : Screen

    @Serializable
    data object ModelChecks : Screen

    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        val programmingSetup: Boolean = false,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    /** 记忆库 [memoryId] 里的一个文件，[path] 为 null 时新建 */
    @Serializable
    data class MemoryFile(val memoryId: String, val path: String? = null) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data object MediaCreationSessions : Screen

    @Serializable
    data class MediaCreation(val id: String) : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMedia : Screen

    @Serializable
    data class SettingMediaProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data class SettingMcpDetail(val serverId: String) : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data object Repositories : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    /** [id] 为 null 时新建 */
    @Serializable
    data class WorkspaceSshEditor(val id: String? = null) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen
}
