package com.knightindustries.knighttraining.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.feature.plan.ActivityGrid
import com.knightindustries.knighttraining.feature.plan.TimeControlPicker

/**
 * "Something else": a category outside the plan, games with a time control (iOS asks for it
 * alongside the pick), so the segment can be matched to a goal of the right category.
 */
@Composable
fun AdHocPicker(onPick: (PracticeSession.Attribution) -> Unit) {
    var playing by rememberSaveable { mutableStateOf(false) }
    var timeControl by rememberSaveable { mutableStateOf<String?>(null) }

    if (!playing) {
        ActivityGrid { code ->
            if (code == ActivityType.PLAYING) playing = true else onPick(PracticeSession.adHoc(code, null))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeControlPicker(timeControl) { timeControl = it }
        Button(onClick = { onPick(PracticeSession.adHoc(ActivityType.PLAYING, timeControl)) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_action))
        }
    }
}
