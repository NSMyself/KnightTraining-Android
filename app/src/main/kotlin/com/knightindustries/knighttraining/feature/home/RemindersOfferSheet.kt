package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R

/**
 * "Want a nudge?" (iOS `EnableRemindersSheet`), once, after a week is committed: reminders come
 * only on days without training while the plan has work left. Remind me asks for notification
 * permission and turns them on; Not now, or dismissing, declines. Either way it isn't asked again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersOfferSheet(onRemind: () -> Unit, onDecline: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDecline, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text(stringResource(R.string.reminder_offer_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.reminder_offer_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRemind, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.remind_me)) }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_tip_not_now)) }
            Text(stringResource(R.string.reminder_offer_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
