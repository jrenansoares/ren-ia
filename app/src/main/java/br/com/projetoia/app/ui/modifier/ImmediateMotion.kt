package br.com.projetoia.app.ui.modifier

import androidx.compose.animation.AnimatedContent as MaterialAnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility as MaterialAnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable

@Composable
fun ImmediateVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") enter: EnterTransition = EnterTransition.None,
    @Suppress("UNUSED_PARAMETER") exit: ExitTransition = ExitTransition.None,
    @Suppress("UNUSED_PARAMETER") label: String = "AnimatedVisibility",
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

@Composable
fun <S> ImmediateContent(
    targetState: S,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") transitionSpec: AnimatedContentTransitionScope<S>.() -> androidx.compose.animation.ContentTransform = {
        EnterTransition.None togetherWith ExitTransition.None using null
    },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (S) -> Any? = { it },
    label: String = "AnimatedContent",
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    MaterialAnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None using null },
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = label,
        content = content,
    )
}

@Composable
fun ImmediateColorAsState(
    targetValue: androidx.compose.ui.graphics.Color,
    @Suppress("UNUSED_PARAMETER") animationSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.graphics.Color> = androidx.compose.animation.core.snap(),
    @Suppress("UNUSED_PARAMETER") label: String = "",
    @Suppress("UNUSED_PARAMETER") finishedListener: ((androidx.compose.ui.graphics.Color) -> Unit)? = null,
): androidx.compose.runtime.State<androidx.compose.ui.graphics.Color> =
    androidx.compose.runtime.rememberUpdatedState(targetValue)

@Composable
fun ImmediateDpAsState(
    targetValue: androidx.compose.ui.unit.Dp,
    @Suppress("UNUSED_PARAMETER") animationSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> = androidx.compose.animation.core.snap(),
    @Suppress("UNUSED_PARAMETER") visibilityThreshold: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0.1f),
    @Suppress("UNUSED_PARAMETER") label: String = "",
    @Suppress("UNUSED_PARAMETER") finishedListener: ((androidx.compose.ui.unit.Dp) -> Unit)? = null,
): androidx.compose.runtime.State<androidx.compose.ui.unit.Dp> =
    androidx.compose.runtime.rememberUpdatedState(targetValue)

@Composable
fun ImmediateFloatAsState(
    targetValue: Float,
    @Suppress("UNUSED_PARAMETER") animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.snap(),
    @Suppress("UNUSED_PARAMETER") visibilityThreshold: Float = 0.01f,
    @Suppress("UNUSED_PARAMETER") label: String = "",
    @Suppress("UNUSED_PARAMETER") finishedListener: ((Float) -> Unit)? = null,
): androidx.compose.runtime.State<Float> = androidx.compose.runtime.rememberUpdatedState(targetValue)
