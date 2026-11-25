package com.knightindustries.knighttraining.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * What's left of the old purple palette (iOS `Theme.swift`): only the white overlays used as
 * surfaces, which iOS deliberately left out of the move to [Palette]. New colors go in [Palette].
 */
object Theme {
    /** A relative ordering; check the visual role at the call site before reusing one. */
    object Opacity {
        const val TEXT_FAINT = 0.35f
        const val TEXT_DIM = 0.4f
        const val TEXT_MUTED = 0.5f
        const val TEXT_SECONDARY = 0.6f
        const val TEXT_STRONG = 0.7f

        const val SURFACE_FAINT = 0.06f
        const val SURFACE_SUBTLE = 0.08f
        const val SURFACE_VISIBLE = 0.15f
    }
}

/**
 * The Material 3 scheme, filled from [Palette] so stock components (cards, sheets, the navigation
 * bar, buttons) are the app's navy or cream. Shapes, type and motion stay Material.
 *
 * **`primary` is iOS's `accentText`, not `accent`.** Material draws text buttons, links and tinted
 * icons in `primary`, and iOS's fill blue (`0162E4`) is 3.4:1 on navy, too faint for text. So
 * filled buttons are `accentText` too: in dark mode a lighter blue with navy text, Material's own
 * dark-theme convention, where iOS fills `accent` with white. The true fill blue stays available
 * as `primaryContainer` and `Palette.accent` (progress fills, rings, the selected-day label).
 */
fun colorScheme(p: PaletteColors): ColorScheme {
    // The container ladder steps from the background to the raised surface; cards sit on `surface`.
    val ladder = { amount: Float -> lerp(p.background, p.surfaceRaised, amount) }
    val onPrimary = if (p.isDark) p.background else p.onAccent
    val selected = p.accent.copy(alpha = if (p.isDark) 0.35f else 0.16f).compositeOver(p.surface)
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accentText,
        onPrimary = onPrimary,
        primaryContainer = p.accent,
        onPrimaryContainer = p.onAccent,
        inversePrimary = if (p.isDark) LightPalette.accentText else DarkPalette.accentText,
        secondary = p.accentText,
        onSecondary = onPrimary,
        secondaryContainer = selected,
        onSecondaryContainer = p.textPrimary,
        tertiary = p.rating,
        onTertiary = if (p.isDark) p.background else Color.White,
        tertiaryContainer = p.highlight.copy(alpha = 0.2f).compositeOver(p.surface),
        onTertiaryContainer = p.textPrimary,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.background,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.textSecondary,
        surfaceTint = p.accent,
        surfaceBright = p.surfaceRaised,
        surfaceDim = p.background,
        surfaceContainerLowest = if (p.isDark) p.background else p.surface,
        surfaceContainerLow = if (p.isDark) lerp(p.background, p.surface, 0.5f) else p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = if (p.isDark) lerp(p.surface, p.surfaceRaised, 0.5f) else ladder(0.5f),
        surfaceContainerHighest = p.surfaceRaised,
        inverseSurface = p.textPrimary,
        inverseOnSurface = p.background,
        outline = p.border,
        outlineVariant = lerp(p.border, p.background, 0.4f),
        error = p.error,
        onError = if (p.isDark) p.background else Color.White,
        errorContainer = p.error.copy(alpha = 0.2f).compositeOver(p.surface),
        onErrorContainer = p.textPrimary,
    )
}

/**
 * Follows the system's light/dark setting, like iOS since 2026-11-24. [darkTheme] pins a mode:
 * share cards render dark whatever the sender's setting.
 */
@Composable
fun KnightTrainingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme(palette), content = content)
    }
}
