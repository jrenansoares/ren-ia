package br.com.projetoia.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import br.com.projetoia.app.data.datastore.DisplaySetting

val LocalChatFontFamily = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun ChatFontProvider(
    @Suppress("UNUSED_PARAMETER") displaySetting: DisplaySetting,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalChatFontFamily provides JetbrainsMono) {
        content()
    }
}

@Composable
fun rememberChatFontFamily(@Suppress("UNUSED_PARAMETER") displaySetting: DisplaySetting): FontFamily = JetbrainsMono

@Suppress("UNUSED_PARAMETER")
fun DisplaySetting.resolveChatFontFamily(context: android.content.Context): FontFamily = JetbrainsMono
