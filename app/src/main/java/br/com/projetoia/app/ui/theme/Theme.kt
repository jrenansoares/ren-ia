package br.com.projetoia.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import br.com.projetoia.app.ui.hooks.rememberCurrentColorMode
import br.com.projetoia.app.utils.getActivity

private val RenBlack = Color(0xFF000000)
private val RenNeon = Color(0xFF00FF00)
private val RenBone = Color(0xFFF3F3F3)
private val RenColorScheme = darkColorScheme(
    primary = RenNeon,
    onPrimary = RenBlack,
    primaryContainer = Color(0xFF003300),
    onPrimaryContainer = RenNeon,
    inversePrimary = Color(0xFF00D900),
    secondary = RenNeon,
    onSecondary = RenBlack,
    secondaryContainer = Color(0xFF101810),
    onSecondaryContainer = RenBone,
    tertiary = RenNeon,
    onTertiary = RenBlack,
    tertiaryContainer = Color(0xFF003300),
    onTertiaryContainer = RenNeon,
    background = RenBlack,
    onBackground = RenBone,
    surface = RenBlack,
    onSurface = RenBone,
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFB8B8B8),
    surfaceTint = RenNeon,
    inverseSurface = RenBone,
    inverseOnSurface = RenBlack,
    error = Color(0xFFFF5252),
    onError = RenBlack,
    errorContainer = Color(0xFF3A0000),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFF303030),
    scrim = RenBlack,
    surfaceBright = Color(0xFF181818),
    surfaceDim = RenBlack,
    surfaceContainerLowest = RenBlack,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0B0B0B),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF181818),
)

private val RenRectangle = CutCornerShape(0.dp)
private val RenShapes = Shapes(
    extraSmall = RenRectangle,
    small = RenRectangle,
    medium = RenRectangle,
    large = RenRectangle,
    extraLarge = RenRectangle,
    largeIncreased = RenRectangle,
    extraLargeIncreased = RenRectangle,
    extraExtraLarge = RenRectangle,
)

private val RenExtendColors = darkExtendColors().copy(
    green1 = Color(0xFF001A00),
    green2 = Color(0xFF003300),
    green3 = Color(0xFF006600),
    green4 = Color(0xFF009900),
    green5 = Color(0xFF00CC00),
    green6 = RenNeon,
    green7 = Color(0xFF00E600),
    green8 = Color(0xFF00B800),
    green9 = Color(0xFF008F00),
    green10 = Color(0xFF006600),
)

private object RenMotionScheme : MotionScheme {
    override fun <T : Any?> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T : Any?> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T : Any?> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T : Any?> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T : Any?> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T : Any?> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
}

val LocalExtendColors = compositionLocalOf { RenExtendColors }
val LocalDarkMode = compositionLocalOf { true }

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun ProjetoIATheme(
    @Suppress("UNUSED_PARAMETER") colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val activity = view.context.getActivity()

    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides true,
        LocalExtendColors provides RenExtendColors,
        LocalOverscrollFactory provides null,
        LocalTonalElevationEnabled provides false,
        LocalRippleConfiguration provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = RenColorScheme,
            typography = Typography,
            shapes = RenShapes,
            motionScheme = RenMotionScheme,
            content = content,
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
