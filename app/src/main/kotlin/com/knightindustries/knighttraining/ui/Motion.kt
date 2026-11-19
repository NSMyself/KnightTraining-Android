package com.knightindustries.knighttraining.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Small motion pieces shared across screens, so a value that changes moves the same way
 * everywhere (iOS uses `.contentTransition(.numericText())` and animated progress throughout).
 * All of them are Compose animations, so the system's "Remove animations" setting snaps them.
 */

/** A progress bar that moves to a new value instead of jumping there. */
@Composable
fun AnimatedLinearProgress(progress: Float, modifier: Modifier = Modifier, color: Color = ProgressIndicatorDefaults.linearColor) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), ProgressIndicatorDefaults.ProgressAnimationSpec, label = "progress")
    LinearProgressIndicator(progress = { shown }, modifier = modifier, color = color)
}

/** A value (a count, a duration, a percentage) that rolls up to its new text when it changes. */
@Composable
fun AnimatedValueText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    AnimatedContent(
        text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
        },
        label = "value",
    ) { value ->
        Text(value, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A screen title that eases in the first time its screen appears (iOS's Home greeting), not again
 * on returning to it.
 */
@Composable
fun RevealedTitle(text: String) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = androidx.compose.animation.core.EaseOutCubic))
        revealed = true
    }
    Text(
        text,
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1 - progress.value) * 12.dp.toPx()
        },
    )
}
