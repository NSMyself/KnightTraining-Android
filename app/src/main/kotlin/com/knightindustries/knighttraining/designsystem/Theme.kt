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
 * Material 3 is only the plumbing here: the scheme is filled from the tokens above so stock
 * components don't fall back to Material's own palette. Dark-only, like iOS.
 */
@Composable
fun KnightTrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Theme.accent,
            onPrimary = Color.White,
            background = Theme.background,
            onBackground = Color.White,
            surface = Theme.background,
            onSurface = Color.White,
        ),
        content = content,
    )
}
