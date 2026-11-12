package com.knightindustries.knighttraining.feature.share

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.LibraryShareSummary
import com.knightindustries.knighttraining.core.model.WeekShareSummary
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.libraryShareSummary
import com.knightindustries.knighttraining.core.store.weekShareSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Share my week". Notes and reflection are on by default: they're what a coach most wants. */
@Composable
fun ShareWeekSheet(plan: WeeklyPlan, onDismiss: () -> Unit) {
    val container = AppContainer.from(LocalContext.current.applicationContext as Application)
    var summary by remember { mutableStateOf<WeekShareSummary?>(null) }
    var failed by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(true) }
    var reflection by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(plan.id) {
        val loaded = runCatching { container.store.weekShareSummary(plan, container.preferences.firstLaunch()) }
        // State is written on the main thread whatever dispatcher the load resumed on.
        withContext(Dispatchers.Main) {
            summary = loaded.getOrNull()
            failed = loaded.isFailure
        }
    }
    val current = summary
    ShareCardSheet(
        title = stringResource(R.string.share_week_title),
        body = stringResource(R.string.share_week_body),
        toggles = buildList {
            if (current?.notes?.isNotEmpty() == true) add(ShareToggle(stringResource(R.string.share_include_notes), notes) { notes = it })
            if (current?.review != null) add(ShareToggle(stringResource(R.string.share_include_reflection), reflection) { reflection = it })
        },
        failed = failed,
        onDismiss = onDismiss,
        card = current?.let { { WeekShareCard(it, notes, reflection) } },
    )
}

@Composable
fun ShareLibrarySheet(onDismiss: () -> Unit) {
    val container = AppContainer.from(LocalContext.current.applicationContext as Application)
    var summary by remember { mutableStateOf<LibraryShareSummary?>(null) }
    var failed by remember { mutableStateOf(false) }
    var upNext by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val loaded = runCatching { container.store.libraryShareSummary() }
        withContext(Dispatchers.Main) {
            summary = loaded.getOrNull()
            failed = loaded.isFailure
        }
    }
    val current = summary
    ShareCardSheet(
        title = stringResource(R.string.share_library_title),
        body = stringResource(R.string.share_library_body),
        toggles = if (current?.notStarted?.isNotEmpty() == true) {
            listOf(ShareToggle(stringResource(R.string.share_include_up_next), upNext) { upNext = it })
        } else {
            emptyList()
        },
        note = if (current?.hasUnstartedStudies == true) stringResource(R.string.share_studies_note) else null,
        failed = failed,
        onDismiss = onDismiss,
        card = current?.let { { LibraryShareCard(it, upNext) } },
    )
}
