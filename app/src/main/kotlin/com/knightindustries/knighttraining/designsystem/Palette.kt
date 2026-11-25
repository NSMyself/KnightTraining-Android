package com.knightindustries.knighttraining.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil

/**
 * The app's color system, light and dark (iOS `Shared/Palette.swift`, from the 2026-11-23 "Color ·
 * Dark" / "Color · Light" spec: the logo's navy, royal blue, cyan and cream). Token names match the
 * spec and iOS exactly.
 *
 * Call sites ask for a role (`Palette.textSecondary`), never a hex value; the theme picks the light
 * or dark set. Most of the app reaches these through the Material scheme [KnightTrainingTheme]
 * fills from them; `Palette` is for the roles Material has no slot for (success, rating,
 * highlight, the heatmap, the accent as a fill).
 *
 * - Text on an [accent] fill uses [onAccent]. Accent-colored text and icons use [accentText], tuned
 *   for contrast on [background]/[surface] in each mode; [accent] is a fill.
 * - Don't add a token outside the spec: raise it with design, then add it here and on iOS.
 */
@Immutable
data class PaletteColors(
    /** The screen background, behind everything. */
    val background: Color,
    /** Cards and sheets sitting on [background]. */
    val surface: Color,
    /** A surface on top of [surface]: a selected row, a nested card, a pressed state. */
    val surfaceRaised: Color,
    /** Hairlines, card outlines, dividers. */
    val border: Color,
    val textPrimary: Color,
    /** Subtitles, metadata, captions. */
    val textSecondary: Color,
    /** Placeholders, disabled labels, axis ticks. */
    val textTertiary: Color,
    /** Text and icons on an [accent] fill. */
    val onAccent: Color,
    /** The brand blue, for fills: buttons, selected chips, progress fills. */
    val accent: Color,
    /** The brand blue for text, icons and links on [background]/[surface]. */
    val accentText: Color,
    /** The one standout moment on a screen (a goal hit, a personal best). Use sparingly. */
    val highlight: Color,
    /** Completed goals, positive changes. */
    val success: Color,
    /** Ratings: star fills, Elo lines. */
    val rating: Color,
    /** Errors and destructive actions. */
    val error: Color,
    /** Six steps from no activity (0) to the most (5), ending on [highlight]. */
    val heatmap: List<Color>,
    val isDark: Boolean,
) {
    /** The heatmap step for a 0…1 intensity: anything above 0 gets at least the first colored step. */
    fun heatmapColor(intensity: Double): Color {
        val clamped = intensity.coerceIn(0.0, 1.0)
        if (clamped <= 0.0) return heatmap[0]
        val step = ceil(clamped * (heatmap.size - 1)).toInt()
        return heatmap[step.coerceIn(1, heatmap.size - 1)]
    }
}

val LightPalette = PaletteColors(
    background = Color(0xFFFAF6EC),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1EBDC),
    border = Color(0xFFE0D8C3),
    textPrimary = Color(0xFF00113B),
    textSecondary = Color(0xFF43527D),
    textTertiary = Color(0xFF5E6B8C),
    onAccent = Color(0xFFFFFFFF),
    accent = Color(0xFF0162E4),
    accentText = Color(0xFF0155C7),
    highlight = Color(0xFF007F8A),
    success = Color(0xFF0B7A54),
    rating = Color(0xFF9A6700),
    error = Color(0xFFC93A3A),
    heatmap = listOf(
        Color(0xFFF2F6FF), Color(0xFFE6F0FC), Color(0xFFB3D1F7), Color(0xFF80B1F3), Color(0xFF3481E9), Color(0xFF007F8A),
    ),
    isDark = false,
)

val DarkPalette = PaletteColors(
    background = Color(0xFF00113B),
    surface = Color(0xFF0A1F52),
    surfaceRaised = Color(0xFF12306F),
    border = Color(0xFF234C9E),
    textPrimary = Color(0xFFFEEECD),
    textSecondary = Color(0xFFA9B8DB),
    textTertiary = Color(0xFF8092BD),
    onAccent = Color(0xFFFFFFFF),
    accent = Color(0xFF0162E4),
    accentText = Color(0xFF4D9BFF),
    highlight = Color(0xFF02F8FF),
    success = Color(0xFF34D399),
    rating = Color(0xFFF5C451),
    error = Color(0xFFFF6B6B),
    heatmap = listOf(
        Color(0xFF0B215A), Color(0xFF092660), Color(0xFF07337C), Color(0xFF06419C), Color(0xFF0453C7), Color(0xFF02F8FF),
    ),
    isDark = true,
)

/** Provided by [KnightTrainingTheme]; dark outside it, the app's long-standing look. */
val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** The current [PaletteColors]: `Palette.accentText`, `Palette.success`, … */
object Palette {
    val current: PaletteColors
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val background: Color @Composable @ReadOnlyComposable get() = current.background
    val surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val surfaceRaised: Color @Composable @ReadOnlyComposable get() = current.surfaceRaised
    val border: Color @Composable @ReadOnlyComposable get() = current.border
    val textPrimary: Color @Composable @ReadOnlyComposable get() = current.textPrimary
    val textSecondary: Color @Composable @ReadOnlyComposable get() = current.textSecondary
    val textTertiary: Color @Composable @ReadOnlyComposable get() = current.textTertiary
    val onAccent: Color @Composable @ReadOnlyComposable get() = current.onAccent
    val accent: Color @Composable @ReadOnlyComposable get() = current.accent
    val accentText: Color @Composable @ReadOnlyComposable get() = current.accentText
    val highlight: Color @Composable @ReadOnlyComposable get() = current.highlight
    val success: Color @Composable @ReadOnlyComposable get() = current.success
    val rating: Color @Composable @ReadOnlyComposable get() = current.rating
    val error: Color @Composable @ReadOnlyComposable get() = current.error
}

/**
 * The app icon's glow colors (iOS `GlowColors`, 2026-11-24): decoration for the backgrounds, the
 * timer's blobs and the pause button's ring, not UI tokens, so they live outside [PaletteColors].
 */
object GlowColors {
    @Composable @ReadOnlyComposable
    fun blue(): Color = Palette.accent

    /** The icon's violet corner, pushed brighter and redder so it doesn't read as another blue. */
    @Composable @ReadOnlyComposable
    fun violet(): Color = if (Palette.current.isDark) Color(0xFFA66BFF) else Color(0xFF9F5FF5)

    /** [PaletteColors.highlight] in dark; a fresher cyan in light, where the highlight teal turns muddy as a wash. */
    @Composable @ReadOnlyComposable
    fun cyan(): Color = if (Palette.current.isDark) Color(0xFF02F8FF) else Color(0xFF1AB0CC)

    /** One of the timer's blobs (RGB 65, 188, 235), so they don't all read as one blue. */
    val sky = Color(0xFF41BCEB)
}
