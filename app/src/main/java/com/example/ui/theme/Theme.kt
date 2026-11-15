package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TacticalDarkColorScheme = darkColorScheme(
    primary = AmberBeam,
    onPrimary = AmberOnPrimary,
    primaryContainer = AmberBeamMuted,
    onPrimaryContainer = AmberBeamBright,
    secondary = SkyInfo,
    onSecondary = ObsidianBackground,
    secondaryContainer = SkyContainer,
    onSecondaryContainer = SlateTextPrimary,
    tertiary = EmeraldActive,
    onTertiary = ObsidianBackground,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldActive,
    background = ObsidianBackground,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = CoralAlert,
    onError = ObsidianBackground,
    errorContainer = CoralContainer,
    onErrorContainer = CoralAlert,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DoubleTapFlashlightTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
