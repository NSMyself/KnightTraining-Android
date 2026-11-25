package com.knightindustries.knighttraining.feature.session

import com.knightindustries.knighttraining.designsystem.Palette
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.designsystem.AnimatedMeshBackground
import com.knightindustries.knighttraining.designsystem.GradientBackground
import java.time.Instant

/**
 * The timer's background and the rules for it (iOS `StudyingView`). The drifting mesh is
 * atmosphere for the *start* of a session: nobody watches it at minute twenty, and it's the most
 * expensive thing the app draws, while the app encourages long sessions. So it runs for the first
 * [BUDGET_SECONDS] of running time, never while paused, and never under battery saver or with
 * system animations off. Tapping the notice brings it back for the rest of the session.
 */
class TimerBackgroundState(
    /** Battery saver or animations off: the system's call, so no notice and no override. */
    val meshAllowed: Boolean,
    val showsMesh: Boolean,
    /** The mesh is off *because of the budget*, the only case the notice explains. */
    val dimmedToSaveBattery: Boolean,
    val keepMesh: () -> Unit,
)

const val BUDGET_SECONDS = 10 * 60

@Composable
fun rememberTimerBackground(session: PracticeSession, now: Instant): TimerBackgroundState {
    val powerSave = rememberPowerSaveMode()
    val animationsOff = rememberAnimationsOff()
    var keepsMesh by rememberSaveable { mutableStateOf(false) }
    val running = session.phase == PracticeSession.Phase.Running && !session.isPaused
    val meshAllowed = !powerSave && !animationsOff
    val showsMesh = running && meshAllowed && (keepsMesh || session.elapsedSeconds(now) < BUDGET_SECONDS)
    return TimerBackgroundState(
        meshAllowed = meshAllowed,
        showsMesh = showsMesh,
        dimmedToSaveBattery = running && meshAllowed && !showsMesh,
        keepMesh = { keepsMesh = true },
    )
}

/**
 * The brand glow while the timer runs, with the mesh over it; flat once stopped, where the motion
 * would distract from reading and typing the review. The glow is always the base: the mesh freezes
 * and fades to reveal it, rather than the two cross-dissolving.
 */
@Composable
fun TimerBackground(running: Boolean, state: TimerBackgroundState) {
    val glowAlpha by animateFloatAsState(if (running) 1f else 0f, tween(600), label = "glow")
    // Dimming on its own happens over 20 s, so nothing appears to switch mid-concentration; a
    // response to a tap (pausing, the notice) takes 0.6 s, or it would read as broken.
    val meshAlpha by animateFloatAsState(
        if (state.showsMesh) 1f else 0f,
        tween(if (state.dimmedToSaveBattery) 20_000 else 600, easing = EaseInOut),
        label = "mesh",
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (glowAlpha > 0f) {
            GradientBackground(Modifier.graphicsLayer { alpha = glowAlpha })
            if (state.meshAllowed) AnimatedMeshBackground(isAnimating = state.showsMesh, alpha = { meshAlpha * glowAlpha })
        }
    }
}

/** Says why the background went flat, which would otherwise read as something breaking, and undoes it. */
@Composable
fun DimmedNotice(state: TimerBackgroundState) {
    // Drifts in alongside the dimming, but far short of its 20 s, or it would sit unreadable.
    AnimatedVisibility(state.dimmedToSaveBattery, enter = fadeIn(tween(3_000)), exit = fadeOut(tween(600))) {
        TextButton(onClick = state.keepMesh, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Palette.textTertiary)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Battery5Bar, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.mesh_dimmed), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A halo in the background color: a dark shadow on navy, a soft cream glow on cream, so the clock lifts off the blobs. */
@Composable
fun clockShadow(): Shadow {
    val blur = with(LocalDensity.current) { 20.dp.toPx() }
    val color = Palette.background.copy(alpha = 0.7f)
    return remember(blur, color) { Shadow(color, blurRadius = blur) }
}

@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                on = power.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

/** Developer options' or Accessibility's "Remove animations": the mesh is pure motion, so it goes. */
@Composable
private fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    var off by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        off = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        onPauseOrDispose { }
    }
    return off
}
