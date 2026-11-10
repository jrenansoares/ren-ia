package br.com.projetoia.app.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import br.com.projetoia.app.data.datastore.DisplaySetting

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color,
    shape: Shape,
): Modifier = this
