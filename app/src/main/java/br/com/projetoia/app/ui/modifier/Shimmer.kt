package br.com.projetoia.app.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Suppress("UNUSED_PARAMETER")
fun Modifier.shimmer(
    isLoading: Boolean,
    shimmerColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    durationMillis: Int = 1200,
    angle: Float = 20f,
    gradientWidthRatio: Float = 0.5f,
): Modifier = this
