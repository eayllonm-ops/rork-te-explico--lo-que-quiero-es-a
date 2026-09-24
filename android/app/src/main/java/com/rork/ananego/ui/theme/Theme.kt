package com.rork.ananego.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JungleColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = JungleDeep,
    primaryContainer = GoldDeep,
    onPrimaryContainer = JungleDeep,
    secondary = GoldDeep,
    onSecondary = JungleDeep,
    secondaryContainer = JungleSurfaceHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    onTertiary = JungleDeep,
    background = JungleCanvas,
    onBackground = TextPrimary,
    surface = JungleSurface,
    onSurface = TextPrimary,
    surfaceVariant = JungleSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = JungleSurface,
    surfaceContainerHigh = JungleSurfaceHigh,
    surfaceContainerLow = JungleDeep,
    outline = JungleOutline,
    outlineVariant = JungleOutline,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JungleColorScheme,
        typography = AppTypography,
        content = content
    )
}
