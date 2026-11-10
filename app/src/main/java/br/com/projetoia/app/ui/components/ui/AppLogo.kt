package br.com.projetoia.app.ui.components.ui

import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastForEach
import androidx.core.content.ContextCompat
import br.com.projetoia.app.R
import kotlin.math.roundToInt

/** 应用图标的各层：直接画图层，整张图标带着系统的图标蒙版，会和外面的轮廓叠在一起 */
@Composable
fun rememberAppLogoLayers(): List<Drawable> {
    val context = LocalContext.current
    return remember(context) {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        if (icon is AdaptiveIconDrawable) listOfNotNull(icon.background, icon.foreground) else listOfNotNull(icon)
    }
}

/** 把 logo 铺满当前区域，轮廓由外面的 clip 决定 */
fun Modifier.drawAppLogo(layers: List<Drawable>): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        layers.fastForEach { layer ->
            layer.bounds = logoLayerBounds(size)
            layer.draw(canvas.nativeCanvas)
        }
    }
}

// 自适应图标的图层是 108dp，可见的只有中间 72dp，四周各多出四分之一
private fun logoLayerBounds(size: Size): Rect {
    val insetX = (size.width / 4f).roundToInt()
    val insetY = (size.height / 4f).roundToInt()
    return Rect(-insetX, -insetY, size.width.roundToInt() + insetX, size.height.roundToInt() + insetY)
}

