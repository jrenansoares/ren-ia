package br.com.projetoia.app.ui.theme

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ProjetoThemePreset = PresetTheme(
    id = "projeto",
    name = { Text("Ren") },
    standardLight = lightColorScheme(
        primary = Color(0xFF007A59),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F5DF),
        onPrimaryContainer = Color(0xFF002D20),
        secondary = Color(0xFF466459),
        background = Color(0xFFF6FAF8),
        surface = Color(0xFFF6FAF8),
    ),
    standardDark = darkColorScheme(
        primary = Color(0xFF10B981), onPrimary = Color(0xFF002D20),
        primaryContainer = Color(0xFF103C30), onPrimaryContainer = Color(0xFFA7F3D0),
        primaryFixed = Color(0xFFA7F3D0), primaryFixedDim = Color(0xFF10B981),
        onPrimaryFixed = Color(0xFF002D20), onPrimaryFixedVariant = Color(0xFF064E3B),
        secondary = Color(0xFFA1A8B3), onSecondary = Color(0xFF171A20),
        secondaryContainer = Color(0xFF23272E), onSecondaryContainer = Color(0xFFE5E7EB),
        secondaryFixed = Color(0xFFE5E7EB), secondaryFixedDim = Color(0xFFA1A8B3),
        onSecondaryFixed = Color(0xFF111318), onSecondaryFixedVariant = Color(0xFF353B44),
        tertiary = Color(0xFF10B981), onTertiary = Color(0xFF002D20),
        tertiaryContainer = Color(0xFF103C30), onTertiaryContainer = Color(0xFFA7F3D0),
        tertiaryFixed = Color(0xFFA7F3D0), tertiaryFixedDim = Color(0xFF10B981),
        onTertiaryFixed = Color(0xFF002D20), onTertiaryFixedVariant = Color(0xFF064E3B),
        background = Color(0xFF0C0E11), surface = Color(0xFF0C0E11),
        onBackground = Color(0xFFF0F2F5), onSurface = Color(0xFFF0F2F5),
        surfaceContainerLowest = Color(0xFF090B0E), surfaceContainerLow = Color(0xFF14171C),
        surfaceContainer = Color(0xFF191D23), surfaceContainerHigh = Color(0xFF20252C),
        surfaceContainerHighest = Color(0xFF282E36), onSurfaceVariant = Color(0xFF9CA3AF),
        surfaceVariant = Color(0xFF191D23), outline = Color(0xFF6B7280), outlineVariant = Color(0xFF272C34),
    ),
)
