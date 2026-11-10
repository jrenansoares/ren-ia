package br.com.projetoia.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import br.com.projetoia.app.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
