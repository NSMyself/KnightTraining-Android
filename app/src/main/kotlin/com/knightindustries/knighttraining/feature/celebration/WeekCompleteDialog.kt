package com.knightindustries.knighttraining.feature.celebration

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.store.WeeklyPlanCompleted
import com.knightindustries.knighttraining.designsystem.Theme
import com.knightindustries.knighttraining.feedback.WeekCompleteSequence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fires once, when a week's last committed goal completes (`WeeklyPlanCompleted`), from wherever
 * it was logged (iOS `WeeklyPlanCelebrationScreen`). A full-screen dialog over the app: the
 * week-complete sound and its taps, confetti that lands on the mating move, and the trophy
 * springing in. Back or Continue closes it.
 */
@Composable
fun WeekCompleteDialog(event: WeeklyPlanCompleted, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // A brand color isn't in the scheme, so the content color is named, or a dialog's window
        // falls back to black text.
        Surface(Modifier.fillMaxSize(), color = Theme.gradientBase, contentColor = MaterialTheme.colorScheme.onSurface) {
            val context = LocalContext.current
            var showsConfetti by remember { mutableStateOf(false) }
            val trophy = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                launch { trophy.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                val feedback = AppContainer.from(context.applicationContext as Application).feedback
                // Held for the mate when the sound or taps play, so the burst lands with the payoff.
                if (feedback.playWeekComplete()) delay(WeekCompleteSequence.MATE_MILLIS)
                // Decoration: skipped under battery saver (iOS's Low Power Mode) or with animations off.
                val powerSave = context.getSystemService(PowerManager::class.java).isPowerSaveMode
                val animationsOff = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                showsConfetti = !powerSave && !animationsOff
            }

            Box {
                if (showsConfetti) Confetti()
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = Theme.ratingGold,
                        modifier = Modifier.size(80.dp).graphicsLayer {
                            scaleX = trophy.value
                            scaleY = trophy.value
                        },
                    )
                    Text(
                        stringResource(R.string.week_complete_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        pluralStringResource(R.plurals.week_complete_body, event.goalCount, event.goalCount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // A streak is never shown below 2 (iOS AGENTS.md §7).
                    if (event.streakLength >= 2) {
                        Text(stringResource(R.string.week_streak_label, event.streakLength), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.continue_action)) }
                }
            }
        }
    }
}
