package com.kevinbevan.rivals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme. The palette, type, spacing and shapes all come from `design/brief.md` and are
 * reached through [Rivals]; the Material 3 scheme below only exists so that a stock component
 * that slips through (a dialog, a dropdown) lands on the same colours.
 */
@Composable
fun RivalsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkRivalsColors else LightRivalsColors
    val type = RivalsTypography()
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.fg,
            onPrimary = colors.onFg,
            secondary = colors.you,
            onSecondary = colors.base,
            tertiary = colors.rival,
            onTertiary = colors.base,
            error = colors.live,
            onError = colors.fg,
            background = colors.base,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.fg2,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.raised,
            surfaceContainerHighest = colors.raised,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.base,
            outline = colors.hairline,
            outlineVariant = colors.hairline,
            inverseSurface = colors.fg,
            inverseOnSurface = colors.base,
        )
    } else {
        lightColorScheme(
            primary = colors.fg,
            onPrimary = colors.onFg,
            secondary = colors.you,
            onSecondary = colors.surface,
            tertiary = colors.rival,
            onTertiary = colors.surface,
            error = colors.live,
            onError = colors.surface,
            background = colors.base,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.fg2,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.raised,
            surfaceContainerHighest = colors.raised,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.base,
            outline = colors.hairline,
            outlineVariant = colors.hairline,
            inverseSurface = colors.fg,
            inverseOnSurface = colors.base,
        )
    }
    CompositionLocalProvider(
        LocalRivalsColors provides colors,
        LocalRivalsTypography provides type,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = materialTypography(type),
            content = content,
        )
    }
}

/** How a screen reaches the brief's tokens: `Rivals.colors.you`, `Rivals.type.label`. */
object Rivals {
    val colors: RivalsColors
        @Composable @ReadOnlyComposable get() = LocalRivalsColors.current

    val type: RivalsTypography
        @Composable @ReadOnlyComposable get() = LocalRivalsTypography.current
}
