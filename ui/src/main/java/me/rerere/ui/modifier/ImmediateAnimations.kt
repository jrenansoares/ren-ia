package me.rerere.ui.modifier

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp

@Composable
fun ImmediateDpAsState(
    targetValue: Dp,
    @Suppress("UNUSED_PARAMETER") animationSpec: AnimationSpec<Dp> = snap(),
    @Suppress("UNUSED_PARAMETER") visibilityThreshold: Dp = Dp(0.1f),
    @Suppress("UNUSED_PARAMETER") label: String = "",
    @Suppress("UNUSED_PARAMETER") finishedListener: ((Dp) -> Unit)? = null,
): State<Dp> = rememberUpdatedState(targetValue)

@Composable
fun ImmediateFloatAsState(
    targetValue: Float,
    @Suppress("UNUSED_PARAMETER") animationSpec: AnimationSpec<Float> = snap(),
    @Suppress("UNUSED_PARAMETER") visibilityThreshold: Float = 0.01f,
    @Suppress("UNUSED_PARAMETER") label: String = "",
    @Suppress("UNUSED_PARAMETER") finishedListener: ((Float) -> Unit)? = null,
): State<Float> = rememberUpdatedState(targetValue)
