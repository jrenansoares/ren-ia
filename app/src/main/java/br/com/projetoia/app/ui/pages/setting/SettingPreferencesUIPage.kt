package br.com.projetoia.app.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import br.com.projetoia.app.R
import br.com.projetoia.app.data.datastore.DisplaySetting
import br.com.projetoia.app.ui.components.nav.BackButton
import br.com.projetoia.app.ui.components.richtext.MarkdownBlock
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.components.ui.CardGroupValue
import br.com.projetoia.app.ui.components.ui.switchItem
import br.com.projetoia.app.ui.theme.CustomColors
import br.com.projetoia.app.ui.theme.JetbrainsMono
import br.com.projetoia.app.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesUIPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences_ui))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    title = { Text(stringResource(R.string.setting_page_message_display_settings)) },
                ) {
                    switchItem(
                        checked = displaySetting.showUserAvatar,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showAssistantBubble,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_title)) },
                    )
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_bubble_opacity_title)) },
                        trailingContent = { CardGroupValue("${(displaySetting.bubbleOpacity * 100).roundToInt()}%") },
                    ) {
                        Slider(
                            value = displaySetting.bubbleOpacity,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                            },
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                CardGroup {
                    switchItem(
                        checked = displaySetting.showModelIcon,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showModelName,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_model_name_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_model_name_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showDateTimeInMessage,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showTokenUsage,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_title)) },
                    )
                }
            }

            item {
                CardGroup {
                    switchItem(
                        checked = displaySetting.showThinkingContent,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_title)) },
                    )
                    switchItem(
                        checked = displaySetting.autoCloseThinking,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableLatexRendering,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableLatexRendering = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_title)) },
                    )
                    switchItem(
                        checked = displaySetting.enableQuoteHighlight,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableQuoteHighlight = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_title)) },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("JetBrains Mono") },
                        supportingContent = { Text("Fonte fixa da identidade Ren") },
                    )
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                        trailingContent = { CardGroupValue("${(displaySetting.fontSizeRatio * 100).toInt()}%") },
                    ) {
                        Slider(
                            value = displaySetting.fontSizeRatio,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                            },
                            valueRange = 0.5f..2f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            MarkdownBlock(
                                content = stringResource(R.string.setting_display_page_font_size_preview),
                                modifier = Modifier.padding(12.dp),
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                    lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                    fontFamily = JetbrainsMono,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                CardGroup(
                    title = { Text(stringResource(R.string.setting_page_code_display_settings)) },
                ) {
                    switchItem(
                        checked = displaySetting.codeBlockAutoWrap,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title)) },
                    )
                    switchItem(
                        checked = displaySetting.codeBlockAutoCollapse,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title)) },
                    )
                    switchItem(
                        checked = displaySetting.showLineNumbers,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(showLineNumbers = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_title)) },
                    )
                }
            }
        }
    }
}

