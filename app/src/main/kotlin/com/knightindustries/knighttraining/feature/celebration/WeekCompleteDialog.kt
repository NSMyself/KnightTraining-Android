package com.knightindustries.knighttraining.feature.celebration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.store.WeeklyPlanCompleted

/**
 * Fires once, when a week's last committed goal completes (`WeeklyPlanCompleted`). A quieter
 * moment than iOS's confetti screen: a dialog, as Android apps mark an achievement.
 */
@Composable
fun WeekCompleteDialog(event: WeeklyPlanCompleted, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp)) },
        title = { Text(stringResource(R.string.week_complete_title), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.week_complete_body, event.goalCount, event.goalCount), textAlign = TextAlign.Center)
                // A streak is never shown below 2 (iOS AGENTS.md §7).
                if (event.streakLength >= 2) {
                    Text(stringResource(R.string.week_streak_label, event.streakLength), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
