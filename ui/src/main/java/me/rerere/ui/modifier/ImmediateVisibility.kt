package me.rerere.ui.modifier

import androidx.compose.animation.AnimatedVisibility as MaterialAnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable

@Composable
fun ImmediateVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") enter: EnterTransition = EnterTransition.None,
    @Suppress("UNUSED_PARAMETER") exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    MaterialAnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        content = content,
    )
}
