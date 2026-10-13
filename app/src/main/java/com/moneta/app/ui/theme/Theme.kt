package com.moneta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * ARCHITECTURE.md Section 29: dark mode is a true separate palette with its
 * own elevated surfaces, not simply an inverted light theme.
 */
private val MonetaLightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Neutral99,
    background = Neutral99,
    onBackground = Neutral0,
    surface = Neutral99,
    onSurface = Neutral0,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral0,
    outline = Neutral80,
    error = ExpenseRed,
    onError = Neutral99
)

private val MonetaDarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Neutral0,
    background = Neutral0,
    onBackground = Neutral99,
    surface = Neutral10,
    onSurface = Neutral99,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    outline = Neutral30,
    error = ExpenseRed,
    onError = Neutral0
)

@Composable
fun MonetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonetaDarkColors else MonetaLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonetaTypography,
        shapes = MonetaShapes,
        content = content
    )
}
