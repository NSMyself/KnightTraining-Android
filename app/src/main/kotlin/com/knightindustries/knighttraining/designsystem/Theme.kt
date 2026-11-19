package com.knightindustries.knighttraining.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Color tokens, mirroring iOS `KnightTraining/DesignSystem/Theme.swift`. */
object Theme {
    val background = Color(0xFF0D0217)
    val gradientBase = Color(0xFF151022)

    val accent = Color(0xFF8D59E9)
    val accentBorder = Color(0xFF834EFF)

    object Resource {
        val title = Color(0xFFEBECFE)
        val author = Color(0xFF9580F8)
        val description = Color(0xFFE1DCFD)
    }

    val ratingGold = Color(0xFFF5C451)
    val progressGradient = listOf(Color(0xFFF3F2F8), Color(0xFFA282FF))

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

    object MeshBlob {
        val indigo = Color(0xFF6366F1)
        val purple = Color(0xFF8B5CF6)
        val pink = Color(0xFFEC4899)
    }
}

/**
 * Dark-only, like iOS. The Material 3 scheme is filled from the brand tokens, including the
 * surface-container ladder, so stock components (cards, sheets, the navigation bar) read as the
 * app's purple-black rather than Material's neutral greys. The container ladder leans indigo, toward
 * iOS's cards (#6B6EF8 at 0.28 over the background), so cards read as lifted off the glow. Shapes, type and motion stay Material:
 * the app follows native Android UX (AGENTS.md).
 */
private val colors = darkColorScheme(
    primary = Theme.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2E85),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCDBEEB),
    onSecondary = Color(0xFF2E2347),
    secondaryContainer = Color(0xFF3A2C57),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Theme.ratingGold,
    background = Theme.background,
    onBackground = Color(0xFFEDE6F7),
    surface = Theme.background,
    onSurface = Color(0xFFEDE6F7),
    surfaceVariant = Color(0xFF2A2236),
    onSurfaceVariant = Color(0xFFCBC2DB),
    surfaceContainerLowest = Color(0xFF080110),
    surfaceContainerLow = Color(0xFF15102A),
    surfaceContainer = Color(0xFF1E1938),
    surfaceContainerHigh = Color(0xFF272145),
    surfaceContainerHighest = Color(0xFF2F2952),
    outline = Color(0xFF5A4E6D),
    outlineVariant = Color(0xFF3A3047),
)

@Composable
fun KnightTrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
