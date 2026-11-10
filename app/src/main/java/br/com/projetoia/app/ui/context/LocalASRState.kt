package br.com.projetoia.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import br.com.projetoia.app.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

