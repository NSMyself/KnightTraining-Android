package com.knightindustries.knighttraining.feature.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.designsystem.GlowColors
import com.knightindustries.knighttraining.designsystem.Palette

/**
 * The timer's three controls (iOS `StudyingControlBar`): pause/play is the large one in the middle,
 * ringed in the icon's glow colors; attach-a-resource and stop sit either side, smaller and outlined.
 * Stop is always there, not hidden behind a pause.
 */
@Composable
fun TimerControls(isPaused: Boolean, hasResource: Boolean, onTogglePause: () -> Unit, onStop: () -> Unit, onAttach: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        SideButton(
            Icons.AutoMirrored.Rounded.PlaylistAdd,
            stringResource(R.string.link_resource),
            onAttach,
            tint = if (hasResource) MaterialTheme.colorScheme.primary else Palette.textSecondary,
        )
        Surface(
            onClick = onTogglePause,
            shape = CircleShape,
            color = Palette.surfaceRaised.copy(alpha = 0.6f),
            contentColor = Palette.textPrimary,
            border = BorderStroke(2.dp, Brush.linearGradient(listOf(GlowColors.blue(), GlowColors.violet(), GlowColors.cyan()))),
            modifier = Modifier.size(80.dp).semantics { role = Role.Button },
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(isPaused, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) }, label = "pause") { paused ->
                    Icon(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        SideButton(Icons.Rounded.Stop, stringResource(R.string.stop), onStop, tint = Palette.textSecondary)
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color) {
    OutlinedIconButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Palette.border),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = tint),
        modifier = Modifier.size(52.dp),
    ) { Icon(icon, contentDescription = label) }
}

/**
 * What's being studied, pinned to the bottom of the timer (iOS `StudySessionCard`): a progress bar
 * along its top edge, the title, and Change. Reaching a duration target is a passive cue only, the
 * bar turning green and a check fading in: never a popup or a buzz while the timer runs.
 */
@Composable
fun StudyCard(icon: ImageVector, title: String, subtitle: String?, progress: Float?, reachedTarget: Boolean, onChange: () -> Unit) {
    val tint by animateColorAsState(if (reachedTarget) Palette.success else Palette.accent, tween(400), label = "tint")
    val shownProgress by animateFloatAsState(progress ?: 0f, tween(400), label = "progress")
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // iOS tints its glass with the surface color, so it matches the screen in either mode.
        color = Palette.surface.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The card runs under the navigation bar; its content stays above it.
        Column(Modifier.navigationBarsPadding()) {
            if (progress != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Palette.border)) {
                    Box(Modifier.fillMaxWidth(shownProgress.coerceIn(0f, 1f)).height(4.dp).background(tint))
                }
            }
            Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        AnimatedVisibility(reachedTarget, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.target_reached), tint = tint, modifier = Modifier.size(18.dp))
                        }
                    }
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                // "Change" switches to something else; a pencil would read as editing this one.
                FilledTonalButton(onClick = onChange, modifier = Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.change)) }
            }
        }
    }
}
